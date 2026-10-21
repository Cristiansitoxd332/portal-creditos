package com.portalcreditos.service;

import com.portalcreditos.dto.admin.AdminDtos.ClienteInfo;
import com.portalcreditos.dto.admin.AdminDtos.ClienteRequest;
import com.portalcreditos.dto.admin.AdminDtos.CredencialesTemporales;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Usuario;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.ClienteRepository;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.UsuarioRepository;
import com.portalcreditos.util.Textos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Gestion de clientes desde el panel administrativo: consulta, creacion con cuenta de acceso,
 * edicion, desbloqueo, activacion/inactivacion y restablecimiento de contrasena.
 */
@Service
public class ClienteAdminService {

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final CreditoRepository creditos;
    private final ClienteFactory factory;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final int maxIntentos;

    public ClienteAdminService(ClienteRepository clientes, UsuarioRepository usuarios, CreditoRepository creditos,
                               ClienteFactory factory, PasswordEncoder passwordEncoder, AuditoriaService auditoria,
                               @Value("${app.seguridad.max-intentos-fallidos}") int maxIntentos) {
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.creditos = creditos;
        this.factory = factory;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.maxIntentos = maxIntentos;
    }

    /** Lista de clientes; el texto busca por documento, nombres o apellidos. */
    @Transactional(readOnly = true)
    public List<ClienteInfo> listar(String texto) {
        String filtro = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        return clientes.findAll().stream()
                .filter(c -> filtro.isEmpty()
                        || c.getNumeroDocumento().toLowerCase(Locale.ROOT).contains(filtro)
                        || c.getNombreCompleto().toLowerCase(Locale.ROOT).contains(filtro))
                .sorted(Comparator.comparing(Cliente::getApellidos).thenComparing(Cliente::getNombres))
                .map(this::info)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteInfo obtener(Long id) {
        return info(buscar(id));
    }

    @Transactional
    public CredencialesTemporales crear(ClienteRequest r, String username, String ip) {
        String password = Textos.passwordTemporal();
        Cliente c = factory.crear(r, password);
        auditoria.registrar(username, "CREAR_CLIENTE", "cliente", String.valueOf(c.getId()), c.getNumeroDocumento(), ip);
        return new CredencialesTemporales(c.getId(), c.getNumeroDocumento(), password,
                "Cliente creado. Entregue estas credenciales al cliente; debera cambiar la contraseña en su primer ingreso. "
                        + "La contraseña temporal no se volverá a mostrar.");
    }

    @Transactional
    public ClienteInfo actualizar(Long id, ClienteRequest r, String username, String ip) {
        Cliente c = buscar(id);
        if (!c.getNumeroDocumento().equals(r.numeroDocumento().trim())) {
            throw NegocioException.solicitudInvalida("El número de documento no se puede modificar porque es el usuario de acceso.");
        }
        c.setTipoDocumento(r.tipoDocumento());
        factory.aplicarDatos(c, r);
        auditoria.registrar(username, "EDITAR_CLIENTE", "cliente", String.valueOf(id), c.getNumeroDocumento(), ip);
        return info(c);
    }

    @Transactional
    public ClienteInfo desbloquear(Long id, String username, String ip) {
        Usuario u = usuarioDe(id);
        u.setIntentosFallidos(0);
        auditoria.registrar(username, "DESBLOQUEAR_USUARIO", "usuario", u.getUsername(), null, ip);
        return info(u.getCliente());
    }

    @Transactional
    public ClienteInfo cambiarActivo(Long id, boolean activo, String username, String ip) {
        Usuario u = usuarioDe(id);
        u.setActivo(activo);
        auditoria.registrar(username, activo ? "ACTIVAR_USUARIO" : "INACTIVAR_USUARIO", "usuario", u.getUsername(), null, ip);
        return info(u.getCliente());
    }

    @Transactional
    public CredencialesTemporales restablecerPassword(Long id, String username, String ip) {
        Usuario u = usuarioDe(id);
        String password = Textos.passwordTemporal();
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDebeCambiarPassword(true);
        u.setIntentosFallidos(0);
        auditoria.registrar(username, "RESTABLECER_PASSWORD", "usuario", u.getUsername(), null, ip);
        return new CredencialesTemporales(id, u.getUsername(), password,
                "Contraseña restablecida. El cliente debera cambiarla en su próximo ingreso.");
    }

    private ClienteInfo info(Cliente c) {
        Usuario u = usuarios.findByClienteId(c.getId()).orElse(null);
        return new ClienteInfo(c.getId(), c.getTipoDocumento(), c.getNumeroDocumento(), c.getNombres(), c.getApellidos(),
                c.getCorreo(), c.getTelefono(), c.getDireccion(), c.getCiudad(), (int) creditos.countByClienteId(c.getId()),
                u != null && u.isActivo(), u != null && u.getIntentosFallidos() >= maxIntentos,
                u != null && u.isDebeCambiarPassword(), u != null ? u.getUltimoAcceso() : null);
    }

    private Cliente buscar(Long id) {
        return clientes.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Cliente no encontrado"));
    }

    private Usuario usuarioDe(Long clienteId) {
        buscar(clienteId);
        return usuarios.findByClienteId(clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("El cliente no tiene usuario de acceso"));
    }
}
