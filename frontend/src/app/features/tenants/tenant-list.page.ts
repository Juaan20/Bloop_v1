import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Tenant, TenantsService } from './tenants.service';

/** Empresas clientes de la plataforma (solo SUPER_ADMIN). */
@Component({
  selector: 'vf-tenant-list-page',
  imports: [DatePipe, FormsModule, RouterLink, PageHeader, Spinner, EmptyState, StatusBadge],
  template: `
    <vf-page-header title="Empresas" subtitle="Clientes de la plataforma Bloop">
      <a routerLink="/admin/empresas/nueva" class="vf-btn-primary">+ Nueva empresa</a>
    </vf-page-header>
    <form class="mb-5 flex gap-3" (ngSubmit)="load(0)">
      <input name="search" type="search" class="vf-input-search max-w-xs" placeholder="Nombre o email" [(ngModel)]="search" />
      <button type="submit" class="vf-btn-secondary">Buscar</button>
    </form>
    @if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (result(); as page) {
      @if (page.content.length === 0) {
        <vf-empty-state title="No hay empresas" icon="building" />
      } @else {
        <div class="vf-card overflow-x-auto p-0">
          <table class="vf-table">
            <thead><tr><th>Empresa</th><th>Contacto</th><th>Alta</th><th>Estado</th></tr></thead>
            <tbody>
              @for (t of page.content; track t.id) {
                <tr>
                  <td><a [routerLink]="['/admin/empresas', t.id]" class="font-medium text-white hover:underline">{{ t.name }}</a>
                    <p class="text-xs text-zinc-500">{{ t.legalName }} {{ t.taxId ? '· ' + t.taxId : '' }}</p></td>
                  <td class="text-zinc-300">{{ t.contactEmail }}</td>
                  <td class="text-zinc-400">{{ t.createdAt | date: 'd MMM yyyy' }}</td>
                  <td><vf-status-badge [status]="t.status" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else {
      <vf-spinner />
    }
  `,
})
export class TenantListPage implements OnInit {
  private readonly tenantsService = inject(TenantsService);
  protected readonly result = signal<Page<Tenant> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected search = '';

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.tenantsService.list(this.search, page).subscribe({
      next: (p) => this.result.set(p),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
