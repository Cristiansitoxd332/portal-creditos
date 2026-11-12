package com.portalcreditos.service.importacion;

import com.portalcreditos.dto.admin.AdminDtos.ClienteRequest;
import com.portalcreditos.dto.admin.AdminDtos.CreditoRequest;
import com.portalcreditos.dto.importacion.ImportacionDtos.ErrorFila;
import com.portalcreditos.dto.importacion.ImportacionDtos.ImportacionInfo;
import com.portalcreditos.dto.importacion.ImportacionDtos.ResultadoImportacion;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Importacion;
import com.portalcreditos.entity.ImportacionError;
import com.portalcreditos.entity.Usuario;
import com.portalcreditos.entity.enums.ClaseVehiculo;
import com.portalcreditos.entity.enums.EstadoImportacion;
import com.portalcreditos.entity.enums.MedioPago;
import com.portalcreditos.entity.enums.TipoCarga;
import com.portalcreditos.entity.enums.TipoDocumento;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.ClienteRepository;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.ImportacionRepository;
import com.portalcreditos.repository.UsuarioRepository;
import com.portalcreditos.service.AuditoriaService;
import com.portalcreditos.service.ClienteFactory;
import com.portalcreditos.service.CreditoFactory;
import com.portalcreditos.service.DistribuidorPagos;
import com.portalcreditos.service.PagoService;
import com.portalcreditos.service.importacion.LectorArchivos.Fila;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * IMPORTACION DE ARCHIVOS EXCEL / CSV hacia la base de datos propia del prototipo.
 *
 * Es el mecanismo propuesto para actualizar la informacion SIN acceso directo a la base de datos
 * de la empresa: la empresa exportaria un reporte y el administrador lo cargaria aqui.
 *
 * Flujo recomendado:
 * 1. Descargar la plantilla.
 * 2. VISTA PREVIA: valida el archivo fila por fila y muestra los errores, sin guardar nada.
 * 3. IMPORTAR: guarda solo las filas validas y registra en el historial las filas rechazadas y su motivo.
 */
@Service
public class ImportacionService {

    private static final DateTimeFormatter FECHA_CO = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final LectorArchivos lector;
    private final PlantillasImportacion plantillas;
    private final ClienteFactory clienteFactory;
    private final CreditoFactory creditoFactory;
    private final PagoService pagoService;
    private final DistribuidorPagos distribuidor;
    private final ClienteRepository clientes;
    private final CreditoRepository creditos;
    private final UsuarioRepository usuarios;
    private final ImportacionRepository importaciones;
    private final AuditoriaService auditoria;
    private final int maxFilas;
    private final String passwordInicial;

    public ImportacionService(LectorArchivos lector, PlantillasImportacion plantillas, ClienteFactory clienteFactory,
                              CreditoFactory creditoFactory, PagoService pagoService, DistribuidorPagos distribuidor,
                              ClienteRepository clientes, CreditoRepository creditos, UsuarioRepository usuarios,
                              ImportacionRepository importaciones, AuditoriaService auditoria,
                              @Value("${app.importacion.max-filas}") int maxFilas,
                              @Value("${app.importacion.password-inicial}") String passwordInicial) {
        this.lector = lector;
        this.plantillas = plantillas;
        this.clienteFactory = clienteFactory;
        this.creditoFactory = creditoFactory;
        this.pagoService = pagoService;
        this.distribuidor = distribuidor;
        this.clientes = clientes;
        this.creditos = creditos;
        this.usuarios = usuarios;
        this.importaciones = importaciones;
        this.auditoria = auditoria;
        this.maxFilas = maxFilas;
        this.passwordInicial = passwordInicial;
    }

    public byte[] plantilla(TipoCarga tipo) {
        return plantillas.plantilla(tipo);
    }

    /** Valida el archivo sin guardar nada. */
    @Transactional(readOnly = true)
    public ResultadoImportacion previsualizar(TipoCarga tipo, MultipartFile archivo) {
        List<Fila> filas = leer(tipo, archivo);
        Procesamiento p = procesar(tipo, filas, false, null);
        return new ResultadoImportacion(null, tipo, archivo.getOriginalFilename(), false, filas.size(),
                p.validas, p.errores.size(), estado(p.validas, p.errores.size()), p.errores, p.muestra);
    }

    /** Guarda las filas validas y registra la importacion en el historial. */
    @Transactional
    public ResultadoImportacion importar(TipoCarga tipo, MultipartFile archivo, String username, String ip) {
        Usuario admin = usuarios.findByUsername(username)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        List<Fila> filas = leer(tipo, archivo);
        Procesamiento p = procesar(tipo, filas, true, admin);

        Importacion imp = new Importacion();
        imp.setTipoCarga(tipo);
        imp.setNombreArchivo(recortar(archivo.getOriginalFilename() == null ? "archivo" : archivo.getOriginalFilename(), 255));
        imp.setUsuario(admin);
        imp.setTotalFilas(filas.size());
        imp.setFilasExitosas(p.validas);
        imp.setFilasConError(p.errores.size());
        imp.setEstado(estado(p.validas, p.errores.size()));
        for (ErrorFila e : p.errores) {
            ImportacionError ie = new ImportacionError();
            ie.setImportacion(imp);
            ie.setNumeroFila(e.fila());
            ie.setCampo(e.campo() == null ? null : recortar(e.campo(), 60));
            ie.setMensaje(recortar(e.mensaje(), 500));
            imp.getErrores().add(ie);
        }
        importaciones.save(imp);
        auditoria.registrar(username, "IMPORTACION_" + tipo, "importacion", String.valueOf(imp.getId()),
                imp.getNombreArchivo() + ": " + p.validas + " filas guardadas, " + p.errores.size() + " con error", ip);

        return new ResultadoImportacion(imp.getId(), tipo, imp.getNombreArchivo(), true, filas.size(),
                p.validas, p.errores.size(), imp.getEstado(), p.errores, p.muestra);
    }

    @Transactional(readOnly = true)
    public List<ImportacionInfo> historial() {
        return importaciones.findAllByOrderByFechaCargaDesc().stream()
                .map(i -> new ImportacionInfo(i.getId(), i.getTipoCarga(), i.getNombreArchivo(), i.getFechaCarga(),
                        i.getUsuario().getUsername(), i.getTotalFilas(), i.getFilasExitosas(), i.getFilasConError(),
                        i.getEstado()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResultadoImportacion detalle(Long id) {
        Importacion i = importaciones.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("Importacion no encontrada"));
        List<ErrorFila> errores = i.getErrores().stream()
                .map(e -> new ErrorFila(e.getNumeroFila(), e.getCampo(), e.getMensaje())).toList();
        return new ResultadoImportacion(i.getId(), i.getTipoCarga(), i.getNombreArchivo(), true, i.getTotalFilas(),
                i.getFilasExitosas(), i.getFilasConError(), i.getEstado(), errores, List.of());
    }

    // ================================================================== procesamiento por fila

    /** Acumula el resultado de procesar el archivo. */
    private static final class Procesamiento {
        int validas;
        final List<ErrorFila> errores = new ArrayList<>();
        final List<Map<String, String>> muestra = new ArrayList<>();
    }

    /** Error de una fila (no detiene el procesamiento del resto del archivo). */
    private static final class ErrorDeFila extends RuntimeException {
        final String campo;

        ErrorDeFila(String campo, String mensaje) {
            super(mensaje);
            this.campo = campo;
        }
    }

    private Procesamiento procesar(TipoCarga tipo, List<Fila> filas, boolean guardar, Usuario admin) {
        Procesamiento p = new Procesamiento();
        Set<String> vistos = new HashSet<>();                 // duplicados dentro del mismo archivo
        Map<String, BigDecimal> pendientePorCredito = new HashMap<>(); // para la vista previa de pagos
        for (Fila f : filas) {
            try {
                switch (tipo) {
                    case CLIENTES -> filaCliente(f, guardar, vistos);
                    case CREDITOS -> filaCredito(f, guardar, vistos);
                    case PAGOS -> filaPago(f, guardar, admin, pendientePorCredito);
                    case VEHICULOS -> throw new ErrorDeFila(null, "Los vehiculos se importan con el tipo CREDITOS");
                }
                p.validas++;
                if (p.muestra.size() < 10) {
                    p.muestra.add(f.valores());
                }
            } catch (ErrorDeFila e) {
                p.errores.add(new ErrorFila(f.numero(), e.campo, e.getMessage()));
            } catch (NegocioException | IllegalArgumentException e) {
                p.errores.add(new ErrorFila(f.numero(), null, e.getMessage()));
            }
        }
        return p;
    }

    private void filaCliente(Fila f, boolean guardar, Set<String> vistos) {
        ClienteRequest r = new ClienteRequest(
                enumeracion(TipoDocumento.class, f, "tipo_documento", true),
                obligatorio(f, "numero_documento"), obligatorio(f, "nombres"), obligatorio(f, "apellidos"),
                f.get("correo"), f.get("telefono"), f.get("direccion"), f.get("ciudad"));
        if (!vistos.add(r.numeroDocumento())) {
            throw new ErrorDeFila("numero_documento", "Documento repetido dentro del mismo archivo");
        }
        String error = clienteFactory.validar(r);
        if (error != null) {
            throw new ErrorDeFila(null, error);
        }
        if (guardar) {
            clienteFactory.crear(r, passwordInicial);
        }
    }

    private void filaCredito(Fila f, boolean guardar, Set<String> vistos) {
        String documento = obligatorio(f, "numero_documento_cliente");
        Cliente cliente = clientes.findByNumeroDocumento(documento)
                .orElseThrow(() -> new ErrorDeFila("numero_documento_cliente", "No existe un cliente con documento " + documento));
        String placa = obligatorio(f, "placa").toUpperCase(Locale.ROOT);
        if (!vistos.add(placa)) {
            throw new ErrorDeFila("placa", "Placa repetida dentro del mismo archivo");
        }
        CreditoRequest r = new CreditoRequest(cliente.getId(), placa, obligatorio(f, "marca"), obligatorio(f, "linea"),
                entero(f, "modelo"), f.get("color"), enumeracion(ClaseVehiculo.class, f, "clase", true),
                null, null, decimal(f, "valor_vehiculo", true), decimal(f, "cuota_inicial", true),
                decimal(f, "tasa_mensual", true), entero(f, "plazo_meses"),
                fecha(f, "fecha_desembolso", true), fecha(f, "fecha_primera_cuota", false), Boolean.TRUE);
        String error = creditoFactory.validar(r);
        if (error != null) {
            throw new ErrorDeFila(null, error);
        }
        if (guardar) {
            creditoFactory.crear(r);
        }
    }

    private void filaPago(Fila f, boolean guardar, Usuario admin, Map<String, BigDecimal> pendientePorCredito) {
        String numero = obligatorio(f, "numero_credito").toUpperCase(Locale.ROOT);
        Credito credito = creditos.findByNumeroCredito(numero)
                .orElseThrow(() -> new ErrorDeFila("numero_credito", "No existe el credito " + numero));
        BigDecimal valor = decimal(f, "valor", true);
        if (valor.signum() <= 0) {
            throw new ErrorDeFila("valor", "El valor debe ser mayor que cero");
        }
        MedioPago medio = enumeracion(MedioPago.class, f, "medio_pago", true);
        String obs = f.get("observacion");
        if (obs != null && obs.length() > 150) {
            throw new ErrorDeFila("observacion", "La observacion supera 150 caracteres");
        }
        if (guardar) {
            pagoService.registrarDesdeImportacion(credito.getId(), valor, medio,
                    "Importado" + (obs != null ? " - " + obs : ""), admin);
        } else {
            BigDecimal pendiente = pendientePorCredito.computeIfAbsent(numero,
                    k -> distribuidor.totalPendiente(credito.getCuotas()));
            if (pendiente.signum() == 0) {
                throw new ErrorDeFila(null, "El credito " + numero + " ya esta cancelado");
            }
            if (valor.compareTo(pendiente) > 0) {
                throw new ErrorDeFila("valor", "El valor supera el saldo pendiente del credito ($" + pendiente.toPlainString() + ")");
            }
            pendientePorCredito.put(numero, pendiente.subtract(valor));
        }
    }

    // ================================================================== conversion de valores

    private static String obligatorio(Fila f, String columna) {
        String v = f.get(columna);
        if (v == null) {
            throw new ErrorDeFila(columna, "El campo " + columna + " es obligatorio");
        }
        return v;
    }

    private static Integer entero(Fila f, String columna) {
        String v = obligatorio(f, columna);
        try {
            return new BigDecimal(v.replace(" ", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ErrorDeFila(columna, "El campo " + columna + " debe ser un numero entero");
        }
    }

    /** Acepta 75000000, 1.5 o 1,5 (coma decimal). Rechaza separadores de miles. */
    static BigDecimal decimal(Fila f, String columna, boolean requerido) {
        String v = requerido ? obligatorio(f, columna) : f.get(columna);
        if (v == null) {
            return null;
        }
        String t = v.replace("$", "").replace(" ", "");
        if (t.contains(",") && !t.contains(".")) {
            t = t.replace(',', '.');
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new ErrorDeFila(columna, "El campo " + columna + " debe ser un numero sin separadores de miles (ej. 75000000)");
        }
    }

    private static LocalDate fecha(Fila f, String columna, boolean requerido) {
        String v = requerido ? obligatorio(f, columna) : f.get(columna);
        if (v == null) {
            return null;
        }
        try {
            return v.contains("/") ? LocalDate.parse(v, FECHA_CO) : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new ErrorDeFila(columna, "La fecha de " + columna + " debe tener el formato aaaa-mm-dd o dd/mm/aaaa");
        }
    }

    private static <E extends Enum<E>> E enumeracion(Class<E> tipo, Fila f, String columna, boolean requerido) {
        String v = requerido ? obligatorio(f, columna) : f.get(columna);
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, v.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            StringBuilder opciones = new StringBuilder();
            for (E c : tipo.getEnumConstants()) {
                opciones.append(opciones.isEmpty() ? "" : ", ").append(c.name());
            }
            throw new ErrorDeFila(columna, "Valor no valido en " + columna + ": '" + v + "'. Opciones: " + opciones);
        }
    }

    // ================================================================== utilidades

    private List<Fila> leer(TipoCarga tipo, MultipartFile archivo) {
        if (tipo == TipoCarga.VEHICULOS) {
            throw NegocioException.solicitudInvalida("Los vehiculos se importan junto con el credito (tipo CREDITOS)");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw NegocioException.solicitudInvalida("Debe adjuntar un archivo .xlsx o .csv");
        }
        try (InputStream in = archivo.getInputStream()) {
            List<Fila> filas = lector.leer(archivo.getOriginalFilename(), in, maxFilas);
            List<String> faltantes = plantillas.columnas(tipo).stream()
                    .filter(PlantillasImportacion.Columna::obligatoria)
                    .map(PlantillasImportacion.Columna::nombre)
                    .filter(c -> !filas.get(0).valores().containsKey(c))
                    .toList();
            if (!faltantes.isEmpty()) {
                throw NegocioException.solicitudInvalida("Faltan columnas obligatorias en el archivo: "
                        + String.join(", ", faltantes) + ". Descargue la plantilla para ver el formato.");
            }
            return filas;
        } catch (IOException e) {
            throw NegocioException.solicitudInvalida("No fue posible leer el archivo");
        }
    }

    private static EstadoImportacion estado(int validas, int errores) {
        if (validas == 0) {
            return EstadoImportacion.FALLIDA;
        }
        return errores == 0 ? EstadoImportacion.COMPLETADA : EstadoImportacion.COMPLETADA_CON_ERRORES;
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
