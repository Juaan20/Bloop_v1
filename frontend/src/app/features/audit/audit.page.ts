import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { Pagination } from '../../shared/ui/pagination';
import { AuditEntry, AuditService } from './audit.service';

const ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILED', 'USER_CREATED', 'USER_UPDATED', 'VENUE_CREATED', 'VENUE_UPDATED',
  'EVENT_CREATED', 'EVENT_UPDATED', 'EVENT_PUBLISHED', 'EVENT_UNPUBLISHED', 'EVENT_CANCELLED',
  'SESSION_CHANGED', 'TICKET_TYPE_CHANGED', 'ORDER_PAID', 'PAYMENT_FAILED', 'TICKET_VALIDATED', 'TENANT_UPDATED',
  // Fase 2 (deben coincidir con AuditAction.java)
  'CUSTOMER_UPDATED', 'CUSTOMER_EXPORTED', 'CUSTOMER_ANONYMIZED', 'CONSENT_CHANGED', 'PROMOTER_CREATED',
  'PROMOTER_UPDATED', 'PROMO_CODE_CREATED', 'PROMO_CODE_UPDATED', 'VIP_TABLE_CHANGED', 'RESERVATION_CREATED',
  'RESERVATION_STATUS_CHANGED', 'RESERVATION_DEPOSIT_PAID', 'TICKETS_RESENT', 'TICKET_HOLDER_CHANGED',
  'TICKET_TRANSFERRED',
  // Fase 3
  'POS_CATALOG_CHANGED', 'POS_TERMINAL_CHANGED', 'CASH_SESSION_OPENED', 'CASH_SESSION_CLOSED', 'CASH_MOVEMENT',
  'POS_SALE_DISCOUNT', 'POS_SALE_VOIDED', 'WAREHOUSE_CHANGED', 'STOCK_MOVEMENT',
];

/** Registro de auditoria: quien hizo que y cuando dentro de la empresa. */
@Component({
  selector: 'vf-audit-page',
  imports: [Pagination, DatePipe, FormsModule, PageHeader, Spinner],
  template: `
    <vf-page-header title="Auditoria" subtitle="Accesos, cambios de configuracion, operaciones economicas y validaciones">
      <select class="vf-input w-auto" [(ngModel)]="action" (change)="load(0)" aria-label="Accion">
        <option value="">Todas las acciones</option>
        @for (a of actions; track a) { <option [value]="a">{{ a }}</option> }
      </select>
    </vf-page-header>
    @if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (result(); as page) {
      <div class="vf-card overflow-x-auto p-0">
        <table class="vf-table">
          <thead><tr><th>Fecha</th><th>Usuario</th><th>Accion</th><th>Detalle</th><th>IP</th></tr></thead>
          <tbody>
            @for (e of page.content; track e.id) {
              <tr>
                <td class="whitespace-nowrap text-zinc-400">{{ e.createdAt | date: 'd MMM HH:mm:ss' }}</td>
                <td class="text-zinc-300">{{ e.userEmail ?? '—' }}</td>
                <td><span class="rounded bg-zinc-800 px-1.5 py-0.5 font-mono text-xs text-zinc-200">{{ e.action }}</span></td>
                <td class="max-w-md truncate text-zinc-400" [title]="e.details ?? ''">{{ e.entityType }} {{ e.details }}</td>
                <td class="font-mono text-xs text-zinc-500">{{ e.ipAddress }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="py-8 text-center text-zinc-500">Sin registros.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <vf-pagination [page]="page" (pageChange)="load($event)" />
    } @else {
      <vf-spinner />
    }
  `,
})
export class AuditPage implements OnInit {
  private readonly auditService = inject(AuditService);
  protected readonly actions = ACTIONS;
  protected readonly result = signal<Page<AuditEntry> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected action = '';

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.auditService.list(this.action, page).subscribe({
      next: (p) => this.result.set(p),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
