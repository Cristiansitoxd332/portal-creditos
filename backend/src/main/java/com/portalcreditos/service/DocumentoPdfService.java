package com.portalcreditos.service;

import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
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

    private final QrService qrService;
    private final String entidad;

    public DocumentoPdfService(QrService qrService, @Value("${app.documentos.entidad}") String entidad) {
        this.qrService = qrService;
        this.entidad = entidad;
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

    public byte[] pazYSalvo(DatosPazYSalvo d) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 60, 60, 110, 70);
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        doc.addTitle("Paz y salvo " + d.numeroCredito());
        doc.addCreator("Portal de Consulta de Creditos - prototipo");
        doc.open();
        encabezado(writer, doc, "CERTIFICADO DE PAZ Y SALVO");
        marcaDeAgua(writer, doc);

        doc.add(parrafo("Código de verificación: " + d.codigoVerificacion(), fuente(9, Font.NORMAL, GRIS), Element.ALIGN_RIGHT, 0));
        doc.add(parrafo("Fecha de expedición: " + d.fechaEmision().format(FECHA), fuente(9, Font.NORMAL, GRIS), Element.ALIGN_RIGHT, 24));

        doc.add(parrafo(entidad.toUpperCase(CO), fuente(13, Font.BOLD, Color.BLACK), Element.ALIGN_CENTER, 4));
        doc.add(parrafo("CERTIFICA QUE:", fuente(12, Font.BOLD, Color.BLACK), Element.ALIGN_CENTER, 22));

        Paragraph cuerpo = new Paragraph();
        cuerpo.setAlignment(Element.ALIGN_JUSTIFIED);
        cuerpo.setLeading(18);
        Font normal = fuente(11, Font.NORMAL, Color.BLACK);
        Font negrita = fuente(11, Font.BOLD, Color.BLACK);
        cuerpo.add(new Chunk("El(la) señor(a) ", normal));
        cuerpo.add(new Chunk(d.titular().toUpperCase(CO), negrita));
        cuerpo.add(new Chunk(", identificado(a) con " + d.tipoDocumento() + " No. ", normal));
        cuerpo.add(new Chunk(d.numeroDocumento(), negrita));
        cuerpo.add(new Chunk(", se encuentra a ", normal));
        cuerpo.add(new Chunk("PAZ Y SALVO", negrita));
        cuerpo.add(new Chunk(" por todo concepto con la obligación No. ", normal));
        cuerpo.add(new Chunk(d.numeroCredito(), negrita));
        cuerpo.add(new Chunk(", otorgada el " + d.fechaDesembolso().format(FECHA) + " por valor de "
                + pesos(d.valorFinanciado()) + " para la financiación del vehículo " + d.vehiculo() + " de placa ", normal));
        cuerpo.add(new Chunk(d.placa(), negrita));
        cuerpo.add(new Chunk(". La obligación fue cancelada en su totalidad el "
                + (d.fechaCancelacion() != null ? d.fechaCancelacion().format(FECHA) : "(fecha no registrada)") + ".", normal));
        cuerpo.setSpacingAfter(16);
        doc.add(cuerpo);

        doc.add(parrafo("Con este certificado el titular puede iniciar el trámite de levantamiento de la prenda "
                + "sobre el vehículo, de acuerdo con los requisitos establecidos por la entidad.", normal, Element.ALIGN_JUSTIFIED, 30));

        // Bloque de verificacion con QR
        PdfPTable tabla = new PdfPTable(new float[]{1f, 3f});
        tabla.setWidthPercentage(100);
        PdfPCell celdaQr = new PdfPCell(new Phrase(" "));
        celdaQr.setFixedHeight(110);
        celdaQr.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(celdaQr);
        PdfPCell texto = new PdfPCell();
        texto.setBorder(Rectangle.NO_BORDER);
        texto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        texto.addElement(parrafo("Verifique la autenticidad de este documento", fuente(10, Font.BOLD, Color.BLACK), Element.ALIGN_LEFT, 4));
        texto.addElement(parrafo("Escanee el código QR o ingrese a:", fuente(9, Font.NORMAL, GRIS), Element.ALIGN_LEFT, 2));
        texto.addElement(parrafo(d.urlVerificacion(), fuente(9, Font.NORMAL, AZUL), Element.ALIGN_LEFT, 2));
        texto.addElement(parrafo("Código: " + d.codigoVerificacion(), fuente(9, Font.BOLD, Color.BLACK), Element.ALIGN_LEFT, 0));
        tabla.addCell(texto);
        doc.add(tabla);

        // El QR se dibuja en la posicion de la primera celda
        float qrY = writer.getVerticalPosition(true) + 5;
        dibujarQr(writer.getDirectContent(), qrService.generar(d.urlVerificacion()), doc.left(), qrY, 100);

        pieDePagina(writer, doc);
        doc.close();
        return salida.toByteArray();
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
