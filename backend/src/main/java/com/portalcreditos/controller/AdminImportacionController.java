package com.portalcreditos.controller;

import com.portalcreditos.dto.importacion.ImportacionDtos.ImportacionInfo;
import com.portalcreditos.dto.importacion.ImportacionDtos.ResultadoImportacion;
import com.portalcreditos.entity.enums.TipoCarga;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.importacion.ImportacionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * Importacion de archivos Excel/CSV (solo rol ADMIN).
 * Tipos: clientes, creditos, pagos. El archivo se envia en el campo "archivo" (multipart/form-data).
 */
@RestController
@RequestMapping("/api/admin/importaciones")
public class AdminImportacionController {

    private final ImportacionService servicio;
    private final UsuarioActual usuario;

    public AdminImportacionController(ImportacionService servicio, UsuarioActual usuario) {
        this.servicio = servicio;
        this.usuario = usuario;
    }

    /** Descarga la plantilla Excel del tipo indicado. */
    @GetMapping("/plantillas/{tipo}")
    public ResponseEntity<byte[]> plantilla(@PathVariable String tipo) {
        TipoCarga t = tipo(tipo);
        return Descargas.excel(servicio.plantilla(t), "plantilla-" + t.name().toLowerCase(Locale.ROOT) + ".xlsx");
    }

    /** Valida el archivo y muestra los errores por fila, SIN guardar nada. */
    @PostMapping(value = "/{tipo}/vista-previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultadoImportacion vistaPrevia(@PathVariable String tipo, @RequestParam("archivo") MultipartFile archivo) {
        return servicio.previsualizar(tipo(tipo), archivo);
    }

    /** Importa las filas validas y registra el resultado en el historial. */
    @PostMapping(value = "/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultadoImportacion importar(@PathVariable String tipo, @RequestParam("archivo") MultipartFile archivo,
                                         HttpServletRequest request) {
        return servicio.importar(tipo(tipo), archivo, usuario.getUsername(), request.getRemoteAddr());
    }

    @GetMapping
    public List<ImportacionInfo> historial() {
        return servicio.historial();
    }

    @GetMapping("/{id:\\d+}")
    public ResultadoImportacion detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    private static TipoCarga tipo(String texto) {
        try {
            return TipoCarga.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw com.portalcreditos.exception.NegocioException.solicitudInvalida(
                    "Tipo de carga no valido. Use: clientes, creditos o pagos");
        }
    }
}
