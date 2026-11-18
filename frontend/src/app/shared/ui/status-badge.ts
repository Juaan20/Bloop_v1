import { Component, computed, input } from '@angular/core';
import { statusLabel } from '../pipes/status-label.pipe';

const TONES: Record<string, string> = {
  green: 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/20',
  amber: 'bg-amber-500/10 text-amber-300 ring-amber-500/20',
  red: 'bg-red-500/10 text-red-300 ring-red-500/20',
  zinc: 'bg-zinc-500/10 text-zinc-300 ring-zinc-500/20',
  violet: 'bg-brand-500/10 text-brand-300 ring-brand-500/20',
};

const STATUS_TONE: Record<string, string> = {
  ACTIVE: 'green', PUBLISHED: 'green', PAID: 'green', VALID: 'green', ON_SALE: 'green', ACCEPTED: 'green',
  SCHEDULED: 'violet', USED: 'violet', SUCCEEDED: 'green',
  DRAFT: 'zinc', INACTIVE: 'zinc', DISABLED: 'zinc', NOT_STARTED: 'zinc', ENDED: 'zinc', EXPIRED: 'zinc',
  PENDING_PAYMENT: 'amber', PAUSED: 'amber', PENDING: 'amber', SOLD_OUT: 'amber', ALREADY_USED: 'amber',
  CONFIRMED: 'green', SEATED: 'violet', COMPLETED: 'zinc', NO_SHOW: 'red', NEW: 'violet', RECURRING: 'green',
  OPEN: 'green', CLOSED: 'zinc', VOIDED: 'red', LOW_INVENTORY: 'amber',
  SENT: 'green', LOGGED: 'zinc', LOW_STOCK: 'amber', RESERVATION_REQUESTED: 'violet', RESERVATION_CANCELLED: 'red',
  CANCELLED: 'red', SUSPENDED: 'red', FAILED: 'red', REFUNDED: 'red', INVALID_CODE: 'red', WRONG_SESSION: 'red',
};

/** Etiqueta de color para estados (PUBLISHED, PAID...). */
@Component({
  selector: 'vf-status-badge',
  template: `<span class="vf-badge" [class]="classes()"><span class="size-1.5 rounded-full bg-current opacity-80"></span>{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<string>();
  protected readonly label = computed(() => statusLabel(this.status()));
  protected readonly classes = computed(() => TONES[STATUS_TONE[this.status()] ?? 'zinc']);
}
