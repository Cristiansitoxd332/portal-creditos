import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_URL } from './config';
import { LoginResponse, Rol } from './models';

/** Datos de la sesion guardados en el navegador. */
interface Sesion {
  token: string;
  expiraEn: string;
  username: string;
  rol: Rol;
  nombre: string;
  debeCambiarPassword: boolean;
}

const CLAVE = 'portal-creditos.sesion';

/**
 * Maneja la sesion del usuario.
 * - El token JWT se guarda en sessionStorage: se borra al cerrar la pestana (mas seguro que localStorage).
 * - La sesion se expone como "signal": las pantallas se actualizan solas cuando cambia.
 * - Si el token expira, la sesion se considera cerrada.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesion = signal<Sesion | null>(this.leer());

  readonly usuario = computed(() => this.sesion());
  readonly autenticado = computed(() => this.sesion() !== null);
  readonly rol = computed(() => this.sesion()?.rol ?? null);
  readonly nombre = computed(() => this.sesion()?.nombre ?? '');
  readonly primerNombre = computed(() => this.nombre().split(' ')[0] ?? '');

  get token(): string | null {
    const s = this.sesion();
    if (!s) {
      return null;
    }
    if (new Date(s.expiraEn).getTime() <= Date.now()) {
      this.limpiar();
      return null;
    }
    return s.token;
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_URL}/auth/login`, { username, password }).pipe(
      tap((r) => this.guardar({
        token: r.token,
        expiraEn: r.expiraEn,
        username: r.username,
        rol: r.rol,
        nombre: r.nombre,
        debeCambiarPassword: r.debeCambiarPassword,
      })),
    );
  }

  cambiarPassword(passwordActual: string, passwordNueva: string, confirmacion: string): Observable<{ mensaje: string }> {
    return this.http
      .post<{ mensaje: string }>(`${API_URL}/auth/cambiar-password`, { passwordActual, passwordNueva, confirmacion })
      .pipe(tap(() => {
        const s = this.sesion();
        if (s) {
          this.guardar({ ...s, debeCambiarPassword: false });
        }
      }));
  }

  /** Ruta de inicio segun el rol. */
  rutaInicio(): string {
    const s = this.sesion();
    if (!s) {
      return '/login';
    }
    if (s.debeCambiarPassword) {
      return '/cambiar-password';
    }
    return s.rol === 'ADMIN' ? '/admin' : '/cliente';
  }

  logout(): void {
    this.limpiar();
    this.router.navigateByUrl('/login');
  }

  limpiar(): void {
    this.sesion.set(null);
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      /* almacenamiento no disponible */
    }
  }

  private guardar(s: Sesion): void {
    this.sesion.set(s);
    try {
      sessionStorage.setItem(CLAVE, JSON.stringify(s));
    } catch {
      /* almacenamiento no disponible: la sesion vive solo en memoria */
    }
  }

  private leer(): Sesion | null {
    try {
      const texto = sessionStorage.getItem(CLAVE);
      if (!texto) {
        return null;
      }
      const s = JSON.parse(texto) as Sesion;
      return new Date(s.expiraEn).getTime() > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
