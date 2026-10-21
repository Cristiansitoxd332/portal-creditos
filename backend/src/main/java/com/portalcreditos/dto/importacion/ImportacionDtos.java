package com.portalcreditos.dto.importacion;

import com.portalcreditos.entity.enums.EstadoImportacion;
import com.portalcreditos.entity.enums.TipoCarga;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTO de la importacion de archivos Excel/CSV. */
public final class ImportacionDtos {

    private ImportacionDtos() {
    }

    public record ErrorFila(int fila, String campo, String mensaje) {
    }

    /** Resultado de validar (vista previa) o de importar un archivo. */
    public record ResultadoImportacion(
            Long importacionId,
            TipoCarga tipo,
            String nombreArchivo,
            boolean guardado,
            int totalFilas,
            int filasValidas,
            int filasConError,
            EstadoImportacion estado,
            List<ErrorFila> errores,
            List<Map<String, String>> muestra
    ) {
    }

    public record ImportacionInfo(
            Long id,
            TipoCarga tipo,
            String nombreArchivo,
            LocalDateTime fecha,
            String usuario,
            int totalFilas,
            int filasExitosas,
            int filasConError,
            EstadoImportacion estado
    ) {
    }
}
