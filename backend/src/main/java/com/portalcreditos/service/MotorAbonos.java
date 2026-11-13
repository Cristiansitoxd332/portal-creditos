package com.portalcreditos.service;

import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.entity.enums.TipoAbono;
import com.portalcreditos.exception.NegocioException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de abonos (logica pura, sin base de datos) para la pantalla "Abonos individuales".
 *
 * Orden de aplicacion de cualquier abono:
 *   1. Mora por dias de atraso ($380 por dia).
 *   2. Lo EXIGIBLE: cuotas vencidas o que vencen hoy y cuotas con pago parcial. Dentro de cada cuota:
 *      seguro de vida, FOGADE, interes y por ultimo capital.
 *   3. Segun el tipo de abono:
 *      - NORMAL: sigue con las cuotas siguientes en orden.
 *      - CAPITAL_PLAZO / CAPITAL_CUOTA: el excedente se abona a capital y se reliquidan las cuotas futuras
 *        (misma cuota y menos cuotas, o mismas cuotas y cuota mas baja).
 *      - TOTAL: capital pendiente + interes y seguros causados de la cuota en curso (proporcional a los dias).
 *
 * Trabaja sobre la lista de cuotas que recibe (las modifica). Para la vista previa se le pasa una copia.
 */
@Component
public class MotorAbonos {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final DistribuidorPagos distribuidor;

    public MotorAbonos(DistribuidorPagos distribuidor) {
        this.distribuidor = distribuidor;
    }

    /** Una fila del detalle del abono (como la tabla de Sadmit). */
    public record Linea(String tipo, Integer cuota, String detalle, BigDecimal saldoAnterior, BigDecimal valor, BigDecimal saldo) {
    }

    /** Lo que costaria cancelar todo el credito en una fecha. */
    public record Liquidacion(
            LocalDate fecha,
            BigDecimal mora,
            int diasMora,
            BigDecimal capital,
            BigDecimal interes,
            BigDecimal seguroVida,
            BigDecimal fogade,
            BigDecimal exigible,
            BigDecimal interesCausado,
            BigDecimal segurosCausados,
            int diasCausados,
            BigDecimal total
    ) {
    }

    /** Resultado de aplicar un abono. */
    public record Resultado(
            List<Linea> lineas,
            BigDecimal aMora,
            BigDecimal aCuotas,
            BigDecimal aCapital,
            List<DistribuidorPagos.Aplicacion> aplicaciones,
            List<Cuota> eliminadas,
            BigDecimal nuevaCuota,
            String resumen
    ) {
    }

    // ================================================================ calculos

    /** Cuotas exigibles a la fecha: no pagadas y (vencidas, que vencen ese dia o con pago parcial). */
    static List<Cuota> exigibles(List<Cuota> cuotas, LocalDate fecha) {
        return cuotas.stream()
                .filter(q -> q.getEstado() != EstadoCuota.PAGADA)
                .filter(q -> !q.getFechaVencimiento().isAfter(fecha) || q.getValorPagado().signum() > 0)
                .sorted(Comparator.comparing(Cuota::getNumeroCuota))
                .toList();
    }

    /** Cuotas futuras sin ningun pago. */
    static List<Cuota> futuras(List<Cuota> cuotas, LocalDate fecha) {
        return cuotas.stream()
                .filter(q -> q.getEstado() != EstadoCuota.PAGADA)
                .filter(q -> q.getFechaVencimiento().isAfter(fecha) && q.getValorPagado().signum() == 0)
                .sorted(Comparator.comparing(Cuota::getNumeroCuota))
                .toList();
    }

    /**
     * Valor para cancelar el credito en la fecha: mora + todo lo exigible + capital de las cuotas futuras +
     * interes y seguros de la cuota en curso proporcionales a los dias transcurridos del periodo.
     */
    public Liquidacion liquidacion(List<Cuota> cuotas, LocalDate fechaDesembolso, BigDecimal mora, int diasMora, LocalDate fecha) {
        BigDecimal capital = BigDecimal.ZERO;
        BigDecimal interes = BigDecimal.ZERO;
        BigDecimal vida = BigDecimal.ZERO;
        BigDecimal fogade = BigDecimal.ZERO;
        BigDecimal exigible = BigDecimal.ZERO;
        for (Cuota q : exigibles(cuotas, fecha)) {
            Partes p = pendientes(q);
            vida = vida.add(p.vida());
            fogade = fogade.add(p.fogade());
            interes = interes.add(p.interes());
            capital = capital.add(p.capital());
            exigible = exigible.add(q.getSaldoPendiente());
        }
        List<Cuota> futuras = futuras(cuotas, fecha);
        BigDecimal interesCausado = BigDecimal.ZERO;
        BigDecimal segurosCausados = BigDecimal.ZERO;
        int dias = 0;
        if (!futuras.isEmpty()) {
            Cuota enCurso = futuras.get(0);
            LocalDate inicio = inicioPeriodo(cuotas, enCurso, fechaDesembolso);
            long periodo = Math.max(1, ChronoUnit.DAYS.between(inicio, enCurso.getFechaVencimiento()));
            dias = (int) Math.max(0, Math.min(periodo, ChronoUnit.DAYS.between(inicio, fecha)));
            BigDecimal fraccion = BigDecimal.valueOf(dias).divide(BigDecimal.valueOf(periodo), MC);
            interesCausado = pesos(enCurso.getValorInteres().multiply(fraccion, MC));
            BigDecimal vidaCausada = pesos(enCurso.getValorSeguroVida().multiply(fraccion, MC));
            BigDecimal fogadeCausado = pesos(enCurso.getValorFogade().multiply(fraccion, MC));
            segurosCausados = vidaCausada.add(fogadeCausado);
            vida = vida.add(vidaCausada);
            fogade = fogade.add(fogadeCausado);
            interes = interes.add(interesCausado);
            for (Cuota q : futuras) {
                capital = capital.add(q.getValorCapital());
            }
        }
        BigDecimal total = mora.add(exigible).add(interesCausado).add(segurosCausados)
                .add(futuras.stream().map(Cuota::getValorCapital).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Liquidacion(fecha, dinero(mora), diasMora, dinero(capital), dinero(interes), dinero(vida), dinero(fogade),
                dinero(exigible), dinero(interesCausado), dinero(segurosCausados), dias, dinero(total));
    }

    /** Lo que se debe al CORTE de una fecha: mora + cuotas vencidas o que vencen hasta esa fecha (sin anticipar capital). */
    public Liquidacion alCorte(List<Cuota> cuotas, BigDecimal mora, int diasMora, LocalDate fecha) {
        BigDecimal capital = BigDecimal.ZERO;
        BigDecimal interes = BigDecimal.ZERO;
        BigDecimal vida = BigDecimal.ZERO;
        BigDecimal fogade = BigDecimal.ZERO;
        BigDecimal exigible = BigDecimal.ZERO;
        for (Cuota q : cuotas) {
            if (q.getEstado() == EstadoCuota.PAGADA || q.getFechaVencimiento().isAfter(fecha)) {
                continue;
            }
            Partes p = pendientes(q);
            vida = vida.add(p.vida());
            fogade = fogade.add(p.fogade());
            interes = interes.add(p.interes());
            capital = capital.add(p.capital());
            exigible = exigible.add(q.getSaldoPendiente());
        }
        return new Liquidacion(fecha, dinero(mora), diasMora, dinero(capital), dinero(interes), dinero(vida), dinero(fogade),
                dinero(exigible), CERO, CERO, 0, dinero(mora.add(exigible)));
    }

    // ================================================================ aplicar

    /**
     * Aplica el abono a las cuotas (las modifica). El valor de la mora NO se registra aqui: lo hace quien llama
     * (con la calculadora de mora) usando {@link Resultado#aMora()}.
     */
    public Resultado aplicar(TipoAbono tipo, BigDecimal valor, List<Cuota> cuotas, BigDecimal tasaMensualPct,
                             LocalDate fechaDesembolso, BigDecimal moraPendiente, int diasMora, BigDecimal moraDia,
                             LocalDate fecha) {
        if (valor == null || valor.signum() <= 0) {
            throw NegocioException.solicitudInvalida("El valor del abono debe ser mayor que cero.");
        }
        valor = valor.setScale(2, RoundingMode.HALF_UP);
        List<Linea> lineas = new ArrayList<>();
        BigDecimal saldoCapital = distribuidor.saldoCapital(BigDecimal.ZERO, cuotas);

        BigDecimal aMora = valor.min(moraPendiente);
        if (aMora.signum() > 0) {
            lineas.add(new Linea("MORA", null, "Mora: " + diasMora + (diasMora == 1 ? " día" : " días") + " × "
                    + MensajeService.pesos(moraDia), moraPendiente, aMora, moraPendiente.subtract(aMora)));
        }
        BigDecimal restante = valor.subtract(aMora);
        List<Cuota> exigibles = exigibles(cuotas, fecha);
        BigDecimal totalExigible = exigibles.stream().map(Cuota::getSaldoPendiente).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DistribuidorPagos.Aplicacion> aplicaciones = new ArrayList<>();
        List<Cuota> eliminadas = new ArrayList<>();
        BigDecimal aCapital = CERO;
        BigDecimal nuevaCuota = null;
        String resumen;

        switch (tipo) {
            case NORMAL -> {
                BigDecimal pendienteTotal = distribuidor.totalPendiente(cuotas);
                if (restante.compareTo(pendienteTotal) > 0) {
                    throw NegocioException.solicitudInvalida("El valor supera lo que se debe en el plan ("
                            + MensajeService.pesos(pendienteTotal.add(moraPendiente)) + "). Use \"Total pago\" para cancelar el crédito.");
                }
                saldoCapital = distribuirConDetalle(cuotas, restante, fecha, aplicaciones, lineas, saldoCapital);
                resumen = "Abono normal: se aplicó a la mora y a las cuotas en orden.";
            }
            case CAPITAL_PLAZO, CAPITAL_CUOTA -> {
                if (restante.compareTo(totalExigible) <= 0) {
                    throw NegocioException.solicitudInvalida("El valor no alcanza a cubrir lo vencido ("
                            + MensajeService.pesos(totalExigible.add(moraPendiente))
                            + "). Para abonar a capital primero debe quedar al día; use \"Abono normal\".");
                }
                saldoCapital = distribuirConDetalle(cuotas, totalExigible, fecha, aplicaciones, lineas, saldoCapital);
                BigDecimal excedente = restante.subtract(totalExigible);
                List<Cuota> futuras = futuras(cuotas, fecha);
                BigDecimal capitalFuturo = futuras.stream().map(Cuota::getValorCapital).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (futuras.isEmpty() || excedente.compareTo(capitalFuturo) > 0) {
                    throw NegocioException.solicitudInvalida("El abono a capital (" + MensajeService.pesos(excedente)
                            + ") supera el capital pendiente (" + MensajeService.pesos(capitalFuturo)
                            + "). Use \"Total pago\" para cancelar el crédito.");
                }
                BigDecimal nuevoCapital = capitalFuturo.subtract(excedente);
                lineas.add(new Linea("ABONO_CAPITAL", null, "Abono extraordinario a capital ("
                        + (tipo == TipoAbono.CAPITAL_PLAZO ? "bajar tiempo" : "bajar cuota") + ")",
                        saldoCapital, excedente, saldoCapital.subtract(excedente)));
                aCapital = excedente;
                int antes = futuras.size();
                if (nuevoCapital.signum() == 0) {
                    eliminadas.addAll(futuras);
                    resumen = "El abono cubrió todo el capital: el crédito queda cancelado.";
                } else if (tipo == TipoAbono.CAPITAL_CUOTA) {
                    nuevaCuota = reliquidarBajandoCuota(futuras, nuevoCapital, tasaMensualPct);
                    resumen = "Se mantienen " + antes + " cuotas y la cuota baja a " + MensajeService.pesos(nuevaCuota) + ".";
                } else {
                    int quedan = reliquidarBajandoPlazo(futuras, nuevoCapital, tasaMensualPct, eliminadas);
                    nuevaCuota = futuras.get(0).getValorCuota();
                    resumen = "Se mantiene la cuota y el plazo baja de " + antes + " a " + quedan + " cuotas pendientes ("
                            + (antes - quedan) + (antes - quedan == 1 ? " cuota menos" : " cuotas menos") + ").";
                }
            }
            case TOTAL -> {
                Liquidacion liq = liquidacion(cuotas, fechaDesembolso, moraPendiente, diasMora, fecha);
                if (valor.compareTo(liq.total()) != 0) {
                    throw NegocioException.solicitudInvalida("Para el total pago el valor debe ser exactamente "
                            + MensajeService.pesos(liq.total()) + ".");
                }
                saldoCapital = distribuirConDetalle(cuotas, totalExigible, fecha, aplicaciones, lineas, saldoCapital);
                List<Cuota> futuras = futuras(cuotas, fecha);
                if (!futuras.isEmpty()) {
                    Cuota liquidacion = futuras.get(0);
                    BigDecimal capital = futuras.stream().map(Cuota::getValorCapital).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal fraccionVida = liquidacion.getValorSeguroVida();
                    BigDecimal fraccionFogade = liquidacion.getValorFogade();
                    BigDecimal segurosOriginales = fraccionVida.add(fraccionFogade);
                    BigDecimal vida = segurosOriginales.signum() == 0 ? CERO
                            : dinero(pesos(liq.segurosCausados().multiply(fraccionVida, MC).divide(segurosOriginales, MC)));
                    BigDecimal fogade = liq.segurosCausados().subtract(vida);
                    BigDecimal interes = liq.interesCausado();
                    agregarLinea(lineas, "SEGURO_VIDA", liquidacion, "causado " + liq.diasCausados() + " días", vida, vida, CERO);
                    agregarLinea(lineas, "FOGADE", liquidacion, "causado " + liq.diasCausados() + " días", fogade, fogade, CERO);
                    agregarLinea(lineas, "INTERES", liquidacion, "causado " + liq.diasCausados() + " días", interes, interes, CERO);
                    lineas.add(new Linea("CAPITAL", liquidacion.getNumeroCuota(), "Capital total pendiente (cancelación)",
                            saldoCapital, capital, CERO));
                    liquidacion.setValorSeguroVida(vida);
                    liquidacion.setValorFogade(dinero(fogade));
                    liquidacion.setValorInteres(dinero(interes));
                    liquidacion.setValorCapital(dinero(capital));
                    liquidacion.setValorCuota(dinero(capital.add(interes).add(vida).add(fogade)));
                    liquidacion.setSaldoCapitalFinal(CERO);
                    liquidacion.setValorPagado(liquidacion.getValorCuota());
                    liquidacion.setEstado(EstadoCuota.PAGADA);
                    liquidacion.setFechaPago(fecha);
                    aplicaciones.add(new DistribuidorPagos.Aplicacion(liquidacion, liquidacion.getValorCuota()));
                    eliminadas.addAll(futuras.subList(1, futuras.size()));
                    aCapital = dinero(capital);
                }
                resumen = "Total pago: el crédito queda cancelado (paz y salvo disponible).";
            }
            default -> throw new IllegalStateException();
        }
        BigDecimal aCuotas = valor.subtract(aMora);
        return new Resultado(lineas, aMora, aCuotas, aCapital, aplicaciones, eliminadas, nuevaCuota, resumen);
    }

    // ================================================================ reliquidacion

    /** Mismo numero de cuotas y cuota mas baja (sistema frances sobre el nuevo capital). */
    BigDecimal reliquidarBajandoCuota(List<Cuota> futuras, BigDecimal capital, BigDecimal tasaPct) {
        BigDecimal i = tasaPct.divide(CIEN, MC);
        int n = futuras.size();
        BigDecimal cuota;
        if (i.signum() == 0) {
            cuota = capital.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);
        } else {
            BigDecimal factor = BigDecimal.ONE.add(i).pow(n, MC);
            cuota = capital.multiply(i, MC).multiply(factor, MC).divide(factor.subtract(BigDecimal.ONE), MC)
                    .setScale(0, RoundingMode.HALF_UP);
        }
        BigDecimal saldo = capital.setScale(0, RoundingMode.HALF_UP);
        for (int k = 0; k < n; k++) {
            Cuota q = futuras.get(k);
            BigDecimal interes = pesos(saldo.multiply(i, MC));
            BigDecimal cap = k == n - 1 ? saldo : cuota.subtract(interes);
            saldo = saldo.subtract(cap);
            fijar(q, cap, interes, saldo);
        }
        return futuras.get(0).getValorCuota();
    }

    /** Misma cuota financiera y menos cuotas. Devuelve cuantas quedan; las sobrantes van a {@code eliminadas}. */
    int reliquidarBajandoPlazo(List<Cuota> futuras, BigDecimal capital, BigDecimal tasaPct, List<Cuota> eliminadas) {
        BigDecimal i = tasaPct.divide(CIEN, MC);
        Cuota primera = futuras.get(0);
        BigDecimal cuota = primera.getValorCapital().add(primera.getValorInteres());
        BigDecimal saldo = capital.setScale(0, RoundingMode.HALF_UP);
        int k = 0;
        while (saldo.signum() > 0 && k < futuras.size()) {
            BigDecimal interes = pesos(saldo.multiply(i, MC));
            BigDecimal cap = cuota.subtract(interes);
            if (cap.signum() <= 0) {
                throw NegocioException.solicitudInvalida("La cuota actual no alcanza a cubrir los intereses del nuevo saldo.");
            }
            if (cap.compareTo(saldo) >= 0 || k == futuras.size() - 1) {
                cap = saldo;
            }
            saldo = saldo.subtract(cap);
            fijar(futuras.get(k), cap, interes, saldo);
            k++;
        }
        eliminadas.addAll(futuras.subList(k, futuras.size()));
        return k;
    }

    private static void fijar(Cuota q, BigDecimal capital, BigDecimal interes, BigDecimal saldoFinal) {
        q.setValorCapital(dinero(capital));
        q.setValorInteres(dinero(interes));
        q.setValorCuota(dinero(capital.add(interes).add(q.getValorSeguros())));
        q.setSaldoCapitalFinal(dinero(saldoFinal));
    }

    // ================================================================ detalle por componente

    private record Partes(BigDecimal vida, BigDecimal fogade, BigDecimal interes, BigDecimal capital) {
    }

    /** Lo que falta por pagar de cada componente de la cuota (orden: vida, FOGADE, interes, capital). */
    private static Partes pendientes(Cuota q) {
        BigDecimal[] limites = limites(q);
        BigDecimal pagado = q.getValorPagado();
        return new Partes(pendienteEntre(pagado, BigDecimal.ZERO, limites[0]), pendienteEntre(pagado, limites[0], limites[1]),
                pendienteEntre(pagado, limites[1], limites[2]), pendienteEntre(pagado, limites[2], limites[3]));
    }

    private static BigDecimal[] limites(Cuota q) {
        BigDecimal a = q.getValorSeguroVida();
        BigDecimal b = a.add(q.getValorFogade());
        BigDecimal c = b.add(q.getValorInteres());
        BigDecimal d = c.add(q.getValorCapital());
        return new BigDecimal[]{a, b, c, d};
    }

    private static BigDecimal pendienteEntre(BigDecimal pagado, BigDecimal desde, BigDecimal hasta) {
        return hasta.subtract(pagado.max(desde)).max(BigDecimal.ZERO).min(hasta.subtract(desde));
    }

    /** Distribuye el valor con las reglas de siempre y arma una linea por componente pagado. */
    private BigDecimal distribuirConDetalle(List<Cuota> cuotas, BigDecimal valor, LocalDate fecha,
                                            List<DistribuidorPagos.Aplicacion> aplicaciones, List<Linea> lineas, BigDecimal saldoCapital) {
        if (valor.signum() <= 0) {
            return saldoCapital;
        }
        Map<Cuota, BigDecimal> antes = new HashMap<>();
        for (Cuota q : cuotas) {
            antes.put(q, q.getValorPagado());
        }
        List<DistribuidorPagos.Aplicacion> hechas = distribuidor.distribuir(cuotas, valor, fecha);
        aplicaciones.addAll(hechas);
        BigDecimal saldo = saldoCapital;
        for (DistribuidorPagos.Aplicacion ap : hechas) {
            Cuota q = ap.cuota();
            BigDecimal p0 = antes.get(q);
            BigDecimal p1 = p0.add(ap.valorAplicado());
            BigDecimal[] l = limites(q);
            String[] tipos = {"SEGURO_VIDA", "FOGADE", "INTERES", "CAPITAL"};
            BigDecimal desde = BigDecimal.ZERO;
            for (int k = 0; k < 4; k++) {
                BigDecimal hasta = l[k];
                BigDecimal tramo = p1.min(hasta).subtract(p0.max(desde)).max(BigDecimal.ZERO);
                if (tramo.signum() > 0) {
                    if (k == 3) {
                        lineas.add(new Linea("CAPITAL", q.getNumeroCuota(), "Capital cuota " + q.getNumeroCuota()
                                + " · vence " + FECHA.format(q.getFechaVencimiento()), saldo, tramo, saldo.subtract(tramo)));
                        saldo = saldo.subtract(tramo);
                    } else {
                        BigDecimal pendiente = hasta.subtract(p0.max(desde)).min(hasta.subtract(desde));
                        agregarLinea(lineas, tipos[k], q, "vence " + FECHA.format(q.getFechaVencimiento()), pendiente, tramo,
                                pendiente.subtract(tramo));
                    }
                }
                desde = hasta;
            }
        }
        return saldo;
    }

    private static void agregarLinea(List<Linea> lineas, String tipo, Cuota q, String detalle, BigDecimal anterior,
                                     BigDecimal valor, BigDecimal saldo) {
        if (valor.signum() <= 0) {
            return;
        }
        String nombre = switch (tipo) {
            case "SEGURO_VIDA" -> "Seguro de vida";
            case "FOGADE" -> "FOGADE";
            default -> "Interés";
        };
        lineas.add(new Linea(tipo, q.getNumeroCuota(), nombre + " cuota " + q.getNumeroCuota() + " · " + detalle,
                dinero(anterior), dinero(valor), dinero(saldo)));
    }

    /** Inicio del periodo de una cuota: vencimiento de la cuota anterior o el desembolso. */
    private static LocalDate inicioPeriodo(List<Cuota> cuotas, Cuota q, LocalDate desembolso) {
        return cuotas.stream()
                .filter(x -> x.getNumeroCuota() < q.getNumeroCuota())
                .map(Cuota::getFechaVencimiento)
                .max(LocalDate::compareTo)
                .orElse(desembolso != null ? desembolso : q.getFechaVencimiento().minusMonths(1));
    }

    private static BigDecimal pesos(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
