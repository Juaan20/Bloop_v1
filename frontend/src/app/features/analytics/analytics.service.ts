import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Analytics {
  from: string;
  to: string;
  summary: {
    ticketRevenue: number;
    paidOrders: number;
    ticketsSold: number;
    refundedAmount: number;
    refundedOrders: number;
    posRevenue: number;
    posSales: number;
    voidedPosSales: number;
    posAverageTicket: number;
    totalRevenue: number;
    promoterCommissions: number;
  };
  revenueByDay: { day: string; ticketRevenue: number; posRevenue: number }[];
  topEvents: { eventId: string; eventName: string; venueName: string; ticketsSold: number; checkedIn: number; revenue: number }[];
  topProducts: { productName: string; variantName: string; quantity: number; revenue: number }[];
  posByTerminal: { terminalId: string; terminalName: string; type: string; venueName: string; sales: number; revenue: number }[];
  posByPaymentMethod: { paymentMethod: string; sales: number; revenue: number }[];
  posByHour: { hour: number; sales: number; revenue: number }[];
  promoters: { promoterId: string; promoterName: string; referralCode: string; orders: number; tickets: number; revenue: number; commission: number }[];
  vip: { reservations: number; seatedOrCompleted: number; noShows: number; cancelled: number; bottlesSold: number; prepaidRevenue: number; extrasRevenue: number };
  occupancy: { sessions: number; capacity: number; ticketsSold: number; checkedIn: number };
}

export type ForecastMethod = 'PACE' | 'HISTORICAL' | 'ACTUAL' | 'NONE';
export type ForecastConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';

/** Prevision de asistencia (calculada en el backend: ForecastService). */
export interface Forecast {
  session: { sessionId: string; eventName: string; venueName: string; startsAt: string; weekday: string; capacity: number; ticketsSold: number; checkedIn: number; future: boolean; daysToGo: number };
  forecast: { method: ForecastMethod; confidence: ForecastConfidence; dataPoints: number; soldLow: number; soldMid: number; soldHigh: number; attendanceLow: number; attendanceMid: number; attendanceHigh: number; attendanceRate: number; explanation: string };
  comparables: { sessionId: string; eventName: string; startsAt: string; sameEvent: boolean; capacity: number; ticketsSold: number; checkedIn: number; attendanceRate: number; soldAtSameLead: number; vipReservations: number; revenue: number }[];
  salesCurve: { daysBefore: number; current: number | null; comparableAverage: number | null }[];
}

export interface UpcomingSession {
  sessionId: string;
  eventName: string;
  venueName: string;
  startsAt: string;
  capacity: number;
  ticketsSold: number;
}

export type ReportType = 'orders' | 'pos-sales' | 'products' | 'promoters';

export interface AnalyticsFilters {
  from: string;
  to: string;
  venueId: string;
}

/** Analitica avanzada e informes CSV. Endpoints: AnalyticsController.java (permiso REPORT_VIEW). */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/analytics`;

  overview(filters: AnalyticsFilters): Observable<Analytics> {
    return this.http.get<Analytics>(this.url, { params: this.params(filters) });
  }

  upcomingSessions(): Observable<UpcomingSession[]> {
    return this.http.get<UpcomingSession[]>(`${this.url}/forecast`);
  }

  forecast(sessionId: string): Observable<Forecast> {
    return this.http.get<Forecast>(`${this.url}/forecast/${sessionId}`);
  }

  /** Descarga un informe CSV. Se pide como Blob para poder guardarlo con su nombre de fichero. */
  report(type: ReportType, filters: AnalyticsFilters): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.url}/reports/${type}.csv`, {
      params: this.params(filters),
      responseType: 'blob',
      observe: 'response',
    });
  }

  private params(filters: AnalyticsFilters): HttpParams {
    let params = new HttpParams().set('from', filters.from).set('to', filters.to);
    if (filters.venueId) params = params.set('venueId', filters.venueId);
    return params;
  }
}
