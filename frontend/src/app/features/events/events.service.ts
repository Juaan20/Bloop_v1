import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';
import {
  CreateEventPayload,
  EventDetail,
  EventSession,
  EventStatus,
  EventSummary,
  SessionPayload,
  TicketType,
  TicketTypePayload,
  UpdateEventPayload,
} from './event.models';

export interface EventFilters {
  status?: EventStatus | '';
  venueId?: string;
  search?: string;
  page?: number;
  size?: number;
}

/** Eventos, sesiones y tipos de entrada. Endpoints: EventController, EventSessionController, TicketTypeController. */
@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/events`;

  list(filters: EventFilters): Observable<Page<EventSummary>> {
    let params = new HttpParams().set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.venueId) params = params.set('venueId', filters.venueId);
    if (filters.search) params = params.set('search', filters.search);
    return this.http.get<Page<EventSummary>>(this.url, { params });
  }

  get(id: string): Observable<EventDetail> {
    return this.http.get<EventDetail>(`${this.url}/${id}`);
  }

  create(payload: CreateEventPayload): Observable<EventDetail> {
    return this.http.post<EventDetail>(this.url, payload);
  }

  update(id: string, payload: UpdateEventPayload): Observable<EventDetail> {
    return this.http.put<EventDetail>(`${this.url}/${id}`, payload);
  }

  changeStatus(id: string, action: 'publish' | 'unpublish' | 'cancel'): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${this.url}/${id}/${action}`, {});
  }

  addSession(eventId: string, payload: SessionPayload): Observable<EventSession> {
    return this.http.post<EventSession>(`${this.url}/${eventId}/sessions`, payload);
  }

  updateSession(eventId: string, sessionId: string, payload: SessionPayload): Observable<EventSession> {
    return this.http.put<EventSession>(`${this.url}/${eventId}/sessions/${sessionId}`, payload);
  }

  cancelSession(eventId: string, sessionId: string): Observable<EventSession> {
    return this.http.post<EventSession>(`${this.url}/${eventId}/sessions/${sessionId}/cancel`, {});
  }

  deleteSession(eventId: string, sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${eventId}/sessions/${sessionId}`);
  }

  createTicketType(eventId: string, sessionId: string, payload: TicketTypePayload): Observable<TicketType> {
    return this.http.post<TicketType>(`${this.url}/${eventId}/sessions/${sessionId}/ticket-types`, payload);
  }

  updateTicketType(eventId: string, sessionId: string, id: string, payload: TicketTypePayload): Observable<TicketType> {
    return this.http.put<TicketType>(`${this.url}/${eventId}/sessions/${sessionId}/ticket-types/${id}`, payload);
  }

  deleteTicketType(eventId: string, sessionId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${eventId}/sessions/${sessionId}/ticket-types/${id}`);
  }
}
