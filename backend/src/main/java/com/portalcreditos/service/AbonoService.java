package com.portalcreditos.service;

import com.portalcreditos.dto.abono.AbonoDtos.AbonoRequest;
import com.portalcreditos.dto.abono.AbonoDtos.CalculoFecha;
import com.portalcreditos.dto.abono.AbonoDtos.CreditoHistorial;
import com.portalcreditos.dto.abono.AbonoDtos.CreditoParaAbono;
import com.portalcreditos.dto.abono.AbonoDtos.DeudorAbonos;
import com.portalcreditos.dto.abono.AbonoDtos.Historial;
import com.portalcreditos.dto.abono.AbonoDtos.LineaAbono;
import com.portalcreditos.dto.abono.AbonoDtos.PersonaHistorial;
import com.portalcreditos.dto.abono.AbonoDtos.VistaPreviaAbono;
import com.portalcreditos.dto.pago.PagoDtos.Comprobante;
import com.portalcreditos.entity.AplicacionPago;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Codeudor;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.Pago;
import com.portalcreditos.entity.enums.CanalPago;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.entity.enums.EstadoPago;
import com.portalcreditos.entity.enums.TipoAbono;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.ClienteRepository;
import com.portalcreditos.repository.CodeudorRepository;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.PagoRepository;
import com.portalcreditos.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portalcreditos.util.Sucursales;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Abonos individuales" e "Historial de creditos" del administrador (inspirados en el flujo de Sadmit,
 * pero SIN conexion con Sadmit: todo ocurre en la base de datos de este prototipo).
 *
 * Los abonos se registran como pagos del canal ADMIN (efectivo, transferencia o consignacion recibidos
 * en la oficina). No se mueve dinero real.
 */
@Service
public class AbonoService {

    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ClienteRepository clientes;
    private final CodeudorRepository codeudores;
    private final CreditoRepository creditos;
    private final PagoRepository pagos;
    private final UsuarioRepository usuarios;
    private final CreditoConsultaService consulta;
    private final SituacionCreditoService situacion;
    private final CalculadoraMora mora;
    private final DistribuidorPagos distribuidor;
    private final MotorAbonos motor;
    private final PagoService pagoService;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    public AbonoService(ClienteRepository clientes, CodeudorRepository codeudores, CreditoRepository creditos,
                        PagoRepository pagos, UsuarioRepository usuarios, CreditoConsultaService consulta,
                        SituacionCreditoService situacion, CalculadoraMora mora, DistribuidorPagos distribuidor,
                        MotorAbonos motor, PagoService pagoService, AuditoriaService auditoria, Clock reloj) {
        this.clientes = clientes;
        this.codeudores = codeudores;
        this.creditos = creditos;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.consulta = consulta;
        this.situacion = situacion;
        this.mora = mora;
        this.distribuidor = distribuidor;
        this.motor = motor;
        this.pagoService = pagoService;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // ================================================================ abonos individuales

    /** Busca el deudor por identificacion y trae sus creditos activos con el valor normal y total a pagar HOY. */
    @Transactional(readOnly = true)
    public DeudorAbonos deudor(String documento) {
        Cliente c = clientes.findByNumeroDocumento(documento.trim())
                .orElseThrow(() -> NegocioException.noEncontrado("No hay ningún cliente con la identificación " + documento.trim()));
        LocalDate hoy = LocalDate.now(reloj);
        List<CreditoParaAbono> lista = new ArrayList<>();
        for (Credito cr : creditos.findByClienteIdOrderByFechaDesembolsoDesc(c.getId())) {
            if (cr.getEstado() == EstadoCredito.CANCELADO) {
                continue;
            }
            var r = consulta.resumenDe(cr);
            BigDecimal normal = r.valorVencido().add(r.valorMora())
                    .add(r.proximaCuota() == null ? BigDecimal.ZERO : r.proximaCuota().valor());
            BigDecimal total = liquidacion(cr, hoy).total();
            lista.add(new CreditoParaAbono(r, normal.setScale(2, RoundingMode.HALF_UP), total,
                    cr.getSeguroVidaCuota(), cr.getFogadeCuota(), cr.getSucursal()));
        }
        return new DeudorAbonos(c.getId(), c.getNumeroDocumento(), c.getNombreCompleto(), c.getTelefono(), hoy, lista);
    }

    /** Muestra como se distribuiria el abono sin guardar nada (trabaja sobre una copia de las cuotas). */
    @Transactional(readOnly = true)
    public VistaPreviaAbono vistaPrevia(AbonoRequest r) {
        Credito cr = creditos.findById(r.creditoId())
                .orElseThrow(() -> NegocioException.noEncontrado("El crédito no existe"));
        LocalDate hoy = LocalDate.now(reloj);
        List<Cuota> copia = new ArrayList<>(cr.getCuotas().stream().map(AbonoService::copiar).toList());
        return previa(cr, copia, r, hoy, valorDelAbono(cr, r, hoy));
    }

    /** Registra el abono: aplica mora, cuotas y, segun el tipo, reliquida el plan o cancela el credito. */
    @Transactional
    public Comprobante registrar(AbonoRequest r, String admin, String ip) {
        Credito cr = creditos.bloquearPorId(r.creditoId())
                .orElseThrow(() -> NegocioException.noEncontrado("El crédito no existe"));
        if (cr.getEstado() == EstadoCredito.CANCELADO) {
            throw NegocioException.solicitudInvalida("El crédito ya está cancelado.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        BigDecimal valor = valorDelAbono(cr, r, hoy);
        List<Cuota> cuotas = cr.getCuotas();
        BigDecimal saldoAnterior = cr.getSaldoCapital();
        BigDecimal moraPendiente = mora.moraPendiente(cr, cuotas, hoy);
        int dias = mora.diasPorCobrar(cr, cuotas, hoy);

        // La mora se registra ANTES de tocar las cuotas (igual que en los pagos del portal).
        mora.registrarPago(cr, cuotas, valor.min(moraPendiente), hoy);
        MotorAbonos.Resultado res = motor.aplicar(r.tipoAbono(), valor, cuotas, cr.getTasaInteresMensual(),
                cr.getFechaDesembolso(), moraPendiente, dias, mora.valorDia(), hoy);
        cuotas.removeAll(res.eliminadas());

        Pago p = new Pago();
        p.setCredito(cr);
        p.setReferencia(nuevaReferencia(ahora));
        p.setFechaPago(ahora);
        p.setValor(valor);
        p.setValorMora(res.aMora());
        p.setValorAbonoCapital(r.tipoAbono() == TipoAbono.CAPITAL_PLAZO || r.tipoAbono() == TipoAbono.CAPITAL_CUOTA
                ? res.aCapital() : BigDecimal.ZERO.setScale(2));
        p.setTipoAbono(r.tipoAbono());
        try {
            p.setSucursal(r.sucursal() == null || r.sucursal().isBlank() ? cr.getSucursal() : Sucursales.normalizar(r.sucursal()));
        } catch (IllegalArgumentException e) {
            throw NegocioException.solicitudInvalida(e.getMessage());
        }
        p.setMedioPago(r.medioPago());
        p.setCanal(CanalPago.ADMIN);
        p.setEstado(EstadoPago.APROBADO);
        p.setRegistradoPor(usuarios.findByUsername(admin).orElse(null));
        String concepto = r.concepto() == null || r.concepto().isBlank() ? null : r.concepto().trim();
        p.setObservacion(textoTipo(r.tipoAbono()) + (concepto == null ? "" : " - " + concepto));
        for (DistribuidorPagos.Aplicacion d : res.aplicaciones()) {
            AplicacionPago ap = new AplicacionPago();
            ap.setPago(p);
            ap.setCuota(d.cuota());
            ap.setValorAplicado(d.valorAplicado());
            p.getAplicaciones().add(ap);
        }
        actualizarCredito(cr, hoy, res);
        pagos.save(p);
        auditoria.registrar(admin, "ABONO_" + r.tipoAbono().name(), "pago", p.getReferencia(),
                "Credito " + cr.getNumeroCredito() + " - sucursal " + Sucursales.etiqueta(p.getSucursal()) + " - " + textoTipo(r.tipoAbono()) + " - valor $" + valor.toPlainString()
                        + " - saldo capital $" + saldoAnterior.toPlainString() + " -> $" + cr.getSaldoCapital().toPlainString(), ip);
        pagos.flush();
        return pagoService.comprobante(cr.getId(), p.getId());
    }

    // ================================================================ historial de creditos

    /** Historial por identificacion: creditos como deudor y como codeudor. */
    @Transactional(readOnly = true)
    public Historial historial(String documento) {
        String doc = documento.trim();
        Optional<Cliente> cliente = clientes.findByNumeroDocumento(doc);
        Optional<Codeudor> codeudor = codeudores.findByNumeroDocumento(doc);
        if (cliente.isEmpty() && codeudor.isEmpty()) {
            throw NegocioException.noEncontrado("No hay ningún deudor ni codeudor con la identificación " + doc);
        }
        PersonaHistorial persona = cliente.map(c -> new PersonaHistorial(c.getNumeroDocumento(), c.getNombreCompleto(),
                        c.getTelefono(), c.getCorreo(), c.getDireccion(), c.getCiudad(), true, codeudor.isPresent()))
                .orElseGet(() -> {
                    Codeudor cd = codeudor.get();
                    return new PersonaHistorial(cd.getNumeroDocumento(), cd.getNombreCompleto(), cd.getTelefono(),
                            cd.getCorreo(), cd.getDireccion(), cd.getCiudad(), false, true);
                });
        List<CreditoHistorial> deudor = cliente.map(c -> creditos.findByClienteIdOrderByFechaDesembolsoDesc(c.getId())
                .stream().map(this::historialDe).toList()).orElse(List.of());
        List<CreditoHistorial> comoCodeudor = creditos.findByCodeudorNumeroDocumentoOrderByFechaDesembolsoDesc(doc)
                .stream().map(this::historialDe).toList();
        return new Historial(persona, LocalDate.now(reloj), deudor, comoCodeudor);
    }

    /**
     * Calcula lo que se debe en una fecha (de hoy en adelante):
     * - corte: mora + cuotas vencidas o que vencen hasta esa fecha;
     * - total: valor para cancelar todo el credito en esa fecha.
     */
    @Transactional(readOnly = true)
    public CalculoFecha calcular(Long creditoId, LocalDate fecha, boolean total) {
        Credito cr = creditos.findById(creditoId)
                .orElseThrow(() -> NegocioException.noEncontrado("El crédito no existe"));
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate f = fecha == null ? hoy : fecha;
        if (f.isBefore(hoy)) {
            throw NegocioException.solicitudInvalida("La fecha de corte debe ser de hoy en adelante.");
        }
        if (cr.getEstado() == EstadoCredito.CANCELADO) {
            BigDecimal cero = BigDecimal.ZERO.setScale(2);
            return new CalculoFecha(f, total, cero, cero, cero, cero, cero, 0, 0, cero, "El crédito está cancelado: no debe nada.");
        }
        List<Cuota> cuotas = cr.getCuotas();
        BigDecimal valorMora = mora.moraPendiente(cr, cuotas, f);
        int dias = mora.diasPorCobrar(cr, cuotas, f);
        MotorAbonos.Liquidacion l = total
                ? motor.liquidacion(cuotas, cr.getFechaDesembolso(), valorMora, dias, f)
                : motor.alCorte(cuotas, valorMora, dias, f);
        String explicacion = total
                ? "Valor para cancelar el crédito el " + f + ": capital pendiente, interés y seguros causados ("
                  + l.diasCausados() + " días de la cuota en curso), lo vencido y la mora."
                : "Valor al corte del " + f + ": cuotas vencidas o que vencen hasta esa fecha, más la mora ($"
                  + mora.valorDia().setScale(0, RoundingMode.HALF_UP).toPlainString() + " por día).";
        return new CalculoFecha(f, total, l.capital(), l.interes(), l.seguroVida(), l.fogade(), l.mora(), l.diasMora(),
                l.diasCausados(), l.total(), explicacion);
    }

    // ================================================================ apoyo

    private VistaPreviaAbono previa(Credito cr, List<Cuota> cuotas, AbonoRequest r, LocalDate hoy, BigDecimal valor) {
        BigDecimal saldoAntes = distribuidor.saldoCapital(cr.getValorFinanciado(), cuotas);
        int pendientesAntes = (int) cuotas.stream().filter(q -> q.getEstado() != EstadoCuota.PAGADA).count();
        BigDecimal cuotaAntes = cr.getValorCuota();
        BigDecimal moraPendiente = mora.moraPendiente(cr, cuotas, hoy);
        int dias = mora.diasPorCobrar(cr, cuotas, hoy);
        MotorAbonos.Resultado res = motor.aplicar(r.tipoAbono(), valor, cuotas, cr.getTasaInteresMensual(),
                cr.getFechaDesembolso(), moraPendiente, dias, mora.valorDia(), hoy);
        cuotas.removeAll(res.eliminadas());
        BigDecimal saldoDespues = distribuidor.saldoCapital(cr.getValorFinanciado(), cuotas);
        int pendientesDespues = (int) cuotas.stream().filter(q -> q.getEstado() != EstadoCuota.PAGADA).count();
        List<LineaAbono> lineas = res.lineas().stream()
                .map(l -> new LineaAbono(l.tipo(), l.cuota(), l.detalle(), l.saldoAnterior(), l.valor(), l.saldo())).toList();
        return new VistaPreviaAbono(cr.getId(), cr.getNumeroCredito(), r.tipoAbono(), valor, res.aMora(), res.aCuotas(),
                res.aCapital(), saldoAntes, saldoDespues, pendientesAntes, pendientesDespues, cuotaAntes,
                res.nuevaCuota() == null ? cuotaAntes : res.nuevaCuota(), pendientesDespues == 0, res.resumen(), lineas);
    }

    private BigDecimal valorDelAbono(Credito cr, AbonoRequest r, LocalDate hoy) {
        if (r.tipoAbono() == TipoAbono.TOTAL) {
            return liquidacion(cr, hoy).total();
        }
        if (r.valor() == null) {
            throw NegocioException.solicitudInvalida("Escriba el valor del abono.");
        }
        return r.valor().setScale(2, RoundingMode.HALF_UP);
    }

    private MotorAbonos.Liquidacion liquidacion(Credito cr, LocalDate fecha) {
        List<Cuota> cuotas = cr.getCuotas();
        return motor.liquidacion(cuotas, cr.getFechaDesembolso(), mora.moraPendiente(cr, cuotas, fecha),
                mora.diasPorCobrar(cr, cuotas, fecha), fecha);
    }

    private void actualizarCredito(Credito cr, LocalDate hoy, MotorAbonos.Resultado res) {
        List<Cuota> cuotas = cr.getCuotas();
        cr.setPlazoMeses(cuotas.size());
        if (res.nuevaCuota() != null) {
            cr.setValorCuota(res.nuevaCuota());
        }
        cr.setSaldoCapital(distribuidor.saldoCapital(cr.getValorFinanciado(), cuotas));
        EstadoCredito estado = situacion.evaluar(cr, cuotas, hoy).estado();
        cr.setEstado(estado);
        if (estado == EstadoCredito.CANCELADO && cr.getFechaCancelacion() == null) {
            cr.setFechaCancelacion(hoy);
        }
    }

    private CreditoHistorial historialDe(Credito cr) {
        return new CreditoHistorial(consulta.resumenDe(cr), "RENTAR_MOTOCICLETAS", "MENSUAL", cr.getFechaDesembolso(),
                cr.getFechaCancelacion(), cr.getAsesor(), Sucursales.etiqueta(cr.getSucursal()), cr.getCuotaOficial(), cr.getSeguroVidaCuota(),
                cr.getFogadeCuota(), CreditoConsultaService.codeudorInfo(cr.getCodeudor()));
    }

    static String textoTipo(TipoAbono t) {
        return switch (t) {
            case NORMAL -> "Abono normal";
            case CAPITAL_PLAZO -> "Abono a capital - bajar tiempo";
            case CAPITAL_CUOTA -> "Abono a capital - bajar cuota";
            case TOTAL -> "Total pago";
        };
    }

    /** Copia una cuota (sin persistir) para simular sin tocar la base de datos. */
    static Cuota copiar(Cuota q) {
        Cuota c = new Cuota();
        c.setId(q.getId());
        c.setCredito(q.getCredito());
        c.setNumeroCuota(q.getNumeroCuota());
        c.setFechaVencimiento(q.getFechaVencimiento());
        c.setValorCapital(q.getValorCapital());
        c.setValorInteres(q.getValorInteres());
        c.setValorSeguroVida(q.getValorSeguroVida());
        c.setValorFogade(q.getValorFogade());
        c.setValorCuota(q.getValorCuota());
        c.setSaldoCapitalFinal(q.getSaldoCapitalFinal());
        c.setValorPagado(q.getValorPagado());
        c.setFechaPago(q.getFechaPago());
        c.setEstado(q.getEstado());
        return c;
    }

    private String nuevaReferencia(LocalDateTime ahora) {
        String fecha = ahora.format(DateTimeFormatter.BASIC_ISO_DATE);
        String referencia;
        do {
            StringBuilder sb = new StringBuilder("ABN-").append(fecha).append('-');
            for (int i = 0; i < 6; i++) {
                sb.append(CARACTERES.charAt(aleatorio.nextInt(CARACTERES.length())));
            }
            referencia = sb.toString();
        } while (pagos.existsByReferencia(referencia));
        return referencia;
    }
}
