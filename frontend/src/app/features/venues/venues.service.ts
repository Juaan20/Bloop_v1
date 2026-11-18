import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Venue, VenuePayload } from './venue.models';

/** Llamadas HTTP de locales (backoffice). Endpoints: VenueController.java. */
@Injectable({ providedIn: 'root' })
export class VenuesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/venues`;

  list(): Observable<Venue[]> {
    return this.http.get<Venue[]>(this.url);
  }

  get(id: string): Observable<Venue> {
    return this.http.get<Venue>(`${this.url}/${id}`);
  }

  create(payload: VenuePayload): Observable<Venue> {
    return this.http.post<Venue>(this.url, payload);
  }

  update(id: string, payload: VenuePayload): Observable<Venue> {
    return this.http.put<Venue>(`${this.url}/${id}`, payload);
  }
}
