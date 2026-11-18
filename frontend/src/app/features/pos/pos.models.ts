import { DiscountType } from '../promoters/promoter.models';

export type TerminalType = 'BAR' | 'BOX_OFFICE' | 'VIP_SERVICE';

/** Mesa ocupada a la que un terminal de servicio VIP puede cargar pedidos. */
export interface VipOccupiedTable {
  reservationId: string;
  tableName: string;
  zoneName: string;
  contactName: string;
  guests: number;
}
export type PaymentMethod = 'CASH' | 'CARD';
export type CashMovementType = 'CASH_IN' | 'CASH_OUT';
export type StockMovementType = 'IN' | 'OUT' | 'ADJUSTMENT' | 'SALE' | 'SALE_VOID';

/** Colores de los botones de categoria del TPV (deben coincidir con CategoryRequest en el backend). */
export const CATEGORY_COLORS: Record<string, string> = {
  violet: 'bg-violet-600 hover:bg-violet-500',
  amber: 'bg-amber-600 hover:bg-amber-500',
  emerald: 'bg-emerald-600 hover:bg-emerald-500',
  sky: 'bg-sky-600 hover:bg-sky-500',
  rose: 'bg-rose-600 hover:bg-rose-500',
  zinc: 'bg-zinc-600 hover:bg-zinc-500',
};

/** Tipo de categoria: decide en que terminales y en que carta aparece. */
export type CategoryKind = 'BAR' | 'VIP_BOTTLE' | 'SERVICE' | 'OTHER' | 'SUPPLY';

export const CATEGORY_KINDS: { value: CategoryKind; label: string; hint: string }[] = [
  { value: 'BAR', label: 'Barra', hint: 'Consumo rapido: refrescos, agua, cerveza, copas' },
  { value: 'VIP_BOTTLE', label: 'Botellas VIP', hint: 'Carta de las mesas VIP y paquetes' },
  { value: 'SERVICE', label: 'Servicio', hint: 'Hielo, refrescos para mesa, bengalas...' },
  { value: 'OTHER', label: 'Otros', hint: 'Taquilla, guardarropa, merchandising' },
  { value: 'SUPPLY', label: 'Solo inventario', hint: 'Botellas de las que se sirven combinados y chupitos (no sale en el TPV)' },
];

export interface Category {
  id: string;
  name: string;
  color: string;
  sortOrder: number;
  active: boolean;
  kind: CategoryKind;
}

/** UNIT = se cuenta en unidades (latas, botellines). ML = en mililitros (botellas de las que se sirven copas). */
export type StockUnit = 'UNIT' | 'ML';

/** Linea de receta: lo que se descuenta al vender la variante (ml de una botella, una lata...). */
export interface RecipeItem {
  componentVariantId: string;
  componentName?: string;
  unit?: StockUnit;
  quantity: number;
}

export interface Variant {
  id: string | null;
  name: string;
  price: number;
  sku: string | null;
  sortOrder: number;
  active: boolean;
  stockUnit: StockUnit;
  unitSizeMl: number | null;
  cost: number | null;
  /** Al venderla el TPV pide elegir el refresco (copa / combinado). */
  requiresMixer: boolean;
  recipe: RecipeItem[];
  /** Envase retornable: el proveedor se lleva el vacio. */
  returnable: boolean;
}

export interface Product {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  trackStock: boolean;
  sortOrder: number;
  active: boolean;
  variants: Variant[];
  /** Suplemento como refresco de un combinado (0 = sin suplemento). null = no es refresco de combinado. */
  mixerSurcharge: number | null;
}

export type ProductPayload = Omit<Product, 'id' | 'categoryName'>;

/** "1.400 ml (2 botellas)" o "12 uds." segun la unidad de inventario. */
export function formatStock(quantity: number, unit: StockUnit, unitSizeMl: number | null): string {
  if (unit === 'ML' && unitSizeMl) {
    const bottles = quantity / unitSizeMl;
    return `${bottles.toLocaleString('es-ES', { maximumFractionDigits: 1 })} bot. (${quantity.toLocaleString('es-ES')} ml)`;
  }
  return `${quantity.toLocaleString('es-ES')} uds.`;
}

export interface Terminal {
  id: string;
  name: string;
  venueId: string;
  venueName: string;
  warehouseId: string;
  warehouseName: string;
  type: TerminalType;
  active: boolean;
  openSessionId: string | null;
  openSessionSince: string | null;
  openedByName: string | null;
  allowedKinds: CategoryKind[];
}

export interface CashSession {
  id: string;
  terminalId: string;
  terminalName: string;
  status: 'OPEN' | 'CLOSED';
  openedByName: string;
  openedAt: string;
  openingFloat: number;
  closedByName: string | null;
  closedAt: string | null;
  countedCash: number | null;
  difference: number | null;
  closingNotes: string | null;
  summary: {
    cashSales: number;
    cardSales: number;
    totalSales: number;
    discounts: number;
    cashIn: number;
    cashOut: number;
    expectedCash: number;
    salesCount: number;
    voidedCount: number;
    topProducts: { productName: string; variantName: string; quantity: number; total: number }[];
    movements: { id: string; type: CashMovementType; amount: number; reason: string; userName: string; createdAt: string }[];
  };
}

/** Pantalla tactil: terminal, caja abierta y catalogo vendible con existencias. */
export interface PosScreen {
  terminal: Terminal;
  session: CashSession | null;
  categories: {
    id: string;
    name: string;
    color: string;
    kind: CategoryKind;
    products: { id: string; name: string; trackStock: boolean; variants: PosVariant[] }[];
  }[];
  /** Refrescos que se pueden elegir para un combinado; surcharge se suma al precio de la copa. */
  mixers: PosMixer[];
}

/** stock: raciones que se pueden servir con las existencias del almacen (null = sin control). */
export interface PosVariant {
  id: string;
  name: string;
  price: number;
  stock: number | null;
  requiresMixer: boolean;
}

export interface PosMixer {
  variantId: string;
  name: string;
  surcharge: number;
  stock: number | null;
}

export interface PosSale {
  id: string;
  reference: string;
  status: 'COMPLETED' | 'VOIDED';
  terminalId: string;
  cashSessionId: string;
  subtotalAmount: number;
  discountAmount: number;
  totalAmount: number;
  paymentMethod: PaymentMethod;
  cashReceived: number | null;
  changeGiven: number | null;
  discountReason: string | null;
  discountAuthorizedByName: string | null;
  soldByName: string;
  voidedByName: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
  items: { variantId: string; productName: string; variantName: string; quantity: number; unitPrice: number; lineTotal: number; mixerName: string | null }[];
}

export interface SalePayload {
  items: { variantId: string; quantity: number; mixerVariantId?: string | null }[];
  paymentMethod: PaymentMethod;
  cashReceived: number | null;
  discount: {
    type: DiscountType;
    value: number;
    reason: string;
    supervisorEmail: string | null;
    supervisorPassword: string | null;
  } | null;
}

export interface Warehouse {
  id: string;
  venueId: string;
  venueName: string;
  name: string;
  active: boolean;
}

export interface StockItem {
  variantId: string;
  categoryName: string;
  productName: string;
  variantName: string;
  sku: string | null;
  variantActive: boolean;
  /** En la unidad de inventario: unidades o ml. */
  quantity: number;
  minQuantity: number;
  low: boolean;
  stockUnit: StockUnit;
  unitSizeMl: number | null;
  cost: number | null;
}

export interface StockMovement {
  id: string;
  variantId: string;
  productName: string;
  variantName: string;
  type: StockMovementType;
  quantity: number;
  reason: string | null;
  saleId: string | null;
  userName: string | null;
  createdAt: string;
}
