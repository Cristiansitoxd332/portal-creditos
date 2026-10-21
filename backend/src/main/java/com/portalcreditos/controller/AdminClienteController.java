package com.portalcreditos.controller;

import com.portalcreditos.dto.admin.AdminDtos.ClienteInfo;
import com.portalcreditos.dto.admin.AdminDtos.ClienteRequest;
import com.portalcreditos.dto.admin.AdminDtos.CredencialesTemporales;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.ClienteAdminService;
import com.portalcreditos.service.CreditoConsultaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Gestion de clientes y de sus cuentas de acceso (solo rol ADMIN). */
@RestController
@RequestMapping("/api/admin/clientes")
public class AdminClienteController {

    private final ClienteAdminService servicio;
    private final CreditoConsultaService creditos;
    private final UsuarioActual usuario;

    public AdminClienteController(ClienteAdminService servicio, CreditoConsultaService creditos, UsuarioActual usuario) {
        this.servicio = servicio;
        this.creditos = creditos;
        this.usuario = usuario;
    }

    /** Lista de clientes. Parametro opcional ?buscar= (documento o nombre). */
    @GetMapping
    public List<ClienteInfo> listar(@RequestParam(required = false) String buscar) {
        return servicio.listar(buscar);
    }

    @GetMapping("/{id}")
    public ClienteInfo obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Creditos de un cliente. */
    @GetMapping("/{id}/creditos")
    public List<CreditoResumen> creditos(@PathVariable Long id) {
        servicio.obtener(id);
        return creditos.listarDelCliente(id);
    }

    /** Crea el cliente y su usuario; responde la contrasena temporal (se muestra una sola vez). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredencialesTemporales crear(@Valid @RequestBody ClienteRequest r, HttpServletRequest request) {
        return servicio.crear(r, usuario.getUsername(), request.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public ClienteInfo actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest r, HttpServletRequest request) {
        return servicio.actualizar(id, r, usuario.getUsername(), request.getRemoteAddr());
    }

    /** Quita el bloqueo por intentos fallidos. */
    @PostMapping("/{id}/desbloquear")
    public ClienteInfo desbloquear(@PathVariable Long id, HttpServletRequest request) {
        return servicio.desbloquear(id, usuario.getUsername(), request.getRemoteAddr());
    }

    /** Activa o inactiva el acceso del cliente: ?valor=true|false */
    @PatchMapping("/{id}/activo")
    public ClienteInfo cambiarActivo(@PathVariable Long id, @RequestParam boolean valor, HttpServletRequest request) {
        return servicio.cambiarActivo(id, valor, usuario.getUsername(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/restablecer-password")
    public CredencialesTemporales restablecerPassword(@PathVariable Long id, HttpServletRequest request) {
        return servicio.restablecerPassword(id, usuario.getUsername(), request.getRemoteAddr());
    }
}
