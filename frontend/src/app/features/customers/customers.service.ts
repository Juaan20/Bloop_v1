import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';
import { Customer, CustomerDetail, CustomerSegment, CustomerUpdate } from './customer.models';

export interface CustomerFilters {
  search?: string;
  segment?: CustomerSegment | '';
  marketingOnly?: boolean;
  page?: number;
}

/** CRM de clientes (backoffice). Endpoints: CustomerController.java. */
@Injectable({ providedIn: 'root' })
export class CustomersService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/customers`;

  list(filters: CustomerFilters): Observable<Page<Customer>> {
    let params = new HttpParams().set('page', filters.page ?? 0).set('size', 20);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.segment) params = params.set('segment', filters.segment);
    if (filters.marketingOnly) params = params.set('marketingOnly', true);
    return this.http.get<Page<Customer>>(this.url, { params });
  }

  get(id: string): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`${this.url}/${id}`);
  }

  update(id: string, payload: CustomerUpdate): Observable<CustomerDetail> {
    return this.http.put<CustomerDetail>(`${this.url}/${id}`, payload);
  }

  /** RGPD: todos los datos del cliente en esta empresa (se descarga como JSON). */
  export(id: string): Observable<unknown> {
    return this.http.get<unknown>(`${this.url}/${id}/export`);
  }

  /** RGPD: anonimiza la ficha (irreversible). */
  anonymize(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
