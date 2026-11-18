import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Role, StaffUser, UsersService } from './users.service';

/** Equipo de la empresa y catalogo de roles con sus permisos. */
@Component({
  selector: 'vf-user-list-page',
  imports: [DatePipe, FormsModule, RouterLink, StatusLabelPipe, PageHeader, Spinner, StatusBadge],
  template: `
    <vf-page-header title="Equipo y roles" subtitle="Empleados de tu empresa, sus roles y locales asignados">
      <a routerLink="/admin/usuarios/nuevo" class="vf-btn-primary">+ Nuevo usuario</a>
    </vf-page-header>

    <form class="mb-5 flex gap-3" (ngSubmit)="load(0)">
      <input name="search" type="search" class="vf-input-search max-w-xs" placeholder="Nombre o email" [(ngModel)]="search" />
      <button type="submit" class="vf-btn-secondary">Buscar</button>
    </form>

    @if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (result(); as page) {
      <div class="vf-card overflow-x-auto p-0">
        <table class="vf-table">
          <thead><tr><th>Usuario</th><th>Roles</th><th class="text-right">Locales</th><th>Ultimo acceso</th><th>Estado</th></tr></thead>
          <tbody>
            @for (u of page.content; track u.id) {
              <tr>
                <td><a [routerLink]="['/admin/usuarios', u.id]" class="font-medium text-white hover:underline">{{ u.firstName }} {{ u.lastName }}</a>
                  <p class="text-xs text-zinc-500">{{ u.email }}</p></td>
                <td class="text-zinc-300">
                  @for (r of u.roles; track r) { <span class="mr-1 rounded bg-zinc-800 px-1.5 py-0.5 text-xs">{{ r | statusLabel }}</span> }
                </td>
                <td class="text-right text-zinc-300">{{ u.venueIds.length || 'Todos' }}</td>
                <td class="text-zinc-400">{{ u.lastLoginAt ? (u.lastLoginAt | date: 'd MMM HH:mm') : 'Nunca' }}</td>
                <td><vf-status-badge [status]="u.status" /></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <vf-spinner />
    }

    <h2 class="mt-10 mb-4 font-display text-lg font-semibold text-white">Roles disponibles</h2>
    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      @for (r of roles(); track r.code) {
        <div class="vf-card">
          <p class="font-semibold text-white">{{ r.name }}</p>
          <p class="mt-1 text-sm text-zinc-400">{{ r.description }}</p>
          <div class="mt-3 flex flex-wrap gap-1">
            @for (p of r.permissions; track p) { <span class="rounded bg-brand-500/10 px-1.5 py-0.5 font-mono text-[11px] text-brand-300">{{ p }}</span> }
          </div>
        </div>
      }
    </div>
  `,
})
export class UserListPage implements OnInit {
  private readonly usersService = inject(UsersService);
  protected readonly result = signal<Page<StaffUser> | null>(null);
  protected readonly roles = signal<Role[]>([]);
  protected readonly error = signal<string | null>(null);
  protected search = '';

  ngOnInit(): void {
    this.load(0);
    this.usersService.roles().subscribe((roles) => this.roles.set(roles));
  }

  protected load(page: number): void {
    this.usersService.list(this.search, page).subscribe({
      next: (p) => this.result.set(p),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
