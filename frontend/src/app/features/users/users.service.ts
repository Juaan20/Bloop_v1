import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';

export type UserStatus = 'ACTIVE' | 'DISABLED';

export interface StaffUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  status: UserStatus;
  roles: string[];
  venueIds: string[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface Role {
  code: string;
  name: string;
  description: string;
  permissions: string[];
}

export interface CreateUserPayload {
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  password: string;
  roles: string[];
  venueIds: string[];
}

export interface UpdateUserPayload {
  firstName: string;
  lastName: string;
  phone: string | null;
  status: UserStatus;
  roles: string[];
  venueIds: string[];
}

/** Empleados, roles y permisos. Endpoints: UserController.java. */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users`;

  list(search: string, page: number): Observable<Page<StaffUser>> {
    const params = new HttpParams().set('search', search).set('page', page).set('size', 20);
    return this.http.get<Page<StaffUser>>(this.url, { params });
  }

  get(id: string): Observable<StaffUser> {
    return this.http.get<StaffUser>(`${this.url}/${id}`);
  }

  create(payload: CreateUserPayload): Observable<StaffUser> {
    return this.http.post<StaffUser>(this.url, payload);
  }

  update(id: string, payload: UpdateUserPayload): Observable<StaffUser> {
    return this.http.put<StaffUser>(`${this.url}/${id}`, payload);
  }

  roles(): Observable<Role[]> {
    return this.http.get<Role[]>(`${this.url}/roles`);
  }
}
