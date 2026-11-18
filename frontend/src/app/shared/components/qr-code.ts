import { Component, effect, input, signal } from '@angular/core';
import QRCode from 'qrcode';

/**
 * Dibuja un codigo QR a partir de un texto (el qrPayload que devuelve el backend).
 * La imagen se genera en el navegador: el contenido nunca sale a servicios externos.
 */
@Component({
  selector: 'vf-qr-code',
  template: `
    @if (dataUrl(); as src) {
      <img [src]="src" [width]="size()" [height]="size()" alt="Codigo QR de la entrada" class="rounded-lg bg-white p-2" />
    }
  `,
})
export class QrCode {
  readonly value = input.required<string>();
  readonly size = input(200);
  protected readonly dataUrl = signal<string | null>(null);

  constructor() {
    // effect(): se vuelve a ejecutar cada vez que cambian value() o size().
    effect(() => {
      QRCode.toDataURL(this.value(), { width: this.size(), margin: 1, errorCorrectionLevel: 'M' })
        .then((url) => this.dataUrl.set(url))
        .catch(() => this.dataUrl.set(null));
    });
  }
}
