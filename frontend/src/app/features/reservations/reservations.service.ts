import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';
import {
  CreateReservationPayload,
  Reservation,
  ReservationStatus,
  TableAvailability,
  VipTablePayload,
  VipZone,
  VipZonePayload,
} from './reservation.models';

export interface ReservationFilters {
  from?: string | null;
  to?: string | null;
  venueId?: string;
  status?: ReservationStatus | '';
  search?: string;
}

/** Reservas VIP y configuracion de zonas/mesas (backoffice). Endpoints: ReservationController, VipLayoutController. */
@Injectable({ providedIn: 'root' })
export class ReservationsService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  list(filters: ReservationFilters): Observable<Page<Reservation>> {
    let params = new HttpParams().set('size', 100);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.venueId) params = params.set('venueId', filters.venueId);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.search) params = params.set('search', filters.search);
    return this.http.get<Page<Reservation>>(`${this.api}/reservations`, { params });
  }

  availability(venueId: string, sessionId: string | null, startsAt: string | null, endsAt: string | null): Observable<TableAvailability[]> {
    let params = new HttpParams().set('venueId', venueId);
    if (sessionId) params = params.set('sessionId', sessionId);
    if (startsAt) params = params.set('startsAt', startsAt);
    if (endsAt) params = params.set('endsAt', endsAt);
    return this.http.get<TableAvailability[]>(`${this.api}/reservations/availability`, { params });
  }

  create(payload: CreateReservationPayload): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.api}/reservations`, payload);
  }

  changeStatus(id: string, status: ReservationStatus): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.api}/reservations/${id}/status`, { status });
  }

  registerDeposit(id: string, method?: 'CASH' | 'CARD'): Observable<Reservation> {
    const params = method ? new HttpParams().set('method', method) : undefined;
    return this.http.post<Reservation>(`${this.api}/reservations/${id}/deposit`, {}, { params });
  }

  // ---- Zonas y mesas de un local ----

  zones(venueId: string): Observable<VipZone[]> {
    return this.http.get<VipZone[]>(`${this.api}/venues/${venueId}/vip-zones`);
  }

  createZone(venueId: string, payload: VipZonePayload): Observable<VipZone> {
    return this.http.post<VipZone>(`${this.api}/venues/${venueId}/vip-zones`, payload);
  }

  updateZone(zoneId: string, payload: VipZonePayload): Observable<VipZone> {
    return this.http.put<VipZone>(`${this.api}/vip-zones/${zoneId}`, payload);
  }

  createTable(zoneId: string, payload: VipTablePayload): Observable<VipZone> {
    return this.http.post<VipZone>(`${this.api}/vip-zones/${zoneId}/tables`, payload);
  }

  updateTable(tableId: string, payload: VipTablePayload): Observable<VipZone> {
    return this.http.put<VipZone>(`${this.api}/vip-tables/${tableId}`, payload);
  }
}
