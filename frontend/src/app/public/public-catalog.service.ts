import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Page } from '../core/models/api.models';
import { TableAvailability } from '../features/reservations/reservation.models';
import { PublicVenue } from '../features/venues/venue.models';
import { PublicEventCard, PublicEventDetail } from './public.models';

/** Cartelera publica (no requiere login). Endpoints: PublicEventController y PublicVenueController. */
@Injectable({ providedIn: 'root' })
export class PublicCatalogService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/public`;

  events(filters: { search?: string; city?: string; venue?: string; page?: number; size?: number }): Observable<Page<PublicEventCard>> {
    let params = new HttpParams().set('page', filters.page ?? 0).set('size', filters.size ?? 12);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.city) params = params.set('city', filters.city);
    if (filters.venue) params = params.set('venue', filters.venue);
    return this.http.get<Page<PublicEventCard>>(`${this.url}/events`, { params });
  }

  event(slug: string): Observable<PublicEventDetail> {
    return this.http.get<PublicEventDetail>(`${this.url}/events/${slug}`);
  }

  venue(slug: string): Observable<PublicVenue> {
    return this.http.get<PublicVenue>(`${this.url}/venues/${slug}`);
  }

  cities(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/cities`);
  }

  /** Mesas VIP del local de una sesion y si estan libres (CustomerReservationController). */
  vipTables(sessionId: string): Observable<TableAvailability[]> {
    return this.http.get<TableAvailability[]>(`${this.url}/sessions/${sessionId}/vip-tables`);
  }
}
