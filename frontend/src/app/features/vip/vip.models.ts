import { ReservationStatus } from '../reservations/reservation.models';
import { Reservation } from '../reservations/reservation.models';

export type VipTableShape = 'ROUND' | 'RECT';

/** Estado de una mesa en el plano en vivo (lo calcula el backend). */
export type VipTableStatus =
  | 'AVAILABLE'
  | 'RESERVED'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'OCCUPIED'
  | 'SERVICE_REQUESTED'
  | 'INACTIVE';

/** Texto y colores de cada estado (leyenda y mesas del plano). */
export const TABLE_STATUS: Record<VipTableStatus, { label: string; fill: string; ring: string; dot: string }> = {
  AVAILABLE: { label: 'Disponible', fill: 'bg-zinc-800/80', ring: 'ring-zinc-600', dot: 'bg-zinc-400' },
  RESERVED: { label: 'Reservada', fill: 'bg-sky-500/20', ring: 'ring-sky-400', dot: 'bg-sky-400' },
  PENDING_PAYMENT: { label: 'Pendiente de pago', fill: 'bg-amber-500/20', ring: 'ring-amber-400', dot: 'bg-amber-400' },
  PAID: { label: 'Pagada', fill: 'bg-emerald-500/20', ring: 'ring-emerald-400', dot: 'bg-emerald-400' },
  OCCUPIED: { label: 'Ocupada', fill: 'bg-brand-500/30', ring: 'ring-brand-400', dot: 'bg-brand-400' },
  SERVICE_REQUESTED: { label: 'Servicio solicitado', fill: 'bg-rose-500/25', ring: 'ring-rose-400', dot: 'bg-rose-400' },
  INACTIVE: { label: 'Desactivada', fill: 'bg-zinc-900', ring: 'ring-zinc-800', dot: 'bg-zinc-700' },
};

export interface VipBooking {
  id: string;
  reference: string;
  contactName: string;
  guests: number;
  startsAt: string;
  endsAt: string;
  status: ReservationStatus;
  eventName: string | null;
  prepaidAmount: number;
  prepaid: boolean;
  extrasTotal: number;
  pendingTotal: number;
  openOrders: number;
}

export interface VipMapTable {
  id: string;
  zoneId: string;
  zoneName: string;
  name: string;
  capacity: number;
  price: number;
  minSpend: number;
  active: boolean;
  posX: number | null;
  posY: number | null;
  width: number;
  height: number;
  shape: VipTableShape;
  status: VipTableStatus;
  reservation: VipBooking | null;
  /** Colocada en el plano: solo esas se pueden reservar o vender. */
  onPlan: boolean;
  /** Reservas anteriores de la mesa en esta fiesta (liberadas, canceladas...). */
  history: { id: string; reference: string; contactName: string; guests: number; status: ReservationStatus; closedAt: string | null }[];
}

/** Fiesta del selector del plano: una sesion de un evento del local. */
export interface VipParty {
  sessionId: string;
  eventId: string;
  eventName: string;
  title: string | null;
  startsAt: string;
  endsAt: string;
  /** Se esta celebrando ahora mismo. */
  now: boolean;
}

/** Sala del local en el plano en vivo. */
export interface VipRoom {
  id: string;
  name: string;
  active: boolean;
  description: string | null;
  capacity: number | null;
  color: RoomColor;
  layout: import('./floor-layout').FloorLayout | null;
}

export type RoomColor = 'violet' | 'sky' | 'emerald' | 'amber' | 'rose' | 'zinc';

/** Punto de color de cada sala en el selector. */
export const ROOM_COLORS: Record<RoomColor, string> = {
  violet: 'bg-violet-500', sky: 'bg-sky-500', emerald: 'bg-emerald-500', amber: 'bg-amber-500', rose: 'bg-rose-500', zinc: 'bg-zinc-400',
};

export interface VipMap {
  venueId: string;
  at: string;
  /** Salas del local, cada una con su forma (layout null = sin disenar). */
  zones: VipRoom[];
  /** Fiesta del plano (null si no hay ninguna en curso). */
  sessionId: string | null;
  tables: VipMapTable[];
}

export type VipOrderStatus = 'NEW' | 'PENDING' | 'PREPARING' | 'READY' | 'ON_THE_WAY' | 'DELIVERED' | 'CANCELLED';

export const ORDER_STATUS: Record<VipOrderStatus, { label: string; next: VipOrderStatus | null; action: string | null; tone: string }> = {
  NEW: { label: 'Nuevo', next: 'PENDING', action: 'Visto', tone: 'bg-rose-500/15 text-rose-300 ring-rose-500/30' },
  PENDING: { label: 'Pendiente de preparacion', next: 'PREPARING', action: 'Empezar a preparar', tone: 'bg-amber-500/15 text-amber-300 ring-amber-500/30' },
  PREPARING: { label: 'En preparacion', next: 'READY', action: 'Marcar preparado', tone: 'bg-sky-500/15 text-sky-300 ring-sky-500/30' },
  READY: { label: 'Preparado', next: 'ON_THE_WAY', action: 'Llevar a la mesa', tone: 'bg-brand-500/15 text-brand-300 ring-brand-500/30' },
  ON_THE_WAY: { label: 'En camino', next: 'DELIVERED', action: 'Entregado', tone: 'bg-accent-500/15 text-accent-300 ring-accent-500/30' },
  DELIVERED: { label: 'Entregado', next: null, action: null, tone: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30' },
  CANCELLED: { label: 'Cancelado', next: null, action: null, tone: 'bg-zinc-500/15 text-zinc-400 ring-zinc-500/30' },
};

export interface VipOrderItem {
  variantId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  included: boolean;
  lineTotal: number;
}

export interface VipOrder {
  id: string;
  reservationId: string;
  tableName: string;
  zoneName: string;
  status: VipOrderStatus;
  source: 'GUEST' | 'STAFF';
  notes: string | null;
  createdAt: string;
  acknowledgedAt: string | null;
  preparingAt: string | null;
  preparedByName: string | null;
  readyAt: string | null;
  onTheWayAt: string | null;
  deliveredAt: string | null;
  deliveredByName: string | null;
  cancelledAt: string | null;
  extraTotal: number;
  items: VipOrderItem[];
}

export interface PackageLine {
  variantId: string;
  productName: string;
  included: number;
  consumed: number;
  remaining: number;
}

export interface VipAccount {
  prepaidAmount: number;
  prepaid: boolean;
  prepaidAt: string | null;
  extrasTotal: number;
  paidTotal: number;
  pendingTotal: number;
  openOrders: number;
  closed: boolean;
  closedAt: string | null;
  canClose: boolean;
  packageItems: PackageLine[];
  payments: { id: string; amount: number; method: 'CASH' | 'CARD'; createdAt: string }[];
}

export interface VipReservationDetail {
  reservation: Reservation;
  account: VipAccount;
  orders: VipOrder[];
  qr: { token: string | null; active: boolean };
}

export interface OrderLine {
  variantId: string;
  quantity: number;
}

/** Pagina publica de la mesa (QR). */
export interface GuestTable {
  venueName: string;
  zoneName: string;
  tableName: string;
  eventName: string | null;
  startsAt: string;
  endsAt: string;
  hostFirstName: string;
  canOrder: boolean;
  cannotOrderReason: string | null;
  packageItems: PackageLine[];
  menu: { name: string; items: GuestMenuItem[] }[];
  orders: VipOrder[];
  extrasTotal: number;
  pendingTotal: number;
}

export interface GuestMenuItem {
  variantId: string;
  productName: string;
  variantName: string;
  description: string | null;
  imageUrl: string | null;
  price: number;
  remainingInPackage: number;
}

/** Posicion de una mesa en el plano (para guardar el editor). */
export interface TablePosition {
  id: string;
  posX: number | null;
  posY: number | null;
  width: number;
  height: number;
  shape: VipTableShape;
}

/** Clave de idempotencia: un reintento de la misma accion no crea otro pedido/cobro. */
export function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
