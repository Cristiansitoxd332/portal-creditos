package com.portalcreditos.util;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/** Utilidades de texto: normalizacion, enmascaramiento de datos personales y contrasenas temporales. */
public final class Textos {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String ESPECIALES = "*#$%&@!";

    private Textos() {
    }

    /** "Número Documento " -> "numero_documento": sin tildes, minusculas, espacios como guion bajo. */
    public static String normalizarClave(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /** Recorta espacios; devuelve null si queda vacio. */
    public static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    /** "1075200303" -> "******0303". Protege el documento en la verificacion publica. */
    public static String enmascararDocumento(String documento) {
        if (documento == null || documento.length() <= 4) {
            return "****";
        }
        return "*".repeat(documento.length() - 4) + documento.substring(documento.length() - 4);
    }

    /** "Diana Patricia" + "Vargas Silva" -> "Diana P. V. S." */
    public static String enmascararNombre(String nombres, String apellidos) {
        StringBuilder sb = new StringBuilder();
        String[] n = nombres == null ? new String[0] : nombres.trim().split("\\s+");
        if (n.length > 0 && !n[0].isEmpty()) {
            sb.append(n[0]);
        }
        for (int i = 1; i < n.length; i++) {
            sb.append(' ').append(n[i].charAt(0)).append('.');
        }
        if (apellidos != null) {
            for (String a : apellidos.trim().split("\\s+")) {
                if (!a.isEmpty()) {
                    sb.append(' ').append(a.charAt(0)).append('.');
                }
            }
        }
        return sb.toString().trim();
    }

    /** Contrasena temporal de 12 caracteres que cumple la politica (mayuscula, minuscula, numero, especial). */
    public static String passwordTemporal() {
        StringBuilder sb = new StringBuilder();
        sb.append(caracter(MAYUSCULAS)).append(caracter(MINUSCULAS)).append(caracter(NUMEROS)).append(caracter(ESPECIALES));
        String todos = MAYUSCULAS + MINUSCULAS + NUMEROS;
        for (int i = 0; i < 8; i++) {
            sb.append(caracter(todos));
        }
        char[] c = sb.toString().toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = ALEATORIO.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }

    /** Codigo aleatorio en mayusculas y numeros (sin caracteres confusos como O/0 o I/1). */
    public static String codigo(int longitud) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < longitud; i++) {
            sb.append(caracter(MAYUSCULAS + NUMEROS));
        }
        return sb.toString();
    }

    /** Solo se aceptan enlaces http(s), tel: y mailto: (evita enlaces "javascript:" maliciosos). */
    public static boolean enlaceSeguro(String enlace) {
        if (enlace == null || enlace.isBlank()) {
            return true;
        }
        String e = enlace.trim().toLowerCase(Locale.ROOT);
        return e.startsWith("https://") || e.startsWith("http://") || e.startsWith("tel:") || e.startsWith("mailto:");
    }

    private static char caracter(String fuente) {
        return fuente.charAt(ALEATORIO.nextInt(fuente.length()));
    }
}
