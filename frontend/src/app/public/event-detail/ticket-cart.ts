import { PublicTicket } from '../public.models';

/**
 * Logica pura del selector de entradas (sin Angular), facil de probar con tests unitarios.
 * El backend vuelve a validar todo: precios, stock y limites.
 */
export interface CartLine {
  ticket: PublicTicket;
  quantity: number;
}

/** Maximo que se puede elegir de un tipo: limite por pedido y stock disponible. */
export function maxSelectable(ticket: PublicTicket): number {
  if (ticket.saleStatus !== 'ON_SALE') return 0;
  return Math.max(0, Math.min(ticket.maxPerOrder, ticket.available));
}

export function clampQuantity(ticket: PublicTicket, quantity: number): number {
  return Math.min(Math.max(0, Math.floor(quantity)), maxSelectable(ticket));
}

export function cartCount(lines: CartLine[]): number {
  return lines.reduce((sum, line) => sum + line.quantity, 0);
}

/** Total en euros redondeado a 2 decimales (se trabaja en centimos para evitar errores de coma flotante). */
export function cartTotal(lines: CartLine[]): number {
  const cents = lines.reduce((sum, line) => sum + Math.round(line.ticket.price * 100) * line.quantity, 0);
  return cents / 100;
}
