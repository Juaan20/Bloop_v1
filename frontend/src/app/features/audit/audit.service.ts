import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';

export interface AuditEntry {
  id: string;
  userEmail: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

/** Registro de auditoria de la empresa. Endpoint: AuditController.java. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  list(action: string, page: number): Observable<Page<AuditEntry>> {
    let params = new HttpParams().set('page', page).set('size', 25);
    if (action) params = params.set('action', action);
    return this.http.get<Page<AuditEntry>>(`${environment.apiUrl}/audit`, { params });
  }
}
