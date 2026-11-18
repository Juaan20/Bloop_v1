import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ImagePicker } from '../media/image-picker';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { FieldError } from '../../shared/ui/field-error';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { EventDetail, EventSession, SessionPayload, TicketType, TicketTypePayload } from './event.models';
import { EventsService } from './events.service';
import { SessionForm } from './session-form';
import { Icon } from '../../shared/ui/icon';
import { TicketTypeForm } from './ticket-type-form';

/** Que modal esta abierto y sobre que elemento. */
type ModalState =
  | { kind: 'session'; session: EventSession | null }
  | { kind: 'ticket'; session: EventSession; ticketType: TicketType | null }
  | null;

/**
 * Detalle de un evento en el backoffice: datos, publicacion, sesiones y tipos de entrada.
 * Tras cada cambio se recarga el evento desde el backend para mostrar siempre datos reales.
 */
@Component({
  selector: 'vf-event-detail-admin-page',
  imports: [ImagePicker, Icon, 
    CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, StatusLabelPipe, FieldError, Modal, PageHeader,
    Spinner, StatusBadge, SessionForm, TicketTypeForm,
  ],
  templateUrl: './event-detail.page.html',
})
export class EventDetailAdminPage {
  private readonly eventsService = inject(EventsService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly canManage = inject(AuthService).hasPermission(Permission.EVENT_MANAGE);
  protected readonly event = signal<EventDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly modal = signal<ModalState>(null);
  protected readonly modalError = signal<string | null>(null);

  protected readonly infoForm = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    imageUrl: ['' as string | null],
    minAge: [null as number | null],
  });

  constructor() {
    effect(() => this.reload(this.id()));
  }

  protected saveInfo(): void {
    if (this.infoForm.invalid) {
      this.infoForm.markAllAsTouched();
      return;
    }
    const v = this.infoForm.getRawValue();
    this.run(this.eventsService.update(this.id(), { ...v, description: v.description || null, imageUrl: v.imageUrl || null }),
      'Datos guardados');
  }

  protected changeStatus(action: 'publish' | 'unpublish' | 'cancel'): void {
    if (action === 'cancel' && !confirm('¿Cancelar el evento? Se reembolsaran automaticamente todas las entradas vendidas y se avisara a los compradores. Esta accion no se puede deshacer.')) return;
    const messages = { publish: 'Evento publicado: ya esta a la venta', unpublish: 'Evento oculto de la web', cancel: 'Evento cancelado. Los pedidos pagados se han reembolsado' };
    this.run(this.eventsService.changeStatus(this.id(), action), messages[action]);
  }

  protected saveSession(payload: SessionPayload): void {
    const state = this.modal();
    if (state?.kind !== 'session') return;
    const request = state.session
      ? this.eventsService.updateSession(this.id(), state.session.id, payload)
      : this.eventsService.addSession(this.id(), payload);
    this.runInModal(request, state.session ? 'Sesion actualizada' : 'Sesion anadida');
  }

  protected deleteSession(session: EventSession): void {
    if (!confirm('¿Eliminar esta sesion?')) return;
    this.run(this.eventsService.deleteSession(this.id(), session.id), 'Sesion eliminada');
  }

  protected cancelSession(session: EventSession): void {
    if (!confirm('¿Cancelar esta sesion? Dejara de venderse y se reembolsaran automaticamente sus entradas vendidas.')) return;
    this.run(this.eventsService.cancelSession(this.id(), session.id), 'Sesion cancelada');
  }

  protected saveTicketType(payload: TicketTypePayload): void {
    const state = this.modal();
    if (state?.kind !== 'ticket') return;
    const request = state.ticketType
      ? this.eventsService.updateTicketType(this.id(), state.session.id, state.ticketType.id, payload)
      : this.eventsService.createTicketType(this.id(), state.session.id, payload);
    this.runInModal(request, 'Entrada guardada');
  }

  protected deleteTicketType(session: EventSession, ticketType: TicketType): void {
    if (!confirm(`¿Eliminar el tipo "${ticketType.name}"?`)) return;
    this.run(this.eventsService.deleteTicketType(this.id(), session.id, ticketType.id), 'Tipo de entrada eliminado');
  }

  protected openModal(state: ModalState): void {
    this.modalError.set(null);
    this.modal.set(state);
  }

  /** Ejecuta una accion, muestra el resultado y recarga el evento. */
  private run(request: Observable<unknown>, successMessage: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.toast.success(successMessage);
        this.reload(this.id());
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  private runInModal(request: Observable<unknown>, successMessage: string): void {
    this.busy.set(true);
    this.modalError.set(null);
    request.subscribe({
      next: () => {
        this.modal.set(null);
        this.toast.success(successMessage);
        this.reload(this.id());
      },
      error: (err) => {
        this.modalError.set(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  private reload(id: string): void {
    this.eventsService.get(id).subscribe({
      next: (event) => {
        this.event.set(event);
        this.infoForm.reset({
          name: event.name,
          description: event.description ?? '',
          imageUrl: event.imageUrl ?? '',
          minAge: event.minAge,
        });
        if (!this.canManage || event.status === 'CANCELLED') this.infoForm.disable();
        this.busy.set(false);
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
