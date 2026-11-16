package com.portalcreditos.service;

import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.portalcreditos.dto.pago.PagoDtos.AplicacionInfo;
import com.portalcreditos.dto.pago.PagoDtos.Comprobante;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera los documentos PDF del prototipo con OpenPDF:
 * - Paz y salvo con codigo QR de verificacion.
 * - Comprobante de pago.
 *
 * TODOS llevan la marca de agua "DOCUMENTO DE PRUEBA - SIN VALIDEZ" porque se generan
 * con datos ficticios y no tienen valor legal.
 */
@Service
public class DocumentoPdfService {

    /** Azul de la identidad de Rentar (encabezados). El rojo se reserva para avisos. */
    private static final Color AZUL = new Color(0x15, 0x65, 0xC0);
    private static final Color ROJO = new Color(0xC6, 0x28, 0x28);
    private static final Color GRIS = new Color(0x5F, 0x63, 0x68);
    private static final Color GRIS_CLARO = new Color(0xF2, 0xF2, 0xF3);
    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", CO);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", CO);

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM", CO);

    private final QrService qrService;
    private final String entidad;
    private final String razonSocial;
    private final String nit;
    private final String ciudadFirma;
    private final String elaboro;
    private final String rutaFirma;
    private final String pie;
    private final boolean marcaDeAguaPazYSalvo;
    private final boolean qrPazYSalvo;
    private final String bancoTramite;
    private final String cuentaTramite;
    private final String correoGarantias;
    private final String pieLevantamiento;

    public DocumentoPdfService(QrService qrService,
                               @Value("${app.documentos.entidad}") String entidad,
                               @Value("${app.documentos.razon-social:RENTAR INVERSIONES LTDA}") String razonSocial,
                               @Value("${app.documentos.nit:813.007.547-8}") String nit,
                               @Value("${app.documentos.ciudad-firma:Neiva}") String ciudadFirma,
                               @Value("${app.documentos.elaboro:Cristian Sarmiento}") String elaboro,
                               @Value("${app.documentos.firma:firma/firma.png}") String rutaFirma,
                               @Value("${app.documentos.pie:Carrera 5 n 15-01 oficina 201}") String pie,
                               @Value("${app.documentos.paz-y-salvo.marca-de-agua:false}") boolean marcaDeAguaPazYSalvo,
                               @Value("${app.documentos.paz-y-salvo.qr:true}") boolean qrPazYSalvo,
                               @Value("${app.levantamiento.formato.banco:Bancolombia}") String bancoTramite,
                               @Value("${app.levantamiento.formato.cuenta:cuenta de ahorros # 45548922341 a nombre de Rentar Inversiones ltda nit 813.007.547-8.}") String cuentaTramite,
                               @Value("${app.levantamiento.formato.correo:rentargarantiasmobiliarias@gmail.com}") String correoGarantias,
                               @Value("${app.levantamiento.formato.pie:Cra 5 # 15-01 Tel 3108898007 rentargarantiasmobiliarias@gmail.com}") String pieLevantamiento) {
        this.bancoTramite = bancoTramite;
        this.cuentaTramite = cuentaTramite;
        this.correoGarantias = correoGarantias;
        this.pieLevantamiento = pieLevantamiento;
        this.marcaDeAguaPazYSalvo = marcaDeAguaPazYSalvo;
        this.qrPazYSalvo = qrPazYSalvo;
        this.qrService = qrService;
        this.entidad = entidad;
        this.razonSocial = razonSocial;
        this.nit = nit;
        this.ciudadFirma = ciudadFirma;
        this.elaboro = elaboro;
        this.rutaFirma = rutaFirma;
        this.pie = pie;
    }

    /** Datos que necesita el paz y salvo. */
    public record DatosPazYSalvo(
            String titular,
            String tipoDocumento,
            String numeroDocumento,
            String numeroCredito,
            String vehiculo,
            String placa,
            BigDecimal valorFinanciado,
            LocalDate fechaDesembolso,
            LocalDate fechaCancelacion,
            String codigoVerificacion,
            String urlVerificacion,
            LocalDateTime fechaEmision
    ) {
    }

    // ================================================================== paz y salvo

    /**
     * Paz y salvo con el formato de Rentar Inversiones: logo, razon social y NIT, "CERTIFICA QUE:",
     * texto del credito de motocicleta a PAZ Y SALVO con la fecha de cancelacion, fecha de firma (el dia en que
     * se descarga), firma del area de cartera, "ELABORO", codigo QR de verificacion y pie con los datos de la oficina.
     * Conserva la marca de agua de documento de prueba (prototipo con datos ficticios).
     */
    public byte[] pazYSalvo(DatosPazYSalvo d) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 72, 72, 175, 95);
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        doc.addTitle("Paz y salvo " + d.numeroCredito());
        doc.addCreator("Portal de creditos Rentar - prototipo");
        doc.open();
        if (marcaDeAguaPazYSalvo) {
            marcaDeAgua(writer, doc);
        }
        PdfContentByte cb = writer.getDirectContent();
        Rectangle pagina = doc.getPageSize();

        // Encabezado: logo + razon social y NIT
        Image logo = imagenRecurso("/documentos/logo-rentar.png");
        if (logo != null) {
            logo.scaleToFit(92, 105);
            logo.setAbsolutePosition(62, pagina.getHeight() - 40 - logo.getScaledHeight());
            agregarImagen(cb, logo);
        }
        float centro = pagina.getWidth() / 2 + 45;
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(razonSocial, serif(19, Font.BOLD)),
                centro, pagina.getHeight() - 78, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase("NIT. " + nit, serif(19, Font.BOLD)),
                centro, pagina.getHeight() - 100, 0);

        doc.add(parrafo("CERTIFICA QUE:", fuente(13, Font.BOLD, Color.BLACK), Element.ALIGN_CENTER, 48));

        Font normal = fuente(11.5f, Font.NORMAL, Color.BLACK);
        Font negrita = fuente(11.5f, Font.BOLD, Color.BLACK);
        Paragraph cuerpo = new Paragraph();
        cuerpo.setAlignment(Element.ALIGN_JUSTIFIED);
        cuerpo.setLeading(21);
        cuerpo.add(new Chunk("El crédito de Motocicleta ", normal));
        cuerpo.add(new Chunk("N°" + d.numeroCredito(), negrita));
        cuerpo.add(new Chunk(" correspondiente al señor(a) ", normal));
        cuerpo.add(new Chunk(d.titular().toUpperCase(CO), negrita));
        cuerpo.add(new Chunk(" con " + nombreDocumento(d.tipoDocumento()) + " ", normal));
        cuerpo.add(new Chunk(d.numeroDocumento(), negrita));
        cuerpo.add(new Chunk(" a la fecha se encuentra a ", normal));
        cuerpo.add(new Chunk("PAZ\u00a0Y\u00a0SALVO\u00a0" + (d.fechaCancelacion() != null ? d.fechaCancelacion().format(DDMMYYYY) : ""), negrita));
        cuerpo.setSpacingAfter(46);
        doc.add(cuerpo);

        // Fecha de firma: el dia en que se descarga (tiempo real)
        LocalDateTime emision = d.fechaEmision();
        Paragraph constancia = new Paragraph();
        constancia.setAlignment(Element.ALIGN_JUSTIFIED);
        constancia.setLeading(21);
        constancia.add(new Chunk("Para constancia se firma en " + ciudadFirma + ", a los (" + emision.getDayOfMonth()
                + ") días del mes de ", normal));
        constancia.add(new Chunk(emision.format(MES), negrita));
        constancia.add(new Chunk(" de " + emision.getYear() + ".", normal));
        constancia.setSpacingAfter(4);
        doc.add(constancia);

        // Firma (imagen local, no se sube a GitHub) o linea para firmar
        float yFirma = writer.getVerticalPosition(true) - 64;
        Image firma = imagenArchivo(rutaFirma);
        if (firma != null) {
            firma.scaleToFit(175, 66);
            firma.setAbsolutePosition(doc.left() - 6, yFirma);
            agregarImagen(cb, firma);
        } else {
            cb.saveState();
            cb.setColorFill(Color.BLACK);
            cb.rectangle(doc.left(), yFirma + 6, 170, 0.8f);
            cb.fill();
            cb.restoreState();
        }
        doc.add(parrafo(" ", fuente(10, Font.NORMAL, Color.BLACK), Element.ALIGN_LEFT, 52));
        doc.add(parrafo("ÁREA DE CARTERA", fuente(11, Font.BOLD, Color.BLACK), Element.ALIGN_LEFT, 2));
        doc.add(parrafo("RENTAR INVERSIONES", fuente(11, Font.BOLD, Color.BLACK), Element.ALIGN_LEFT, 30));
        doc.add(parrafo("ELABORÓ: " + elaboro.toUpperCase(CO), fuente(7.5f, Font.BOLD, Color.BLACK), Element.ALIGN_LEFT, 26));

        // Codigo QR pequeno en la esquina inferior derecha para verificar que el documento es autentico
        if (qrPazYSalvo) {
            float lado = 54;
            float x = pagina.getWidth() - doc.rightMargin() - lado;
            dibujarQr(cb, qrService.generar(d.urlVerificacion()), x, 100, lado);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(d.codigoVerificacion(), fuente(6, Font.NORMAL, GRIS)),
                    x + lado / 2, 93, 0);
        }

        pieRentar(cb, pagina);
        doc.close();
        return salida.toByteArray();
    }

    /** Datos para el formato oficial de solicitud de levantamiento de prenda. */
    public record DatosSolicitudLevantamiento(
            java.time.LocalDate fecha,
            String ciudad,
            String titular,
            String tipoDocumento,
            String numeroDocumento,
            String marca,
            String modelo,
            String color,
            String numeroChasis,
            String numeroMotor,
            String placa,
            String celular,
            String direccion,
            String correo,
            String numeroCredito,
            String pagoTramitePortal,
            String cuentaRecaudo
    ) {
    }

    /**
     * Formato "Solicitud de levantamiento de prenda" de Rentar Inversiones, prellenado con los datos del
     * titular y de la moto. Los datos que no estan en el sistema quedan con linea para llenarlos a mano.
     */
    public byte[] solicitudLevantamiento(DatosSolicitudLevantamiento d) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 80, 80, 130, 80);
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        doc.addTitle("Solicitud de levantamiento de prenda " + d.numeroCredito());
        doc.addCreator("Portal de creditos Rentar");
        doc.open();
        PdfContentByte cb = writer.getDirectContent();
        Rectangle pagina = doc.getPageSize();

        Image logo = imagenRecurso("/documentos/logo-rentar.png");
        if (logo != null) {
            logo.scaleToFit(70, 80);
            logo.setAbsolutePosition(80, pagina.getHeight() - 30 - logo.getScaledHeight());
            agregarImagen(cb, logo);
        }
        float centro = pagina.getWidth() / 2 + 30;
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(razonSocial, serif(14, Font.BOLD)), centro, pagina.getHeight() - 62, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase("NIT " + nit, serif(14, Font.BOLD)), centro, pagina.getHeight() - 79, 0);

        Font normal = fuente(10.5f, Font.NORMAL, Color.BLACK);
        Font negrita = fuente(10.5f, Font.BOLD, Color.BLACK);
        String ciudad = vacio(d.ciudad()) ? ciudadFirma : d.ciudad();

        Paragraph fecha = new Paragraph();
        fecha.add(new Chunk(ciudad + ", " + d.fecha().format(MES) + " " + d.fecha().getDayOfMonth() + " de " + d.fecha().getYear(), normal));
        fecha.setSpacingAfter(26);
        doc.add(fecha);

        Paragraph yo = new Paragraph();
        yo.setAlignment(Element.ALIGN_JUSTIFIED);
        yo.setLeading(15);
        yo.add(new Chunk("Yo, ", normal));
        yo.add(new Chunk(d.titular().toUpperCase(CO), negrita));
        yo.add(new Chunk(", mayor de edad, con " + nombreDocumento(d.tipoDocumento()) + " No. ", normal));
        yo.add(new Chunk(d.numeroDocumento(), negrita));
        yo.add(new Chunk(" vecino de ", normal));
        yo.add(new Chunk(ciudad, negrita));
        yo.add(new Chunk(", identificado como aparece al pie de mi firma, obrando en mi calidad de Propietario Inscrito de la "
                + "motocicleta que describo a continuación, solicito a ustedes se sirvan cancelar la Prenda sin Tenencia del "
                + "Acreedor (Rentar Inversiones ltda) que se encuentra registrada a su favor.", normal));
        yo.setSpacingAfter(12);
        doc.add(yo);

        PdfPTable moto = new PdfPTable(new float[]{1.3f, 3f});
        moto.setWidthPercentage(62);
        moto.setHorizontalAlignment(Element.ALIGN_LEFT);
        String[][] filas = {
                {"MOTO:", vacio(d.marca()) ? "YAMAHA" : d.marca().toUpperCase(CO)},
                {"MODELO:", d.modelo()}, {"COLOR:", d.color()}, {"NÚMERO CHASIS:", d.numeroChasis()},
                {"NÚMERO MOTOR:", d.numeroMotor()}, {"PLACA:", d.placa()},
        };
        for (String[] f : filas) {
            PdfPCell etiqueta = new PdfPCell(new Phrase(f[0], normal));
            etiqueta.setBorder(Rectangle.NO_BORDER);
            etiqueta.setPaddingBottom(2);
            moto.addCell(etiqueta);
            PdfPCell valor = new PdfPCell(new Phrase(vacio(f[1]) ? " " : f[1], negrita));
            valor.setBorder(f[0].equals("MOTO:") ? Rectangle.NO_BORDER : Rectangle.BOTTOM);
            valor.setPaddingBottom(2);
            moto.addCell(valor);
        }
        moto.setSpacingAfter(12);
        doc.add(moto);

        doc.add(parrafo("Lo anterior por cuanto me encuentro a Paz y Salvo con Ustedes.", normal, Element.ALIGN_LEFT, 12));
        doc.add(parrafo("Anexo:", normal, Element.ALIGN_LEFT, 6));
        com.lowagie.text.List anexos = new com.lowagie.text.List(false, 12);
        anexos.setListSymbol(new Chunk("\u2022", normal));
        anexos.add(new com.lowagie.text.ListItem("Fotocopia de Mi cédula de ciudadanía 100%", normal));
        anexos.add(new com.lowagie.text.ListItem("Fotocopia de la tarjeta de propiedad de la motocicleta 100%\n"
                + "en caso de que no cuentes con este requisito, deberás enviarnos el denuncio por pérdida de documento, con las "
                + "especificaciones de la motocicleta o en su defecto el certificado de Libertad y Tradición de la motocicleta", normal));
        Phrase consignacion = new Phrase();
        consignacion.add(new Chunk("Soporte de la consignación por valor de $ 30.000= esta se debe realizar por ", normal));
        consignacion.add(new Chunk(bancoTramite, negrita));
        consignacion.add(new Chunk(" " + cuentaTramite, normal));
        anexos.add(new com.lowagie.text.ListItem(consignacion));
        anexos.add(new com.lowagie.text.ListItem("También puede pagar el trámite desde el portal (PSE, Efecty o consignación) en la cuenta de recaudo N.° "
                + d.cuentaRecaudo() + ", exclusiva para levantamientos.", normal));
        if (!vacio(d.pagoTramitePortal())) {
            anexos.add(new com.lowagie.text.ListItem(d.pagoTramitePortal(), negrita));
        }
        doc.add(anexos);

        doc.add(parrafo(" ", normal, Element.ALIGN_LEFT, 22));
        doc.add(parrafo("_______________________", normal, Element.ALIGN_LEFT, 0));
        doc.add(parrafo("(Firma y c.c. del propietario)", normal, Element.ALIGN_LEFT, 14));

        doc.add(parrafo("Teléfono: ______________________   Celular: " + linea(d.celular(), 18), normal, Element.ALIGN_LEFT, 2));
        doc.add(parrafo("Dirección: " + linea(d.direccion(), 32) + "   Ciudad: " + linea(d.ciudad(), 16), normal, Element.ALIGN_LEFT, 2));
        doc.add(parrafo("Departamento: __________________", normal, Element.ALIGN_LEFT, 2));
        doc.add(parrafo("E-Mail: " + linea(d.correo(), 40), normal, Element.ALIGN_LEFT, 16));

        doc.add(parrafo("INSTRUCCIONES:", negrita, Element.ALIGN_LEFT, 4));
        com.lowagie.text.List instrucciones = new com.lowagie.text.List(true, 14);
        instrucciones.add(new com.lowagie.text.ListItem("La presente comunicación debe traer la diligencia Notarial denominada "
                + "\u201cRECONOCIMIENTO DE FIRMA Y DE DOCUMENTO\u201d", normal));
        instrucciones.add(new com.lowagie.text.ListItem("Todos los demás documentos anexos deben aportarse autenticados.", normal));
        instrucciones.add(new com.lowagie.text.ListItem("Sin los documentos 1. y 2. diligenciados, no se expide el Levantamiento de Prenda.", normal));
        instrucciones.add(new com.lowagie.text.ListItem("Los documentos anexos deben aportarse de carácter obligatorio", normal));
        instrucciones.add(new com.lowagie.text.ListItem("Todos los documentos deberán ser enviados al correo de " + correoGarantias, normal));
        doc.add(instrucciones);

        // Pie con linea
        cb.saveState();
        cb.setColorFill(Color.BLACK);
        cb.rectangle(80, 50, pagina.getWidth() - 160, 0.7f);
        cb.fill();
        cb.restoreState();
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(pieLevantamiento, fuente(9, Font.NORMAL, Color.BLACK)),
                pagina.getWidth() / 2, 38, 0);
        doc.close();
        return salida.toByteArray();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /** El valor si existe; si no, una linea para llenar a mano. */
    private static String linea(String valor, int largo) {
        return vacio(valor) ? "_".repeat(largo) : valor;
    }

    /** Pie con los datos de la oficina (como el formato fisico) y la advertencia de prototipo. */
    private void pieRentar(PdfContentByte cb, Rectangle pagina) {
        float y = 72;
        for (String linea : pie.split("\\|")) {
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(linea.trim(), serif(10.5f, Font.NORMAL)),
                    pagina.getWidth() / 2, y, 0);
            y -= 14;
        }
        if (marcaDeAguaPazYSalvo) {
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("DOCUMENTO DE PRUEBA – generado por un prototipo con datos ficticios; no reemplaza el documento oficial.",
                            fuente(7.5f, Font.BOLD, ROJO)), pagina.getWidth() / 2, 18, 0);
        }
    }

    private static String nombreDocumento(String tipo) {
        if (tipo == null) {
            return "cédula de ciudadanía";
        }
        return switch (tipo) {
            case "CE" -> "cédula de extranjería";
            case "PAS" -> "pasaporte";
            case "NIT" -> "NIT";
            default -> "cédula de ciudadanía";
        };
    }

    private Image imagenRecurso(String ruta) {
        try (java.io.InputStream in = getClass().getResourceAsStream(ruta)) {
            return in == null ? null : Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private static Image imagenArchivo(String ruta) {
        try {
            java.nio.file.Path p = java.nio.file.Path.of(ruta);
            return java.nio.file.Files.isRegularFile(p) ? Image.getInstance(java.nio.file.Files.readAllBytes(p)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void agregarImagen(PdfContentByte cb, Image img) {
        try {
            cb.addImage(img);
        } catch (Exception e) {
            // si la imagen no se puede dibujar, el documento sale sin ella
        }
    }

    private static Font serif(float tamano, int estilo) {
        return new Font(Font.TIMES_ROMAN, tamano, estilo, Color.BLACK);
    }

    // ================================================================== comprobante de pago

    public byte[] comprobantePago(Comprobante c) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 60, 60, 110, 70);
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        doc.addTitle("Comprobante " + c.referencia());
        doc.open();
        encabezado(writer, doc, "COMPROBANTE DE PAGO");
        marcaDeAgua(writer, doc);

        boolean aprobado = "APROBADO".equals(String.valueOf(c.estado()));
        doc.add(parrafo(aprobado ? "PAGO APROBADO" : "PAGO RECHAZADO",
                fuente(16, Font.BOLD, aprobado ? new Color(0x1E, 0x8E, 0x3E) : ROJO), Element.ALIGN_CENTER, 4));
        doc.add(parrafo(pesos(c.valor()), fuente(22, Font.BOLD, Color.BLACK), Element.ALIGN_CENTER, 18));

        PdfPTable datos = new PdfPTable(new float[]{1.3f, 2f});
        datos.setWidthPercentage(100);
        fila(datos, "Referencia", c.referencia());
        fila(datos, "Código de autorización", c.codigoAutorizacion() != null ? c.codigoAutorizacion() : "No aplica");
        fila(datos, "Fecha", c.fecha() != null ? c.fecha().format(FECHA_HORA) : "");
        fila(datos, "Crédito", c.numeroCredito());
        fila(datos, "Titular", c.cliente() + " (" + c.documentoCliente() + ")");
        fila(datos, "Medio de pago", String.valueOf(c.medioPago()).replace('_', ' '));
        fila(datos, "Canal", String.valueOf(c.canal()));
        fila(datos, "Procesado por", c.pasarela());
        if (c.valorMora() != null && c.valorMora().signum() > 0) {
            fila(datos, "Mora por días de atraso", pesos(c.valorMora()));
            fila(datos, "Aplicado a cuotas", pesos(c.valor().subtract(c.valorMora())));
        }
        if (c.saldoCapitalAnterior() != null && c.saldoCapitalNuevo() != null) {
            fila(datos, "Saldo de capital anterior", pesos(c.saldoCapitalAnterior()));
            fila(datos, "Saldo de capital actual", pesos(c.saldoCapitalNuevo()));
        }
        datos.setSpacingAfter(16);
        doc.add(datos);

        if (c.aplicaciones() != null && !c.aplicaciones().isEmpty()) {
            doc.add(parrafo("Aplicación del pago a cuotas", fuente(11, Font.BOLD, Color.BLACK), Element.ALIGN_LEFT, 6));
            PdfPTable cuotas = new PdfPTable(new float[]{1f, 2f, 2f});
            cuotas.setWidthPercentage(100);
            for (String t : new String[]{"Cuota", "Valor aplicado", "Estado de la cuota"}) {
                PdfPCell h = new PdfPCell(new Phrase(t, fuente(9, Font.BOLD, Color.WHITE)));
                h.setBackgroundColor(AZUL);
                h.setPadding(5);
                cuotas.addCell(h);
            }
            for (AplicacionInfo a : c.aplicaciones()) {
                cuotas.addCell(celda("No. " + a.numeroCuota()));
                cuotas.addCell(celda(pesos(a.valorAplicado())));
                cuotas.addCell(celda(String.valueOf(a.estadoCuota())));
            }
            doc.add(cuotas);
        }

        doc.add(parrafo(" ", fuente(6, Font.NORMAL, GRIS), Element.ALIGN_LEFT, 10));
        doc.add(parrafo(c.aviso(), fuente(9, Font.ITALIC, GRIS), Element.ALIGN_JUSTIFIED, 0));
        pieDePagina(writer, doc);
        doc.close();
        return salida.toByteArray();
    }

    // ================================================================== utilidades de dibujo

    private void encabezado(PdfWriter writer, Document doc, String titulo) {
        PdfContentByte cb = writer.getDirectContent();
        Rectangle pagina = doc.getPageSize();
        cb.saveState();
        cb.setColorFill(AZUL);
        cb.rectangle(0, pagina.getHeight() - 70, pagina.getWidth(), 70);
        cb.fill();
        cb.restoreState();
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(titulo, fuente(16, Font.BOLD, Color.WHITE)), doc.left(), pagina.getHeight() - 38, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("Portal de Consulta de Créditos Vehiculares · Prototipo académico", fuente(9, Font.NORMAL, Color.WHITE)),
                doc.left(), pagina.getHeight() - 55, 0);
    }

    private void marcaDeAgua(PdfWriter writer, Document doc) {
        PdfContentByte cb = writer.getDirectContentUnder();
        Rectangle pagina = doc.getPageSize();
        cb.saveState();
        PdfGState transparencia = new PdfGState();
        transparencia.setFillOpacity(0.10f);
        cb.setGState(transparencia);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("DOCUMENTO DE PRUEBA", fuente(54, Font.BOLD, ROJO)),
                pagina.getWidth() / 2, pagina.getHeight() / 2 + 30, 45);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("SIN VALIDEZ", fuente(54, Font.BOLD, ROJO)),
                pagina.getWidth() / 2 + 40, pagina.getHeight() / 2 - 40, 45);
        cb.restoreState();
    }

    private void pieDePagina(PdfWriter writer, Document doc) {
        PdfContentByte cb = writer.getDirectContent();
        Rectangle pagina = doc.getPageSize();
        cb.saveState();
        cb.setColorFill(GRIS_CLARO);
        cb.rectangle(0, 0, pagina.getWidth(), 50);
        cb.fill();
        cb.restoreState();
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("DOCUMENTO DE PRUEBA – SIN VALIDEZ LEGAL. Generado por un prototipo académico con datos ficticios.",
                        fuente(8, Font.BOLD, ROJO)), pagina.getWidth() / 2, 28, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("No está conectado a los sistemas de ninguna entidad ni reemplaza los documentos oficiales.",
                        fuente(8, Font.NORMAL, GRIS)), pagina.getWidth() / 2, 16, 0);
    }

    /** Dibuja el QR como cuadros negros; queda vectorial y nitido a cualquier zoom. */
    private void dibujarQr(PdfContentByte cb, BitMatrix matriz, float x, float y, float tamano) {
        float modulo = tamano / matriz.getWidth();
        cb.saveState();
        cb.setColorFill(Color.BLACK);
        for (int fila = 0; fila < matriz.getHeight(); fila++) {
            for (int col = 0; col < matriz.getWidth(); col++) {
                if (matriz.get(col, fila)) {
                    cb.rectangle(x + col * modulo, y + tamano - (fila + 1) * modulo, modulo, modulo);
                }
            }
        }
        cb.fill();
        cb.restoreState();
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, fuente(9, Font.BOLD, GRIS)));
        e.setBackgroundColor(GRIS_CLARO);
        e.setPadding(6);
        e.setBorderColor(Color.WHITE);
        tabla.addCell(e);
        PdfPCell v = new PdfPCell(new Phrase(valor == null ? "" : valor, fuente(10, Font.NORMAL, Color.BLACK)));
        v.setPadding(6);
        v.setBorderColor(GRIS_CLARO);
        tabla.addCell(v);
    }

    private static PdfPCell celda(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente(9, Font.NORMAL, Color.BLACK)));
        c.setPadding(5);
        c.setBorderColor(GRIS_CLARO);
        return c;
    }

    private static Paragraph parrafo(String texto, Font fuente, int alineacion, float espacioDespues) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(alineacion);
        p.setSpacingAfter(espacioDespues);
        return p;
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return new Font(Font.HELVETICA, tamano, estilo, color);
    }

    static String pesos(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        NumberFormat f = NumberFormat.getNumberInstance(CO);
        f.setMaximumFractionDigits(0);
        return "$" + f.format(valor);
    }
}
