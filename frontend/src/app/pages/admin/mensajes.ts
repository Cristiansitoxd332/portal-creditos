import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AvisoSugerido, MensajeInfo, PanelAvisos, TipoMensaje } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe } from '../../shared/pipes';

type Grupo = 'MORA' | 'VENCE_HOY' | 'RECORDATORIO' | 'ENVIADOS';

const TEXTO_TIPO: Record<TipoMensaje, string> = {
  MORA: 'Aviso de mora', VENCE_HOY: 'Vence hoy', RECORDATORIO: 'Vence pronto', GENERAL: 'Mensaje',
};
const CLASE_TIPO: Record<TipoMensaje, string> = {
  MORA: 'chip-mora', VENCE_HOY: 'chip-primario', RECORDATORIO: 'chip-alerta', GENERAL: 'chip-neutro',
};

/**
 * Recordatorios de pago (fechas de corte 5 y 20) y avisos de mora, calculados en tiempo real.
 * El mensaje queda en el portal del cliente; el boton de WhatsApp abre el chat con el texto listo.
 */
@Component({
  selector: 'app-mensajes',
  imports: [FormsModule, RouterLink, Icono, Encabezado, Cargando, FechaPipe, MonedaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Mensajes a clientes" subtitulo="Recordatorios de pago y avisos de mora, calculados con la fecha de hoy" />

    @if (panel(); as p) {
      <div class="aviso" [class.aviso-corte]="p.esDiaDeCorte">
        <app-icono nombre="calendar" [tamano]="18" />
        <span>
          Hoy es <strong>{{ p.fecha | fecha: 'larga' }}</strong>.
          @if (p.esDiaDeCorte) { <strong>Es día de corte:</strong> hoy vencen cuotas. }
          @else { Próxima fecha de corte: <strong>{{ p.proximoCorte | fecha: 'larga' }}</strong>. }
          Fechas de pago de Rentar: días 5 y 20 de cada mes.
        </span>
      </div>

      <div class="pestanas" role="tablist">
        @for (g of grupos; track g.clave) {
          <button type="button" role="tab" [attr.aria-selected]="grupo() === g.clave" [class.activa]="grupo() === g.clave" (click)="grupo.set(g.clave)">
            {{ g.texto }}<span class="cuenta">{{ cantidad(g.clave) }}</span>
          </button>
        }
      </div>

      @if (grupo() !== 'ENVIADOS') {
        @let lista = listaActual();
        <div class="cabecera-grupo">
          <p class="texto-suave">{{ descripcion[grupo()] }}</p>
          @if (pendientes() > 0) {
            <button type="button" class="btn btn-primario" [disabled]="enviando()" (click)="enviarTodos()">
              <app-icono nombre="bell" [tamano]="18" />Enviar a todos ({{ pendientes() }})
            </button>
          }
        </div>

        <div class="lista">
          @for (a of lista; track a.creditoId) {
            <article class="tarjeta aviso-cliente aparecer" [class.mora-borde]="a.tipo === 'MORA'">
              <header>
                <div>
                  <strong>{{ a.cliente }}</strong>
                  <span class="texto-suave">CC {{ a.documentoCliente }} · <a [routerLink]="['/admin/creditos', a.creditoId]">{{ a.numeroCredito }}</a> · {{ a.placa }}</span>
                </div>
                @if (a.enviadoHoy) { <span class="chip chip-ok"><app-icono nombre="check" [tamano]="14" />Enviado hoy</span> }
              </header>
              <dl>
                @if (a.tipo === 'MORA') {
                  <div><dt>Días de mora</dt><dd class="mora">{{ a.diasMora }}</dd></div>
                  <div><dt>Valor vencido</dt><dd>{{ a.valor | moneda }}</dd></div>
                  <div><dt>Mora acumulada</dt><dd class="mora">{{ a.valorMora | moneda }}</dd></div>
                } @else {
                  <div><dt>Cuota</dt><dd>N.° {{ a.numeroCuota }}</dd></div>
                  <div><dt>Vence</dt><dd>{{ a.fechaCuota | fecha }}</dd></div>
                  <div><dt>Valor</dt><dd>{{ a.valor | moneda }}</dd></div>
                }
              </dl>
              <div class="campo">
                <label [for]="'tx' + a.creditoId">Mensaje (puede editarlo antes de enviar)</label>
                <textarea [id]="'tx' + a.creditoId" rows="4" maxlength="700" [ngModel]="texto(a)" (ngModelChange)="editar(a, $event)"></textarea>
              </div>
              <div class="whatsapp">
                <div class="campo">
                  <label [for]="'wa' + a.creditoId">Número de WhatsApp (puede cambiarlo)</label>
                  <input [id]="'wa' + a.creditoId" inputmode="tel" maxlength="15" placeholder="3001234567"
                         [ngModel]="numero(a)" (ngModelChange)="editarNumero(a, $event)">
                </div>
                @if (whatsapp(a); as enlace) {
                  <a class="btn btn-whatsapp" [href]="enlace" target="_blank" rel="noopener noreferrer">
                    <app-icono nombre="chat" [tamano]="18" />Enviar por WhatsApp
                  </a>
                } @else {
                  <button type="button" class="btn btn-whatsapp" disabled><app-icono nombre="chat" [tamano]="18" />Número no válido</button>
                }
              </div>
              <div class="acciones">
                @if (a.enlaceWhatsappCodeudor) {
                  <a class="btn btn-secundario btn-pequeno" [href]="a.enlaceWhatsappCodeudor" target="_blank" rel="noopener noreferrer"
                     [title]="'Avisar al codeudor ' + a.codeudor">
                    <app-icono nombre="users" [tamano]="16" />Codeudor {{ a.telefonoCodeudor }}
                  </a>
                }
                <button type="button" class="btn btn-primario btn-pequeno" [disabled]="enviando() || !texto(a).trim()" (click)="enviar(a)">
                  <app-icono nombre="bell" [tamano]="16" />{{ a.enviadoHoy ? 'Enviar de nuevo' : 'Enviar al portal' }}
                </button>
              </div>
            </article>
          } @empty {
            <div class="tarjeta vacio"><app-icono nombre="check" [tamano]="32" /><p>{{ vacio[grupo()] }}</p></div>
          }
        </div>
      } @else {
        @if (historial(); as h) {
          <div class="tabla-caja tarjeta">
            <table class="tabla">
              <thead><tr><th>Fecha</th><th>Cliente</th><th>Tipo</th><th>Mensaje</th><th>Estado</th></tr></thead>
              <tbody>
                @for (m of h; track m.id) {
                  <tr>
                    <td class="nowrap">{{ m.fechaEnvio | fecha: 'hora' }}</td>
                    <td>{{ m.cliente }}<br><small class="texto-suave">{{ m.numeroCredito ?? '' }}</small></td>
                    <td><span [class]="'chip ' + claseTipo[m.tipo]">{{ textoTipo[m.tipo] }}</span></td>
                    <td class="texto-msj">{{ m.texto }}</td>
                    <td class="nowrap">
                      @if (m.leido) { <span class="chip chip-ok">Leído</span><br><small class="texto-suave">{{ m.fechaLectura | fecha: 'hora' }}</small> }
                      @else { <span class="chip chip-neutro">Sin leer</span> }
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="vacio-tabla">Aún no se han enviado mensajes.</td></tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <app-cargando [cantidad]="3" [alto]="60" />
        }
      }
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="3" [alto]="160" />
    }
  `,
  styles: [`
    .aviso-corte { background: var(--primario-suave); color: var(--primario-oscuro); }
    .pestanas { display: flex; gap: 6px; flex-wrap: wrap; margin: 16px 0; }
    .pestanas button { height: 40px; padding: 0 14px; border-radius: 999px; border: 1.5px solid var(--borde-fuerte); background: var(--superficie);
      font: inherit; font-size: 13px; font-weight: 700; color: var(--texto-2); cursor: pointer; display: flex; align-items: center; gap: 8px; }
    .pestanas .activa { background: var(--texto); color: var(--superficie); border-color: var(--texto); }
    .cuenta { min-width: 22px; height: 22px; padding: 0 6px; border-radius: 11px; background: var(--superficie-2); color: var(--texto);
      font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
    .cabecera-grupo { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
    .cabecera-grupo p { margin: 0; font-size: 13.5px; max-width: 640px; }
    .lista { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr)); align-items: start; }
    .aviso-cliente { display: flex; flex-direction: column; gap: 12px; }
    .mora-borde { border-left: 4px solid var(--mora); }
    header { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
    header div { display: flex; flex-direction: column; gap: 2px; }
    header span.texto-suave { font-size: 12.5px; }
    .chip { display: inline-flex; align-items: center; gap: 4px; }
    dl { margin: 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; background: var(--superficie-2); border-radius: 12px; padding: 10px 12px; }
    dt { font-size: 11.5px; color: var(--texto-3); }
    dd { margin: 0; font-weight: 800; font-size: 14.5px; }
    .mora { color: var(--mora); }
    textarea { font-size: 13.5px; line-height: 1.45; }
    .acciones { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
    .whatsapp { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; background: var(--ok-fondo); border-radius: 12px; padding: 10px 12px; }
    .whatsapp .campo { flex: 1; min-width: 180px; margin: 0; }
    .btn-whatsapp { background: #128c4a; color: #fff; border-color: #128c4a; }
    .btn-whatsapp:hover { background: #0d6e3a; }
    .btn-whatsapp:disabled { opacity: 0.6; }
    .vacio { display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--texto-3); padding: 36px; grid-column: 1 / -1; text-align: center; }
    .vacio p { margin: 0; }
    .nowrap { white-space: nowrap; }
    .texto-msj { font-size: 13px; color: var(--texto-2); min-width: 260px; }
    .vacio-tabla { text-align: center; color: var(--texto-3); padding: 24px; }
  `],
})
export class Mensajes {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly panel = signal<PanelAvisos | null>(null);
  protected readonly historial = signal<MensajeInfo[] | null>(null);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly grupo = signal<Grupo>('MORA');
  /** Textos editados por el administrador (por credito y tipo). */
  private readonly ediciones = signal<Record<string, string>>({});

  protected readonly textoTipo = TEXTO_TIPO;
  protected readonly claseTipo = CLASE_TIPO;
  protected readonly grupos: { clave: Grupo; texto: string }[] = [
    { clave: 'MORA', texto: 'En mora' },
    { clave: 'VENCE_HOY', texto: 'Vence hoy' },
    { clave: 'RECORDATORIO', texto: 'Vence pronto' },
    { clave: 'ENVIADOS', texto: 'Enviados' },
  ];
  protected readonly descripcion: Record<Grupo, string> = {
    MORA: 'Créditos con cuotas vencidas. El mensaje le recuerda al cliente ponerse al día en las fechas establecidas para evitar reportes.',
    VENCE_HOY: 'Clientes cuya cuota vence hoy (día de corte 5 o 20).',
    RECORDATORIO: 'Clientes cuya cuota vence en los próximos 3 días.',
    ENVIADOS: '',
  };
  protected readonly vacio: Record<Grupo, string> = {
    MORA: 'Ningún crédito está en mora hoy.',
    VENCE_HOY: 'Hoy no vence ninguna cuota.',
    RECORDATORIO: 'Ninguna cuota vence en los próximos 3 días.',
    ENVIADOS: '',
  };

  protected readonly listaActual = computed<AvisoSugerido[]>(() => {
    const p = this.panel();
    if (!p) return [];
    return this.grupo() === 'MORA' ? p.mora : this.grupo() === 'VENCE_HOY' ? p.venceHoy : this.grupo() === 'RECORDATORIO' ? p.proximos : [];
  });
  protected readonly pendientes = computed(() => this.listaActual().filter((a) => !a.enviadoHoy).length);

  constructor() {
    this.cargar(true);
  }

  private cargar(elegirGrupo = false): void {
    this.api.avisosMensajes().subscribe({
      next: (p) => {
        this.panel.set(p);
        if (elegirGrupo) {
          this.grupo.set(p.mora.length ? 'MORA' : p.venceHoy.length ? 'VENCE_HOY' : p.proximos.length ? 'RECORDATORIO' : 'MORA');
        }
      },
      error: (e) => this.error.set(mensajeError(e)),
    });
    this.api.historialMensajes().subscribe({ next: (h) => this.historial.set(h), error: () => this.historial.set([]) });
  }

  protected cantidad(g: Grupo): number {
    const p = this.panel();
    if (!p) return 0;
    if (g === 'ENVIADOS') return this.historial()?.length ?? 0;
    return (g === 'MORA' ? p.mora : g === 'VENCE_HOY' ? p.venceHoy : p.proximos).length;
  }

  private clave(a: AvisoSugerido): string {
    return a.tipo + '-' + a.creditoId;
  }

  protected texto(a: AvisoSugerido): string {
    return this.ediciones()[this.clave(a)] ?? a.texto;
  }

  protected editar(a: AvisoSugerido, texto: string): void {
    this.ediciones.update((e) => ({ ...e, [this.clave(a)]: texto }));
  }

  /** Numeros de WhatsApp editados por el administrador. */
  private readonly numeros = signal<Record<string, string>>({});

  protected numero(a: AvisoSugerido): string {
    return this.numeros()[this.clave(a)] ?? (a.telefono ?? '');
  }

  protected editarNumero(a: AvisoSugerido, numero: string): void {
    this.numeros.update((n) => ({ ...n, [this.clave(a)]: numero }));
  }

  /**
   * Enlace de WhatsApp con el numero y el texto que el administrador dejo en pantalla.
   * Abre WhatsApp (web o app) con el mensaje listo; el envio se confirma alli con el boton Enviar.
   */
  protected whatsapp(a: AvisoSugerido): string | null {
    let digitos = this.numero(a).replace(/\D/g, '');
    if (digitos.length === 10) digitos = '57' + digitos;
    if (digitos.length < 11 || digitos.length > 15) return null;
    return `https://wa.me/${digitos}?text=${encodeURIComponent(this.texto(a))}`;
  }

  protected enviar(a: AvisoSugerido): void {
    this.enviando.set(true);
    this.api.enviarMensaje(a.creditoId, a.tipo, this.texto(a).trim()).subscribe({
      next: () => {
        this.notificaciones.exito(`Mensaje enviado a ${a.cliente}. Lo verá en su portal.`);
        this.enviando.set(false);
        this.cargar();
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.enviando.set(false); },
    });
  }

  protected enviarTodos(): void {
    const g = this.grupo();
    if (g === 'ENVIADOS') return;
    this.enviando.set(true);
    this.api.enviarMensajesTodos(g).subscribe({
      next: (r) => { this.notificaciones.exito(r.mensaje); this.enviando.set(false); this.cargar(); },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.enviando.set(false); },
    });
  }
}
