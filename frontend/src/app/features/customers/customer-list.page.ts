import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Customer, CustomerSegment } from './customer.models';
import { Pagination } from '../../shared/ui/pagination';
import { CustomersService } from './customers.service';

/** CRM: clientes de la empresa con busqueda, segmento y filtro de consentimiento comercial. */
@Component({
  selector: 'vf-customer-list-page',
  imports: [Pagination, CurrencyPipe, DatePipe, FormsModule, RouterLink, PageHeader, Spinner, EmptyState, StatusBadge],
  template: `
    <vf-page-header title="Clientes" subtitle="Fichas creadas automaticamente con cada compra o reserva" />

    <form class="mb-5 flex flex-wrap items-center gap-3" (ngSubmit)="search()">
      <input name="search" type="search" class="vf-input-search max-w-xs" placeholder="Nombre, email o etiqueta" [(ngModel)]="filters.search" />
      <select name="segment" class="vf-input w-auto" [(ngModel)]="filters.segment" (change)="search()">
        <option value="">Todos los segmentos</option>
        <option value="NEW">Nuevos (1 compra)</option>
        <option value="RECURRING">Recurrentes (2+ compras)</option>
        <option value="VIP">VIP (200 € o mas)</option>
        <option value="INACTIVE">Inactivos (+90 dias)</option>
      </select>
      <label class="flex items-center gap-2 text-sm text-zinc-300">
        <input type="checkbox" name="marketingOnly" [(ngModel)]="filters.marketingOnly" (change)="search()" />
        Solo con consentimiento comercial
      </label>
      <button type="submit" class="vf-btn-secondary">Buscar</button>
    </form>

    @if (loading() && !result()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (result(); as page) {
      @if (page.content.length === 0) {
        <vf-empty-state title="Sin clientes" description="Los clientes aparecen aqui al pagar su primer pedido o reservar una mesa." icon="user" />
      } @else {
        <div class="vf-card overflow-x-auto p-0">
          <table class="vf-table">
            <thead><tr><th>Cliente</th><th>Segmentos</th><th class="text-right">Pedidos</th><th class="text-right">Gastado</th><th>Ultima compra</th><th>Comercial</th></tr></thead>
            <tbody>
              @for (c of page.content; track c.id) {
                <tr>
                  <td>
                    <a [routerLink]="['/admin/clientes', c.id]" class="text-white hover:underline">{{ c.firstName }} {{ c.lastName }}</a>
                    <p class="text-xs text-zinc-500">{{ c.email }}</p>
                  </td>
                  <td><div class="flex flex-wrap gap-1">@for (s of c.segments; track s) { <vf-status-badge [status]="s" /> }</div></td>
                  <td class="text-right text-zinc-300">{{ c.ordersCount }}</td>
                  <td class="text-right font-medium text-white">{{ c.totalSpent | currency: 'EUR' }}</td>
                  <td class="text-zinc-400">{{ c.lastPurchaseAt ? (c.lastPurchaseAt | date: 'd MMM yyyy') : '—' }}</td>
                  <td>{{ c.marketingConsent ? '✓' : '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <vf-pagination [page]="page" (pageChange)="goToPage($event)" />
      }
    }
  `,
})
export class CustomerListPage implements OnInit {
  private readonly customersService = inject(CustomersService);
  protected readonly result = signal<Page<Customer> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected filters: { search: string; segment: CustomerSegment | ''; marketingOnly: boolean; page: number } = {
    search: '',
    segment: '',
    marketingOnly: false,
    page: 0,
  };

  ngOnInit(): void {
    this.load();
  }

  protected search(): void {
    this.filters.page = 0;
    this.load();
  }

  protected goToPage(page: number): void {
    this.filters.page = page;
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.customersService.list(this.filters).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
