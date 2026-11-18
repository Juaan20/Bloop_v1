import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Reservation, VipZone } from '../reservations/reservation.models';
import { FloorLayout } from './floor-layout';
import { GuestTable, OrderLine, TablePosition, VipMap, VipParty, VipOrder, VipOrderStatus, VipReservationDetail } from './vip.models';

/**
 * Servicio VIP: plano, ficha de la mesa, QR, cuenta y pedidos.
 * Endpoints: VipServiceController, VipLayoutController y PublicVipTableController (pagina del QR).
 */
@Injectable({ providedIn: 'root' })
export class VipService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  // ---- Plano ----
  /** Plano de una fiesta: mesas del plano con su reserva activa e historial de esa fiesta. */
  map(venueId: string, sessionId: string | null): Observable<VipMap> {
    const params = sessionId ? new HttpParams().set('sessionId', sessionId) : undefined;
    return this.http.get<VipMap>(`${this.api}/venues/${venueId}/vip-map`, { params });
  }

  /** Fiestas del local (en curso y proximas; past=true incluye las de los ultimos 30 dias). */
  parties(venueId: string, past = false): Observable<VipParty[]> {
    return this.http.get<VipParty[]>(`${this.api}/venues/${venueId}/vip-parties`, { params: { past } });
  }

  saveLayout(venueId: string, tables: TablePosition[]): Observable<VipZone[]> {
    return this.http.put<VipZone[]>(`${this.api}/venues/${venueId}/vip-layout`, { tables });
  }

  /** Forma del local; null si aun no se ha disenado. */
  /** Forma de una sala (null si aun no se ha disenado). */
  floorPlan(zoneId: string): Observable<FloorLayout | null> {
    return this.http.get<FloorLayout | null>(`${this.api}/vip-zones/${zoneId}/floor-plan`);
  }

  saveFloorPlan(zoneId: string, layout: FloorLayout): Observable<FloorLayout> {
    return this.http.put<FloorLayout>(`${this.api}/vip-zones/${zoneId}/floor-plan`, layout);
  }

  /** Borra una sala sin reservas (con historial: desactivarla). */
  deleteZone(zoneId: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/vip-zones/${zoneId}`);
  }

  /** Mesa vendida en taquilla: reserva desde ahora, cobro y acceso en un paso. */
  walkIn(payload: { tableId: string; guests: number; contactName: string; contactPhone: string | null; contactEmail: string | null; sessionId: string; paymentMethod: 'CASH' | 'CARD' | null; notes: string | null }): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.api}/reservations/walk-in`, payload);
  }

  setPackage(tableId: string, items: OrderLine[]): Observable<VipZone> {
    return this.http.put<VipZone>(`${this.api}/vip-tables/${tableId}/package`, { items });
  }

  deleteTable(tableId: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/vip-tables/${tableId}`);
  }

  // ---- Ficha VIP de una reserva ----
  detail(reservationId: string): Observable<VipReservationDetail> {
    return this.http.get<VipReservationDetail>(`${this.api}/reservations/${reservationId}/vip`);
  }

  generateQr(reservationId: string): Observable<VipReservationDetail> {
    return this.http.post<VipReservationDetail>(`${this.api}/reservations/${reservationId}/table-qr`, null);
  }

  deactivateQr(reservationId: string): Observable<VipReservationDetail> {
    return this.http.delete<VipReservationDetail>(`${this.api}/reservations/${reservationId}/table-qr`);
  }

  pay(reservationId: string, amount: number, method: 'CASH' | 'CARD', idempotencyKey: string): Observable<VipReservationDetail> {
    return this.http.post<VipReservationDetail>(`${this.api}/reservations/${reservationId}/account/payments`, {
      amount,
      method,
      idempotencyKey,
    });
  }

  close(reservationId: string): Observable<VipReservationDetail> {
    return this.http.post<VipReservationDetail>(`${this.api}/reservations/${reservationId}/account/close`, null);
  }

  staffOrder(reservationId: string, lines: OrderLine[], idempotencyKey: string, notes: string | null): Observable<VipOrder> {
    return this.http.post<VipOrder>(`${this.api}/reservations/${reservationId}/vip-orders`, { idempotencyKey, lines, notes });
  }

  // ---- Panel de servicio ----
  board(venueId: string): Observable<VipOrder[]> {
    return this.http.get<VipOrder[]>(`${this.api}/venues/${venueId}/vip-orders`);
  }

  changeStatus(orderId: string, status: VipOrderStatus): Observable<VipOrder> {
    return this.http.post<VipOrder>(`${this.api}/vip-orders/${orderId}/status`, { status });
  }

  // ---- Pagina publica de la mesa (sin login) ----
  guestView(token: string): Observable<GuestTable> {
    return this.http.get<GuestTable>(`${this.api}/public/vip-tables/${token}`);
  }

  guestOrder(token: string, lines: OrderLine[], idempotencyKey: string, notes: string | null): Observable<VipOrder> {
    return this.http.post<VipOrder>(`${this.api}/public/vip-tables/${token}/orders`, { idempotencyKey, lines, notes });
  }
}
