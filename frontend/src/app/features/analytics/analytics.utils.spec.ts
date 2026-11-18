import { fileNameFrom } from '../../shared/utils/download';
import { barHeights, isoDate, presetRange, previousRange } from './analytics.utils';

describe('analytics.utils', () => {
  it('formatea fechas locales como yyyy-MM-dd', () => {
    expect(isoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('calcula el periodo de los ultimos N dias incluyendo hoy', () => {
    const today = new Date(2026, 10, 17);
    expect(presetRange(1, today)).toEqual({ from: '2026-11-17', to: '2026-11-17' });
    expect(presetRange(30, today)).toEqual({ from: '2026-10-19', to: '2026-11-17' });
  });

  it('apila entradas y TPV respecto al dia de mayor facturacion', () => {
    const bars = barHeights([
      { day: '2026-11-15', ticketRevenue: 60, posRevenue: 40 },
      { day: '2026-11-16', ticketRevenue: 25, posRevenue: 25 },
    ]);
    expect(bars[0]).toMatchObject({ total: 100, ticketHeight: 60, posHeight: 40 });
    expect(bars[1]).toMatchObject({ total: 50, ticketHeight: 25, posHeight: 25 });
  });

  it('no divide por cero cuando no hay ventas', () => {
    expect(barHeights([{ day: '2026-11-15', ticketRevenue: 0, posRevenue: 0 }])[0].ticketHeight).toBe(0);
  });

  it('lee el nombre del fichero de Content-Disposition', () => {
    expect(fileNameFrom('attachment; filename="bloop-pedidos-2026-11-01-a-2026-11-17.csv"', 'x.csv'))
      .toBe('bloop-pedidos-2026-11-01-a-2026-11-17.csv');
    expect(fileNameFrom(null, 'informe.csv')).toBe('informe.csv');
  });

  it('calcula el periodo anterior de la misma duracion', () => {
    expect(previousRange('2026-11-01', '2026-11-30')).toEqual({ from: '2026-10-02', to: '2026-10-31' });
    expect(previousRange('2026-11-17', '2026-11-17')).toEqual({ from: '2026-11-16', to: '2026-11-16' });
  });
});
