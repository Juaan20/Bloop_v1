import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { PublicVenue } from '../../features/venues/venue.models';
import { Spinner } from '../../shared/ui/spinner';
import { EventCard } from '../events/event-card';
import { PublicCatalogService } from '../public-catalog.service';
import { Icon } from '../../shared/ui/icon';
import { PublicEventCard } from '../public.models';

/** Pagina publica de un local con sus proximos eventos. */
@Component({
  selector: 'vf-venue-detail-page',
  imports: [Icon, RouterLink, EventCard, Spinner],
  template: `
    @if (error()) {
      <div class="mx-auto max-w-3xl px-4 py-20 text-center text-zinc-300">
        {{ error() }} <a routerLink="/eventos" class="vf-link">Ver eventos</a>
      </div>
    } @else if (venue(); as v) {
      <section class="border-b border-white/5 bg-gradient-to-b from-brand-800/25 to-zinc-950">
        <div class="mx-auto max-w-7xl px-4 py-16 sm:px-6">
          <p class="text-sm font-semibold tracking-widest text-cyan-300 uppercase">{{ v.city }}</p>
          <h1 class="mt-2 font-display text-5xl font-bold text-white">{{ v.name }}</h1>
          @if (v.description) {
            <p class="mt-4 max-w-2xl text-zinc-300">{{ v.description }}</p>
          }
          <div class="mt-6 flex flex-wrap gap-6 text-sm text-zinc-400">
            <span class="inline-flex items-center gap-1.5"><vf-icon name="pin" class="size-4 text-zinc-500" /> {{ v.addressLine }}{{ v.postalCode ? ', ' + v.postalCode : '' }}</span>
            @if (v.openingHours) { <span class="inline-flex items-center gap-1.5"><vf-icon name="clock" class="size-4 text-zinc-500" /> {{ v.openingHours }}</span> }
            @if (v.phone) { <span class="inline-flex items-center gap-1.5"><vf-icon name="phone" class="size-4 text-zinc-500" /> {{ v.phone }}</span> }
          </div>
        </div>
      </section>
      <section class="mx-auto max-w-7xl px-4 py-12 sm:px-6">
        <h2 class="mb-6 font-display text-2xl font-semibold text-white">Proximos eventos</h2>
        @if (events().length === 0) {
          <p class="text-zinc-400">No hay eventos programados en este local.</p>
        } @else {
          <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            @for (e of events(); track e.sessionId) { <vf-event-card [event]="e" /> }
          </div>
        }
      </section>
    } @else {
      <vf-spinner />
    }
  `,
})
export class VenueDetailPage {
  private readonly catalog = inject(PublicCatalogService);
  readonly slug = input.required<string>();
  protected readonly venue = signal<PublicVenue | null>(null);
  protected readonly events = signal<PublicEventCard[]>([]);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.catalog.venue(slug).subscribe({
        next: (v) => this.venue.set(v),
        error: (err) => this.error.set(errorMessage(err, 'Local no encontrado')),
      });
      this.catalog.events({ venue: slug, size: 24 }).subscribe((page) => this.events.set(page.content));
    });
  }
}
