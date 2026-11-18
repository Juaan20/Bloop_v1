import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { EventSummary } from '../events/event.models';
import { EventsService } from '../events/events.service';
import { CommissionType, PromoterDetail } from './promoter.models';
import { PromoterPerformance } from './promoter-performance';
import { PromotersService } from './promoters.service';

interface AssignmentRow {
  event: EventSummary;
  assigned: boolean;
  target: number | null;
}

/** Ficha de un promotor (admin): rendimiento, comision, estado y eventos asignados con objetivo. */
@Component({
  selector: 'vf-promoter-detail-page',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, PageHeader, Spinner, PromoterPerformance],
  template: `
    @if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (detail(); as d) {
      <vf-page-header [title]="d.promoter.name" [subtitle]="'Codigo de enlace: ' + d.promoter.referralCode">
        <a routerLink="/admin/promotores" class="vf-btn-secondary">Volver</a>
      </vf-page-header>

      <vf-promoter-performance [detail]="d" />

      <div class="mt-6 grid gap-6 lg:grid-cols-2">
        <form class="vf-card space-y-4" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display font-semibold text-white">Comision y estado</h2>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="vf-label" for="commissionType">Tipo</label>
              <select id="commissionType" class="vf-input" formControlName="commissionType">
                <option value="PERCENTAGE">Porcentaje (%)</option>
                <option value="FIXED_PER_TICKET">Fija por entrada (€)</option>
              </select>
            </div>
            <div>
              <label class="vf-label" for="commissionValue">Valor</label>
              <input id="commissionValue" type="number" min="0" step="0.01" class="vf-input" formControlName="commissionValue" />
            </div>
          </div>
          <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" formControlName="active" /> Activo (sus enlaces y codigos atribuyen ventas)</label>
          <p class="text-xs text-zinc-500">Los cambios de comision solo afectan a las ventas nuevas.</p>
          <button type="submit" class="vf-btn-primary" [disabled]="saving() || form.invalid">Guardar</button>
        </form>

        <div class="vf-card">
          <h2 class="font-display font-semibold text-white">Eventos asignados</h2>
          <p class="mt-1 text-sm text-zinc-500">El enlace ?rrpp= solo atribuye ventas en los eventos marcados.</p>
          @if (rows().length === 0) {
            <p class="mt-4 text-sm text-zinc-500">No hay eventos.</p>
          } @else {
            <ul class="mt-4 max-h-96 space-y-2 overflow-y-auto pr-1">
              @for (row of rows(); track row.event.id) {
                <li class="flex items-center justify-between gap-3 text-sm">
                  <label class="flex min-w-0 items-center gap-2 text-zinc-300">
                    <input type="checkbox" [(ngModel)]="row.assigned" [ngModelOptions]="{ standalone: true }" />
                    <span class="truncate">{{ row.event.name }}</span>
                  </label>
                  <input type="number" min="1" class="vf-input w-28" placeholder="Objetivo" [disabled]="!row.assigned"
                         [(ngModel)]="row.target" [ngModelOptions]="{ standalone: true }" />
                </li>
              }
            </ul>
            <button type="button" class="vf-btn-primary mt-4" [disabled]="saving()" (click)="saveAssignments()">Guardar asignaciones</button>
          }
        </div>
      </div>
    } @else {
      <vf-spinner />
    }
  `,
})
export class PromoterDetailPage {
  private readonly promotersService = inject(PromotersService);
  private readonly eventsService = inject(EventsService);
  private readonly toast = inject(ToastService);
  readonly id = input.required<string>();

  protected readonly detail = signal<PromoterDetail | null>(null);
  protected readonly rows = signal<AssignmentRow[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  private events: EventSummary[] = [];

  protected readonly form = inject(NonNullableFormBuilder).group({
    commissionType: ['PERCENTAGE' as CommissionType],
    commissionValue: [0, [Validators.required, Validators.min(0)]],
    active: [true],
  });

  constructor() {
    effect(() => this.load(this.id()));
    this.eventsService.list({ size: 100 }).subscribe({
      next: (page) => {
        this.events = page.content.filter((e) => e.status !== 'CANCELLED');
        this.buildRows();
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  protected save(): void {
    this.saving.set(true);
    this.promotersService.update(this.id(), this.form.getRawValue()).subscribe({
      next: (d) => this.done(d, 'Promotor actualizado'),
      error: (err) => this.fail(err),
    });
  }

  protected saveAssignments(): void {
    const assignments = this.rows()
      .filter((r) => r.assigned)
      .map((r) => ({ eventId: r.event.id, targetTickets: r.target && r.target > 0 ? r.target : null }));
    this.saving.set(true);
    this.promotersService.updateAssignments(this.id(), assignments).subscribe({
      next: (d) => this.done(d, 'Asignaciones guardadas'),
      error: (err) => this.fail(err),
    });
  }

  private load(id: string): void {
    this.promotersService.get(id).subscribe({
      next: (d) => this.setDetail(d),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private done(d: PromoterDetail, message: string): void {
    this.setDetail(d);
    this.toast.success(message);
    this.saving.set(false);
  }

  private fail(err: unknown): void {
    this.toast.error(errorMessage(err));
    this.saving.set(false);
  }

  private setDetail(d: PromoterDetail): void {
    this.detail.set(d);
    this.form.patchValue({ commissionType: d.promoter.commissionType, commissionValue: d.promoter.commissionValue, active: d.promoter.active });
    this.buildRows();
  }

  /** Une la lista de eventos de la empresa con las asignaciones actuales del promotor. */
  private buildRows(): void {
    const assigned = new Map((this.detail()?.events ?? []).filter((e) => e.assigned).map((e) => [e.eventId, e.targetTickets]));
    this.rows.set(this.events.map((event) => ({ event, assigned: assigned.has(event.id), target: assigned.get(event.id) ?? null })));
  }
}
