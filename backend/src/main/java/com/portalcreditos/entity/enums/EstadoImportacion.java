package com.portalcreditos.entity.enums;

/** Resultado de una importacion de archivo. Coincide con la restriccion CHECK de la base de datos. */
public enum EstadoImportacion {
    PROCESANDO, COMPLETADA, COMPLETADA_CON_ERRORES, FALLIDA
}
