import { presetCells, resizeCells } from './floor-layout';

describe('floor-layout', () => {
  it('un rectangulo es todo suelo', () => {
    expect(presetCells('RECT', 4, 3)).toBe('111111111111');
  });

  it('la forma en L deja fuera la esquina superior derecha', () => {
    const cells = presetCells('L', 10, 8);
    expect(cells[0]).toBe('1'); // arriba a la izquierda
    expect(cells[9]).toBe('0'); // arriba a la derecha
    expect(cells[7 * 10 + 9]).toBe('1'); // abajo a la derecha
  });

  it('la U tiene hueco en el centro superior', () => {
    const cells = presetCells('U', 9, 8);
    expect(cells[4]).toBe('0');
    expect(cells[0]).toBe('1');
    expect(cells[7 * 9 + 4]).toBe('1');
  });

  it('al ampliar la rejilla conserva lo pintado', () => {
    expect(resizeCells({ cols: 2, rows: 1, cells: '01', elements: [] }, 3, 2)).toBe('011111');
  });
});
