import { Icon } from '../../shared/ui/icon';
import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { IScannerControls } from '@zxing/browser';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { AccessService, AccessSession, AccessValidation, ValidationResult } from './access.service';

/**
 * Pantalla de puerta: el empleado elige la sesion y escanea QR con la camara (o escribe el codigo).
 * La validacion real la hace el backend; aqui solo mostramos el resultado en grande (verde/rojo).
 */
@Component({
  selector: 'vf-scanner-page',
  imports: [Icon, DatePipe, FormsModule, PageHeader, Spinner, EmptyState, StatusBadge],
  templateUrl: './scanner.page.html',
})
export class ScannerPage implements OnInit, OnDestroy {
  private readonly access = inject(AccessService);
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  protected readonly sessions = signal<AccessSession[]>([]);
  protected readonly loading = signal(true);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly result = signal<ValidationResult | null>(null);
  protected readonly history = signal<AccessValidation[]>([]);
  protected readonly cameraOn = signal(false);
  protected readonly cameraError = signal<string | null>(null);
  protected readonly validating = signal(false);
  protected manualCode = '';

  protected readonly session = computed(() => this.sessions().find((s) => s.sessionId === this.selectedId()) ?? null);

  private controls: IScannerControls | null = null;
  private lastScan = { code: '', at: 0 };
  private clearTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.refreshSessions();
  }

  ngOnDestroy(): void {
    this.stopCamera();
    if (this.clearTimer) clearTimeout(this.clearTimer);
  }

  protected select(id: string): void {
    this.selectedId.set(id);
    this.result.set(null);
    this.loadHistory();
  }

  protected validateManual(): void {
    if (this.manualCode.trim()) {
      this.validate(this.manualCode.trim());
      this.manualCode = '';
    }
  }

  /** Activa la camara. La libreria ZXing se carga solo cuando se necesita (import dinamico). */
  protected async startCamera(): Promise<void> {
    this.cameraError.set(null);
    this.cameraOn.set(true);
    try {
      const { BrowserQRCodeReader } = await import('@zxing/browser');
      const reader = new BrowserQRCodeReader();
      const videoEl = this.video()?.nativeElement;
      if (!videoEl) throw new Error('Sin elemento de video');
      this.controls = await reader.decodeFromVideoDevice(undefined, videoEl, (res) => {
        if (res) this.onScan(res.getText());
      });
    } catch {
      this.cameraOn.set(false);
      this.cameraError.set('No se pudo acceder a la camara. Revisa los permisos del navegador o usa la entrada manual.');
    }
  }

  protected stopCamera(): void {
    this.controls?.stop();
    this.controls = null;
    this.cameraOn.set(false);
  }

  private onScan(code: string): void {
    // La camara lee el mismo QR muchas veces por segundo: ignoramos repeticiones durante 3 s.
    const now = Date.now();
    if (code === this.lastScan.code && now - this.lastScan.at < 3000) return;
    this.lastScan = { code, at: now };
    this.validate(code);
  }

  private validate(code: string): void {
    const sessionId = this.selectedId();
    if (!sessionId || this.validating()) return;
    this.validating.set(true);
    this.access.validate(code, sessionId).subscribe({
      next: (res) => {
        this.result.set(res);
        this.validating.set(false);
        this.navigatorVibrate(res.accepted);
        this.loadHistory();
        this.refreshSessions();
        if (this.clearTimer) clearTimeout(this.clearTimer);
        this.clearTimer = setTimeout(() => this.result.set(null), 6000);
      },
      error: (err) => {
        this.result.set({ accepted: false, result: 'INVALID_CODE', message: errorMessage(err), ticketTypeName: null, holderName: null, usedAt: null });
        this.validating.set(false);
      },
    });
  }

  private refreshSessions(): void {
    this.access.sessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
        if (!this.selectedId() && sessions.length === 1) this.select(sessions[0].sessionId);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadHistory(): void {
    const id = this.selectedId();
    if (id) this.access.history(id).subscribe((page) => this.history.set(page.content));
  }

  private navigatorVibrate(ok: boolean): void {
    if ('vibrate' in navigator) navigator.vibrate(ok ? 80 : [80, 60, 80]);
  }
}
