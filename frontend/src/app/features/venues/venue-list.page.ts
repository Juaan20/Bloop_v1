import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Venue } from './venue.models';
import { VenuesService } from './venues.service';

@Component({
  selector: 'vf-venue-list-page',
  imports: [RouterLink, PageHeader, Spinner, EmptyState, StatusBadge],
  template: `
    <vf-page-header title="Locales" subtitle="Establecimientos de tu empresa">
      @if (canManage) {
        <a routerLink="/admin/locales/nuevo" class="vf-btn-primary">+ Nuevo local</a>
      }
    </vf-page-header>

    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (venues().length === 0) {
      <vf-empty-state title="Aun no tienes locales" description="Crea tu primer establecimiento para poder publicar eventos." icon="pin">
        @if (canManage) {
          <a routerLink="/admin/locales/nuevo" class="vf-btn-primary">Crear local</a>
        }
      </vf-empty-state>
    } @else {
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        @for (v of venues(); track v.id) {
          <a [routerLink]="['/admin/locales', v.id]" class="vf-card block transition hover:border-zinc-600">
            <div class="flex items-start justify-between gap-2">
              <h3 class="font-display text-lg font-semibold text-white">{{ v.name }}</h3>
              <vf-status-badge [status]="v.status" />
            </div>
            <p class="mt-1 text-sm text-zinc-400">{{ v.addressLine }}, {{ v.city }}</p>
            <div class="mt-4 flex gap-6 text-sm">
              <div><p class="text-xs text-zinc-500">Aforo</p><p class="text-white">{{ v.capacity }}</p></div>
              @if (v.openingHours) {
                <div><p class="text-xs text-zinc-500">Horario</p><p class="text-white">{{ v.openingHours }}</p></div>
              }
            </div>
          </a>
        }
      </div>
    }
  `,
})
export class VenueListPage implements OnInit {
  private readonly venuesService = inject(VenuesService);
  protected readonly canManage = inject(AuthService).hasPermission(Permission.VENUE_MANAGE);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.venuesService.list().subscribe({
      next: (v) => {
        this.venues.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
