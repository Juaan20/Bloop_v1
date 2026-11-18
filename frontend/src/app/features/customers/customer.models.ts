import { OrderStatus } from '../orders/order.models';
import { ReservationStatus } from '../reservations/reservation.models';

export type CustomerSegment = 'NEW' | 'RECURRING' | 'VIP' | 'INACTIVE';
export type ConsentSource = 'CHECKOUT' | 'CUSTOMER' | 'BACKOFFICE' | 'MIGRATION';

/** Cliente del CRM (CustomerResponse en el backend). */
export interface Customer {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  tags: string | null;
  marketingConsent: boolean;
  ordersCount: number;
  ticketsCount: number;
  totalSpent: number;
  firstPurchaseAt: string | null;
  lastPurchaseAt: string | null;
  segments: CustomerSegment[];
  hasAccount: boolean;
  createdAt: string;
}

export interface CustomerDetail {
  customer: Customer;
  notes: string | null;
  orders: {
    id: string;
    reference: string;
    eventName: string;
    sessionStartsAt: string;
    status: OrderStatus;
    ticketCount: number;
    totalAmount: number;
    currency: string;
    createdAt: string;
  }[];
  reservations: {
    id: string;
    reference: string;
    zoneName: string;
    tableName: string;
    startsAt: string;
    guests: number;
    status: ReservationStatus;
  }[];
  consents: { type: string; granted: boolean; source: ConsentSource; createdAt: string }[];
}

export interface CustomerUpdate {
  phone: string | null;
  notes: string | null;
  tags: string | null;
  marketingConsent: boolean;
}
