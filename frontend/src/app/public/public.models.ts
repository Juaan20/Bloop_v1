import { SaleStatus, TicketCategory } from '../features/events/event.models';
import { PublicVenue } from '../features/venues/venue.models';

/** Tarjeta de la cartelera (una sesion de un evento). */
export interface PublicEventCard {
  eventSlug: string;
  eventName: string;
  imageUrl: string | null;
  venueName: string;
  venueSlug: string;
  city: string;
  sessionId: string;
  startsAt: string;
  minPrice: number | null;
  currency: string;
  soldOut: boolean;
  /** Titulo del dia en eventos de varios dias ("Sabado - Techno"). */
  sessionTitle: string | null;
}

export interface PublicTicket {
  id: string;
  name: string;
  description: string | null;
  category: TicketCategory;
  price: number;
  currency: string;
  available: number;
  maxPerOrder: number;
  saleStatus: SaleStatus;
}

export interface PublicSession {
  id: string;
  startsAt: string;
  endsAt: string;
  doorsOpenAt: string | null;
  soldOut: boolean;
  ticketTypes: PublicTicket[];
  /** Titulo, descripcion y cartel propios del dia (pueden ser null). */
  title: string | null;
  description: string | null;
  imageUrl: string | null;
  salesPaused: boolean;
}

export interface PublicEventDetail {
  slug: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  minAge: number | null;
  venue: PublicVenue;
  sessions: PublicSession[];
}

export interface MyTicket {
  id: string;
  orderId: string;
  eventName: string;
  eventSlug: string;
  venueName: string;
  venueAddress: string;
  city: string;
  startsAt: string;
  doorsOpenAt: string | null;
  ticketTypeName: string;
  holderName: string;
  status: 'VALID' | 'USED' | 'CANCELLED';
  usedAt: string | null;
  qrPayload: string;
  /** Titulo del dia ("Sabado - Techno") si el evento lo tiene. */
  sessionTitle: string | null;
  /** Cartel del dia o, si no tiene, imagen general del evento. */
  imageUrl: string | null;
  endsAt: string;
  minAge: number | null;
}

export interface Profile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  marketingConsent: boolean;
  privacyAcceptedAt: string | null;
  createdAt: string;
}

/** Empresa que tiene una ficha del cliente y su consentimiento comercial (privacidad). */
export interface MyConsent {
  customerId: string;
  companyName: string;
  marketingConsent: boolean;
  lastPurchaseAt: string | null;
}
