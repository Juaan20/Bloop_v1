import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormArray, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { Icon } from '../../shared/ui/icon';
import { ImagePicker } from '../media/image-picker';
import { CATEGORY_COLORS, CATEGORY_KINDS, Category, CategoryKind, Product, ProductPayload, StockUnit, Variant } from './pos.models';
import { PosService } from './pos.service';

/** Catalogo del TPV: categorias (pestanas de la pantalla) y productos con sus variantes y precios. */
@Component({
  selector: 'vf-pos-catalog-page',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink, EmptyState, Icon, ImagePicker, Modal, PageHeader, Spinner],
  template: `
    <vf-page-header title="Catalogo del TPV" subtitle="Lo que se vende en barra y taquilla">
      <a routerLink="/admin/tpv/terminales" class="vf-btn-secondary">Terminales y cajas</a>
      <button type="button" class="vf-btn-secondary" (click)="openCategory(null)">+ Categoria</button>
      <button type="button" class="vf-btn-primary" [disabled]="categories().length === 0" (click)="openProduct(null)">+ Producto</button>
    </vf-page-header>

    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (categories().length === 0) {
      <vf-empty-state title="Catalogo vacio" description="Crea primero una categoria (Cervezas, Copas, Taquilla...) y despues sus productos." icon="beer" />
    } @else {
      <div class="space-y-6">
        @for (c of categories(); track c.id) {
          <section class="vf-card" [class.opacity-50]="!c.active">
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-3">
                <span class="h-4 w-4 rounded" [class]="colors[c.color]"></span>
                <h2 class="font-display text-lg font-semibold text-white">{{ c.name }} {{ c.active ? '' : '(inactiva)' }}</h2>
                <span class="vf-badge bg-zinc-500/10 text-zinc-300 ring-zinc-500/25">{{ kindLabel(c.kind) }}</span>
              </div>
              <button type="button" class="vf-btn-ghost text-xs" (click)="openCategory(c)">Editar categoria</button>
            </div>
            @if (productsOf(c.id).length === 0) {
              <p class="mt-3 text-sm text-zinc-500">Sin productos.</p>
            } @else {
              <table class="vf-table mt-3">
                <thead><tr><th>Producto</th><th>Variantes y precios</th><th>Stock</th><th></th></tr></thead>
                <tbody>
                  @for (p of productsOf(c.id); track p.id) {
                    <tr [class.opacity-50]="!p.active">
                      <td class="text-zinc-200">{{ p.name }} {{ p.active ? '' : '(inactivo)' }}</td>
                      <td class="text-sm text-zinc-300">
                        @for (v of p.variants; track v.id) {
                          <span class="mr-3 inline-flex items-center gap-1" [class.line-through]="!v.active">{{ v.name }} {{ v.price | currency: 'EUR' }}
                            @if (v.requiresMixer) { <span class="vf-badge bg-sky-500/10 text-sky-300 ring-sky-500/20">+ refresco</span> }
                            @if (v.recipe.length) { <span class="text-[11px] text-zinc-500">({{ recipeText(v) }})</span> }
                          </span>
                        }
                      </td>
                      <td class="text-xs text-zinc-400">{{ p.mixerSurcharge !== null ? 'Refresco de combinado · ' : '' }}{{ p.trackStock ? 'Controla existencias' : 'Sin existencias' }}</td>
                      <td><button type="button" class="vf-btn-ghost text-xs" (click)="openProduct(p)">Editar</button></td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </section>
        }
      </div>
    }

    <vf-modal [open]="categoryModal()" [title]="editingCategory() ? 'Editar categoria' : 'Nueva categoria'" (closed)="categoryModal.set(false)">
      <form class="space-y-4" [formGroup]="categoryForm" (ngSubmit)="saveCategory()">
        <div><label class="vf-label" for="cName">Nombre</label><input id="cName" class="vf-input" formControlName="name" /></div>
        <div>
          <p class="vf-label">Color del boton</p>
          <div class="flex gap-2">
            @for (key of colorKeys; track key) {
              <button type="button" class="h-9 w-9 rounded-lg ring-2" [class]="colors[key]"
                      [class.ring-white]="categoryForm.controls.color.value === key" [class.ring-transparent]="categoryForm.controls.color.value !== key"
                      (click)="categoryForm.controls.color.setValue(key)" [attr.aria-label]="key"></button>
            }
          </div>
        </div>
        <fieldset>
          <legend class="vf-label">Tipo de categoria</legend>
          <div class="grid gap-2 sm:grid-cols-2">
            @for (k of kinds; track k.value) {
              <label class="flex cursor-pointer items-start gap-2 rounded-lg border p-2.5 text-sm transition"
                     [class]="categoryForm.controls.kind.value === k.value ? 'border-brand-400 bg-brand-500/10' : 'border-zinc-800 hover:border-zinc-600'">
                <input type="radio" class="mt-1 accent-brand-500" formControlName="kind" [value]="k.value" />
                <span><span class="font-medium text-zinc-100">{{ k.label }}</span><br /><span class="text-xs text-zinc-500">{{ k.hint }}</span></span>
              </label>
            }
          </div>
        </fieldset>
        <div class="grid grid-cols-2 gap-4">
          <div><label class="vf-label" for="cOrder">Orden</label><input id="cOrder" type="number" min="0" class="vf-input" formControlName="sortOrder" /></div>
          <label class="flex items-end gap-2 pb-2 text-sm text-zinc-300"><input type="checkbox" formControlName="active" /> Activa</label>
        </div>
        <div class="vf-modal-actions flex justify-end"><button type="submit" class="vf-btn-primary" [disabled]="saving() || categoryForm.invalid">Guardar</button></div>
      </form>
    </vf-modal>

    <vf-modal [open]="productModal()" size="lg" [title]="editingProduct() ? 'Editar producto' : 'Nuevo producto'" (closed)="productModal.set(false)">
      <form class="space-y-5" [formGroup]="productForm" (ngSubmit)="saveProduct()">
        <div class="grid gap-4 sm:grid-cols-2">
          <div><label class="vf-label" for="pName">Nombre (marca)</label><input id="pName" class="vf-input" formControlName="name" placeholder="Ron Barcelo, Coca-Cola..." /></div>
          <div>
            <label class="vf-label" for="pCategory">Categoria (tipo de bebida)</label>
            <select id="pCategory" class="vf-input" formControlName="categoryId">
              @for (c of categories(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
            </select>
          </div>
        </div>
        <div><label class="vf-label" for="pDesc">Descripcion <span class="text-zinc-600">(se muestra en la carta VIP)</span></label><input id="pDesc" class="vf-input" formControlName="description" maxlength="500" /></div>
        <div><p class="vf-label">Foto <span class="text-zinc-600">opcional</span></p><vf-image-picker formControlName="imageUrl" label="foto del producto" /></div>
        <div class="grid gap-2 sm:grid-cols-2">
          <label class="flex items-start gap-2 text-sm text-zinc-300">
            <input type="checkbox" class="mt-1 accent-brand-500" formControlName="trackStock" />
            <span>Controla existencias <span class="block text-xs text-zinc-500">Desmarcalo en entradas de taquilla o servicios.</span></span>
          </label>
          <div class="text-sm text-zinc-300">
            <label class="flex items-start gap-2">
              <input type="checkbox" class="mt-1 accent-brand-500" formControlName="mixer" />
              <span>Es un refresco para combinados <span class="block text-xs text-zinc-500">Aparece al vender una copa (Coca-Cola, Tonica, Red Bull...). Se descuenta su primera variante (la lata).</span></span>
            </label>
            @if (productForm.controls.mixer.value) {
              <div class="mt-2 ml-6 w-40"><label class="vf-label" for="pSurcharge">Suplemento en la copa</label>
                <input id="pSurcharge" type="number" min="0" step="0.10" class="vf-input" formControlName="mixerSurcharge" /></div>
            }
          </div>
        </div>

        <div>
          <p class="vf-label">Variantes (lo que se vende: botella, copa, chupito...)</p>
          <div class="space-y-3" formArrayName="variants">
            @for (v of variants.controls; track $index; let i = $index) {
              <div class="rounded-xl border border-zinc-800 bg-zinc-950/40 p-3" [formGroupName]="i">
                <div class="grid grid-cols-2 gap-2 sm:grid-cols-[1fr_6rem_6rem_auto]">
                  <div class="col-span-2 sm:col-span-1"><label class="vf-label" [for]="'vName' + i">Nombre</label><input [id]="'vName' + i" class="vf-input" formControlName="name" placeholder="Botella 70 cl, Copa..." /></div>
                  <div><label class="vf-label" [for]="'vPrice' + i">Precio</label><input [id]="'vPrice' + i" class="vf-input" type="number" min="0" step="0.01" formControlName="price" /></div>
                  <div><label class="vf-label" [for]="'vCost' + i">Coste</label><input [id]="'vCost' + i" class="vf-input" type="number" min="0" step="0.01" formControlName="cost" placeholder="—" /></div>
                  <div class="flex items-end gap-2 pb-2">
                    <label class="flex items-center gap-1 text-xs text-zinc-400"><input type="checkbox" class="accent-brand-500" formControlName="active" /> Activa</label>
                    @if (!v.value.id) { <button type="button" class="vf-btn-icon size-7" (click)="variants.removeAt(i)" aria-label="Quitar variante"><vf-icon name="x" class="size-3.5" /></button> }
                  </div>
                </div>
                <div class="mt-2 grid gap-2 sm:grid-cols-[auto_8rem_1fr]">
                  <div><label class="vf-label" [for]="'vUnit' + i">Inventario en</label>
                    <select [id]="'vUnit' + i" class="vf-input" formControlName="stockUnit"><option value="UNIT">Unidades</option><option value="ML">Mililitros (botella)</option></select></div>
                  @if (v.value.stockUnit === 'ML') {
                    <div><label class="vf-label" [for]="'vSize' + i">ml por botella</label><input [id]="'vSize' + i" class="vf-input" type="number" min="1" formControlName="unitSizeMl" placeholder="700" /></div>
                  } @else { <span></span> }
                  <div class="flex flex-col justify-end gap-1 pb-1 text-sm text-zinc-300">
                    <label class="flex items-center gap-2"><input type="checkbox" class="accent-brand-500" formControlName="requiresMixer" /> Se sirve con refresco (copa / combinado)</label>
                    <label class="flex items-center gap-2"><input type="checkbox" class="accent-brand-500" formControlName="returnable" /> Envase retornable (el proveedor se lleva el vacio)</label>
                  </div>
                </div>
                <!-- Receta: que se descuenta al venderla -->
                <div class="mt-3 border-t border-zinc-800 pt-3" formArrayName="recipe">
                  <p class="text-xs font-medium text-zinc-400">Receta <span class="font-normal text-zinc-500">· vacia = se descuenta la propia variante</span></p>
                  @for (r of recipeOf(i).controls; track $index; let j = $index) {
                    <div class="mt-2 flex items-center gap-2" [formGroupName]="j">
                      <select class="vf-input" formControlName="componentVariantId" [attr.aria-label]="'Componente ' + (j + 1)">
                        @for (o of componentOptions(); track o.id) { <option [value]="o.id">{{ o.label }}</option> }
                      </select>
                      <input class="vf-input w-24" type="number" min="1" formControlName="quantity" [attr.aria-label]="'Cantidad ' + (j + 1)" />
                      <span class="w-8 text-xs text-zinc-500">{{ unitOf(r.value.componentVariantId) }}</span>
                      <button type="button" class="vf-btn-icon size-7 shrink-0" (click)="recipeOf(i).removeAt(j)" aria-label="Quitar componente"><vf-icon name="x" class="size-3.5" /></button>
                    </div>
                  }
                  <button type="button" class="vf-btn-ghost vf-btn-sm mt-2" [disabled]="componentOptions().length === 0" (click)="addRecipeLine(i)"><vf-icon name="plus" class="size-3" /> Componente</button>
                  @if (componentOptions().length === 0) { <p class="mt-1 text-xs text-zinc-500">Primero crea las botellas o latas que se usan como componente (con control de existencias).</p> }
                </div>
              </div>
            }
          </div>
          <button type="button" class="vf-btn-ghost vf-btn-sm mt-2" (click)="addVariant()"><vf-icon name="plus" class="size-3" /> Variante</button>
          <p class="mt-2 text-xs text-zinc-500">Ejemplo: "Botella 70 cl" en mililitros (700 ml) y una "Copa" que se sirve con refresco y lleva en su receta 50 ml de esa botella. Al vender una copa se descuentan 50 ml de ron y el refresco elegido.</p>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div><label class="vf-label" for="pOrder">Orden</label><input id="pOrder" type="number" min="0" class="vf-input" formControlName="sortOrder" /></div>
          <label class="flex items-end gap-2 pb-2 text-sm text-zinc-300"><input type="checkbox" class="accent-brand-500" formControlName="active" /> Activo</label>
        </div>
        <div class="vf-modal-actions flex justify-end gap-2">
          <button type="button" class="vf-btn-ghost" (click)="productModal.set(false)">Cancelar</button>
          <button type="submit" class="vf-btn-primary" [disabled]="saving() || productForm.invalid || variants.length === 0">Guardar</button>
        </div>
      </form>
    </vf-modal>
  `,
})
export class PosCatalogPage implements OnInit {
  private readonly posService = inject(PosService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly colors = CATEGORY_COLORS;
  protected readonly colorKeys = Object.keys(CATEGORY_COLORS);
  protected readonly categories = signal<Category[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly categoryModal = signal(false);
  protected readonly productModal = signal(false);
  protected readonly editingCategory = signal<Category | null>(null);
  protected readonly editingProduct = signal<Product | null>(null);
  private readonly byCategory = computed(() => {
    const map = new Map<string, Product[]>();
    this.products().forEach((p) => map.set(p.categoryId, [...(map.get(p.categoryId) ?? []), p]));
    return map;
  });

  protected readonly categoryForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    color: ['violet'],
    sortOrder: [0, Validators.min(0)],
    active: [true],
    kind: ['BAR' as CategoryKind],
  });

  protected readonly kinds = CATEGORY_KINDS;

  protected readonly productForm = this.fb.group({
    categoryId: ['', Validators.required],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: ['', Validators.maxLength(500)],
    imageUrl: ['' as string | null],
    trackStock: [true],
    mixer: [false],
    mixerSurcharge: [0, Validators.min(0)],
    sortOrder: [0, Validators.min(0)],
    active: [true],
    variants: this.fb.array<FormGroup>([]),
  });

  protected get variants(): FormArray<FormGroup> {
    return this.productForm.controls.variants;
  }

  protected recipeOf(i: number): FormArray<FormGroup> {
    return this.variants.at(i).get('recipe') as FormArray<FormGroup>;
  }

  /**
   * Componentes posibles de una receta: variantes guardadas que controlan existencias y no tienen
   * receta propia (botellas, latas, botellines). El backend aplica la misma regla.
   */
  protected readonly componentOptions = computed(() =>
    this.products()
      .filter((p) => p.trackStock)
      .flatMap((p) => p.variants.filter((v) => v.id && v.active && v.recipe.length === 0)
        .map((v) => ({ id: v.id!, label: `${p.name} · ${v.name}`, unit: v.stockUnit }))),
  );

  protected unitOf(variantId: string): string {
    return this.componentOptions().find((o) => o.id === variantId)?.unit === 'ML' ? 'ml' : 'uds.';
  }

  protected recipeText(v: Variant): string {
    return v.recipe.map((r) => `${r.quantity} ${r.unit === 'ML' ? 'ml' : 'ud.'} ${r.componentName ?? ''}`.trim()).join(' + ');
  }

  protected addRecipeLine(i: number): void {
    const first = this.componentOptions()[0];
    if (!first) return;
    this.recipeOf(i).push(this.fb.group({ componentVariantId: [first.id, Validators.required], quantity: [first.unit === 'ML' ? 50 : 1, [Validators.required, Validators.min(1)]] }));
  }

  ngOnInit(): void {
    this.load();
  }

  protected productsOf(categoryId: string): Product[] {
    return this.byCategory().get(categoryId) ?? [];
  }

  protected kindLabel(kind: CategoryKind): string {
    return CATEGORY_KINDS.find((k) => k.value === kind)?.label ?? kind;
  }

  protected openCategory(category: Category | null): void {
    this.editingCategory.set(category);
    this.categoryForm.reset({
      name: category?.name ?? '',
      color: category?.color ?? 'violet',
      sortOrder: category?.sortOrder ?? this.categories().length,
      active: category?.active ?? true,
      kind: category?.kind ?? 'BAR',
    });
    this.categoryModal.set(true);
  }

  protected openProduct(product: Product | null): void {
    this.editingProduct.set(product);
    this.productForm.reset({
      categoryId: product?.categoryId ?? this.categories()[0]?.id ?? '',
      name: product?.name ?? '',
      description: product?.description ?? '',
      imageUrl: product?.imageUrl ?? null,
      trackStock: product?.trackStock ?? true,
      mixer: product ? product.mixerSurcharge !== null : false,
      mixerSurcharge: product?.mixerSurcharge ?? 0,
      sortOrder: product?.sortOrder ?? 0,
      active: product?.active ?? true,
    });
    this.variants.clear();
    (product?.variants ?? [null]).forEach((v) => this.addVariant(v ?? undefined));
    this.productModal.set(true);
  }

  protected addVariant(v?: Variant): void {
    this.variants.push(
      this.fb.group({
        id: [v?.id ?? (null as string | null)],
        name: [v?.name ?? '', [Validators.required, Validators.maxLength(60)]],
        price: [v?.price ?? 0, [Validators.required, Validators.min(0)]],
        cost: [v?.cost ?? (null as number | null), Validators.min(0)],
        active: [v?.active ?? true],
        stockUnit: [v?.stockUnit ?? ('UNIT' as StockUnit)],
        unitSizeMl: [v?.unitSizeMl ?? (null as number | null), Validators.min(1)],
        requiresMixer: [v?.requiresMixer ?? false],
        returnable: [v?.returnable ?? false],
        recipe: this.fb.array<FormGroup>(
          (v?.recipe ?? []).map((r) => this.fb.group({ componentVariantId: [r.componentVariantId, Validators.required], quantity: [r.quantity, [Validators.required, Validators.min(1)]] })),
        ),
      }),
    );
  }

  protected saveCategory(): void {
    this.saving.set(true);
    this.posService.saveCategory(this.editingCategory()?.id ?? null, this.categoryForm.getRawValue()).subscribe({
      next: () => this.done(() => this.categoryModal.set(false)),
      error: (err) => this.fail(err),
    });
  }

  protected saveProduct(): void {
    const v = this.productForm.getRawValue();
    const payload: ProductPayload = {
      categoryId: v.categoryId,
      name: v.name,
      description: v.description.trim() || null,
      imageUrl: v.imageUrl || null,
      trackStock: v.trackStock,
      mixerSurcharge: v.mixer ? Number(v.mixerSurcharge) || 0 : null,
      sortOrder: v.sortOrder,
      active: v.active,
      variants: v.variants.map((variant, index) => ({
        id: variant['id'] ?? null,
        name: variant['name'],
        price: Number(variant['price']),
        sku: null,
        sortOrder: index,
        active: variant['active'],
        cost: variant['cost'] === null || variant['cost'] === '' ? null : Number(variant['cost']),
        stockUnit: variant['stockUnit'],
        unitSizeMl: variant['stockUnit'] === 'ML' ? Number(variant['unitSizeMl']) || null : null,
        requiresMixer: variant['requiresMixer'],
        returnable: variant['returnable'],
        recipe: (variant['recipe'] as { componentVariantId: string; quantity: number }[]).map((r) => ({
          componentVariantId: r.componentVariantId,
          quantity: Number(r.quantity),
        })),
      })),
    };
    this.saving.set(true);
    this.posService.saveProduct(this.editingProduct()?.id ?? null, payload).subscribe({
      next: () => this.done(() => this.productModal.set(false)),
      error: (err) => this.fail(err),
    });
  }

  private done(close: () => void): void {
    this.toast.success('Guardado');
    this.saving.set(false);
    close();
    this.load();
  }

  private fail(err: unknown): void {
    this.toast.error(errorMessage(err));
    this.saving.set(false);
  }

  private load(): void {
    this.posService.categories().subscribe({
      next: (c) => {
        this.categories.set(c);
        this.posService.products().subscribe({
          next: (p) => {
            this.products.set(p);
            this.loading.set(false);
          },
          error: (err) => {
            this.error.set(errorMessage(err));
            this.loading.set(false);
          },
        });
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
