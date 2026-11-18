import { Pipe, PipeTransform } from '@angular/core';

/** Traducciones de los codigos que devuelve el backend. */
const LABELS: Record<string, string> = {
  ACTIVE: 'Activo', INACTIVE: 'Inactivo', DISABLED: 'Desactivado', SUSPENDED: 'Suspendida',
  DRAFT: 'Borrador', PUBLISHED: 'Publicado', CANCELLED: 'Cancelado', SCHEDULED: 'Programada',
  PENDING_PAYMENT: 'Pendiente de pago', PAID: 'Pagado', EXPIRED: 'Caducado', REFUNDED: 'Reembolsado',
  PENDING: 'Pendiente', SUCCEEDED: 'Correcto', FAILED: 'Fallido',
  VALID: 'Valida', USED: 'Utilizada',
  ON_SALE: 'A la venta', NOT_STARTED: 'Proximamente', ENDED: 'Venta cerrada', SOLD_OUT: 'Agotado', PAUSED: 'Venta pausada',
  GENERAL: 'General', EARLY_BIRD: 'Anticipada', VIP: 'VIP', FREE: 'Gratuita',
  ACCEPTED: 'Aceptada', ALREADY_USED: 'Ya utilizada', INVALID_CODE: 'QR no valido',
  WRONG_SESSION: 'Otro evento', NOT_PAID: 'No pagada',
  SUPER_ADMIN: 'Super admin', TENANT_ADMIN: 'Administrador', MANAGER: 'Encargado', CASHIER: 'Cajero', VIP_WAITER: 'Camarero VIP',
  DOOR_STAFF: 'Puerta', PROMOTER: 'Promotor', CUSTOMER: 'Cliente',
  SIMULATED: 'Simulado', STRIPE: 'Stripe',
  // Fase 2
  CONFIRMED: 'Confirmada', SEATED: 'En mesa', COMPLETED: 'Completada', NO_SHOW: 'No se presento',
  NEW: 'Nuevo', RECURRING: 'Recurrente',
  WEB: 'Web', BACKOFFICE: 'Backoffice', CHECKOUT: 'Compra', MIGRATION: 'Migracion',
  PERCENTAGE: 'Porcentaje', FIXED_PER_TICKET: 'Fijo por entrada', FIXED_AMOUNT: 'Importe fijo',
  LOW_STOCK: 'Pocas entradas', RESERVATION_REQUESTED: 'Reserva solicitada', RESERVATION_CANCELLED: 'Reserva cancelada',
  SENT: 'Enviado', LOGGED: 'No enviado (modo pruebas)',
  ORDER_CONFIRMED: 'Compra', ORDER_REFUNDED: 'Reembolso', PASSWORD_RESET: 'Contrasena', WELCOME: 'Bienvenida', TICKETS_RESENT: 'Reenvio de entradas',
  TICKET_TRANSFERRED: 'Transferencia', EVENT_CHANGED: 'Cambio de evento', EVENT_CANCELLED: 'Evento cancelado', RESERVATION_RECEIVED: 'Solicitud de reserva',
  RESERVATION_CONFIRMED: 'Reserva confirmada', RESERVATION_PAID: 'Pago de reserva', TEST: 'Prueba',
  // Fase 3
  OPEN: 'Abierta', CLOSED: 'Cerrada', VOIDED: 'Anulada', CASH: 'Efectivo', CARD: 'Tarjeta', LOW_INVENTORY: 'Stock bajo',
};

export function statusLabel(code: string | null | undefined): string {
  return code ? (LABELS[code] ?? code) : '';
}

/** Uso en plantillas: {{ order.status | statusLabel }} */
@Pipe({ name: 'statusLabel' })
export class StatusLabelPipe implements PipeTransform {
  transform(code: string | null | undefined): string {
    return statusLabel(code);
  }
}
