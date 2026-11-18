import { EventStatus } from '../events/event.models';

export type CommissionType = 'PERCENTAGE' | 'FIXED_PER_TICKET';
export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';

/** Promotor con el resumen de sus ventas pagadas (PromoterResponse). */
export interface Promoter {
  id: string;
  userId: string;
  name: string;
  email: string;
  referralCode: string;
  commissionType: CommissionType;
  commissionValue: number;
  active: boolean;
  eventsAssigned: number;
  ticketsSold: number;
  orders: number;
  revenue: number;
  commission: number;
  createdAt: string;
}

export interface EventPerformance {
  eventId: string;
  eventName: string;
  eventSlug: string;
  eventStatus: EventStatus;
  assigned: boolean;
  salesLink: string;
  targetTickets: number | null;
  ticketsSold: number;
  orders: number;
  revenue: number;
  commission: number;
  targetProgress: number | null;
}

export interface PromoCode {
  id: string;
  code: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  eventId: string | null;
  eventName: string | null;
  promoterId: string | null;
  promoterName: string | null;
  maxUses: number | null;
  timesUsed: number;
  validFrom: string | null;
  validUntil: string | null;
  active: boolean;
  createdAt: string;
}

/** Panel de un promotor (lo ven el admin y el propio promotor). */
export interface PromoterDetail {
  promoter: Promoter;
  events: EventPerformance[];
  promoCodes: PromoCode[];
}

export interface PromoterCandidate {
  userId: string;
  name: string;
  email: string;
}

export interface PromoCodePayload {
  code: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  eventId: string | null;
  promoterId: string | null;
  maxUses: number | null;
  validFrom: string | null;
  validUntil: string | null;
  active: boolean;
}
