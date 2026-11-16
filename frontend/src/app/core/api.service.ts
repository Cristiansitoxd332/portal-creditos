import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './config';
import {
  CanalInfo, ClienteInfo, ClienteRequest, Comprobante, CredencialesTemporales, CreditoDetalle, CreditoRequest,
  CreditoResumen, EstadoLevantamiento, EstadoSolicitud, ImportacionInfo, OpcionesPago, PagoHistorial, PagoRequest,
  PazYSalvoEstado, Perfil, PlanAmortizacion, PlanPagos, RegistroAuditoria, RequisitoInfo, ResultadoImportacion,
  SimulacionAbono, SolicitudInfo, Tablero, TipoCarga, VerificacionPazYSalvo,
  BandejaCliente, MensajeInfo, AbonoRequest, CalculoFecha, CodeudorInfo, DeudorAbonos, Historial, VistaPreviaAbono, PanelAvisos, ResultadoEnvio, TipoMensaje,
} from './models';

/**
 * Cliente de la API REST. Cada metodo corresponde a un endpoint del backend
 * (ver la documentacion en http://localhost:8080/swagger-ui.html).
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ============================================================ perfil
  perfil(): Observable<Perfil> {
    return this.http.get<Perfil>(`${API_URL}/auth/perfil`);
  }

  // ============================================================ publico
  canales(): Observable<CanalInfo[]> {
    return this.http.get<CanalInfo[]>(`${API_URL}/publico/canales`);
  }
  requisitos(): Observable<RequisitoInfo[]> {
    return this.http.get<RequisitoInfo[]>(`${API_URL}/publico/requisitos-levantamiento`);
  }
  verificarPazYSalvo(codigo: string): Observable<VerificacionPazYSalvo> {
    return this.http.get<VerificacionPazYSalvo>(`${API_URL}/publico/paz-y-salvo/${encodeURIComponent(codigo)}`);
  }

  // ============================================================ cliente
  misCreditos(): Observable<CreditoResumen[]> {
    return this.http.get<CreditoResumen[]>(`${API_URL}/cliente/creditos`);
  }
  miCredito(id: number): Observable<CreditoDetalle> {
    return this.http.get<CreditoDetalle>(`${API_URL}/cliente/creditos/${id}`);
  }
  misCuotas(id: number): Observable<PlanPagos> {
    return this.http.get<PlanPagos>(`${API_URL}/cliente/creditos/${id}/cuotas`);
  }
  opcionesPago(id: number): Observable<OpcionesPago> {
    return this.http.get<OpcionesPago>(`${API_URL}/cliente/creditos/${id}/pagos/opciones`);
  }
  pagar(id: number, solicitud: PagoRequest): Observable<Comprobante> {
    return this.http.post<Comprobante>(`${API_URL}/cliente/creditos/${id}/pagos`, solicitud);
  }
  misPagos(id: number): Observable<PagoHistorial[]> {
    return this.http.get<PagoHistorial[]>(`${API_URL}/cliente/creditos/${id}/pagos`);
  }
  comprobante(id: number, pagoId: number): Observable<Comprobante> {
    return this.http.get<Comprobante>(`${API_URL}/cliente/creditos/${id}/pagos/${pagoId}`);
  }
  comprobantePdf(id: number, pagoId: number): Observable<Blob> {
    return this.http.get(`${API_URL}/cliente/creditos/${id}/pagos/${pagoId}/pdf`, { responseType: 'blob' });
  }
  simularAbono(id: number, valorAbono: number): Observable<SimulacionAbono> {
    return this.http.post<SimulacionAbono>(`${API_URL}/cliente/creditos/${id}/simulador-abono`, { valorAbono });
  }
  estadoPazYSalvo(id: number): Observable<PazYSalvoEstado> {
    return this.http.get<PazYSalvoEstado>(`${API_URL}/cliente/creditos/${id}/paz-y-salvo/estado`);
  }
  pazYSalvoPdf(id: number): Observable<Blob> {
    return this.http.get(`${API_URL}/cliente/creditos/${id}/paz-y-salvo`, { responseType: 'blob' });
  }
  estadoLevantamiento(id: number): Observable<EstadoLevantamiento> {
    return this.http.get<EstadoLevantamiento>(`${API_URL}/cliente/creditos/${id}/levantamiento`);
  }
  solicitarLevantamiento(id: number, observacion: string, medioPago: string): Observable<SolicitudInfo> {
    return this.http.post<SolicitudInfo>(`${API_URL}/cliente/creditos/${id}/levantamiento`, { observacion, medioPago });
  }

  // ============================================================ administrador
  tablero(): Observable<Tablero> {
    return this.http.get<Tablero>(`${API_URL}/admin/tablero`);
  }
  clientes(buscar = ''): Observable<ClienteInfo[]> {
    const params = buscar ? new HttpParams().set('buscar', buscar) : undefined;
    return this.http.get<ClienteInfo[]>(`${API_URL}/admin/clientes`, { params });
  }
  crearCliente(r: ClienteRequest): Observable<CredencialesTemporales> {
    return this.http.post<CredencialesTemporales>(`${API_URL}/admin/clientes`, r);
  }
  actualizarCliente(id: number, r: ClienteRequest): Observable<ClienteInfo> {
    return this.http.put<ClienteInfo>(`${API_URL}/admin/clientes/${id}`, r);
  }
  desbloquearCliente(id: number): Observable<ClienteInfo> {
    return this.http.post<ClienteInfo>(`${API_URL}/admin/clientes/${id}/desbloquear`, {});
  }
  activarCliente(id: number, valor: boolean): Observable<ClienteInfo> {
    return this.http.patch<ClienteInfo>(`${API_URL}/admin/clientes/${id}/activo`, {}, { params: { valor } });
  }
  restablecerPassword(id: number): Observable<CredencialesTemporales> {
    return this.http.post<CredencialesTemporales>(`${API_URL}/admin/clientes/${id}/restablecer-password`, {});
  }
  creditosAdmin(): Observable<CreditoResumen[]> {
    return this.http.get<CreditoResumen[]>(`${API_URL}/admin/creditos`);
  }
  creditoAdmin(id: number): Observable<CreditoDetalle> {
    return this.http.get<CreditoDetalle>(`${API_URL}/admin/creditos/${id}`);
  }
  cuotasAdmin(id: number): Observable<PlanPagos> {
    return this.http.get<PlanPagos>(`${API_URL}/admin/creditos/${id}/cuotas`);
  }
  crearCredito(r: CreditoRequest): Observable<CreditoDetalle> {
    return this.http.post<CreditoDetalle>(`${API_URL}/admin/creditos`, r);
  }
  simularPlan(valorFinanciado: number, tasaInteresMensual: number, plazoMeses: number, fechaPrimeraCuota: string): Observable<PlanAmortizacion> {
    return this.http.post<PlanAmortizacion>(`${API_URL}/admin/creditos/simular-plan`,
      { valorFinanciado, tasaInteresMensual, plazoMeses, fechaPrimeraCuota });
  }
  pagosAdmin(id: number): Observable<PagoHistorial[]> {
    return this.http.get<PagoHistorial[]>(`${API_URL}/admin/creditos/${id}/pagos`);
  }
  opcionesPagoAdmin(id: number): Observable<OpcionesPago> {
    return this.http.get<OpcionesPago>(`${API_URL}/admin/creditos/${id}/pagos/opciones`);
  }
  registrarPagoAdmin(id: number, r: PagoRequest): Observable<Comprobante> {
    return this.http.post<Comprobante>(`${API_URL}/admin/creditos/${id}/pagos`, r);
  }
  formatoLevantamientoPdf(id: number): Observable<Blob> {
    return this.http.get(`${API_URL}/cliente/creditos/${id}/levantamiento/formato`, { responseType: 'blob' });
  }
  formatoLevantamientoAdminPdf(creditoId: number): Observable<Blob> {
    return this.http.get(`${API_URL}/admin/levantamiento/creditos/${creditoId}/formato`, { responseType: 'blob' });
  }
  pazYSalvoAdminPdf(id: number): Observable<Blob> {
    return this.http.get(`${API_URL}/admin/creditos/${id}/paz-y-salvo`, { responseType: 'blob' });
  }
  solicitudes(estado?: EstadoSolicitud): Observable<SolicitudInfo[]> {
    const params = estado ? new HttpParams().set('estado', estado) : undefined;
    return this.http.get<SolicitudInfo[]>(`${API_URL}/admin/solicitudes`, { params });
  }
  responderSolicitud(id: number, estado: EstadoSolicitud, respuesta: string): Observable<SolicitudInfo> {
    return this.http.patch<SolicitudInfo>(`${API_URL}/admin/solicitudes/${id}`, { estado, respuesta });
  }
  canalesAdmin(): Observable<CanalInfo[]> {
    return this.http.get<CanalInfo[]>(`${API_URL}/admin/canales`);
  }
  guardarCanal(id: number | null, canal: Omit<CanalInfo, 'id'>): Observable<CanalInfo> {
    return id === null
      ? this.http.post<CanalInfo>(`${API_URL}/admin/canales`, canal)
      : this.http.put<CanalInfo>(`${API_URL}/admin/canales/${id}`, canal);
  }
  eliminarCanal(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/admin/canales/${id}`);
  }
  requisitosAdmin(): Observable<RequisitoInfo[]> {
    return this.http.get<RequisitoInfo[]>(`${API_URL}/admin/requisitos`);
  }
  guardarRequisito(id: number | null, r: Omit<RequisitoInfo, 'id'>): Observable<RequisitoInfo> {
    return id === null
      ? this.http.post<RequisitoInfo>(`${API_URL}/admin/requisitos`, r)
      : this.http.put<RequisitoInfo>(`${API_URL}/admin/requisitos/${id}`, r);
  }
  eliminarRequisito(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/admin/requisitos/${id}`);
  }
  auditoria(cantidad = 100): Observable<RegistroAuditoria[]> {
    return this.http.get<RegistroAuditoria[]>(`${API_URL}/admin/auditoria`, { params: { cantidad } });
  }
  plantilla(tipo: TipoCarga): Observable<Blob> {
    return this.http.get(`${API_URL}/admin/importaciones/plantillas/${tipo.toLowerCase()}`, { responseType: 'blob' });
  }
  vistaPrevia(tipo: TipoCarga, archivo: File): Observable<ResultadoImportacion> {
    return this.http.post<ResultadoImportacion>(`${API_URL}/admin/importaciones/${tipo.toLowerCase()}/vista-previa`, this.formulario(archivo));
  }
  importar(tipo: TipoCarga, archivo: File): Observable<ResultadoImportacion> {
    return this.http.post<ResultadoImportacion>(`${API_URL}/admin/importaciones/${tipo.toLowerCase()}`, this.formulario(archivo));
  }
  importaciones(): Observable<ImportacionInfo[]> {
    return this.http.get<ImportacionInfo[]>(`${API_URL}/admin/importaciones`);
  }

  // ============================================================ mensajes a clientes

  avisosMensajes(): Observable<PanelAvisos> {
    return this.http.get<PanelAvisos>(`${API_URL}/admin/mensajes/avisos`);
  }
  enviarMensaje(creditoId: number, tipo: TipoMensaje, texto: string): Observable<MensajeInfo> {
    return this.http.post<MensajeInfo>(`${API_URL}/admin/mensajes`, { creditoId, tipo, texto });
  }
  enviarMensajesTodos(tipo: Exclude<TipoMensaje, 'GENERAL'>): Observable<ResultadoEnvio> {
    return this.http.post<ResultadoEnvio>(`${API_URL}/admin/mensajes/todos`, { tipo });
  }
  historialMensajes(): Observable<MensajeInfo[]> {
    return this.http.get<MensajeInfo[]>(`${API_URL}/admin/mensajes`);
  }
  misMensajes(): Observable<BandejaCliente> {
    return this.http.get<BandejaCliente>(`${API_URL}/cliente/mensajes`);
  }
  mensajesNoLeidos(): Observable<{ noLeidos: number }> {
    return this.http.get<{ noLeidos: number }>(`${API_URL}/cliente/mensajes/no-leidos`);
  }
  marcarMensajeLeido(id: number): Observable<MensajeInfo> {
    return this.http.post<MensajeInfo>(`${API_URL}/cliente/mensajes/${id}/leido`, {});
  }

  // ============================================================ abonos individuales e historial

  deudorAbonos(documento: string): Observable<DeudorAbonos> {
    return this.http.get<DeudorAbonos>(`${API_URL}/admin/abonos/deudor/${encodeURIComponent(documento)}`);
  }
  vistaPreviaAbono(r: AbonoRequest): Observable<VistaPreviaAbono> {
    return this.http.post<VistaPreviaAbono>(`${API_URL}/admin/abonos/vista-previa`, r);
  }
  registrarAbono(r: AbonoRequest): Observable<Comprobante> {
    return this.http.post<Comprobante>(`${API_URL}/admin/abonos`, r);
  }
  historial(documento: string): Observable<Historial> {
    return this.http.get<Historial>(`${API_URL}/admin/historial/${encodeURIComponent(documento)}`);
  }
  calculoCredito(creditoId: number, fecha: string, total: boolean): Observable<CalculoFecha> {
    const params = new HttpParams().set('fecha', fecha).set('total', total);
    return this.http.get<CalculoFecha>(`${API_URL}/admin/historial/creditos/${creditoId}/calculo`, { params });
  }
  comprobanteAdminPdf(creditoId: number, pagoId: number): Observable<Blob> {
    return this.http.get(`${API_URL}/admin/abonos/creditos/${creditoId}/pagos/${pagoId}/pdf`, { responseType: 'blob' });
  }
  codeudor(documento: string): Observable<CodeudorInfo> {
    return this.http.get<CodeudorInfo>(`${API_URL}/admin/codeudores/${encodeURIComponent(documento)}`);
  }

  private formulario(archivo: File): FormData {
    const datos = new FormData();
    datos.append('archivo', archivo, archivo.name);
    return datos;
  }
}
