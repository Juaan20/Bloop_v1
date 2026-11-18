import { describe, expect, it } from 'vitest';
import { statusLabel } from '../../shared/pipes/status-label.pipe';
import { NEXT_STATUSES, ReservationStatus } from './reservation.models';

describe('estados de reserva', () => {
  it('las reservas terminadas no admiten mas cambios', () => {
    expect(NEXT_STATUSES.COMPLETED).toEqual([]);
    expect(NEXT_STATUSES.CANCELLED).toEqual([]);
    expect(NEXT_STATUSES.NO_SHOW).toEqual([]);
    expect(NEXT_STATUSES.PENDING).toContain('CONFIRMED');
  });

  it('todos los estados y segmentos de la fase 2 tienen etiqueta en espanol', () => {
    const codes: string[] = [...(Object.keys(NEXT_STATUSES) as ReservationStatus[]), 'NEW', 'RECURRING', 'INACTIVE', 'LOW_STOCK'];
    for (const code of codes) {
      expect(statusLabel(code)).not.toBe(code);
    }
  });
});
