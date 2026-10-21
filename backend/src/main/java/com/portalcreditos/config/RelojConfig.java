package com.portalcreditos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reloj de la aplicacion. Toda la logica que depende de "hoy" (cuotas vencidas,
 * dias de mora, proxima cuota) lo usa en lugar de LocalDate.now().
 *
 * - Si app.fecha-sistema esta vacio, se usa la fecha real.
 * - Si tiene una fecha (ej. 2026-10-17), la aplicacion "vive" en ese dia, pero la hora sigue avanzando.
 *   Sirve para que los escenarios de los datos ficticios se vean igual en cualquier demostracion,
 *   y para que las pruebas automaticas sean repetibles.
 */
@Configuration
public class RelojConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Bean
    public Clock reloj(@Value("${app.fecha-sistema:}") String fechaSistema) {
        Clock real = Clock.system(ZONA);
        if (fechaSistema == null || fechaSistema.isBlank()) {
            return real;
        }
        LocalDate hoyReal = LocalDate.now(real);
        LocalDate fechaSimulada = LocalDate.parse(fechaSistema.trim());
        Duration desfase = Duration.between(hoyReal.atStartOfDay(ZONA), fechaSimulada.atStartOfDay(ZONA));
        return Clock.offset(real, desfase);
    }
}
