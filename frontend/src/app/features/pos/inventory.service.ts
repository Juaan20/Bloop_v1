import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';
import { StockItem, StockMovement, StockMovementType, Warehouse } from './pos.models';

/** Inventario: almacenes, existencias y movimientos. Endpoints: InventoryController.java. */
@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/inventory/warehouses`;

  warehouses(): Observable<Warehouse[]> {
    return this.http.get<Warehouse[]>(this.url);
  }

  saveWarehouse(id: string | null, payload: { venueId: string; name: string; active: boolean }): Observable<Warehouse> {
    return id ? this.http.put<Warehouse>(`${this.url}/${id}`, payload) : this.http.post<Warehouse>(this.url, payload);
  }

  stock(warehouseId: string): Observable<StockItem[]> {
    return this.http.get<StockItem[]>(`${this.url}/${warehouseId}/stock`);
  }

  /** IN / OUT / ADJUSTMENT (en un ajuste, quantity es la cantidad contada). */
  registerMovement(warehouseId: string, variantId: string, type: StockMovementType, quantity: number, reason: string | null): Observable<StockItem[]> {
    return this.http.post<StockItem[]>(`${this.url}/${warehouseId}/movements`, { variantId, type, quantity, reason });
  }

  setMinimum(warehouseId: string, variantId: string, minQuantity: number): Observable<StockItem[]> {
    return this.http.put<StockItem[]>(`${this.url}/${warehouseId}/stock/${variantId}/minimum`, { minQuantity });
  }

  movements(warehouseId: string, page: number): Observable<Page<StockMovement>> {
    return this.http.get<Page<StockMovement>>(`${this.url}/${warehouseId}/movements`, { params: new HttpParams().set('page', page).set('size', 50) });
  }
}
