import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Order } from '../../features/orders/order.models';

export interface CheckoutLine {
  ticketTypeId: string;
  quantity: number;
}

export interface CheckoutExtras {
  promoCode?: string | null;
  referralCode?: string | null;
  marketingConsent?: boolean;
  buyerPhone?: string | null;
  /** Un asistente por entrada (obligatorio si el evento tiene edad minima). */
  attendees?: {
    ticketTypeId: string;
    firstName: string;
    lastName: string;
    birthDate: string | null;
    documentType: 'DNI' | 'NIE' | 'PASSPORT' | null;
    documentNumber: string | null;
  }[];
}

export interface PaymentResult {
  approved: boolean;
  message: string;
  order: Order;
}

/**
 * Compra de entradas. Endpoints: CheckoutController.java.
 * Cada intento de compra envia una Idempotency-Key unica: si el usuario pulsa dos veces o la red
 * reintenta, el backend devuelve el mismo pedido en lugar de crear otro.
 */
@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/checkout`;

  /**
   * @param extras codigo promocional, codigo de enlace del promotor (?rrpp=) y consentimiento comercial
   */
  createOrder(sessionId: string, items: CheckoutLine[], idempotencyKey: string, extras: CheckoutExtras = {}): Observable<Order> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<Order>(this.url, { sessionId, items, ...extras }, { headers });
  }

  paySimulated(orderId: string, approve: boolean): Observable<PaymentResult> {
    return this.http.post<PaymentResult>(`${this.url}/orders/${orderId}/simulated-payment`, { approve });
  }

  /** Medios de pago activos en este entorno (simulado en desarrollo, Stripe si esta configurado). */
  paymentMethods(): Observable<{ simulated: boolean; stripe: boolean }> {
    return this.http.get<{ simulated: boolean; stripe: boolean }>(`${environment.apiUrl}/public/payment-methods`);
  }

  /** Crea la pagina de pago de Stripe y devuelve su URL (el navegador se redirige alli). */
  stripeSession(orderId: string): Observable<{ checkoutUrl: string }> {
    return this.http.post<{ checkoutUrl: string }>(`${this.url}/orders/${orderId}/stripe-session`, {});
  }

  cancel(orderId: string): Observable<Order> {
    return this.http.post<Order>(`${this.url}/orders/${orderId}/cancel`, {});
  }

  myOrder(orderId: string): Observable<Order> {
    return this.http.get<Order>(`${environment.apiUrl}/me/orders/${orderId}`);
  }
}

/** Genera una clave aleatoria para la cabecera Idempotency-Key. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
