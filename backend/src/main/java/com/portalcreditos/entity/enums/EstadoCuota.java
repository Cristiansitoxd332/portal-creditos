package com.portalcreditos.entity.enums;

/** Estado almacenado de una cuota. "Vencida" no se guarda: se calcula. Coincide con la restriccion CHECK de la base de datos. */
public enum EstadoCuota {
    PENDIENTE, PARCIAL, PAGADA
}
