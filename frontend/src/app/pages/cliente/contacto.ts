import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { ApiService } from '../../core/api.service';
import { CanalInfo, TipoCanal } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';

const ICONO_CANAL: Record<TipoCanal, string> = {
  WHATSAPP: 'chat', TELEFONO: 'phone', CORREO: 'mail', SITIO_WEB: 'globe', OFICINA: 'pin', REDES_SOCIALES: 'users',
};

/** Oficina principal de Rentar Inversiones Ltda. */
const OFICINA = {
  nombre: 'Oficina principal · Neiva',
  direccion: 'Carrera 5 # 15-01, Neiva, Huila',
  busqueda: 'Carrera 5 #15-01, Neiva, Huila, Colombia',
  telefono: '3102066474',
};

/**
 * Horario de atencion de la oficina (hora de Colombia). Dia: 0 = domingo ... 6 = sabado.
 * Cada franja es [hora inicio, hora fin] en horas decimales (14.5 = 2:30 p. m.).
 */
const HORARIO: Record<number, [number, number][]> = {
  1: [[8, 12], [14, 18]], 2: [[8, 12], [14, 18]], 3: [[8, 12], [14, 18]], 4: [[8, 12], [14, 18]], 5: [[8, 12], [14, 18]],
  6: [[8, 12]],
};
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

/** Hora actual en Colombia (America/Bogota), sin importar la zona horaria del dispositivo. */
function ahoraColombia(): { dia: number; hora: number; texto: string } {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Bogota', weekday: 'short', hour: 'numeric', minute: 'numeric', hourCycle: 'h23',
  }).formatToParts(new Date());
  const v = (t: string) => partes.find((p) => p.type === t)?.value ?? '';
  const dia = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(v('weekday'));
  const hora = Number(v('hour')) + Number(v('minute')) / 60;
  const texto = new Intl.DateTimeFormat('es-CO', { timeZone: 'America/Bogota', hour: 'numeric', minute: '2-digit' }).format(new Date());
  return { dia, hora, texto };
}

function formatoHora(h: number): string {
  if (h === 12) return '12:00 m.';
  const hh = Math.floor(h), mm = Math.round((h - hh) * 60);
  return `${hh > 12 ? hh - 12 : hh}:${String(mm).padStart(2, '0')} ${hh >= 12 ? 'p. m.' : 'a. m.'}`;
}

/** Canales de atencion (configurables por el administrador) y ubicacion de la oficina en tiempo real. */
@Component({
  selector: 'app-contacto',
  imports: [Icono, Encabezado, Cargando],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Contacto" subtitulo="Canales oficiales de atención de Rentar Inversiones Ltda." />

    <section class="tarjeta oficina aparecer">
      <div class="mapa">
        <iframe [src]="mapa" title="Ubicación de la oficina principal de Rentar en Neiva" loading="lazy"
                referrerpolicy="no-referrer-when-downgrade" allowfullscreen></iframe>
      </div>
      <div class="datos">
        <span class="etiqueta">{{ oficina.nombre }}</span>
        <h2>{{ oficina.direccion }}</h2>
        <p class="estado" [class.abierto]="estado().abierto" role="status">
          <span class="punto"></span><strong>{{ estado().abierto ? 'Abierto ahora' : 'Cerrado ahora' }}</strong>
          <span>· {{ estado().detalle }}</span>
        </p>
        <p class="hora"><app-icono nombre="clock" [tamano]="16" />Hora en Neiva: {{ reloj().texto }}</p>
        <div class="acciones">
          <a class="btn btn-primario" [href]="comoLlegar" target="_blank" rel="noopener noreferrer"><app-icono nombre="pin" [tamano]="18" />Cómo llegar</a>
          <a class="btn btn-secundario" [href]="'tel:+57' + oficina.telefono"><app-icono nombre="phone" [tamano]="18" />Llamar</a>
        </div>
        <small class="texto-suave">“Cómo llegar” abre Google Maps con la ruta desde su ubicación actual.</small>
      </div>
    </section>

    @if (canales(); as lista) {
      <div class="lista">
        @for (c of lista; track c.id) {
          <article class="tarjeta canal aparecer">
            <span class="icono" [class.wa]="c.tipo === 'WHATSAPP'"><app-icono [nombre]="icono[c.tipo]" [tamano]="24" /></span>
            <div class="info">
              <strong>{{ c.nombre }}</strong>
              <span class="valor">{{ c.valor }}</span>
              @if (c.horario) { <small>{{ c.horario }}</small> }
            </div>
            @if (c.enlace) {
              <a class="btn btn-secundario btn-pequeno" [href]="c.enlace" target="_blank" rel="noopener noreferrer"
                 [attr.aria-label]="'Abrir ' + c.nombre">Abrir<app-icono nombre="chevron-right" [tamano]="16" /></a>
            }
          </article>
        }
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="4" [alto]="76" />
    }
  `,
  styles: [`
    .oficina { display: grid; gap: 18px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr)); margin-bottom: 16px; padding: 14px; }
    .mapa { border-radius: 14px; overflow: hidden; min-height: 240px; background: var(--superficie-2); }
    .mapa iframe { width: 100%; height: 100%; min-height: 240px; border: 0; display: block; }
    .datos { display: flex; flex-direction: column; gap: 10px; justify-content: center; padding: 4px 6px; }
    .datos h2 { font-size: 20px; font-weight: 800; }
    .estado { margin: 0; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 14px; color: var(--texto-2); }
    .estado strong { color: var(--mora); }
    .estado .punto { width: 10px; height: 10px; border-radius: 5px; background: var(--mora); }
    .estado.abierto strong { color: var(--ok); }
    .estado.abierto .punto { background: var(--ok-solido); box-shadow: 0 0 0 4px var(--ok-fondo); }
    .hora { margin: 0; display: flex; align-items: center; gap: 6px; font-size: 13.5px; color: var(--texto-3); }
    .acciones { display: flex; gap: 8px; flex-wrap: wrap; }
    .datos small { font-size: 12px; }
    .lista { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr)); margin-bottom: 16px; }
    .canal { display: flex; align-items: center; gap: 14px; }
    .icono { width: 50px; height: 50px; border-radius: 16px; background: var(--primario-suave); color: var(--primario); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .icono.wa { background: var(--ok-fondo); color: var(--ok); }
    .info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .valor { font-size: 14px; color: var(--texto-2); overflow-wrap: anywhere; }
    small { font-size: 12px; color: var(--texto-3); }
  `],
})
export class Contacto {
  protected readonly canales = signal<CanalInfo[] | null>(null);
  protected readonly error = signal('');
  protected readonly icono = ICONO_CANAL;
  protected readonly oficina = OFICINA;

  protected readonly mapa = inject(DomSanitizer).bypassSecurityTrustResourceUrl(
    `https://maps.google.com/maps?q=${encodeURIComponent(OFICINA.busqueda)}&z=17&output=embed`);
  protected readonly comoLlegar = `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(OFICINA.busqueda)}`;

  /** Reloj en tiempo real (se actualiza cada 30 segundos). */
  protected readonly reloj = signal(ahoraColombia());
  protected readonly estado = computed(() => {
    const { dia, hora } = this.reloj();
    const franja = (HORARIO[dia] ?? []).find(([ini, fin]) => hora >= ini && hora < fin);
    if (franja) return { abierto: true, detalle: `cierra a las ${formatoHora(franja[1])}` };
    const despues = (HORARIO[dia] ?? []).find(([ini]) => hora < ini);
    if (despues) return { abierto: false, detalle: `abre hoy a las ${formatoHora(despues[0])}` };
    for (let i = 1; i <= 7; i++) {
      const d = (dia + i) % 7;
      const primera = HORARIO[d]?.[0];
      if (primera) return { abierto: false, detalle: `abre ${i === 1 ? 'mañana' : 'el ' + DIAS[d]} a las ${formatoHora(primera[0])}` };
    }
    return { abierto: false, detalle: '' };
  });

  constructor() {
    const id = setInterval(() => this.reloj.set(ahoraColombia()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(id));
    inject(ApiService).canales().subscribe({
      next: (c) => this.canales.set(c),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }
}
