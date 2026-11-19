import { Component, isDevMode } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { injectSpeedInsights } from '@vercel/speed-insights';
import { ToastContainer } from './shared/ui/toast-container';

/** Componente raiz: solo contiene el router y los avisos globales. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastContainer],
  template: `
    <router-outlet />
    <vf-toast-container />
  `,
})
export class App {
  constructor() {
    injectSpeedInsights({
      framework: 'angular',
      debug: isDevMode(),
    });
  }
}
