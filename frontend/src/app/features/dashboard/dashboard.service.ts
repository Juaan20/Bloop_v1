import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Dashboard {
  kpis: {
    revenue: number;
    paidOrders: number;
    ticketsSold: number;
    checkedIn: number;
    upcomingSessions: number;
    publishedEvents: number;
    averageOrderValue: number;
  };
  salesByDay: { day: string; revenue: number; tickets: number }[];
  sessions: {
    sessionId: string;
    eventId: string;
    eventName: string;
    venueName: string;
    startsAt: string;
    capacity: number;
    ticketsSold: number;
    checkedIn: number;
    revenue: number;
  }[];
  salesByVenue: { venueId: string; venueName: string; revenue: number; ticketsSold: number }[];
}

/** Indicadores del panel. Endpoint: DashboardController.java. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  summary(days: number, venueId: string): Observable<Dashboard> {
    let params = new HttpParams().set('days', days);
    if (venueId) params = params.set('venueId', venueId);
    return this.http.get<Dashboard>(`${environment.apiUrl}/dashboard`, { params });
  }
}
