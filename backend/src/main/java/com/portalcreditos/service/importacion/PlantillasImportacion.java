package com.portalcreditos.service.importacion;

import com.portalcreditos.entity.enums.TipoCarga;
import com.portalcreditos.exception.NegocioException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Columnas esperadas por cada tipo de carga y generacion de la plantilla Excel descargable
 * (hoja "datos" con encabezados y una fila de ejemplo + hoja "instrucciones").
 */
@Component
public class PlantillasImportacion {

    /** Definicion de una columna: nombre, si es obligatoria, descripcion y ejemplo. */
    public record Columna(String nombre, boolean obligatoria, String descripcion, String ejemplo) {
    }

    public List<Columna> columnas(TipoCarga tipo) {
        return switch (tipo) {
            case CLIENTES -> List.of(
                    new Columna("tipo_documento", true, "CC, CE, NIT o PAS", "CC"),
                    new Columna("numero_documento", true, "Sin puntos ni espacios (sera el usuario de acceso)", "1075209999"),
                    new Columna("nombres", true, "Nombres del cliente", "Camila Andrea"),
                    new Columna("apellidos", true, "Apellidos del cliente", "Torres Mendez"),
                    new Columna("correo", false, "Correo electronico", "camila.torres@correo-ficticio.com"),
                    new Columna("telefono", false, "Telefono de contacto", "3000009999"),
                    new Columna("direccion", false, "Direccion", "Calle 5 # 10-20"),
                    new Columna("ciudad", false, "Ciudad", "Neiva"));
            case CREDITOS -> List.of(
                    new Columna("numero_documento_cliente", true, "Documento de un cliente ya registrado", "1075200101"),
                    new Columna("placa", true, "ABC12D (moto) o ABC123", "QWE32A"),
                    new Columna("marca", true, "Marca de la moto", "Yamaha"),
                    new Columna("linea", true, "Linea o referencia", "Aerox GDR-155A"),
                    new Columna("modelo", true, "Anio modelo", "2025"),
                    new Columna("color", false, "Color", "Blanco"),
                    new Columna("clase", true, "AUTOMOVIL, CAMIONETA, CAMPERO, MOTOCICLETA, CAMION u OTRO", "MOTOCICLETA"),
                    new Columna("valor_vehiculo", true, "Precio moto + matricula con prenda, sin puntos ni signo $", "13600000"),
                    new Columna("cuota_inicial", true, "Puede ser 0", "2000000"),
                    new Columna("tasa_mensual", true, "Porcentaje mensual, ej. 1.5 (use punto decimal)", "1.85"),
                    new Columna("plazo_meses", true, "Entre 1 y 120", "36"),
                    new Columna("fecha_desembolso", true, "aaaa-mm-dd o dd/mm/aaaa", "2026-09-01"),
                    new Columna("fecha_primera_cuota", false, "Si se omite: un mes despues del desembolso", "2026-10-01"));
            case PAGOS -> List.of(
                    new Columna("numero_credito", true, "Numero del credito, ej. CV-2025-0001", "CV-2025-0001"),
                    new Columna("valor", true, "Valor pagado, sin puntos ni signo $", "2151127"),
                    new Columna("medio_pago", true, "EFECTIVO, TRANSFERENCIA, CONSIGNACION, PSE_SIMULADO o TARJETA_SIMULADA", "CONSIGNACION"),
                    new Columna("observacion", false, "Texto libre (maximo 150 caracteres)", "Consignacion en oficina"));
            case VEHICULOS -> throw NegocioException.solicitudInvalida(
                    "Los vehiculos se importan junto con el credito (tipo CREDITOS)");
        };
    }

    public byte[] plantilla(TipoCarga tipo) {
        List<Columna> cols = columnas(tipo);
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle estiloEncabezado = libro.createCellStyle();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            estiloEncabezado.setFont(negrita);
            estiloEncabezado.setFillForegroundColor(IndexedColors.RED.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet datos = libro.createSheet("datos");
            Row enc = datos.createRow(0);
            Row ejemplo = datos.createRow(1);
            for (int i = 0; i < cols.size(); i++) {
                Cell c = enc.createCell(i);
                c.setCellValue(cols.get(i).nombre());
                c.setCellStyle(estiloEncabezado);
                ejemplo.createCell(i).setCellValue(cols.get(i).ejemplo());
                datos.setColumnWidth(i, Math.max(14, cols.get(i).nombre().length() + 4) * 256);
            }

            Sheet ayuda = libro.createSheet("instrucciones");
            Row h = ayuda.createRow(0);
            String[] titulos = {"Columna", "Obligatoria", "Descripcion", "Ejemplo"};
            for (int i = 0; i < titulos.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(titulos[i]);
                c.setCellStyle(estiloEncabezado);
            }
            for (int i = 0; i < cols.size(); i++) {
                Row r = ayuda.createRow(i + 1);
                r.createCell(0).setCellValue(cols.get(i).nombre());
                r.createCell(1).setCellValue(cols.get(i).obligatoria() ? "SI" : "NO");
                r.createCell(2).setCellValue(cols.get(i).descripcion());
                r.createCell(3).setCellValue(cols.get(i).ejemplo());
            }
            Row nota = ayuda.createRow(cols.size() + 2);
            nota.createCell(0).setCellValue("Reemplace la fila de ejemplo de la hoja 'datos' por sus datos FICTICIOS. "
                    + "Primero valide el archivo con la vista previa y luego importelo.");
            ayuda.setColumnWidth(0, 28 * 256);
            ayuda.setColumnWidth(1, 12 * 256);
            ayuda.setColumnWidth(2, 70 * 256);
            ayuda.setColumnWidth(3, 30 * 256);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible generar la plantilla", e);
        }
    }
}
