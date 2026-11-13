package com.portalcreditos.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sucursales de Rentar Inversiones con su codigo (como en Sadmit: "01 NEIVA"). */
public final class Sucursales {

    /** Sucursal por defecto: oficina principal. */
    public static final String PRINCIPAL = "01";

    private static final Map<String, String> NOMBRES = new LinkedHashMap<>();

    static {
        NOMBRES.put("01", "Neiva");
        NOMBRES.put("02", "Campoalegre");
        NOMBRES.put("03", "Manizales");
        NOMBRES.put("04", "Palmira");
        NOMBRES.put("05", "Mocoa");
        NOMBRES.put("06", "Puerto Asís");
        NOMBRES.put("07", "La Hormiga");
        NOMBRES.put("08", "Orito");
        NOMBRES.put("09", "Villagarzón");
        NOMBRES.put("10", "Sibundoy");
        NOMBRES.put("11", "Puerto Leguízamo");
        NOMBRES.put("12", "Armenia");
        NOMBRES.put("13", "Santa María");
        NOMBRES.put("14", "Guadalupe");
    }

    private Sucursales() {
    }

    public static boolean existe(String codigo) {
        return codigo != null && NOMBRES.containsKey(codigo);
    }

    /** Codigo valido o la sucursal principal si viene vacio. Lanza error si el codigo no existe. */
    public static String normalizar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return PRINCIPAL;
        }
        String c = codigo.trim();
        if (c.length() == 1) {
            c = "0" + c;
        }
        if (!NOMBRES.containsKey(c)) {
            throw new IllegalArgumentException("La sucursal " + codigo + " no existe");
        }
        return c;
    }

    public static String nombre(String codigo) {
        return NOMBRES.getOrDefault(codigo, "Neiva");
    }

    /** Ej.: "01 NEIVA". */
    public static String etiqueta(String codigo) {
        String c = existe(codigo) ? codigo : PRINCIPAL;
        return c + " " + nombre(c).toUpperCase(java.util.Locale.forLanguageTag("es-CO"));
    }

    public static Map<String, String> todas() {
        return NOMBRES;
    }
}
