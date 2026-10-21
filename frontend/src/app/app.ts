import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TemaService } from './core/tema.service';
import { Notificaciones } from './shared/notificaciones';

/** Componente raiz: solo contiene el enrutador y los mensajes emergentes. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Notificaciones],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<router-outlet /><app-notificaciones />`,
})
export class App {
  // Se inyecta para aplicar el tema (claro/oscuro) desde el arranque.
  private readonly tema = inject(TemaService);
}
