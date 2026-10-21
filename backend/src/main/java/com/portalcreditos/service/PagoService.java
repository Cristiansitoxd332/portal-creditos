package com.portalcreditos.service;

import com.portalcreditos.dto.pago.PagoDtos.AplicacionInfo;
import com.portalcreditos.dto.pago.PagoDtos.Comprobante;
import com.portalcreditos.dto.pago.PagoDtos.OpcionesPago;
import com.portalcreditos.dto.pago.PagoDtos.PagoHistorial;
import com.portalcreditos.dto.pago.PagoDtos.PagoRequest;
import com.portalcreditos.entity.AplicacionPago;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.Pago;
import com.portalcreditos.entity.Usuario;
import com.portalcreditos.entity.enums.CanalPago;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPago;
import com.portalcreditos.entity.enums.MedioPago;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.integracion.pagos.PasarelaPago;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.PagoRepository;
import com.portalcreditos.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registro de pagos SIMULADOS y consulta del historial.
 *
 * Flujo de un pago:
 * 1. Se bloquea el credito en la base de datos (evita pagos simultaneos sobre las mismas cuotas).
 * 2. Se valida el valor y el medio de pago.
 * 3. Se envia a la pasarela a traves de la interfaz PasarelaPago (patron Adaptador).
 * 4. Si se aprueba: se aplica a las cuotas mas antiguas y se recalculan saldo y estado del credito.
 * 5. Se guarda el pago (aprobado o rechazado), se registra en auditoria y se devuelve el comprobante.
 * Todo ocurre en una sola transaccion: o se guarda completo, o no se guarda nada.
 */
@Service
public class PagoService {

    public static final String AVISO = "Pago SIMULADO del prototipo: no se realizo ninguna transaccion real "
            + "ni se actualizo ningun sistema de la empresa.";
    private static final BigDecimal VALOR_MINIMO = new BigDecimal("10000.00");
    private static final List<MedioPago> MEDIOS_CLIENTE = List.of(MedioPago.PSE_SIMULADO, MedioPago.TARJETA_SIMULADA);
    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CreditoRepository creditos;
    private final PagoRepository pagos;
    private final UsuarioRepository usuarios;
    private final DistribuidorPagos distribuidor;
    private final SituacionCreditoService situacionService;
    private final PasarelaPago pasarela;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    public PagoService(CreditoRepository creditos, PagoRepository pagos, UsuarioRepository usuarios,
                       DistribuidorPagos distribuidor, SituacionCreditoService situacionService,
                       PasarelaPago pasarela, AuditoriaService auditoria, Clock reloj) {
        this.creditos = creditos;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.distribuidor = distribuidor;
        this.situacionService = situacionService;
        this.pasarela = pasarela;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // ================================================================= cliente

    @Transactional(readOnly = true)
    public OpcionesPago opcionesDelCliente(Long clienteId, Long creditoId) {
        return opciones(creditoDelCliente(clienteId, creditoId), MEDIOS_CLIENTE);
    }

    @Transactional
    public Comprobante pagarComoCliente(Long clienteId, Long creditoId, PagoRequest solicitud, String username, String ip) {
        creditoDelCliente(clienteId, creditoId); // valida que el credito sea del cliente (si no, 404)
        if (!MEDIOS_CLIENTE.contains(solicitud.medioPago())) {
            throw NegocioException.solicitudInvalida("Desde el portal solo se permiten los medios PSE_SIMULADO y TARJETA_SIMULADA");
        }
        return registrar(creditoId, solicitud, CanalPago.PORTAL, null, username, ip);
    }

    @Transactional(readOnly = true)
    public List<PagoHistorial> historialDelCliente(Long clienteId, Long creditoId) {
        creditoDelCliente(clienteId, creditoId);
        return historial(creditoId);
    }

    @Transactional(readOnly = true)
    public Comprobante comprobanteDelCliente(Long clienteId, Long creditoId, Long pagoId) {
        creditoDelCliente(clienteId, creditoId);
        return comprobante(creditoId, pagoId);
    }

    // ================================================================= administrador

    @Transactional(readOnly = true)
    public OpcionesPago opciones(Long creditoId) {
        return opciones(creditos.findById(creditoId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado")), List.of(MedioPago.values()));
    }

    @Transactional
    public Comprobante registrarComoAdmin(Long creditoId, PagoRequest solicitud, String username, String ip) {
        Usuario admin = usuarios.findByUsername(username)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        return registrar(creditoId, solicitud, CanalPago.ADMIN, admin, username, ip);
    }

    @Transactional(readOnly = true)
    public List<PagoHistorial> historial(Long creditoId) {
        return pagos.findByCreditoIdOrderByFechaPagoDesc(creditoId).stream()
                .map(p -> new PagoHistorial(p.getId(), p.getReferencia(), p.getFechaPago(), p.getValor(),
                        p.getMedioPago(), p.getCanal(), p.getEstado(), cuotasAplicadas(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Comprobante comprobante(Long creditoId, Long pagoId) {
        Pago pago = pagos.findById(pagoId)
                .filter(p -> p.getCredito().getId().equals(creditoId))
                .orElseThrow(() -> NegocioException.noEncontrado("Pago no encontrado"));
        LocalDate hoy = LocalDate.now(reloj);
        List<AplicacionInfo> aplicaciones = pago.getAplicaciones().stream()
                .sorted(Comparator.comparing(a -> a.getCuota().getNumeroCuota()))
                .map(a -> new AplicacionInfo(a.getCuota().getNumeroCuota(), a.getValorAplicado(),
                        situacionService.estadoVisible(a.getCuota(), hoy)))
                .toList();
        return construirComprobante(pago, null, null, pago.getEstado() == EstadoPago.APROBADO
                ? "Transaccion aprobada (pago simulado)." : "Transaccion rechazada (pago simulado).", aplicaciones);
    }

    // ================================================================= logica central

    private Comprobante registrar(Long creditoId, PagoRequest solicitud, CanalPago canal, Usuario registradoPor,
                                  String username, String ip) {
        Credito credito = creditos.bloquearPorId(creditoId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
        List<Cuota> cuotas = credito.getCuotas();
        BigDecimal valor = solicitud.valor().setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendiente = distribuidor.totalPendiente(cuotas);

        if (credito.getEstado() == EstadoCredito.CANCELADO || pendiente.signum() == 0) {
            throw NegocioException.solicitudInvalida("El credito ya esta cancelado; no tiene saldo pendiente.");
        }
        if (valor.compareTo(pendiente) > 0) {
            throw NegocioException.solicitudInvalida("El valor supera el saldo pendiente del credito ($"
                    + pendiente.toPlainString() + ").");
        }
        if (valor.compareTo(VALOR_MINIMO) < 0 && valor.compareTo(pendiente) != 0) {
            throw NegocioException.solicitudInvalida("El valor minimo de un pago es $10.000, salvo que sea el saldo total pendiente.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        String referencia = nuevaReferencia(ahora);
        PasarelaPago.ResultadoPasarela resultado = pasarela.procesar(new PasarelaPago.SolicitudPasarela(
                referencia, valor, solicitud.medioPago(), "Pago credito " + credito.getNumeroCredito(),
                Boolean.TRUE.equals(solicitud.simularRechazo())));

        Pago pago = new Pago();
        pago.setCredito(credito);
        pago.setReferencia(referencia);
        pago.setFechaPago(ahora);
        pago.setValor(valor);
        pago.setMedioPago(solicitud.medioPago());
        pago.setCanal(canal);
        pago.setRegistradoPor(registradoPor);
        pago.setEstado(resultado.aprobado() ? EstadoPago.APROBADO : EstadoPago.RECHAZADO);
        pago.setObservacion(observacion(solicitud.observacion(), resultado.codigoAutorizacion()));

        BigDecimal saldoAnterior = credito.getSaldoCapital();
        List<AplicacionInfo> aplicaciones = List.of();

        if (resultado.aprobado()) {
            LocalDate hoy = ahora.toLocalDate();
            List<DistribuidorPagos.Aplicacion> distribucion = distribuidor.distribuir(cuotas, valor, hoy);
            for (DistribuidorPagos.Aplicacion d : distribucion) {
                AplicacionPago ap = new AplicacionPago();
                ap.setPago(pago);
                ap.setCuota(d.cuota());
                ap.setValorAplicado(d.valorAplicado());
                pago.getAplicaciones().add(ap);
            }
            actualizarCredito(credito, cuotas, hoy);
            aplicaciones = distribucion.stream()
                    .map(d -> new AplicacionInfo(d.cuota().getNumeroCuota(), d.valorAplicado(),
                            situacionService.estadoVisible(d.cuota(), hoy)))
                    .toList();
        }

        pagos.save(pago);
        auditoria.registrar(username, resultado.aprobado() ? "PAGO_APROBADO" : "PAGO_RECHAZADO", "pago",
                referencia, "Credito " + credito.getNumeroCredito() + " - valor $" + valor.toPlainString()
                        + " - medio " + solicitud.medioPago() + " - canal " + canal, ip);

        return construirComprobante(pago, saldoAnterior, credito.getSaldoCapital(), resultado.mensaje(), aplicaciones);
    }

    /** Recalcula saldo de capital y estado del credito despues de aplicar un pago. */
    private void actualizarCredito(Credito credito, List<Cuota> cuotas, LocalDate hoy) {
        credito.setSaldoCapital(distribuidor.saldoCapital(credito.getValorFinanciado(), cuotas));
        EstadoCredito estado = situacionService.evaluar(credito, cuotas, hoy).estado();
        credito.setEstado(estado);
        if (estado == EstadoCredito.CANCELADO && credito.getFechaCancelacion() == null) {
            credito.setFechaCancelacion(hoy);
        }
    }

    private OpcionesPago opciones(Credito credito, List<MedioPago> medios) {
        LocalDate hoy = LocalDate.now(reloj);
        List<Cuota> cuotas = credito.getCuotas();
        SituacionCreditoService.Situacion s = situacionService.evaluar(credito, cuotas, hoy);
        BigDecimal pendiente = distribuidor.totalPendiente(cuotas);
        BigDecimal hastaProxima = s.valorVencido().add(s.proximaCuota() == null ? BigDecimal.ZERO : s.proximaCuota().getSaldoPendiente());
        return new OpcionesPago(credito.getId(), credito.getNumeroCredito(), s.valorVencido(), s.cuotasVencidas(),
                hastaProxima, VALOR_MINIMO.min(pendiente), pendiente, medios, AVISO);
    }

    private Comprobante construirComprobante(Pago p, BigDecimal saldoAnterior, BigDecimal saldoNuevo,
                                             String mensaje, List<AplicacionInfo> aplicaciones) {
        Credito c = p.getCredito();
        return new Comprobante(p.getId(), p.getReferencia(), codigoAutorizacion(p), p.getFechaPago(),
                c.getNumeroCredito(), c.getCliente().getNombreCompleto(), c.getCliente().getNumeroDocumento(),
                p.getValor(), p.getMedioPago(), p.getCanal(), p.getEstado(), mensaje, pasarela.nombre(),
                saldoAnterior, saldoNuevo, c.getEstado(), aplicaciones, AVISO);
    }

    private Credito creditoDelCliente(Long clienteId, Long creditoId) {
        return creditos.findByIdAndClienteId(creditoId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
    }

    private String nuevaReferencia(LocalDateTime ahora) {
        String fecha = ahora.format(DateTimeFormatter.BASIC_ISO_DATE);
        String referencia;
        do {
            StringBuilder sb = new StringBuilder("PAG-").append(fecha).append('-');
            for (int i = 0; i < 6; i++) {
                sb.append(CARACTERES.charAt(aleatorio.nextInt(CARACTERES.length())));
            }
            referencia = sb.toString();
        } while (pagos.existsByReferencia(referencia));
        return referencia;
    }

    private static String observacion(String delUsuario, String codigoAutorizacion) {
        String base = "Pago simulado" + (codigoAutorizacion != null ? " - autorizacion " + codigoAutorizacion : " - rechazado");
        String texto = delUsuario == null || delUsuario.isBlank() ? base : base + " - " + delUsuario.trim();
        return texto.length() > 255 ? texto.substring(0, 255) : texto;
    }

    /** El codigo de autorizacion se guarda dentro de la observacion ("autorizacion SIM-12345678"). */
    private static String codigoAutorizacion(Pago p) {
        String obs = p.getObservacion();
        if (obs == null) {
            return null;
        }
        int i = obs.indexOf("SIM-");
        return i < 0 ? null : obs.substring(i, Math.min(obs.length(), i + 12));
    }

    private static String cuotasAplicadas(Pago p) {
        return p.getAplicaciones().stream()
                .map(a -> a.getCuota().getNumeroCuota())
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }
}
