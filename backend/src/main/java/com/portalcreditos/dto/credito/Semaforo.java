package com.portalcreditos.dto.credito;

/**
 * Indicador visual del estado del credito para el cliente.
 * PROXIMA_A_VENCER: al dia, pero la proxima cuota vence en pocos dias (por defecto 5).
 */
public enum Semaforo {
    AL_DIA, PROXIMA_A_VENCER, EN_MORA, CANCELADO
}
