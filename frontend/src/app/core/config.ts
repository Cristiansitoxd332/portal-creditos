/**
 * Configuracion del frontend.
 * API_URL apunta al backend Spring Boot, en el mismo equipo que sirve la pagina.
 * Asi funciona tanto en el PC (localhost) como desde un celular en el mismo WiFi (IP del PC).
 */
export const API_URL = `${window.location.protocol}//${window.location.hostname}:8080/api`;

/** Nombre que se muestra en la aplicacion. */
export const NOMBRE_APP = 'Rentar';
