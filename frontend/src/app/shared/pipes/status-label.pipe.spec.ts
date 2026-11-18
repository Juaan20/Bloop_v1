import { StatusLabelPipe, statusLabel } from './status-label.pipe';

describe('StatusLabelPipe', () => {
  it('traduce los codigos conocidos y deja los desconocidos', () => {
    const pipe = new StatusLabelPipe();
    expect(pipe.transform('PUBLISHED')).toBe('Publicado');
    expect(pipe.transform('PENDING_PAYMENT')).toBe('Pendiente de pago');
    expect(statusLabel('ALGO_NUEVO')).toBe('ALGO_NUEVO');
    expect(statusLabel(null)).toBe('');
  });
});
