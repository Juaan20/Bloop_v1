import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';

export interface AdminAlert {
  id: string;
  type: 'RESERVATION_REQUESTED' | 'RESERVATION_CANCELLED' | 'LOW_STOCK' | 'SOLD_OUT';
  title: string;
  message: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface EmailLog {
  id: string;
  recipientEmail: string;
  type: string;
  subject: string;
  body: string;
  /** Version HTML (la que ve el cliente); null en correos antiguos. */
  htmlBody: string | null;
  status: 'SENT' | 'LOGGED' | 'FAILED';
  errorMessage: string | null;
  createdAt: string;
}

/**
 * Alertas administrativas (campana) e historial de correos. Endpoints: AlertController.java.
 * unreadCount es un signal para que la campana de la barra superior se actualice sola.
 */
@Injectable({ providedIn: 'root' })
export class AlertsService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;
  readonly unreadCount = signal(0);

  latest(): Observable<{ unreadCount: number; alerts: AdminAlert[] }> {
    return this.http
      .get<{ unreadCount: number; alerts: AdminAlert[] }>(`${this.api}/alerts`)
      .pipe(tap((r) => this.unreadCount.set(r.unreadCount)));
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.api}/alerts/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.api}/alerts/read-all`, {}).pipe(tap(() => this.unreadCount.set(0)));
  }

  emailLog(page: number): Observable<Page<EmailLog>> {
    return this.http.get<Page<EmailLog>>(`${this.api}/notifications`, { params: new HttpParams().set('page', page).set('size', 25) });
  }

  /** Envia un correo de prueba al propio usuario y dice si el servidor SMTP lo acepto. */
  testEmail(): Observable<{ recipient: string; status: 'SENT' | 'LOGGED' | 'FAILED'; errorMessage: string | null; mailEnabled: boolean }> {
    return this.http.post<{ recipient: string; status: 'SENT' | 'LOGGED' | 'FAILED'; errorMessage: string | null; mailEnabled: boolean }>(`${this.api}/notifications/test-email`, {});
  }
}
