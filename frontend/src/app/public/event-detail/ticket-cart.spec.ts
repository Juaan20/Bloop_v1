import { PublicTicket } from '../public.models';
import { cartCount, cartTotal, clampQuantity, maxSelectable } from './ticket-cart';

function ticket(overrides: Partial<PublicTicket> = {}): PublicTicket {
  return {
    id: 't1', name: 'General', description: null, category: 'GENERAL', price: 15.5, currency: 'EUR',
    available: 100, maxPerOrder: 6, saleStatus: 'ON_SALE', ...overrides,
  };
}

describe('ticket-cart', () => {
  it('limita la cantidad al maximo por pedido y al stock disponible', () => {
    expect(maxSelectable(ticket())).toBe(6);
    expect(maxSelectable(ticket({ available: 2 }))).toBe(2);
    expect(clampQuantity(ticket(), 10)).toBe(6);
    expect(clampQuantity(ticket(), -3)).toBe(0);
  });

  it('no permite elegir entradas que no estan a la venta', () => {
    expect(maxSelectable(ticket({ saleStatus: 'SOLD_OUT' }))).toBe(0);
    expect(clampQuantity(ticket({ saleStatus: 'ENDED' }), 2)).toBe(0);
  });

  it('calcula el total sin errores de coma flotante', () => {
    const lines = [
      { ticket: ticket({ price: 0.1 }), quantity: 3 },
      { ticket: ticket({ id: 't2', price: 15.5 }), quantity: 2 },
    ];
    expect(cartTotal(lines)).toBe(31.3);
    expect(cartCount(lines)).toBe(5);
  });
});
