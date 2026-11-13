/**
 * Modelos (interfaces) que reflejan los DTO del backend.
 * Mantenerlos alineados con los records de Java evita errores al consumir la API.
 */

export type Rol = 'CLIENTE' | 'ADMIN';
export type EstadoCredito = 'VIGENTE' | 'EN_MORA' | 'CANCELADO';
export type EstadoPrenda = 'VIGENTE' | 'EN_TRAMITE' | 'LEVANTADA';
export type Semaforo = 'AL_DIA' | 'PROXIMA_A_VENCER' | 'EN_MORA' | 'CANCELADO';
export type EstadoCuota = 'PAGADA' | 'PARCIAL' | 'PENDIENTE' | 'VENCIDA';
export type MedioPago = 'PSE_SIMULADO' | 'TARJETA_SIMULADA' | 'EFECTIVO' | 'TRANSFERENCIA' | 'CONSIGNACION';
export type CanalPago = 'PORTAL' | 'ADMIN' | 'IMPORTACION';
export type EstadoPago = 'APROBADO' | 'RECHAZADO' | 'PENDIENTE';
export type EstadoSolicitud = 'RADICADA' | 'EN_REVISION' | 'APROBADA' | 'RECHAZADA';
export type TipoCanal = 'WHATSAPP' | 'TELEFONO' | 'CORREO' | 'SITIO_WEB' | 'OFICINA' | 'REDES_SOCIALES';
export type TipoDocumento = 'CC' | 'CE' | 'NIT' | 'PAS';
export type ClaseVehiculo = 'AUTOMOVIL' | 'CAMIONETA' | 'CAMPERO' | 'MOTOCICLETA' | 'CAMION' | 'OTRO';
export type TipoCarga = 'CLIENTES' | 'CREDITOS' | 'PAGOS';
export type EstadoImportacion = 'PROCESANDO' | 'COMPLETADA' | 'COMPLETADA_CON_ERRORES' | 'FALLIDA';

// ----------------------------------------------------------------- autenticacion
export interface LoginResponse {
  token: string;
  tipo: string;
  expiraEn: string;
  username: string;
  rol: Rol;
  nombre: string;
  debeCambiarPassword: boolean;
}

export interface Perfil {
  username: string;
  rol: Rol;
  nombre: string;
  clienteId: number | null;
  correo: string | null;
  debeCambiarPassword: boolean;
  ultimoAcceso: string | null;
}

export interface ErrorApi {
  estado: number;
  error: string;
  mensaje: string;
  detalles: Record<string, string> | null;
}

// ----------------------------------------------------------------- creditos
export interface ProximaCuota {
  numeroCuota: number;
  fechaVencimiento: string;
  valor: number;
  diasRestantes: number;
}

export interface CreditoResumen {
  id: number;
  numeroCredito: string;
  cliente: string;
  documentoCliente: string;
  vehiculo: string;
  placa: string;
  valorFinanciado: number;
  plazoMeses: number;
  tasaInteresMensual: number;
  valorCuota: number;
  saldoCapital: number;
  estado: EstadoCredito;
  semaforo: Semaforo;
  estadoPrenda: EstadoPrenda;
  cuotasPagadas: number;
  cuotasPendientes: number;
  cuotasVencidas: number;
  valorVencido: number;
  diasMora: number;
  /** Mora por dias de atraso pendiente de pago (valor fijo por dia). */
  valorMora: number;
  moraPorDia: number;
  porcentajePagado: number;
  proximaCuota: ProximaCuota | null;
}

export interface VehiculoInfo {
  placa: string;
  marca: string;
  linea: string;
  modelo: number;
  color: string | null;
  clase: ClaseVehiculo;
  numeroMotor: string | null;
  numeroChasis: string | null;
}

export interface CreditoDetalle {
  resumen: CreditoResumen;
  valorVehiculo: number;
  cuotaInicial: number;
  fechaDesembolso: string;
  fechaPrimeraCuota: string;
  fechaCancelacion: string | null;
  capitalPagado: number;
  interesesPagados: number;
  totalPagado: number;
  totalInteresesPlan: number;
  totalPagarPlan: number;
  vehiculo: VehiculoInfo;
  codeudor: CodeudorInfo | null;
  asesor: string | null;
  cuotaOficial: number | null;
  seguroVidaCuota: number;
  fogadeCuota: number;
}

/** Codeudor (fiador) de un crédito. */
export interface CodeudorInfo {
  tipoDocumento: string;
  numeroDocumento: string;
  nombre: string;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
}

export interface CodeudorRequest {
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  nombres: string | null;
  apellidos: string | null;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
}

export interface CuotaInfo {
  numeroCuota: number;
  fechaVencimiento: string;
  valorCapital: number;
  valorInteres: number;
  valorSeguroVida: number;
  valorFogade: number;
  valorCuota: number;
  saldoCapitalFinal: number;
  valorPagado: number;
  saldoPendiente: number;
  fechaPago: string | null;
  estado: EstadoCuota;
  diasMora: number;
}

export interface PlanPagos {
  creditoId: number;
  numeroCredito: string;
  fechaConsulta: string;
  totalCuotas: number;
  cuotasPagadas: number;
  cuotasVencidas: number;
  valorVencido: number;
  cuotas: CuotaInfo[];
}

// ----------------------------------------------------------------- pagos
export interface OpcionesPago {
  creditoId: number;
  numeroCredito: string;
  valorVencido: number;
  cuotasVencidas: number;
  valorMora: number;
  diasMoraCobrados: number;
  moraPorDia: number;
  valorHastaProximaCuota: number;
  valorMinimo: number;
  valorMaximo: number;
  mediosDisponibles: MedioPago[];
  aviso: string;
}

export interface PagoRequest {
  valor: number;
  medioPago: MedioPago;
  simularRechazo?: boolean;
  observacion?: string;
}

export interface AplicacionInfo {
  numeroCuota: number;
  valorAplicado: number;
  estadoCuota: EstadoCuota;
}

export interface Comprobante {
  pagoId: number;
  referencia: string;
  codigoAutorizacion: string | null;
  fecha: string;
  numeroCredito: string;
  cliente: string;
  documentoCliente: string;
  valor: number;
  valorMora: number;
  medioPago: MedioPago;
  canal: CanalPago;
  estado: EstadoPago;
  mensaje: string;
  pasarela: string;
  saldoCapitalAnterior: number | null;
  saldoCapitalNuevo: number | null;
  estadoCredito: EstadoCredito;
  aplicaciones: AplicacionInfo[];
  aviso: string;
}

export interface PagoHistorial {
  pagoId: number;
  referencia: string;
  fecha: string;
  valor: number;
  medioPago: MedioPago;
  canal: CanalPago;
  estado: EstadoPago;
  cuotasAplicadas: string;
}

// ----------------------------------------------------------------- simulador
export interface Escenario {
  valorCuota: number;
  cuotasRestantes: number;
  totalIntereses: number;
  fechaUltimaCuota: string | null;
  ahorroIntereses: number;
  cuotasMenos: number;
}

export interface SimulacionAbono {
  numeroCredito: string;
  saldoCapitalActual: number;
  valorAbono: number;
  nuevoSaldoCapital: number;
  tasaInteresMensual: number;
  sinAbono: Escenario;
  reducirPlazo: Escenario;
  reducirCuota: Escenario;
  recomendacion: string;
  aviso: string;
}

// ----------------------------------------------------------------- documentos y levantamiento
export interface PazYSalvoEstado {
  disponible: boolean;
  mensaje: string;
  codigoVerificacion: string | null;
  fechaEmision: string | null;
}

export interface VerificacionPazYSalvo {
  valido: boolean;
  codigoVerificacion: string;
  numeroCredito: string | null;
  titular: string | null;
  documento: string | null;
  placa: string | null;
  fechaCancelacion: string | null;
  fechaEmision: string | null;
  aviso: string;
}

export interface PasoSeguimiento {
  nombre: string;
  completado: boolean;
  fecha: string | null;
}

export interface SolicitudInfo {
  id: number;
  numeroRadicado: string;
  creditoId: number;
  numeroCredito: string;
  cliente: string;
  documentoCliente: string;
  placa: string;
  fechaSolicitud: string;
  estado: EstadoSolicitud;
  observacionCliente: string | null;
  respuestaAdmin: string | null;
  fechaRespuesta: string | null;
  seguimiento: PasoSeguimiento[];
  /** Pago SIMULADO del tramite, recaudado en una cuenta exclusiva (separada de las cuotas). */
  valorTramite: number | null;
  medioPagoTramite: MedioPagoTramite | null;
  referenciaPagoTramite: string | null;
  fechaPagoTramite: string | null;
  cuentaRecaudo: string | null;
  /** Vigencia del documento de levantamiento (30 dias desde la aprobacion). */
  vigenteHasta: string | null;
  documentoVigente: boolean | null;
  diasVigencia: number | null;
}

export interface EstadoLevantamiento {
  creditoId: number;
  numeroCredito: string;
  estadoPrenda: EstadoPrenda;
  puedeSolicitar: boolean;
  mensaje: string;
  solicitudes: SolicitudInfo[];
  aviso: string;
  costoTramite: number;
  vigenciaDias: number;
  cuentaRecaudo: string;
  mediosPago: MedioPagoTramite[];
}

export type MedioPagoTramite = 'PSE' | 'EFECTY' | 'CONSIGNACION';

// ----------------------------------------------------------------- configuracion
export interface CanalInfo {
  id: number;
  tipo: TipoCanal;
  nombre: string;
  valor: string;
  enlace: string | null;
  horario: string | null;
  orden: number;
  activo: boolean;
}

export interface RequisitoInfo {
  id: number;
  titulo: string;
  descripcion: string;
  orden: number;
  activo: boolean;
}

// ----------------------------------------------------------------- administrador
export interface ClienteInfo {
  id: number;
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  correo: string | null;
  telefono: string | null;
  direccion: string | null;
  ciudad: string | null;
  creditos: number;
  usuarioActivo: boolean;
  usuarioBloqueado: boolean;
  debeCambiarPassword: boolean;
  ultimoAcceso: string | null;
}

export interface ClienteRequest {
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  correo: string | null;
  telefono: string | null;
  direccion: string | null;
  ciudad: string | null;
}

export interface CredencialesTemporales {
  clienteId: number;
  usuario: string;
  passwordTemporal: string;
  mensaje: string;
}

export interface CreditoRequest {
  clienteId: number;
  placa: string;
  marca: string;
  linea: string;
  modelo: number;
  color: string | null;
  clase: ClaseVehiculo;
  numeroMotor: string | null;
  numeroChasis: string | null;
  valorVehiculo: number;
  cuotaInicial: number;
  tasaInteresMensual: number;
  plazoMeses: number;
  fechaDesembolso: string;
  fechaPrimeraCuota: string | null;
  /** true = el administrador confirmo que es un crédito adicional (otra moto) para un cliente con crédito activo. */
  creditoAdicional: boolean;
  /** Cuota oficial del cotizador: la diferencia con la cuota financiera va a seguro de vida y FOGADE. */
  cuotaOficial: number | null;
  codeudor: CodeudorRequest | null;
  asesor: string | null;
  /** Código de la sucursal ("01" Neiva ... "14" Guadalupe). */
  sucursal: string;
}

export interface FilaAmortizacion {
  numeroCuota: number;
  fechaVencimiento: string;
  valorCapital: number;
  valorInteres: number;
  valorCuota: number;
  saldoCapitalFinal: number;
}

export interface PlanAmortizacion {
  valorCuota: number;
  totalIntereses: number;
  totalPagar: number;
  filas: FilaAmortizacion[];
}

export interface PagosMes {
  mes: string;
  cantidad: number;
  total: number;
}

export interface Tablero {
  fechaCorte: string;
  totalClientes: number;
  totalCreditos: number;
  creditosAlDia: number;
  creditosEnMora: number;
  creditosCancelados: number;
  carteraTotal: number;
  carteraVencida: number;
  indiceMora: number;
  pagosMesCantidad: number;
  pagosMesValor: number;
  solicitudesPendientes: number;
  creditosPorSemaforo: Record<Semaforo, number>;
  pagosUltimosMeses: PagosMes[];
  recaudoTramites: number;
  tramitesPagados: number;
  cuentaRecaudoTramites: string;
}

export interface RegistroAuditoria {
  id: number;
  usuario: string | null;
  accion: string;
  entidad: string | null;
  entidadId: string | null;
  detalle: string | null;
  direccionIp: string | null;
  fecha: string;
}

export interface ErrorFila {
  fila: number;
  campo: string | null;
  mensaje: string;
}

export interface ResultadoImportacion {
  importacionId: number | null;
  tipo: TipoCarga;
  nombreArchivo: string;
  guardado: boolean;
  totalFilas: number;
  filasValidas: number;
  filasConError: number;
  estado: EstadoImportacion;
  errores: ErrorFila[];
  muestra: Record<string, string>[];
}

export interface ImportacionInfo {
  id: number;
  tipo: TipoCarga;
  nombreArchivo: string;
  fecha: string;
  usuario: string;
  totalFilas: number;
  filasExitosas: number;
  filasConError: number;
  estado: EstadoImportacion;
}

// ============================================================ mensajes a clientes

export type TipoMensaje = 'VENCE_HOY' | 'RECORDATORIO' | 'MORA' | 'GENERAL';

export interface AvisoSugerido {
  creditoId: number;
  numeroCredito: string;
  clienteId: number;
  cliente: string;
  documentoCliente: string;
  telefono: string | null;
  placa: string;
  tipo: TipoMensaje;
  fechaCuota: string | null;
  numeroCuota: number;
  valor: number;
  diasMora: number;
  valorMora: number;
  titulo: string;
  texto: string;
  enlaceWhatsapp: string | null;
  enviadoHoy: boolean;
  codeudor: string | null;
  telefonoCodeudor: string | null;
  enlaceWhatsappCodeudor: string | null;
}

export interface PanelAvisos {
  fecha: string;
  esDiaDeCorte: boolean;
  proximoCorte: string;
  venceHoy: AvisoSugerido[];
  proximos: AvisoSugerido[];
  mora: AvisoSugerido[];
}

export interface MensajeInfo {
  id: number;
  tipo: TipoMensaje;
  titulo: string;
  texto: string;
  fechaEnvio: string;
  leido: boolean;
  fechaLectura: string | null;
  numeroCredito: string | null;
  cliente: string;
  documentoCliente: string;
}

export interface BandejaCliente {
  noLeidos: number;
  mensajes: MensajeInfo[];
}

export interface ResultadoEnvio {
  enviados: number;
  omitidos: number;
  mensaje: string;
}

// ============================================================ abonos individuales e historial

export type TipoAbono = 'NORMAL' | 'CAPITAL_PLAZO' | 'CAPITAL_CUOTA' | 'TOTAL';

export interface AbonoRequest {
  creditoId: number;
  tipoAbono: TipoAbono;
  valor: number | null;
  medioPago: MedioPago;
  concepto: string | null;
  /** Sucursal donde se recibe el pago. */
  sucursal: string | null;
}

export interface CreditoParaAbono {
  credito: CreditoResumen;
  valorNormalPago: number;
  valorTotalPago: number;
  seguroVidaCuota: number;
  fogadeCuota: number;
  sucursal: string;
}

export interface DeudorAbonos {
  clienteId: number;
  documento: string;
  nombre: string;
  telefono: string | null;
  fecha: string;
  creditos: CreditoParaAbono[];
}

export interface LineaAbono {
  tipo: string;
  cuota: number | null;
  detalle: string;
  saldoAnterior: number;
  valor: number;
  saldo: number;
}

export interface VistaPreviaAbono {
  creditoId: number;
  numeroCredito: string;
  tipoAbono: TipoAbono;
  valor: number;
  aMora: number;
  aCuotas: number;
  aCapital: number;
  saldoCapitalAnterior: number;
  saldoCapitalNuevo: number;
  cuotasPendientesAntes: number;
  cuotasPendientesDespues: number;
  valorCuotaAntes: number;
  valorCuotaDespues: number;
  cancelaCredito: boolean;
  resumen: string;
  lineas: LineaAbono[];
}

export interface CalculoFecha {
  fecha: string;
  total: boolean;
  capital: number;
  interes: number;
  seguroVida: number;
  fogade: number;
  mora: number;
  diasMora: number;
  diasCausados: number;
  valorPagar: number;
  explicacion: string;
}

export interface PersonaHistorial {
  documento: string;
  nombre: string;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
  ciudad: string | null;
  esCliente: boolean;
  esCodeudor: boolean;
}

export interface CreditoHistorial {
  credito: CreditoResumen;
  tipoCredito: string;
  periodicidad: string;
  fechaDesembolso: string;
  fechaCancelacion: string | null;
  asesor: string | null;
  sucursal: string;
  cuotaOficial: number | null;
  seguroVidaCuota: number;
  fogadeCuota: number;
  codeudor: CodeudorInfo | null;
}

export interface Historial {
  persona: PersonaHistorial;
  fecha: string;
  creditosDeudor: CreditoHistorial[];
  creditosCodeudor: CreditoHistorial[];
}
