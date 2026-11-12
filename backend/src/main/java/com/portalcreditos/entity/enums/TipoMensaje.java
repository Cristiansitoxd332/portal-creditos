package com.portalcreditos.entity.enums;

/** Tipo de mensaje enviado por la entidad al cliente. Coincide con la restriccion CHECK de la base de datos. */
public enum TipoMensaje {
    /** La cuota vence hoy (dia de corte 5 o 20). */
    VENCE_HOY,
    /** La cuota vence en los proximos dias. */
    RECORDATORIO,
    /** El credito tiene cuotas vencidas. */
    MORA,
    /** Mensaje libre escrito por el administrador. */
    GENERAL
}
