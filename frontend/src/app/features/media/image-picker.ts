import { Component, forwardRef, inject, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { Icon } from '../../shared/ui/icon';
import { Modal } from '../../shared/ui/modal';
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_BYTES, MediaAsset, MediaService } from './media.service';

/**
 * Selector de imagen para formularios reactivos (formControlName="imageUrl").
 *
 * 1. Elegir un fichero del ordenador -> se ve la previsualizacion local ANTES de subir.
 * 2. "Subir" -> barra de progreso; al terminar el campo guarda la URL que devuelve el backend.
 * 3. "Biblioteca" -> reutilizar una imagen ya subida (y borrar las que no se usan).
 * 4. "Quitar" -> el evento/producto se queda sin imagen (la imagen sigue en la biblioteca).
 *
 * El navegador solo hace una comprobacion rapida de tipo y tamano; la de verdad la hace el backend.
 */
@Component({
  selector: 'vf-image-picker',
  imports: [Icon, Modal],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ImagePicker), multi: true },
  ],
  template: `
    <div class="flex flex-wrap items-start gap-4">
      <div
        class="relative aspect-[4/3] w-40 shrink-0 overflow-hidden rounded-xl border border-zinc-800 bg-zinc-950"
      >
        @if (preview() ?? value(); as src) {
          <img [src]="src" [alt]="label()" class="size-full object-cover" />
        } @else {
          <div
            class="flex size-full flex-col items-center justify-center gap-1 text-xs text-zinc-500"
          >
            <vf-icon name="image" class="size-6" />Sin imagen
          </div>
        }
        @if (progress() !== null) {
          <div
            class="absolute inset-x-0 bottom-0 h-1.5 bg-zinc-800"
            role="progressbar"
            [attr.aria-valuenow]="progress()"
            aria-valuemin="0"
            aria-valuemax="100"
            [attr.aria-label]="'Subiendo ' + label()"
          >
            <div class="h-full bg-brand-500 transition-[width]" [style.width.%]="progress()"></div>
          </div>
        }
      </div>
      <div class="min-w-0 flex-1 space-y-2">
        <input
          #file
          type="file"
          class="sr-only"
          [accept]="accept"
          (change)="choose($any($event.target))"
          [attr.aria-label]="'Elegir ' + label()"
        />
        @if (pending(); as f) {
          <p class="truncate text-sm text-zinc-300">
            {{ f.name }} <span class="text-zinc-500">· {{ size(f.size) }}</span>
          </p>
          <div class="flex flex-wrap gap-2">
            <button
              type="button"
              class="vf-btn-primary vf-btn-sm"
              [disabled]="progress() !== null"
              (click)="upload()"
            >
              <vf-icon name="upload" class="size-3.5" />
              {{ progress() !== null ? 'Subiendo ' + progress() + '%' : 'Subir imagen' }}
            </button>
            <button
              type="button"
              class="vf-btn-ghost vf-btn-sm"
              [disabled]="progress() !== null"
              (click)="cancel(file)"
            >
              Cancelar
            </button>
          </div>
        } @else {
          <div class="flex flex-wrap gap-2">
            <button
              type="button"
              class="vf-btn-secondary vf-btn-sm"
              [disabled]="disabled()"
              (click)="file.click()"
            >
              <vf-icon name="upload" class="size-3.5" />
              {{ value() ? 'Cambiar' : 'Elegir del ordenador' }}
            </button>
            <button
              type="button"
              class="vf-btn-ghost vf-btn-sm"
              [disabled]="disabled()"
              (click)="openLibrary()"
            >
              <vf-icon name="layers" class="size-3.5" /> Biblioteca
            </button>
            @if (value()) {
              <button
                type="button"
                class="vf-btn-ghost vf-btn-sm text-red-300"
                [disabled]="disabled()"
                (click)="set(null)"
              >
                Quitar
              </button>
            }
          </div>
        }
        <p class="text-xs text-zinc-500">JPG, PNG o WebP, maximo 5 MB. {{ hint() }}</p>
        @if (error()) {
          <p class="text-xs text-red-300" role="alert">{{ error() }}</p>
        }
      </div>
    </div>

    <vf-modal
      [open]="libraryOpen()"
      title="Biblioteca de imagenes"
      size="lg"
      (closed)="libraryOpen.set(false)"
    >
      @if (libraryLoading()) {
        <div class="grid grid-cols-3 gap-3 sm:grid-cols-4">
          @for (i of [1, 2, 3, 4]; track i) {
            <div class="vf-skeleton aspect-[4/3]"></div>
          }
        </div>
      } @else if (library().length === 0) {
        <p class="py-8 text-center text-sm text-zinc-500">Todavia no has subido imagenes.</p>
      } @else {
        <ul class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          @for (a of library(); track a.id) {
            <li
              class="relative overflow-hidden rounded-xl border border-zinc-800"
              [class.ring-2]="a.url === value()"
              [class.ring-brand-500]="a.url === value()"
            >
              <button
                type="button"
                class="block aspect-[4/3] w-full cursor-pointer"
                (click)="pick(a)"
                [attr.aria-label]="'Usar ' + (a.originalName ?? 'imagen')"
              >
                <img
                  [src]="a.url"
                  [alt]="a.originalName ?? ''"
                  class="size-full object-cover"
                  loading="lazy"
                />
              </button>
              <div
                class="flex items-center justify-between gap-1 px-2 py-1.5 text-[11px] text-zinc-400"
              >
                <span class="truncate">{{ a.originalName ?? 'Imagen' }}</span>
                <button
                  type="button"
                  class="vf-btn-icon size-6 shrink-0 text-zinc-500 hover:text-red-300"
                  (click)="remove(a)"
                  [attr.aria-label]="'Borrar ' + (a.originalName ?? 'imagen')"
                >
                  <vf-icon name="trash" class="size-3.5" />
                </button>
              </div>
            </li>
          }
        </ul>
        @if (hasMore()) {
          <div class="mt-4 text-center">
            <button
              type="button"
              class="vf-btn-secondary vf-btn-sm"
              (click)="loadLibrary(libraryPage + 1)"
            >
              Cargar mas
            </button>
          </div>
        }
      }
    </vf-modal>
  `,
})
export class ImagePicker implements ControlValueAccessor {
  private readonly media = inject(MediaService);
  private readonly toast = inject(ToastService);

  readonly label = input('imagen');
  readonly hint = input('');

  protected readonly accept = ACCEPTED_IMAGE_TYPES.join(',');
  protected readonly value = signal<string | null>(null);
  protected readonly disabled = signal(false);
  protected readonly pending = signal<File | null>(null);
  protected readonly preview = signal<string | null>(null);
  protected readonly progress = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly libraryOpen = signal(false);
  protected readonly library = signal<MediaAsset[]>([]);
  protected readonly libraryLoading = signal(false);
  protected readonly hasMore = signal(false);
  protected libraryPage = 0;

  private onChange: (v: string | null) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value || null);
  }
  registerOnChange(fn: (v: string | null) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  protected size(bytes: number): string {
    return bytes > 1024 * 1024
      ? (bytes / 1024 / 1024).toFixed(1) + ' MB'
      : Math.ceil(bytes / 1024) + ' KB';
  }

  protected choose(inputEl: HTMLInputElement): void {
    const file = inputEl.files?.[0];
    this.error.set(null);
    if (!file) return;
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      this.error.set('Formato no permitido: elige una imagen JPG, PNG o WebP.');
      inputEl.value = '';
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      this.error.set('La imagen supera los 5 MB.');
      inputEl.value = '';
      return;
    }
    this.revokePreview();
    this.pending.set(file);
    this.preview.set(URL.createObjectURL(file));
  }

  protected cancel(inputEl: HTMLInputElement): void {
    inputEl.value = '';
    this.pending.set(null);
    this.revokePreview();
  }

  protected upload(): void {
    const file = this.pending();
    if (!file) return;
    this.progress.set(0);
    this.media.upload(file).subscribe({
      next: (state) => {
        this.progress.set(state.progress);
        if (state.asset) {
          this.set(state.asset.url);
          this.pending.set(null);
          this.revokePreview();
          this.progress.set(null);
          this.toast.success('Imagen subida');
        }
      },
      error: (err) => {
        this.progress.set(null);
        this.error.set(errorMessage(err));
      },
    });
  }

  protected set(url: string | null): void {
    this.value.set(url);
    this.onChange(url);
    this.onTouched();
  }

  protected openLibrary(): void {
    this.libraryOpen.set(true);
    this.loadLibrary(0);
  }

  protected loadLibrary(page: number): void {
    this.libraryLoading.set(page === 0);
    this.media.list(page).subscribe({
      next: (p) => {
        this.libraryPage = p.page;
        this.library.update((l) => (page === 0 ? p.content : [...l, ...p.content]));
        this.hasMore.set(p.page + 1 < p.totalPages);
        this.libraryLoading.set(false);
      },
      error: (err) => {
        this.libraryLoading.set(false);
        this.toast.error(errorMessage(err));
      },
    });
  }

  protected pick(asset: MediaAsset): void {
    this.set(asset.url);
    this.libraryOpen.set(false);
  }

  protected remove(asset: MediaAsset): void {
    if (!confirm('¿Borrar esta imagen de la biblioteca? Si se esta usando no se podra borrar.'))
      return;
    this.media.delete(asset.id).subscribe({
      next: () => {
        this.library.update((l) => l.filter((x) => x.id !== asset.id));
        if (this.value() === asset.url) this.set(null);
        this.toast.success('Imagen borrada');
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  private revokePreview(): void {
    const p = this.preview();
    if (p) URL.revokeObjectURL(p);
    this.preview.set(null);
  }
}
