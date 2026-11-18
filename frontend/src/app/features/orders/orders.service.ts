import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';
import { Order, OrderDetail, OrderFilters, OrderSummary } from './order.models';

/** Ventas de la empresa (backoffice). Endpoints: OrderController.java. */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/orders`;

  list(filters: OrderFilters & { page?: number }): Observable<Page<Order>> {
    return this.http.get<Page<Order>>(this.url, { params: toParams(filters).set('page', filters.page ?? 0).set('size', 20) });
  }

  /** Totales de TODOS los pedidos que cumplen los filtros (no solo de la pagina visible). */
  summary(filters: OrderFilters): Observable<OrderSummary> {
    return this.http.get<OrderSummary>(`${this.url}/summary`, { params: toParams(filters) });
  }

  get(id: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.url}/${id}`);
  }

  /** Reenvia las entradas al comprador (ORDER_MANAGE). */
  /** Reembolso del pedido (permiso ORDER_REFUND): devuelve el dinero y anula las entradas. */
  refund(id: string, reason: string): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/refund`, { reason });
  }

  resend(id: string): Observable<void> {
    return this.http.post<void>(`${this.url}/${id}/resend`, {});
  }

  /** Cambia el nombre del titular de una entrada (ORDER_MANAGE). */
  changeHolder(orderId: string, ticketId: string, holderName: string): Observable<void> {
    return this.http.put<void>(`${this.url}/${orderId}/tickets/${ticketId}/holder`, { holderName });
  }
}

/** Solo se envian los filtros con valor; el backend los combina (Y logico). */
function toParams(f: OrderFilters): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(f)) {
    if (key !== 'page' && value !== null && value !== undefined && value !== '') params = params.set(key, String(value));
  }
  return params;
}
