export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'REFUNDED';

export interface OrderItem {
  ticketTypeId: string;
  ticketTypeName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: string;
  reference: string;
  status: OrderStatus;
  /** Importe antes del descuento del codigo promocional. */
  subtotalAmount: number;
  discountAmount: number;
  totalAmount: number;
  currency: string;
  promoCode: string | null;
  eventName: string;
  eventSlug: string;
  venueName: string;
  sessionId: string;
  sessionStartsAt: string;
  /** Titulo del dia ("Sabado - Techno"), si el evento lo tiene. */
  sessionTitle: string | null;
  buyerName: string;
  buyerEmail: string;
  ticketCount: number;
  items: OrderItem[];
  expiresAt: string | null;
  paidAt: string | null;
  createdAt: string;
  refundedAt: string | null;
  refundReason: string | null;
}

export interface OrderDetail {
  order: Order;
  payments: {
    id: string;
    provider: string;
    status: string;
    amount: number;
    failureReason: string | null;
    createdAt: string;
  }[];
  tickets: { id: string; ticketTypeName: string; holderName: string; status: string; usedAt: string | null }[];
  /** Promotor al que se atribuye la venta (null si no hay). */
  promoterName: string | null;
  commissionAmount: number;
}

/** Filtros del listado de ventas (todos opcionales). from/to en ISO UTC. */
export interface OrderFilters {
  status?: OrderStatus | '';
  eventId?: string;
  sessionId?: string;
  venueId?: string;
  from?: string | null;
  to?: string | null;
  paymentStatus?: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED' | '';
  paymentProvider?: 'STRIPE' | 'SIMULATED' | 'FREE' | '';
  ticketCategory?: 'GENERAL' | 'EARLY_BIRD' | 'VIP' | 'FREE' | '';
  search?: string;
  sort?: 'recent' | 'oldest' | 'amount_desc' | 'amount_asc';
}

export interface OrderSummary {
  orders: number;
  paidOrders: number;
  pendingOrders: number;
  refundedOrders: number;
  tickets: number;
  paidAmount: number;
  refundedAmount: number;
  discountAmount: number;
}
