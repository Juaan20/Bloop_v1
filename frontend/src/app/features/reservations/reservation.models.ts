export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'SEATED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

/** Transiciones permitidas (igual que ReservationStatus.canChangeTo en el backend). */
export const NEXT_STATUSES: Record<ReservationStatus, ReservationStatus[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SEATED', 'NO_SHOW', 'CANCELLED'],
  // Una reserva en la mesa se termina cerrando su cuenta (ficha VIP), no con un cambio de estado directo.
  SEATED: [],
  COMPLETED: [],
  CANCELLED: [],
  NO_SHOW: [],
};

export interface VipTable {
  id: string;
  name: string;
  capacity: number;
  minSpend: number;
  /** Precio de la reserva con su paquete (se paga antes de entrar). */
  depositAmount: number;
  active: boolean;
  posX: number | null;
  posY: number | null;
  width: number;
  height: number;
  shape: 'ROUND' | 'RECT';
  packageItems: { variantId: string; productName: string; quantity: number; unitPrice: number }[];
}

export interface VipZone {
  id: string;
  venueId: string;
  name: string;
  description: string | null;
  active: boolean;
  sortOrder: number;
  tables: VipTable[];
  capacity: number | null;
  color: string;
  layout: unknown;
}

export interface VipZonePayload {
  name: string;
  description: string | null;
  active: boolean;
  sortOrder: number;
  capacity?: number | null;
  color?: string;
}

export interface VipTablePayload {
  name: string;
  capacity: number;
  minSpend: number;
  depositAmount: number;
  active: boolean;
  shape?: 'ROUND' | 'RECT';
  width?: number;
  height?: number;
  /** Mover la mesa a otra sala del mismo local. */
  zoneId?: string;
}

/** Mesa y si esta libre en el horario consultado. */
export interface TableAvailability {
  tableId: string;
  zoneName: string;
  zoneDescription: string | null;
  tableName: string;
  capacity: number;
  minSpend: number;
  depositAmount: number;
  available: boolean;
}

export interface Reservation {
  id: string;
  reference: string;
  venueId: string;
  venueName: string;
  zoneName: string;
  tableId: string;
  tableName: string;
  sessionId: string | null;
  eventName: string | null;
  contactName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  guests: number;
  startsAt: string;
  endsAt: string;
  status: ReservationStatus;
  minSpend: number;
  depositAmount: number;
  depositPaidAt: string | null;
  depositPending: boolean;
  notes: string | null;
  source: 'WEB' | 'BACKOFFICE';
  customerId: string | null;
  createdAt: string;
}

export interface CreateReservationPayload {
  tableId: string;
  /** Fiesta: las mesas se reservan para la fiesta completa (no por horas). */
  sessionId: string;
  guests: number;
  contactName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  notes: string | null;
}
