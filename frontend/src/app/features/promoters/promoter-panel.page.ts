import { Component, OnInit, inject, signal } from '@angular/core';
import { errorMessage } from '../../core/services/api-error';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { PromoterDetail } from './promoter.models';
import { PromoterPerformance } from './promoter-performance';
import { PromotersService } from './promoters.service';

/** Panel individual del promotor autenticado (permiso PROMOTER_PANEL). */
@Component({
  selector: 'vf-promoter-panel-page',
  imports: [PageHeader, Spinner, PromoterPerformance],
  template: `
    <vf-page-header title="Mi panel de RRPP" [subtitle]="detail() ? 'Tu codigo de enlace: ' + detail()!.promoter.referralCode : 'Tus ventas y comisiones'" />
    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-zinc-300">{{ error() }}</p>
    } @else if (detail(); as d) {
      <vf-promoter-performance [detail]="d" />
    }
  `,
})
export class PromoterPanelPage implements OnInit {
  private readonly promotersService = inject(PromotersService);
  protected readonly detail = signal<PromoterDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.promotersService.myPanel().subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
