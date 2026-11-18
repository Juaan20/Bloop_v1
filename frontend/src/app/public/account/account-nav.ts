import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Pestanas de la zona "Mi cuenta". */
@Component({
  selector: 'vf-account-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="mb-8 flex gap-2 overflow-x-auto border-b border-white/10">
      @for (tab of tabs; track tab.route) {
        <a [routerLink]="tab.route" routerLinkActive="!border-fuchsia-400 !text-white"
           class="-mb-px border-b-2 border-transparent px-4 py-3 text-sm text-zinc-400 hover:text-white">{{ tab.label }}</a>
      }
    </nav>
  `,
})
export class AccountNav {
  protected readonly tabs = [
    { label: 'Mis entradas', route: '/cuenta/entradas' },
    { label: 'Mis pedidos', route: '/cuenta/pedidos' },
    { label: 'Mis reservas', route: '/cuenta/reservas' },
    { label: 'Mi perfil', route: '/cuenta/perfil' },
    { label: 'Privacidad', route: '/cuenta/privacidad' },
  ];
}
