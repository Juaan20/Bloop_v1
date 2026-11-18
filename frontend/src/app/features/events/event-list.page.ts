import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { EventStatus, EventSummary } from './event.models';
import { Pagination } from '../../shared/ui/pagination';
import { Icon } from '../../shared/ui/icon';
import { eventArt } from '../../public/events/event-card';
import { EventsService } from './events.service';

@Component({
  selector: 'vf-event-list-page',
  imports: [Pagination, Icon, DecimalPipe, DatePipe, FormsModule, RouterLink, PageHeader, Spinner, EmptyState, StatusBadge],
  templateUrl: './event-list.page.html',
})
export class EventListPage implements OnInit {
  /** Miniatura generativa (el listado no incluye la imagen del evento). */
  protected readonly art = eventArt;
  private readonly eventsService = inject(EventsService);
  private readonly venuesService = inject(VenuesService);
  protected readonly canManage = inject(AuthService).hasPermission(Permission.EVENT_MANAGE);

  protected readonly result = signal<Page<EventSummary> | null>(null);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected filters: { status: EventStatus | ''; venueId: string; search: string; page: number } = {
    status: '',
    venueId: '',
    search: '',
    page: 0,
  };

  ngOnInit(): void {
    this.venuesService.list().subscribe({ next: (v) => this.venues.set(v), error: () => this.venues.set([]) });
    this.load();
  }

  protected search(): void {
    this.filters.page = 0;
    this.load();
  }

  protected goToPage(page: number): void {
    this.filters.page = page;
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.eventsService.list(this.filters).subscribe({
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
