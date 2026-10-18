package com.portalcreditos.entity.enums;

/** Medios de pago. Los marcados SIMULADO no procesan dinero real. Coincide con la restriccion CHECK de la base de datos. */
public enum MedioPago {
    PSE_SIMULADO, TARJETA_SIMULADA, EFECTIVO, TRANSFERENCIA, CONSIGNACION
}
