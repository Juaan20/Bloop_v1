import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicEventCard } from '../public.models';

/**
 * Paletas del "cartel generativo" que se pinta cuando el evento no tiene imagen.
 * No es una foto inventada: es arte abstracto que se elige de forma estable a partir del nombre.
 */
const PALETTES = [
  ['#7c6cf8', '#359af2', '#131624'],
  ['#9a8fff', '#f472b6', '#1a1030'],
  ['#5cb6ff', '#34d399', '#0b1a24'],
  ['#f59e0b', '#ef4444', '#1f0f14'],
  ['#a78bfa', '#22d3ee', '#0e1326'],
];

/** Hash sencillo y estable de un texto (mismo nombre => mismo cartel). */
export function textHash(text: string): number {
  return [...text].reduce((acc, c) => (acc * 31 + c.charCodeAt(0)) >>> 0, 7);
}

/** Fondo CSS del cartel generativo de un evento. */
export function eventArt(name: string): string {
  const h = textHash(name);
  const [a, b, bg] = PALETTES[h % PALETTES.length];
  const x1 = 15 + (h % 50);
  const y1 = 10 + ((h >> 3) % 40);
  const x2 = 50 + ((h >> 5) % 45);
  const y2 = 55 + ((h >> 7) % 40);
  return [
    `radial-gradient(60% 55% at ${x1}% ${y1}%, ${a} 0%, transparent 70%)`,
    `radial-gradient(55% 60% at ${x2}% ${y2}%, ${b} 0%, transparent 70%)`,
    `linear-gradient(160deg, ${bg}, #090b13)`,
  ].join(', ');
}

/** Tarjeta de evento de la cartelera publica. */
@Component({
  selector: 'vf-event-card',
  imports: [RouterLink, DatePipe, CurrencyPipe],
  template: `
    <a [routerLink]="['/eventos', event().eventSlug]" [queryParams]="{ sesion: event().sessionId }"
       class="group block overflow-hidden rounded-2xl border border-white/[0.06] bg-zinc-900/40 transition duration-300 hover:-translate-y-1 hover:border-white/15 hover:shadow-(--shadow-pop)">
      <div class="relative aspect-[4/3] overflow-hidden">
        @if (event().imageUrl) {
          <img [src]="event().imageUrl" [alt]="event().eventName" class="h-full w-full object-cover transition duration-700 group-hover:scale-105" loading="lazy" />
        } @else {
          <div class="flex h-full w-full items-end p-5 transition duration-700 group-hover:scale-105" [style.background]="art()" role="img" [attr.aria-label]="'Cartel de ' + event().eventName">
            <div class="pointer-events-none absolute inset-0 bg-[repeating-linear-gradient(90deg,rgb(255_255_255/0.04)_0_1px,transparent_1px_28px)]"></div>
            <span class="relative font-display text-[28px] leading-[0.95] font-semibold tracking-tight text-balance text-white">{{ event().eventName }}</span>
          </div>
        }
        <div class="pointer-events-none absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-black/40 to-transparent"></div>
        <div class="absolute top-3 left-3 rounded-lg border border-white/10 bg-black/60 px-2.5 py-1.5 text-center leading-tight backdrop-blur-md">
          <p class="text-[10px] font-semibold tracking-widest text-brand-300 uppercase">{{ event().startsAt | date: 'MMM' }}</p>
          <p class="font-display text-xl font-semibold text-white">{{ event().startsAt | date: 'd' }}</p>
        </div>
        @if (event().soldOut) {
          <span class="absolute top-3 right-3 rounded-full border border-red-400/30 bg-red-500/80 px-2.5 py-1 text-xs font-semibold text-white backdrop-blur">Agotado</span>
        }
      </div>
      <div class="space-y-1 p-4">
        <h3 class="truncate font-display text-lg font-semibold text-white">{{ event().eventName }}</h3>
        <p class="truncate text-sm text-zinc-400">{{ event().venueName }} · {{ event().city }}</p>
        <div class="flex items-center justify-between pt-3 text-sm">
          <span class="text-zinc-500 first-letter:uppercase">{{ event().startsAt | date: 'EEEE, HH:mm' }}{{ event().sessionTitle ? ' · ' + event().sessionTitle : '' }}</span>
          @if (event().minPrice !== null) {
            <span class="font-medium text-white">
              {{ event().minPrice === 0 ? 'Gratis' : 'Desde ' + (event().minPrice | currency: event().currency) }}
            </span>
          }
        </div>
      </div>
    </a>
  `,
})
export class EventCard {
  readonly event = input.required<PublicEventCard>();
  protected readonly art = computed(() => eventArt(this.event().eventName));
}
