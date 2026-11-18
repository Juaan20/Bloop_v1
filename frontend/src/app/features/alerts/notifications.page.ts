import { DatePipe } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Pagination } from '../../shared/ui/pagination';
import { AdminAlert, AlertsService, EmailLog } from './alerts.service';

/** Alertas administrativas de la empresa y (con AUDIT_VIEW) historial de correos enviados. */
@Component({
  selector: 'vf-notifications-page',
  imports: [Pagination, DatePipe, RouterLink, StatusLabelPipe, PageHeader, Spinner, StatusBadge],
  template: `
    <vf-page-header title="Notificaciones" subtitle="Avisos para el equipo y correos enviados a los clientes">
      @if (alerts().some((a) => !a.read)) {
        <button type="button" class="vf-btn-secondary" (click)="markAllRead()">Marcar todo como leido</button>
      }
    </vf-page-header>

    <div class="vf-card mb-6">
      <h2 class="font-display font-semibold text-white">Alertas</h2>
      @if (loading()) {
        <vf-spinner />
      } @else if (error()) {
        <p class="mt-3 text-sm text-red-400">{{ error() }}</p>
      } @else if (alerts().length === 0) {
        <p class="mt-3 text-sm text-zinc-500">No hay alertas.</p>
      } @else {
        <ul class="mt-3 divide-y divide-zinc-800">
          @for (a of alerts(); track a.id) {
            <li class="flex flex-wrap items-start justify-between gap-3 py-3" [class.opacity-60]="a.read">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  @if (!a.read) { <span class="h-2 w-2 rounded-full bg-fuchsia-400" aria-label="Sin leer"></span> }
                  <p class="font-medium text-zinc-100">{{ a.title }}</p>
                  <vf-status-badge [status]="a.type" />
                </div>
                <p class="mt-1 text-sm text-zinc-400">{{ a.message }}</p>
                <p class="mt-1 text-xs text-zinc-500">{{ a.createdAt | date: 'd MMM yyyy, HH:mm' }}</p>
              </div>
              <div class="flex gap-2">
                @if (a.link) { <a [routerLink]="a.link" class="vf-btn-ghost text-xs" (click)="markRead(a)">Ver</a> }
                @if (!a.read) { <button type="button" class="vf-btn-ghost text-xs" (click)="markRead(a)">Marcar leida</button> }
              </div>
            </li>
          }
        </ul>
      }
    </div>

    @if (canSeeEmails) {
      <div class="vf-card overflow-x-auto">
        <h2 class="font-display font-semibold text-white">Correos enviados</h2>
        <p class="mt-1 text-sm text-zinc-500">"No enviado (modo pruebas)": el envio real esta desactivado (MAIL_ENABLED=false) y el correo solo se guardo aqui. "Fallido": el servidor SMTP lo rechazo; toca la fila para ver el motivo.</p>
        @if (emails(); as page) {
          @if (page.content.length === 0) {
            <p class="mt-3 text-sm text-zinc-500">Aun no se ha enviado ningun correo.</p>
          } @else {
            <table class="vf-table mt-3">
              <thead><tr><th>Fecha</th><th>Destinatario</th><th>Tipo</th><th>Asunto</th><th>Estado</th></tr></thead>
              <tbody>
                @for (e of page.content; track e.id) {
                  <tr class="cursor-pointer" (click)="expanded.set(expanded() === e.id ? null : e.id)">
                    <td class="text-zinc-400">{{ e.createdAt | date: 'd MMM HH:mm' }}</td>
                    <td class="text-zinc-300">{{ e.recipientEmail }}</td>
                    <td class="text-xs text-zinc-400">{{ e.type | statusLabel }}</td>
                    <td class="text-zinc-200">{{ e.subject }}
                      @if (expanded() === e.id) {
                        @if (e.errorMessage) { <p class="mt-2 rounded-lg bg-red-500/10 p-2 text-xs text-red-300">{{ e.errorMessage }}</p> }
                        @if (e.htmlBody) {
                          <!-- Vista previa del correo tal como lo recibe el cliente (sandbox: sin scripts ni enlaces activos) -->
                          <iframe class="mt-2 h-[28rem] w-full rounded-lg border border-zinc-800 bg-white" sandbox="" [srcdoc]="preview(e.htmlBody)" title="Vista previa del correo" (click)="$event.stopPropagation()"></iframe>
                        } @else {
                          <pre class="mt-2 whitespace-pre-wrap text-xs text-zinc-400">{{ e.body }}</pre>
                        }
                      }</td>
                    <td><vf-status-badge [status]="e.status" /></td>
                  </tr>
                }
              </tbody>
            </table>
            <vf-pagination [page]="page" (pageChange)="loadEmails($event)" />
          }
        } @else {
          <vf-spinner />
        }
      </div>
    }
  `,
})
export class NotificationsPage implements OnInit {
  private readonly sanitizer = inject(DomSanitizer);

  /**
   * El HTML lo genera el propio backend (EmailTemplateRenderer escapa todos los datos) y se muestra
   * en un iframe con sandbox vacio: sin scripts, formularios ni navegacion.
   */
  protected preview(html: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  private readonly alertsService = inject(AlertsService);
  protected readonly canSeeEmails = inject(AuthService).hasPermission(Permission.AUDIT_VIEW);
  protected readonly alerts = signal<AdminAlert[]>([]);
  protected readonly emails = signal<Page<EmailLog> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly expanded = signal<string | null>(null);

  ngOnInit(): void {
    this.alertsService.latest().subscribe({
      next: (r) => {
        this.alerts.set(r.alerts);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
    if (this.canSeeEmails) this.loadEmails(0);
  }

  protected loadEmails(page: number): void {
    this.alertsService.emailLog(page).subscribe((p) => this.emails.set(p));
  }

  protected markRead(alert: AdminAlert): void {
    if (alert.read) return;
    this.alertsService.markRead(alert.id).subscribe(() => {
      this.alerts.update((list) => list.map((a) => (a.id === alert.id ? { ...a, read: true } : a)));
      this.alertsService.unreadCount.update((n) => Math.max(0, n - 1));
    });
  }

  protected markAllRead(): void {
    this.alertsService.markAllRead().subscribe(() => this.alerts.update((list) => list.map((a) => ({ ...a, read: true }))));
  }
}
