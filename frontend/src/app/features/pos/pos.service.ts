import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';
import {
  CashMovementType,
  CashSession,
  Category,
  CategoryKind,
  PosSale,
  PosScreen,
  Product,
  ProductPayload,
  SalePayload,
  Terminal,
  TerminalType,
  VipOccupiedTable,
} from './pos.models';

/**
 * TPV: catalogo, terminales, cajas y ventas.
 * Endpoints: PosCatalogController, PosTerminalController y CashSessionController.
 */
@Injectable({ providedIn: 'root' })
export class PosService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/pos`;

  // ---- Catalogo (POS_MANAGE) ----
  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.url}/categories`);
  }

  saveCategory(id: string | null, payload: Omit<Category, 'id'>): Observable<Category> {
    return id ? this.http.put<Category>(`${this.url}/categories/${id}`, payload) : this.http.post<Category>(`${this.url}/categories`, payload);
  }

  products(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.url}/products`);
  }

  saveProduct(id: string | null, payload: ProductPayload): Observable<Product> {
    return id ? this.http.put<Product>(`${this.url}/products/${id}`, payload) : this.http.post<Product>(`${this.url}/products`, payload);
  }

  // ---- Terminales ----
  terminals(): Observable<Terminal[]> {
    return this.http.get<Terminal[]>(`${this.url}/terminals`);
  }

  myTerminals(): Observable<Terminal[]> {
    return this.http.get<Terminal[]>(`${this.url}/my-terminals`);
  }

  saveTerminal(id: string | null, payload: { name: string; venueId: string; warehouseId: string; type: TerminalType; active: boolean; allowedKinds: CategoryKind[] }): Observable<Terminal> {
    return id ? this.http.put<Terminal>(`${this.url}/terminals/${id}`, payload) : this.http.post<Terminal>(`${this.url}/terminals`, payload);
  }

  vipTables(terminalId: string): Observable<VipOccupiedTable[]> {
    return this.http.get<VipOccupiedTable[]>(`${this.url}/terminals/${terminalId}/vip-tables`);
  }

  /** Carga el ticket a la cuenta de una mesa (servicio VIP). */
  vipCharge(terminalId: string, reservationId: string, lines: { variantId: string; quantity: number }[], idempotencyKey: string): Observable<{ tableName: string; extraTotal: number }> {
    return this.http.post<{ tableName: string; extraTotal: number }>(`${this.url}/terminals/${terminalId}/vip-charge`, {
      reservationId,
      order: { idempotencyKey, lines, notes: null },
    });
  }

  screen(terminalId: string): Observable<PosScreen> {
    return this.http.get<PosScreen>(`${this.url}/terminals/${terminalId}/screen`);
  }

  // ---- Caja ----
  openSession(terminalId: string, openingFloat: number): Observable<CashSession> {
    return this.http.post<CashSession>(`${this.url}/terminals/${terminalId}/cash-sessions`, { openingFloat });
  }

  session(id: string): Observable<CashSession> {
    return this.http.get<CashSession>(`${this.url}/cash-sessions/${id}`);
  }

  sessionHistory(terminalId: string | null, page: number): Observable<Page<CashSession>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (terminalId) params = params.set('terminalId', terminalId);
    return this.http.get<Page<CashSession>>(`${this.url}/cash-sessions`, { params });
  }

  sessionSales(id: string): Observable<PosSale[]> {
    return this.http.get<PosSale[]>(`${this.url}/cash-sessions/${id}/sales`);
  }

  addMovement(sessionId: string, type: CashMovementType, amount: number, reason: string): Observable<CashSession> {
    return this.http.post<CashSession>(`${this.url}/cash-sessions/${sessionId}/movements`, { type, amount, reason });
  }

  closeSession(sessionId: string, countedCash: number, notes: string | null): Observable<CashSession> {
    return this.http.post<CashSession>(`${this.url}/cash-sessions/${sessionId}/close`, { countedCash, notes });
  }

  // ---- Ventas ----
  /** La Idempotency-Key es la misma mientras no cambie el ticket: un doble toque no cobra dos veces. */
  sell(terminalId: string, payload: SalePayload, idempotencyKey: string): Observable<PosSale> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<PosSale>(`${this.url}/terminals/${terminalId}/sales`, payload, { headers });
  }

  voidSale(saleId: string, reason: string): Observable<PosSale> {
    return this.http.post<PosSale>(`${this.url}/sales/${saleId}/void`, { reason });
  }
}
