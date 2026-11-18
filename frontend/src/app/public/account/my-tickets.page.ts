import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { QrCode } from '../../shared/components/qr-code';
import { Icon } from '../../shared/ui/icon';
import { Modal } from '../../shared/ui/modal';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Spinner } from '../../shared/ui/spinner';
import { MyTicket } from '../public.models';
import { AccountService } from './account.service';
import { eventArt } from '../events/event-card';
import { AccountNav } from './account-nav';

/**
 * Entradas del cliente con su QR. Se separan proximas y pasadas.
 * Una entrada valida se puede renombrar (mismo QR) o transferir a otra cuenta (QR nuevo).
 */
@Component({
  selector: 'vf-my-tickets-page',
  imports: [DatePipe, FormsModule, Icon, RouterLink, QrCode, Modal, StatusBadge, Spinner, AccountNav],
  templateUrl: './my-tickets.page.html',
})
export class MyTicketsPage implements OnInit {
  /** Franja de color de cada entrada (cartel generativo del evento). */
  protected readonly art = eventArt;
  private readonly account = inject(AccountService);
  private readonly toast = inject(ToastService);

  protected readonly tickets = signal<MyTicket[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Entrada abierta en la vista detallada (QR grande + datos de acceso). */
  protected readonly zoomed = signal<MyTicket | null>(null);

  /** Ventana abierta para la entrada seleccionada. */
  protected readonly mode = signal<'rename' | 'transfer' | null>(null);
  protected readonly busy = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected holderName = '';
  protected recipientEmail = '';
  private selected: MyTicket | null = null;

  private readonly cutoff = Date.now() - 12 * 3600 * 1000;
  protected readonly upcoming = computed(() => this.tickets().filter((t) => new Date(t.startsAt).getTime() >= this.cutoff));
  protected readonly past = computed(() => this.tickets().filter((t) => new Date(t.startsAt).getTime() < this.cutoff));

  ngOnInit(): void {
    this.load();
  }

  protected openRename(ticket: MyTicket): void {
    this.selected = ticket;
    this.holderName = ticket.holderName;
    this.formError.set(null);
    this.mode.set('rename');
  }

  protected openTransfer(ticket: MyTicket): void {
    this.selected = ticket;
    this.holderName = '';
    this.recipientEmail = '';
    this.formError.set(null);
    this.mode.set('transfer');
  }

  protected close(): void {
    this.mode.set(null);
    this.selected = null;
  }

  protected rename(): void {
    if (!this.selected) return;
    this.busy.set(true);
    this.account.changeHolder(this.selected.id, this.holderName.trim()).subscribe({
      next: (updated) => {
        this.tickets.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
        this.toast.success('Titular actualizado');
        this.busy.set(false);
        this.close();
      },
      error: (err) => this.fail(err),
    });
  }

  protected transfer(): void {
    if (!this.selected) return;
    this.busy.set(true);
    this.account.transferTicket(this.selected.id, this.recipientEmail.trim(), this.holderName.trim() || null).subscribe({
      next: () => {
        this.toast.success('Entrada transferida. Le hemos avisado por email');
        this.busy.set(false);
        this.close();
        this.load();
      },
      error: (err) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.formError.set(errorMessage(err));
    this.busy.set(false);
  }

  private load(): void {
    this.account.tickets().subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
