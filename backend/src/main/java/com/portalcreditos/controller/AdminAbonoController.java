package com.portalcreditos.controller;

import com.portalcreditos.dto.abono.AbonoDtos.AbonoRequest;
import com.portalcreditos.dto.abono.AbonoDtos.CalculoFecha;
import com.portalcreditos.dto.abono.AbonoDtos.DeudorAbonos;
import com.portalcreditos.dto.abono.AbonoDtos.Historial;
import com.portalcreditos.dto.abono.AbonoDtos.VistaPreviaAbono;
import com.portalcreditos.dto.credito.CreditoDtos.CodeudorInfo;
import com.portalcreditos.dto.pago.PagoDtos.Comprobante;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CodeudorRepository;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.AbonoService;
import com.portalcreditos.service.CreditoConsultaService;
import com.portalcreditos.service.DocumentoPdfService;
import com.portalcreditos.service.PagoService;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Abonos individuales, historial de creditos y consulta de codeudores (solo rol ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminAbonoController {

    private final AbonoService abonos;
    private final CodeudorRepository codeudores;
    private final UsuarioActual usuario;
    private final PagoService pagos;
    private final DocumentoPdfService pdf;

    public AdminAbonoController(AbonoService abonos, CodeudorRepository codeudores, UsuarioActual usuario,
                                PagoService pagos, DocumentoPdfService pdf) {
        this.pagos = pagos;
        this.pdf = pdf;
        this.abonos = abonos;
        this.codeudores = codeudores;
        this.usuario = usuario;
    }

    /** Deudor por identificacion con sus creditos activos y los valores a pagar hoy. */
    @GetMapping("/abonos/deudor/{documento}")
    public DeudorAbonos deudor(@PathVariable String documento) {
        return abonos.deudor(documento);
    }

    /** Distribucion del abono antes de grabarlo. */
    @PostMapping("/abonos/vista-previa")
    public VistaPreviaAbono vistaPrevia(@Valid @RequestBody AbonoRequest r) {
        return abonos.vistaPrevia(r);
    }

    /** Graba el abono. */
    @PostMapping("/abonos")
    public Comprobante registrar(@Valid @RequestBody AbonoRequest r, HttpServletRequest request) {
        return abonos.registrar(r, usuario.getUsername(), request.getRemoteAddr());
    }

    /** Comprobante en PDF de un pago o abono (para entregarlo al cliente en la oficina). */
    @GetMapping("/abonos/creditos/{creditoId}/pagos/{pagoId}/pdf")
    public ResponseEntity<byte[]> comprobantePdf(@PathVariable Long creditoId, @PathVariable Long pagoId) {
        Comprobante c = pagos.comprobante(creditoId, pagoId);
        return Descargas.pdf(pdf.comprobantePago(c), "comprobante-" + c.referencia() + ".pdf");
    }

    /** Historial por identificacion (creditos como deudor y como codeudor). */
    @GetMapping("/historial/{documento}")
    public Historial historial(@PathVariable String documento) {
        return abonos.historial(documento);
    }

    /** "Calc. corte" (total=false) o "Calc. total" (total=true) de un credito en una fecha. */
    @GetMapping("/historial/creditos/{creditoId}/calculo")
    public CalculoFecha calcular(@PathVariable Long creditoId,
                                 @RequestParam(required = false) String fecha,
                                 @RequestParam(defaultValue = "false") boolean total) {
        LocalDate f;
        try {
            f = fecha == null || fecha.isBlank() ? null : LocalDate.parse(fecha);
        } catch (java.time.format.DateTimeParseException e) {
            throw NegocioException.solicitudInvalida("La fecha debe tener el formato AAAA-MM-DD");
        }
        return abonos.calcular(creditoId, f, total);
    }

    /** Busca un codeudor ya registrado (para no escribir sus datos dos veces). */
    @GetMapping("/codeudores/{documento}")
    @Transactional(readOnly = true)
    public CodeudorInfo codeudor(@PathVariable String documento) {
        return codeudores.findByNumeroDocumento(documento.trim())
                .map(CreditoConsultaService::codeudorInfo)
                .orElseThrow(() -> NegocioException.noEncontrado("No hay un codeudor registrado con ese documento"));
    }
}
