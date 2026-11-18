import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';

export type TenantStatus = 'ACTIVE' | 'SUSPENDED';

export interface Tenant {
  id: string;
  name: string;
  slug: string;
  legalName: string | null;
  taxId: string | null;
  contactEmail: string;
  contactPhone: string | null;
  status: TenantStatus;
  createdAt: string;
}

export interface TenantData {
  name: string;
  legalName: string | null;
  taxId: string | null;
  contactEmail: string;
  contactPhone: string | null;
}

export interface CreateTenantPayload extends TenantData {
  adminFirstName: string;
  adminLastName: string;
  adminEmail: string;
  adminPassword: string;
}

/** Empresas: gestion del super admin (/admin/tenants) y de la propia empresa (/tenant). */
@Injectable({ providedIn: 'root' })
export class TenantsService {
  private readonly http = inject(HttpClient);
  private readonly adminUrl = `${environment.apiUrl}/admin/tenants`;

  list(search: string, page: number): Observable<Page<Tenant>> {
    const params = new HttpParams().set('search', search).set('page', page).set('size', 20);
    return this.http.get<Page<Tenant>>(this.adminUrl, { params });
  }

  get(id: string): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.adminUrl}/${id}`);
  }

  create(payload: CreateTenantPayload): Observable<Tenant> {
    return this.http.post<Tenant>(this.adminUrl, payload);
  }

  update(id: string, payload: TenantData): Observable<Tenant> {
    return this.http.put<Tenant>(`${this.adminUrl}/${id}`, payload);
  }

  changeStatus(id: string, status: TenantStatus): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.adminUrl}/${id}/status`, { status });
  }

  myTenant(): Observable<Tenant> {
    return this.http.get<Tenant>(`${environment.apiUrl}/tenant`);
  }

  updateMyTenant(payload: TenantData): Observable<Tenant> {
    return this.http.put<Tenant>(`${environment.apiUrl}/tenant`, payload);
  }
}
