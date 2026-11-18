import { describe, expect, it } from 'vitest';
import { addToTicket, change, changeQuantity, discountAmount, lineKey, subtotal, unitsOf } from './pos-ticket';

const cana = { variantId: 'cana', label: 'Cerveza (Cana)', price: 3, stock: 2 };
const mojito = { variantId: 'mojito', label: 'Mojito (Copa)', price: 9, stock: null };

describe('ticket del TPV', () => {
  it('suma unidades sin pasar de las existencias', () => {
    let lines = addToTicket([], cana);
    lines = addToTicket(lines, cana);
    lines = addToTicket(lines, cana);
    expect(lines[0].quantity).toBe(2);
    expect(addToTicket([], { ...cana, stock: 0 })).toEqual([]);
  });

  it('quita la linea al llegar a 0 y calcula el subtotal', () => {
    let lines = addToTicket(addToTicket([], cana), mojito);
    expect(subtotal(lines)).toBe(12);
    lines = changeQuantity(lines, 'cana', -1);
    expect(lines.map((l) => l.variantId)).toEqual(['mojito']);
  });

  it('descuentos y cambio', () => {
    expect(discountAmount(40, { type: 'PERCENTAGE', value: 15 })).toBe(6);
    expect(discountAmount(10, { type: 'FIXED_AMOUNT', value: 50 })).toBe(10);
    expect(change(15, 20)).toBe(5);
    expect(change(15, 10)).toBeNull();
  });

  it('la misma copa con distinto refresco va en lineas separadas', () => {
    const rumCola = { variantId: 'copa-ron', mixerVariantId: 'cola', label: 'Ron (Copa) + Coca-Cola', price: 8, stock: 14 };
    const rumRedBull = { ...rumCola, mixerVariantId: 'redbull', label: 'Ron (Copa) + Red Bull', price: 10 };
    let lines = addToTicket(addToTicket(addToTicket([], rumCola), rumCola), rumRedBull);
    expect(lines).toHaveLength(2);
    expect(subtotal(lines)).toBe(26);
    expect(unitsOf(lines, 'copa-ron')).toBe(3);
    lines = changeQuantity(lines, lineKey(rumRedBull), -1);
    expect(lines.map((l) => l.mixerVariantId)).toEqual(['cola']);
  });
});
