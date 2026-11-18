import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CommissionType,
  PromoCode,
  PromoCodePayload,
  Promoter,
  PromoterCandidate,
  PromoterDetail,
} from './promoter.models';

/**
 * Promotores, codigos promocionales y panel del promotor.
 * Endpoints: PromoterController, PromoCodeController y PromoterPanelController.
 */
@Injectable({ providedIn: 'root' })
export class PromotersService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  list(): Observable<Promoter[]> {
    return this.http.get<Promoter[]>(`${this.api}/promoters`);
  }

  candidates(): Observable<PromoterCandidate[]> {
    return this.http.get<PromoterCandidate[]>(`${this.api}/promoters/candidates`);
  }

  get(id: string): Observable<PromoterDetail> {
    return this.http.get<PromoterDetail>(`${this.api}/promoters/${id}`);
  }

  create(payload: { userId: string; referralCode: string | null; commissionType: CommissionType; commissionValue: number }): Observable<PromoterDetail> {
    return this.http.post<PromoterDetail>(`${this.api}/promoters`, payload);
  }

  update(id: string, payload: { commissionType: CommissionType; commissionValue: number; active: boolean }): Observable<PromoterDetail> {
    return this.http.put<PromoterDetail>(`${this.api}/promoters/${id}`, payload);
  }

  updateAssignments(id: string, assignments: { eventId: string; targetTickets: number | null }[]): Observable<PromoterDetail> {
    return this.http.put<PromoterDetail>(`${this.api}/promoters/${id}/events`, { assignments });
  }

  /** Panel del promotor autenticado. */
  myPanel(): Observable<PromoterDetail> {
    return this.http.get<PromoterDetail>(`${this.api}/promoter/me`);
  }

  promoCodes(): Observable<PromoCode[]> {
    return this.http.get<PromoCode[]>(`${this.api}/promo-codes`);
  }

  createPromoCode(payload: PromoCodePayload): Observable<PromoCode> {
    return this.http.post<PromoCode>(`${this.api}/promo-codes`, payload);
  }

  updatePromoCode(id: string, payload: PromoCodePayload): Observable<PromoCode> {
    return this.http.put<PromoCode>(`${this.api}/promo-codes/${id}`, payload);
  }
}
