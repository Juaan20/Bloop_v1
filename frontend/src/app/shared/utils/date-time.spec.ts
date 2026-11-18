import { isoToLocalInput, localInputToIso } from './date-time';

describe('date-time', () => {
  it('convierte entre ISO (UTC) y datetime-local sin perder la hora', () => {
    const iso = '2026-11-20T22:30:00.000Z';
    const local = isoToLocalInput(iso);
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(localInputToIso(local)).toBe(iso);
  });

  it('acepta valores vacios', () => {
    expect(isoToLocalInput(null)).toBe('');
    expect(localInputToIso('')).toBeNull();
  });
});
