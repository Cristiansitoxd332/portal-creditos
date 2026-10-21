package com.portalcreditos.service.importacion;

import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.util.Textos;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee archivos Excel (.xlsx) o CSV y los convierte en filas de texto (columna -> valor).
 * - La primera fila debe tener los nombres de las columnas (no importa tildes ni mayusculas).
 * - En CSV se detecta automaticamente si el separador es coma o punto y coma (Excel en espanol usa ";").
 * - Las filas completamente vacias se ignoran.
 */
@Component
public class LectorArchivos {

    /** Una fila leida: numero de fila tal como aparece en Excel (la fila 1 es el encabezado). */
    public record Fila(int numero, Map<String, String> valores) {
        public String get(String columna) {
            String v = valores.get(columna);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    public List<Fila> leer(String nombreArchivo, InputStream contenido, int maxFilas) {
        String nombre = nombreArchivo == null ? "" : nombreArchivo.toLowerCase(Locale.ROOT);
        try {
            List<Fila> filas;
            if (nombre.endsWith(".xlsx")) {
                filas = leerExcel(contenido);
            } else if (nombre.endsWith(".csv")) {
                filas = leerCsv(contenido);
            } else {
                throw NegocioException.solicitudInvalida("Formato no soportado. Use un archivo .xlsx o .csv");
            }
            if (filas.isEmpty()) {
                throw NegocioException.solicitudInvalida("El archivo no tiene filas de datos");
            }
            if (filas.size() > maxFilas) {
                throw NegocioException.solicitudInvalida("El archivo supera el maximo de " + maxFilas + " filas");
            }
            return filas;
        } catch (IOException e) {
            throw NegocioException.solicitudInvalida("No fue posible leer el archivo: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Excel

    private List<Fila> leerExcel(InputStream in) throws IOException {
        try (Workbook libro = new XSSFWorkbook(in)) {
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter formato = new DataFormatter(Locale.US);
            Row encabezado = hoja.getRow(hoja.getFirstRowNum());
            if (encabezado == null) {
                return List.of();
            }
            List<String> columnas = new ArrayList<>();
            for (int c = 0; c < encabezado.getLastCellNum(); c++) {
                Cell celda = encabezado.getCell(c);
                columnas.add(celda == null ? "" : Textos.normalizarClave(formato.formatCellValue(celda)));
            }
            List<Fila> filas = new ArrayList<>();
            for (int r = encabezado.getRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row fila = hoja.getRow(r);
                if (fila == null) {
                    continue;
                }
                Map<String, String> valores = new LinkedHashMap<>();
                boolean vacia = true;
                for (int c = 0; c < columnas.size(); c++) {
                    String v = texto(fila.getCell(c), formato);
                    if (!v.isBlank()) {
                        vacia = false;
                    }
                    if (!columnas.get(c).isEmpty()) {
                        valores.put(columnas.get(c), v);
                    }
                }
                if (!vacia) {
                    filas.add(new Fila(r + 1, valores));
                }
            }
            return filas;
        }
    }

    /** Convierte una celda a texto: fechas como aaaa-mm-dd y numeros sin notacion cientifica. */
    private static String texto(Cell celda, DataFormatter formato) {
        if (celda == null) {
            return "";
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
        if (tipo == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(celda)) {
                return celda.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            return BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formato.formatCellValue(celda).trim();
    }

    // ------------------------------------------------------------------ CSV

    List<Fila> leerCsv(InputStream in) throws IOException {
        BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String primera = lector.readLine();
        if (primera == null) {
            return List.of();
        }
        if (primera.startsWith("﻿")) {
            primera = primera.substring(1); // marca BOM que agrega Excel al guardar como CSV UTF-8
        }
        char separador = contar(primera, ';') > contar(primera, ',') ? ';' : ',';
        List<String> columnas = dividir(primera, separador).stream().map(Textos::normalizarClave).toList();

        List<Fila> filas = new ArrayList<>();
        String linea;
        int numero = 1;
        while ((linea = lector.readLine()) != null) {
            numero++;
            if (linea.isBlank()) {
                continue;
            }
            List<String> partes = dividir(linea, separador);
            Map<String, String> valores = new LinkedHashMap<>();
            for (int c = 0; c < columnas.size(); c++) {
                if (!columnas.get(c).isEmpty()) {
                    valores.put(columnas.get(c), c < partes.size() ? partes.get(c).trim() : "");
                }
            }
            filas.add(new Fila(numero, valores));
        }
        return filas;
    }

    /** Divide una linea CSV respetando valores entre comillas ("Calle 10, apto 2"). */
    static List<String> dividir(String linea, char separador) {
        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (ch == '"') {
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (ch == separador && !entreComillas) {
                partes.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        partes.add(actual.toString());
        return partes;
    }

    private static long contar(String texto, char c) {
        return texto.chars().filter(x -> x == c).count();
    }
}
