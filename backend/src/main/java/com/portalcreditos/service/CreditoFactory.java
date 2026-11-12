package com.portalcreditos.service;

import com.portalcreditos.dto.admin.AdminDtos.CreditoRequest;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.Vehiculo;
import com.portalcreditos.entity.enums.ClaseVehiculo;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.entity.enums.EstadoPrenda;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.ClienteRepository;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.VehiculoRepository;
import com.portalcreditos.util.Textos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Crea un credito con su vehiculo y su PLAN DE PAGOS calculado automaticamente (sistema frances).
 *
 * Valida todo antes de guardar. No abre transaccion propia, para poder usarse tanto desde el
 * panel administrativo como desde la importacion de archivos.
 */
@Component
public class CreditoFactory {

    private static final Pattern PLACA = Pattern.compile("^[A-Z]{3}[0-9]{2}[0-9A-Z]$");

    private final ClienteRepository clientes;
    private final VehiculoRepository vehiculos;
    private final CreditoRepository creditos;
    private final AmortizacionService amortizacion;
    private final SituacionCreditoService situacionService;
    private final Clock reloj;

    public CreditoFactory(ClienteRepository clientes, VehiculoRepository vehiculos, CreditoRepository creditos,
                          AmortizacionService amortizacion, SituacionCreditoService situacionService, Clock reloj) {
        this.clientes = clientes;
        this.vehiculos = vehiculos;
        this.creditos = creditos;
        this.amortizacion = amortizacion;
        this.situacionService = situacionService;
        this.reloj = reloj;
    }

    /** Devuelve el mensaje del primer error encontrado o null si los datos son validos. */
    public String validar(CreditoRequest r) {
        if (r.clienteId() == null || !clientes.existsById(r.clienteId())) {
            return "El cliente no existe";
        }
        if (!Boolean.TRUE.equals(r.creditoAdicional())) {
            String activo = creditoActivo(r.clienteId());
            if (activo != null) {
                return "Este cliente ya tiene el credito activo " + activo + ". Para registrar OTRO credito (otra moto) "
                        + "confirme que es un credito adicional; si desea cambiar datos, modifique el cliente o el credito existente";
            }
        }
        String placa = r.placa() == null ? "" : r.placa().trim().toUpperCase(Locale.ROOT);
        if (!PLACA.matcher(placa).matches()) {
            return "La placa debe tener el formato ABC123 (carro) o ABC12D (moto)";
        }
        if (vehiculos.existsByPlaca(placa)) {
            return "Ya existe un vehiculo registrado con la placa " + placa;
        }
        if (Textos.limpiar(r.marca()) == null || r.marca().trim().length() > 50) {
            return "La marca es obligatoria (maximo 50 caracteres)";
        }
        if (Textos.limpiar(r.linea()) == null || r.linea().trim().length() > 60) {
            return "La linea es obligatoria (maximo 60 caracteres)";
        }
        if (r.modelo() == null || r.modelo() < 1990 || r.modelo() > LocalDate.now(reloj).getYear() + 1) {
            return "El modelo debe estar entre 1990 y " + (LocalDate.now(reloj).getYear() + 1);
        }
        if (r.clase() != ClaseVehiculo.MOTOCICLETA) {
            return "Rentar solo financia motocicletas: la clase debe ser MOTOCICLETA";
        }
        if (r.valorVehiculo() == null || r.valorVehiculo().compareTo(BigDecimal.valueOf(1_000_000)) < 0) {
            return "El valor del vehiculo debe ser de al menos $1.000.000";
        }
        if (r.cuotaInicial() == null || r.cuotaInicial().signum() < 0) {
            return "La cuota inicial no puede ser negativa";
        }
        if (r.valorVehiculo().subtract(r.cuotaInicial()).compareTo(BigDecimal.valueOf(100_000)) < 0) {
            return "El valor a financiar (valor del vehiculo - cuota inicial) debe ser de al menos $100.000";
        }
        if (!esTasaPermitida(r.tasaInteresMensual())) {
            return "La tasa mensual debe ser " + TASA_GENERAL + " % (general) o " + TASA_ESPECIAL
                    + " % (caso especial: empleado de Rentar Inversiones o de Yamaha)";
        }
        if (r.plazoMeses() == null || r.plazoMeses() < 1 || r.plazoMeses() > 120) {
            return "El plazo debe estar entre 1 y 120 meses";
        }
        if (r.fechaDesembolso() == null) {
            return "La fecha de desembolso es obligatoria";
        }
        if (r.fechaDesembolso().isAfter(LocalDate.now(reloj))) {
            return "La fecha de desembolso no puede ser futura";
        }
        LocalDate primera = primeraCuota(r);
        if (!primera.isAfter(r.fechaDesembolso())) {
            return "La primera cuota debe vencer despues de la fecha de desembolso";
        }
        if (!esFechaDeCorte(primera)) {
            return "La primera cuota debe caer en una fecha de corte de la entidad: el dia 5 o el dia 20 del mes";
        }
        return null;
    }

    public Credito crear(CreditoRequest r) {
        String error = validar(r);
        if (error != null) {
            throw NegocioException.solicitudInvalida(error);
        }
        Cliente cliente = clientes.findById(r.clienteId()).orElseThrow();

        Vehiculo v = new Vehiculo();
        v.setPlaca(r.placa().trim().toUpperCase(Locale.ROOT));
        v.setMarca(r.marca().trim());
        v.setLinea(r.linea().trim());
        v.setModelo(r.modelo().shortValue());
        v.setColor(Textos.limpiar(r.color()));
        v.setClase(r.clase());
        v.setNumeroMotor(Textos.limpiar(r.numeroMotor()));
        v.setNumeroChasis(Textos.limpiar(r.numeroChasis()));
        vehiculos.save(v);

        BigDecimal valorVehiculo = r.valorVehiculo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cuotaInicial = r.cuotaInicial().setScale(2, RoundingMode.HALF_UP);
        BigDecimal financiado = valorVehiculo.subtract(cuotaInicial).setScale(0, RoundingMode.HALF_UP).setScale(2);
        LocalDate primera = primeraCuota(r);
        AmortizacionService.PlanAmortizacion plan =
                amortizacion.calcular(financiado, r.tasaInteresMensual(), r.plazoMeses(), primera);

        Credito c = new Credito();
        c.setNumeroCredito(nuevoNumero(r.fechaDesembolso().getYear()));
        c.setCliente(cliente);
        c.setVehiculo(v);
        c.setValorVehiculo(valorVehiculo);
        c.setCuotaInicial(cuotaInicial);
        c.setValorFinanciado(financiado);
        c.setTasaInteresMensual(r.tasaInteresMensual().setScale(4, RoundingMode.HALF_UP));
        c.setPlazoMeses(r.plazoMeses());
        c.setValorCuota(plan.valorCuota());
        c.setFechaDesembolso(r.fechaDesembolso());
        c.setFechaPrimeraCuota(primera);
        c.setSaldoCapital(financiado);
        c.setEstadoPrenda(EstadoPrenda.VIGENTE);
        for (AmortizacionService.FilaAmortizacion f : plan.filas()) {
            Cuota q = new Cuota();
            q.setCredito(c);
            q.setNumeroCuota(f.numeroCuota());
            q.setFechaVencimiento(f.fechaVencimiento());
            q.setValorCapital(f.valorCapital());
            q.setValorInteres(f.valorInteres());
            q.setValorCuota(f.valorCuota());
            q.setSaldoCapitalFinal(f.saldoCapitalFinal());
            q.setValorPagado(BigDecimal.ZERO.setScale(2));
            q.setEstado(EstadoCuota.PENDIENTE);
            c.getCuotas().add(q);
        }
        c.setEstado(situacionService.evaluar(c, c.getCuotas(), LocalDate.now(reloj)).estado());
        return creditos.save(c);
    }

    /** Numero del credito activo (vigente o en mora) del cliente, o null si no tiene ninguno. */
    public String creditoActivo(Long clienteId) {
        return creditos.findByClienteIdOrderByFechaDesembolsoDesc(clienteId).stream()
                .filter(c -> c.getEstado() != EstadoCredito.CANCELADO)
                .map(c -> c.getNumeroCredito() + " (moto " + c.getVehiculo().getPlaca() + ")")
                .findFirst().orElse(null);
    }

    /** Tasa mensual general de Rentar. */
    public static final BigDecimal TASA_GENERAL = new BigDecimal("2.84");
    /** Tasa mensual de caso especial: empleados de Rentar Inversiones o de Yamaha. */
    public static final BigDecimal TASA_ESPECIAL = new BigDecimal("1.80");

    public static boolean esTasaPermitida(BigDecimal tasa) {
        return tasa != null && (tasa.compareTo(TASA_GENERAL) == 0 || tasa.compareTo(TASA_ESPECIAL) == 0);
    }

    /** Fechas de corte de la entidad: las cuotas vencen el dia 5 o el dia 20 de cada mes. */
    public static final int CORTE_1 = 5;
    public static final int CORTE_2 = 20;

    public static boolean esFechaDeCorte(LocalDate d) {
        return d.getDayOfMonth() == CORTE_1 || d.getDayOfMonth() == CORTE_2;
    }

    /** Primera fecha de corte (5 o 20) igual o posterior a la fecha dada. */
    public static LocalDate siguienteCorte(LocalDate d) {
        if (d.getDayOfMonth() <= CORTE_1) {
            return d.withDayOfMonth(CORTE_1);
        }
        if (d.getDayOfMonth() <= CORTE_2) {
            return d.withDayOfMonth(CORTE_2);
        }
        return d.plusMonths(1).withDayOfMonth(CORTE_1);
    }

    /**
     * Si no se indica, la primera cuota vence en la primera fecha de corte que este al menos
     * 30 dias despues del desembolso. Las siguientes vencen el mismo dia (5 o 20) de cada mes.
     */
    private static LocalDate primeraCuota(CreditoRequest r) {
        return r.fechaPrimeraCuota() != null ? r.fechaPrimeraCuota() : siguienteCorte(r.fechaDesembolso().plusDays(30));
    }

    private String nuevoNumero(int anio) {
        long consecutivo = creditos.count() + 1;
        String numero;
        do {
            numero = String.format("CV-%d-%04d", anio, consecutivo++);
        } while (creditos.existsByNumeroCredito(numero));
        return numero;
    }
}
