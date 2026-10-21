package com.portalcreditos.service;

import com.portalcreditos.dto.admin.AdminDtos.ClienteRequest;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Usuario;
import com.portalcreditos.entity.enums.Rol;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.ClienteRepository;
import com.portalcreditos.repository.UsuarioRepository;
import com.portalcreditos.util.Textos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Crea un cliente junto con su cuenta de usuario (usuario = numero de documento).
 *
 * Valida TODO antes de guardar y lanza NegocioException con un mensaje claro si algo falla.
 * No abre transaccion propia: la usan tanto el panel administrativo como la importacion de
 * archivos, que valida fila por fila sin cancelar toda la carga por un error.
 */
@Component
public class ClienteFactory {

    private static final Pattern DOCUMENTO = Pattern.compile("^[0-9A-Za-z]{5,20}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9+ ()-]{7,20}$");

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public ClienteFactory(ClienteRepository clientes, UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    /** Valida los datos; devuelve el mensaje del primer error encontrado o null si todo esta bien. */
    public String validar(ClienteRequest r) {
        if (r.tipoDocumento() == null) {
            return "El tipo de documento es obligatorio (CC, CE, NIT o PAS)";
        }
        String doc = Textos.limpiar(r.numeroDocumento());
        if (doc == null || !DOCUMENTO.matcher(doc).matches()) {
            return "El numero de documento debe tener entre 5 y 20 letras o numeros";
        }
        if (Textos.limpiar(r.nombres()) == null || r.nombres().trim().length() > 100) {
            return "Los nombres son obligatorios (maximo 100 caracteres)";
        }
        if (Textos.limpiar(r.apellidos()) == null || r.apellidos().trim().length() > 100) {
            return "Los apellidos son obligatorios (maximo 100 caracteres)";
        }
        String correo = Textos.limpiar(r.correo());
        if (correo != null && (correo.length() > 150 || !CORREO.matcher(correo).matches())) {
            return "El correo no es valido";
        }
        String tel = Textos.limpiar(r.telefono());
        if (tel != null && !TELEFONO.matcher(tel).matches()) {
            return "El telefono no es valido";
        }
        if (Textos.limpiar(r.direccion()) != null && r.direccion().trim().length() > 200) {
            return "La direccion supera 200 caracteres";
        }
        if (Textos.limpiar(r.ciudad()) != null && r.ciudad().trim().length() > 80) {
            return "La ciudad supera 80 caracteres";
        }
        if (clientes.existsByNumeroDocumento(doc) || usuarios.existsByUsername(doc)) {
            return "Ya existe un cliente con el documento " + doc;
        }
        return null;
    }

    /** Crea cliente + usuario con la contrasena indicada (que debe cambiar en el primer ingreso). */
    public Cliente crear(ClienteRequest r, String passwordInicial) {
        String error = validar(r);
        if (error != null) {
            throw NegocioException.solicitudInvalida(error);
        }
        Cliente c = new Cliente();
        c.setTipoDocumento(r.tipoDocumento());
        c.setNumeroDocumento(r.numeroDocumento().trim());
        aplicarDatos(c, r);
        clientes.save(c);

        Usuario u = new Usuario();
        u.setUsername(c.getNumeroDocumento());
        u.setPasswordHash(passwordEncoder.encode(passwordInicial));
        u.setRol(Rol.CLIENTE);
        u.setCliente(c);
        u.setActivo(true);
        u.setDebeCambiarPassword(true);
        u.setIntentosFallidos(0);
        usuarios.save(u);
        return c;
    }

    /** Datos editables (el documento no se cambia porque es el usuario de acceso). */
    public void aplicarDatos(Cliente c, ClienteRequest r) {
        c.setNombres(r.nombres().trim());
        c.setApellidos(r.apellidos().trim());
        c.setCorreo(Textos.limpiar(r.correo()));
        c.setTelefono(Textos.limpiar(r.telefono()));
        c.setDireccion(Textos.limpiar(r.direccion()));
        c.setCiudad(Textos.limpiar(r.ciudad()));
    }
}
