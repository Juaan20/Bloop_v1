import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Icon } from '../../shared/ui/icon';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { TableAvailability } from '../../features/reservations/reservation.models';
import { Modal } from '../../shared/ui/modal';
import { AccountService } from '../account/account.service';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { Spinner } from '../../shared/ui/spinner';
import { CheckoutService, newIdempotencyKey } from '../checkout/checkout.service';
import { PublicCatalogService } from '../public-catalog.service';
import { PublicEventDetail, PublicSession, PublicTicket } from '../public.models';
import { eventArt } from '../events/event-card';
import { CartLine, cartCount, cartTotal, clampQuantity, maxSelectable } from './ticket-cart';

/**
 * Ficha publica del evento y selector de entradas.
 *
 * Flujo de compra:
 * 1. El cliente elige sesion y cantidades (estado en signals).
 * 2. "Comprar" llama a POST /api/checkout con una Idempotency-Key.
 * 3. El backend reserva el stock y devuelve el pedido -> navegamos a /checkout/:id para pagar.
 */
@Component({
  selector: 'vf-event-detail-page',
  imports: [DatePipe, CurrencyPipe, FormsModule, RouterLink, Icon, Modal, Spinner, StatusLabelPipe],
  templateUrl: './event-detail.page.html',
})
export class EventDetailPage {
  /** Cartel generativo de respaldo cuando el evento no tiene imagen. */
  protected readonly art = eventArt;
  private readonly catalog = inject(PublicCatalogService);
  private readonly checkout = inject(CheckoutService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly account = inject(AccountService);
  private readonly toast = inject(ToastService);

  readonly slug = input.required<string>();
  /** Sesion preseleccionada desde la cartelera (?sesion=...). */
  readonly sesion = input<string>();
  /** Codigo del enlace de un promotor (?rrpp=...). Se recuerda en la sesion del navegador por si el cliente tiene que iniciar sesion. */
  readonly rrpp = input<string>();

  protected readonly event = signal<PublicEventDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedSessionId = signal<string | null>(null);
  protected readonly quantities = signal<Record<string, number>>({});
  protected readonly buying = signal(false);
  protected readonly buyError = signal<string | null>(null);
  /** Paso del proceso de compra: 1 elegir entradas, 2 datos de asistentes (despues, pago en /checkout). */
  protected readonly step = signal<'select' | 'details'>('select');
  protected readonly attendees = signal<AttendeeDraft[]>([]);
  protected buyerPhone = '';
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly buyerName = computed(() => this.auth.displayName());
  private idempotencyKey = newIdempotencyKey();

  // ---- Fase 2: codigo promocional, consentimiento y mesas VIP ----
  protected promoCode = '';
  protected marketingConsent = false;
  protected readonly vipTables = signal<TableAvailability[]>([]);
  protected readonly reserving = signal<TableAvailability | null>(null);
  protected readonly reservationError = signal<string | null>(null);
  protected readonly sendingReservation = signal(false);
  protected reservationGuests = 2;
  protected reservationPhone = '';
  protected reservationNotes = '';

  protected readonly selectedSession = computed<PublicSession | null>(() => {
    const ev = this.event();
    if (!ev) return null;
    return ev.sessions.find((s) => s.id === this.selectedSessionId()) ?? ev.sessions[0] ?? null;
  });

  protected readonly lines = computed<CartLine[]>(() => {
    const session = this.selectedSession();
    const qty = this.quantities();
    return (session?.ticketTypes ?? [])
      .map((ticket) => ({ ticket, quantity: qty[ticket.id] ?? 0 }))
      .filter((line) => line.quantity > 0);
  });
  protected readonly count = computed(() => cartCount(this.lines()));
  protected readonly total = computed(() => cartTotal(this.lines()));
  protected readonly maxSelectable = maxSelectable;

  constructor() {
    effect(() => this.load(this.slug()));
    effect(() => {
      const code = this.rrpp();
      if (code) sessionStorage.setItem(referralKey(this.slug()), code);
    });
    // Mesas VIP de la sesion elegida (solo si el local tiene zonas configuradas).
    effect(() => {
      const session = this.selectedSession();
      if (!session) return;
      this.catalog.vipTables(session.id).subscribe({
        next: (tables) => this.vipTables.set(tables),
        error: () => this.vipTables.set([]),
      });
    });
  }

  /** Un codigo distinto es un intento de compra distinto: nueva clave de idempotencia. */
  protected promoChanged(): void {
    this.idempotencyKey = newIdempotencyKey();
    this.buyError.set(null);
  }

  protected scrollToVip(): void {
    document.getElementById('mesas-vip')?.scrollIntoView({ behavior: 'smooth' });
  }

  protected openReservation(table: TableAvailability): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.reservationGuests = Math.min(2, table.capacity);
    this.reservationError.set(null);
    this.reserving.set(table);
  }

  protected requestReservation(): void {
    const table = this.reserving();
    const session = this.selectedSession();
    if (!table || !session) return;
    this.sendingReservation.set(true);
    this.account
      .requestReservation({
        sessionId: session.id,
        tableId: table.tableId,
        guests: this.reservationGuests,
        phone: this.reservationPhone.trim() || null,
        notes: this.reservationNotes.trim() || null,
      })
      .subscribe({
        next: () => {
          this.toast.success('Solicitud enviada. El local te confirmara la reserva por email');
          this.router.navigateByUrl('/cuenta/reservas');
        },
        error: (err) => {
          this.reservationError.set(errorMessage(err));
          this.sendingReservation.set(false);
          this.catalog.vipTables(session.id).subscribe((t) => this.vipTables.set(t));
        },
      });
  }

  protected qty(ticketId: string): number {
    return this.quantities()[ticketId] ?? 0;
  }

  protected selectSession(id: string): void {
    this.selectedSessionId.set(id);
    this.quantities.set({});
    this.idempotencyKey = newIdempotencyKey();
  }

  protected change(ticket: PublicTicket, delta: number): void {
    const current = this.qty(ticket.id);
    this.quantities.update((q) => ({ ...q, [ticket.id]: clampQuantity(ticket, current + delta) }));
    // Un carrito distinto es un intento de compra distinto: nueva clave de idempotencia.
    this.idempotencyKey = newIdempotencyKey();
    this.buyError.set(null);
  }

  /** Paso 1 -> 2: prepara un formulario por entrada (el primero con los datos del comprador). */
  protected next(): void {
    if (this.count() === 0) return;
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    const previous = this.attendees();
    const drafts: AttendeeDraft[] = [];
    for (const line of this.lines()) {
      for (let i = 0; i < line.quantity; i++) {
        const kept = previous.filter((p) => p.ticketTypeId === line.ticket.id)[i];
        drafts.push(kept ?? { ticketTypeId: line.ticket.id, ticketName: line.ticket.name, firstName: '', lastName: '', birthDate: '', documentType: 'DNI', documentNumber: '' });
      }
    }
    const user = this.auth.user();
    if (drafts[0] && !drafts[0].firstName && user) {
      drafts[0].firstName = user.firstName;
      drafts[0].lastName = user.lastName;
    }
    this.attendees.set(drafts);
    this.buyError.set(null);
    this.step.set('details');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /** Comprobacion rapida en el navegador; la validacion real (edad, documento) la hace el backend. */
  private attendeesError(minAge: number | null): string | null {
    for (const [i, a] of this.attendees().entries()) {
      const who = `Asistente ${i + 1}`;
      if (!a.firstName.trim() || !a.lastName.trim()) return `${who}: indica nombre y apellidos`;
      if (minAge) {
        if (!a.birthDate) return `${who}: indica la fecha de nacimiento`;
        if (!a.documentNumber.trim()) return `${who}: indica el numero de documento`;
        if (ageOn(a.birthDate, this.selectedSession()!.startsAt) < minAge) return `${who} no cumple la edad minima (${minAge} anos)`;
      }
    }
    return null;
  }

  protected buy(): void {
    const session = this.selectedSession();
    if (!session || this.count() === 0) return;
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    const minAge = this.event()?.minAge ?? null;
    const invalid = this.attendeesError(minAge);
    if (invalid) {
      this.buyError.set(invalid);
      return;
    }
    this.buying.set(true);
    this.buyError.set(null);
    const items = this.lines().map((l) => ({ ticketTypeId: l.ticket.id, quantity: l.quantity }));
    this.checkout
      .createOrder(session.id, items, this.idempotencyKey, {
        promoCode: this.promoCode.trim() || null,
        referralCode: this.rrpp() ?? sessionStorage.getItem(referralKey(this.slug())),
        marketingConsent: this.marketingConsent,
        buyerPhone: this.buyerPhone.trim() || null,
        attendees: this.attendees().map((a) => ({
          ticketTypeId: a.ticketTypeId,
          firstName: a.firstName.trim(),
          lastName: a.lastName.trim(),
          birthDate: minAge ? a.birthDate : null,
          documentType: minAge ? a.documentType : null,
          documentNumber: minAge ? a.documentNumber.trim() : null,
        })),
      })
      .subscribe({
        next: (order) => this.router.navigate(['/checkout', order.id]),
        error: (err) => {
          this.buyError.set(errorMessage(err));
          this.buying.set(false);
          this.idempotencyKey = newIdempotencyKey();
          this.load(this.slug()); // refrescamos disponibilidad
        },
      });
  }

  private load(slug: string): void {
    this.catalog.event(slug).subscribe({
      next: (event) => {
        this.event.set(event);
        const preselected = this.sesion();
        if (!this.selectedSessionId() && preselected && event.sessions.some((s) => s.id === preselected)) {
          this.selectedSessionId.set(preselected);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No se ha encontrado el evento'));
        this.loading.set(false);
      },
    });
  }
}

/** Formulario de un asistente (una entrada). */
interface AttendeeDraft {
  ticketTypeId: string;
  ticketName: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  documentType: 'DNI' | 'NIE' | 'PASSPORT';
  documentNumber: string;
}

/** Edad cumplida el dia de la sesion (aproximacion del navegador; el backend usa la hora de Madrid). */
function ageOn(birthDate: string, sessionStart: string): number {
  const b = new Date(birthDate);
  const s = new Date(sessionStart);
  let age = s.getFullYear() - b.getFullYear();
  if (s.getMonth() < b.getMonth() || (s.getMonth() === b.getMonth() && s.getDate() < b.getDate())) age--;
  return age;
}

/** Clave de sessionStorage donde se recuerda el codigo ?rrpp= de cada evento. */
function referralKey(slug: string): string {
  return `vf-rrpp:${slug}`;
}
