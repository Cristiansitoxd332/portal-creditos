package com.portalcreditos.service;

import com.portalcreditos.dto.admin.AdminDtos.PagosMes;
import com.portalcreditos.dto.admin.AdminDtos.Tablero;
import com.portalcreditos.dto.credito.Semaforo;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Pago;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPago;
import com.portalcreditos.entity.enums.EstadoSolicitud;
import com.portalcreditos.repository.ClienteRepository;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.PagoRepository;
import com.portalcreditos.entity.SolicitudLevantamiento;
import com.portalcreditos.repository.SolicitudLevantamientoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tablero de indicadores para el administrador: cartera, mora, pagos del mes y solicitudes pendientes.
 *
 * Indice de mora = cartera vencida / cartera total x 100. Es el indicador mas usado en entidades
 * de credito para medir el riesgo de la cartera.
 */
@Service
public class DashboardService {

    private final ClienteRepository clientes;
    private final CreditoRepository creditos;
    private final PagoRepository pagos;
    private final SolicitudLevantamientoRepository solicitudes;
    private final String cuentaRecaudo;
    private final SituacionCreditoService situacionService;
    private final Clock reloj;

    public DashboardService(ClienteRepository clientes, CreditoRepository creditos, PagoRepository pagos,
                            SolicitudLevantamientoRepository solicitudes, SituacionCreditoService situacionService,
                            Clock reloj,
                            @Value("${app.levantamiento.cuenta-recaudo:7854}") String cuentaRecaudo) {
        this.cuentaRecaudo = cuentaRecaudo;
        this.clientes = clientes;
        this.creditos = creditos;
        this.pagos = pagos;
        this.solicitudes = solicitudes;
        this.situacionService = situacionService;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Tablero resumen() {
        LocalDate hoy = LocalDate.now(reloj);
        Map<Semaforo, Long> porSemaforo = new EnumMap<>(Semaforo.class);
        for (Semaforo s : Semaforo.values()) {
            porSemaforo.put(s, 0L);
        }
        long alDia = 0;
        long enMora = 0;
        long cancelados = 0;
        BigDecimal cartera = BigDecimal.ZERO;
        BigDecimal vencida = BigDecimal.ZERO;

        List<Credito> lista = creditos.findAll();
        for (Credito c : lista) {
            SituacionCreditoService.Situacion s = situacionService.evaluar(c, c.getCuotas(), hoy);
            porSemaforo.merge(s.semaforo(), 1L, Long::sum);
            if (s.estado() == EstadoCredito.CANCELADO) {
                cancelados++;
            } else {
                cartera = cartera.add(c.getSaldoCapital());
                vencida = vencida.add(s.valorVencido());
                if (s.estado() == EstadoCredito.EN_MORA) {
                    enMora++;
                } else {
                    alDia++;
                }
            }
        }
        BigDecimal indice = cartera.signum() == 0 ? BigDecimal.ZERO
                : vencida.multiply(BigDecimal.valueOf(100)).divide(cartera, 2, RoundingMode.HALF_UP);

        // Pagos aprobados agrupados por mes (ultimos 6 meses)
        YearMonth mesActual = YearMonth.from(hoy);
        List<Pago> aprobados = pagos.findAll().stream().filter(p -> p.getEstado() == EstadoPago.APROBADO).toList();
        List<PagosMes> porMes = new ArrayList<>();
        for (int k = 5; k >= 0; k--) {
            YearMonth mes = mesActual.minusMonths(k);
            List<Pago> delMes = aprobados.stream().filter(p -> YearMonth.from(p.getFechaPago()).equals(mes)).toList();
            BigDecimal total = delMes.stream().map(Pago::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            porMes.add(new PagosMes(mes.toString(), delMes.size(), total));
        }
        PagosMes ultimo = porMes.get(porMes.size() - 1);

        long pendientes = solicitudes.countByEstadoIn(Set.of(EstadoSolicitud.RADICADA, EstadoSolicitud.EN_REVISION));

        // Recaudo de tramites de levantamiento: cuenta exclusiva, NO se suma a los pagos de cuotas
        List<SolicitudLevantamiento> pagadas = solicitudes.findAll().stream().filter(s -> s.getValorTramite() != null).toList();
        BigDecimal recaudo = pagadas.stream().map(SolicitudLevantamiento::getValorTramite).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Tablero(hoy, clientes.count(), lista.size(), alDia, enMora, cancelados, cartera, vencida, indice,
                ultimo.cantidad(), ultimo.total(), pendientes, porSemaforo, porMes, recaudo, pagadas.size(), cuentaRecaudo);
    }
}
