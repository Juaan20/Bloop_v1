import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';

export type ValidationResultCode =
  | 'ACCEPTED'
  | 'ALREADY_USED'
  | 'INVALID_CODE'
  | 'WRONG_SESSION'
  | 'CANCELLED'
  | 'NOT_PAID';

export interface AccessSession {
  sessionId: string;
  eventName: string;
  venueName: string;
  startsAt: string;
  endsAt: string;
  doorsOpenAt: string | null;
  capacity: number;
  ticketsSold: number;
  checkedIn: number;
}

export interface ValidationResult {
  accepted: boolean;
  result: ValidationResultCode;
  message: string;
  ticketTypeName: string | null;
  holderName: string | null;
  /** "DNI ····678Z": compararlo con el documento fisico. */
  holderDocument?: string | null;
  minAge?: number | null;
  usedAt: string | null;
}

export interface AccessValidation {
  id: string;
  result: ValidationResultCode;
  ticketId: string | null;
  validatedBy: string;
  createdAt: string;
}

/** Control de acceso en puerta. Endpoints: AccessControlController.java. */
@Injectable({ providedIn: 'root' })
export class AccessService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/access`;

  sessions(): Observable<AccessSession[]> {
    return this.http.get<AccessSession[]>(`${this.url}/sessions`);
  }

  validate(qrPayload: string, sessionId: string): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.url}/validate`, { qrPayload, sessionId });
  }

  history(sessionId: string): Observable<Page<AccessValidation>> {
    return this.http.get<Page<AccessValidation>>(`${this.url}/sessions/${sessionId}/validations?size=15`);
  }
}
