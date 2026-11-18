import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Icon } from '../shared/ui/icon';
import { Logo } from '../shared/ui/logo';
import { AuthService } from '../core/auth/auth.service';

/** Estructura de la web publica: cabecera, contenido y pie. Identidad visual "nightlife". */
@Component({
  selector: 'vf-public-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Logo],
  templateUrl: './public-layout.html',
})
export class PublicLayout {
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
  protected readonly year = new Date().getFullYear();
}
