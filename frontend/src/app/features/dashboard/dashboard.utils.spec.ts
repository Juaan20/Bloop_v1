import { comparisonRanges, trend } from './dashboard.utils';

describe('dashboard.utils', () => {
  it('calcula la variacion respecto al periodo anterior', () => {
    expect(trend(120, 100)).toEqual({ change: 0.2, direction: 'up' });
    expect(trend(50, 100)?.direction).toBe('down');
    expect(trend(100, 100)?.direction).toBe('flat');
  });

  it('no inventa porcentajes si no hay base de comparacion', () => {
    expect(trend(100, 0)).toBeNull();
    expect(trend(100, null)).toBeNull();
    expect(trend(undefined, 10)).toBeNull();
  });

  it('el periodo anterior tiene la misma duracion y termina justo antes', () => {
    const { current, previous } = comparisonRanges(7, new Date(2026, 10, 17, 15, 0));
    expect(current.from).toEqual(new Date(2026, 10, 11, 0, 0, 0, 0));
    expect(previous.from).toEqual(new Date(2026, 10, 4, 0, 0, 0, 0));
    expect(previous.to.getTime()).toBe(current.from.getTime() - 1);
  });
});
