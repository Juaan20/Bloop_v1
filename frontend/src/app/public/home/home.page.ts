import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { Spinner } from '../../shared/ui/spinner';
import { EventCard, eventArt } from '../events/event-card';
import { PublicCatalogService } from '../public-catalog.service';
import { Icon } from '../../shared/ui/icon';
import { PublicEventCard } from '../public.models';

/** Portada de la web publica: buscador y proximos eventos reales de la base de datos. */
@Component({
  selector: 'vf-home-page',
  imports: [Icon, CurrencyPipe, DatePipe, FormsModule, RouterLink, EventCard, Spinner],
  templateUrl: './home.page.html',
})
export class HomePage implements OnInit {
  private readonly catalog = inject(PublicCatalogService);
  private readonly router = inject(Router);

  protected readonly events = signal<PublicEventCard[]>([]);
  protected readonly cities = signal<string[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected search = '';
  protected readonly art = eventArt;
  /** El evento mas proximo se muestra en grande; el resto, en rejilla. */
  protected readonly featured = computed(() => this.events()[0] ?? null);
  protected readonly rest = computed(() => this.events().slice(1));
  /** Locales con fechas en cartelera (deducidos de los eventos publicados, sin datos inventados). */
  protected readonly venues = computed(() => {
    const map = new Map<string, { slug: string; name: string; city: string; count: number }>();
    for (const e of this.events()) {
      const v = map.get(e.venueSlug) ?? { slug: e.venueSlug, name: e.venueName, city: e.city, count: 0 };
      v.count++;
      map.set(e.venueSlug, v);
    }
    return [...map.values()];
  });

  ngOnInit(): void {
    this.catalog.events({ size: 9 }).subscribe({
      next: (page) => {
        this.events.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
    this.catalog.cities().subscribe({ next: (cities) => this.cities.set(cities), error: () => this.cities.set([]) });
  }

  protected doSearch(city?: string): void {
    this.router.navigate(['/eventos'], { queryParams: { q: this.search || null, ciudad: city ?? null } });
  }
}
