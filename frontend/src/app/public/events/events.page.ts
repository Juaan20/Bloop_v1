import { Component, OnInit, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { Spinner } from '../../shared/ui/spinner';
import { PublicCatalogService } from '../public-catalog.service';
import { PublicEventCard } from '../public.models';
import { EventCard } from './event-card';

/** Cartelera con buscador, filtro por ciudad y paginacion. Los filtros viven en la URL (?q=&ciudad=). */
@Component({
  selector: 'vf-events-page',
  imports: [FormsModule, EventCard, Spinner],
  templateUrl: './events.page.html',
})
export class EventsPage implements OnInit {
  private readonly catalog = inject(PublicCatalogService);
  private readonly router = inject(Router);

  readonly q = input<string>();
  readonly ciudad = input<string>();

  protected readonly result = signal<Page<PublicEventCard> | null>(null);
  protected readonly cities = signal<string[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected search = '';
  protected city = '';
  private page = 0;

  constructor() {
    // Cuando cambian los parametros de la URL, recargamos los resultados.
    effect(() => {
      this.search = this.q() ?? '';
      this.city = this.ciudad() ?? '';
      this.page = 0;
      this.load();
    });
  }

  ngOnInit(): void {
    this.catalog.cities().subscribe({ next: (c) => this.cities.set(c), error: () => this.cities.set([]) });
  }

  protected applyFilters(): void {
    this.router.navigate([], { queryParams: { q: this.search || null, ciudad: this.city || null } });
  }

  protected goToPage(page: number): void {
    this.page = page;
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.catalog.events({ search: this.search, city: this.city, page: this.page }).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
