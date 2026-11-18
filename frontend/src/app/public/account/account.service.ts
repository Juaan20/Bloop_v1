import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Order } from '../../features/orders/order.models';
import { Reservation } from '../../features/reservations/reservation.models';
import { MyConsent, MyTicket, Profile } from '../public.models';

/**
 * Zona "Mi cuenta". Endpoints: MyTicketController, MyOrderController, ProfileController,
 * CustomerReservationController y MyPrivacyController.
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/me`;

  tickets(): Observable<MyTicket[]> {
    return this.http.get<MyTicket[]>(`${this.url}/tickets`);
  }

  /** Cambia el nombre que figura en la entrada (mismo QR). */
  changeHolder(ticketId: string, holderName: string): Observable<MyTicket> {
    return this.http.put<MyTicket>(`${this.url}/tickets/${ticketId}/holder`, { holderName });
  }

  /** Transfiere la entrada a otra cuenta (se genera un QR nuevo). */
  transferTicket(ticketId: string, recipientEmail: string, holderName: string | null): Observable<void> {
    return this.http.post<void>(`${this.url}/tickets/${ticketId}/transfer`, { recipientEmail, holderName });
  }

  orders(): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.url}/orders`);
  }

  resendOrder(orderId: string): Observable<void> {
    return this.http.post<void>(`${this.url}/orders/${orderId}/resend`, {});
  }

  reservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.url}/reservations`);
  }

  requestReservation(payload: { sessionId: string; tableId: string; guests: number; phone: string | null; notes: string | null }): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.url}/reservations`, payload);
  }

  cancelReservation(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.url}/reservations/${id}/cancel`, {});
  }

  profile(): Observable<Profile> {
    return this.http.get<Profile>(`${this.url}/profile`);
  }

  updateProfile(data: Pick<Profile, 'firstName' | 'lastName' | 'phone' | 'marketingConsent'>): Observable<Profile> {
    return this.http.put<Profile>(`${this.url}/profile`, data);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.url}/password`, { currentPassword, newPassword });
  }

  // ---- Privacidad (RGPD) ----

  consents(): Observable<MyConsent[]> {
    return this.http.get<MyConsent[]>(`${this.url}/privacy/consents`);
  }

  updateConsent(customerId: string, marketingConsent: boolean): Observable<MyConsent> {
    return this.http.put<MyConsent>(`${this.url}/privacy/consents/${customerId}`, { marketingConsent });
  }

  deleteAccount(password: string): Observable<void> {
    return this.http.post<void>(`${this.url}/delete-account`, { password });
  }
}
