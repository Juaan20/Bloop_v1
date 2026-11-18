export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
export type SessionStatus = 'SCHEDULED' | 'CANCELLED';
export type TicketCategory = 'GENERAL' | 'EARLY_BIRD' | 'VIP' | 'FREE';
export type SaleStatus = 'ON_SALE' | 'NOT_STARTED' | 'ENDED' | 'SOLD_OUT' | 'INACTIVE' | 'PAUSED';

export interface TicketType {
  id: string;
  name: string;
  description: string | null;
  category: TicketCategory;
  price: number;
  currency: string;
  quantityTotal: number;
  quantitySold: number;
  available: number;
  maxPerOrder: number;
  salesStartAt: string | null;
  salesEndAt: string | null;
  active: boolean;
  sortOrder: number;
  saleStatus: SaleStatus;
}

export interface EventSession {
  id: string;
  startsAt: string;
  endsAt: string;
  doorsOpenAt: string | null;
  capacity: number;
  status: SessionStatus;
  ticketsSold: number;
  ticketsConfigured: number;
  ticketTypes: TicketType[];
  title: string | null;
  description: string | null;
  imageUrl: string | null;
  salesPaused: boolean;
}

export interface EventSummary {
  id: string;
  name: string;
  slug: string;
  status: EventStatus;
  venueId: string;
  venueName: string;
  nextSessionAt: string | null;
  sessionsCount: number;
  ticketsSold: number;
  ticketsConfigured: number;
}

export interface EventDetail {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  minAge: number | null;
  status: EventStatus;
  publishedAt: string | null;
  venueId: string;
  venueName: string;
  venueCapacity: number;
  sessions: EventSession[];
  createdAt: string;
}

/** Un dia del evento. title, description e imageUrl (cartel del dia) son opcionales. */
export interface SessionPayload {
  startsAt: string;
  endsAt: string;
  doorsOpenAt: string | null;
  capacity: number;
  title?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  salesPaused?: boolean;
}

export interface CreateEventPayload {
  venueId: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  minAge: number | null;
  firstSession: SessionPayload;
}

export interface UpdateEventPayload {
  name: string;
  description: string | null;
  imageUrl: string | null;
  minAge: number | null;
}

export interface TicketTypePayload {
  name: string;
  description: string | null;
  category: TicketCategory;
  price: number;
  quantityTotal: number;
  maxPerOrder: number;
  salesStartAt: string | null;
  salesEndAt: string | null;
  active: boolean;
  sortOrder: number;
}
