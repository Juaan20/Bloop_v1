import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { inject as injectAnalytics } from '@vercel/analytics';
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
    // Initialize Vercel Analytics (browser-side only)
    if (typeof window !== 'undefined') {
      injectAnalytics();
    }
  }
}
