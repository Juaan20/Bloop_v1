package com.bloop.inventory.repository;

import com.bloop.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId, Pageable pageable);

    java.util.List<StockMovement> findBySaleIdAndType(UUID saleId, com.bloop.inventory.entity.StockMovementType type);

    /** Suma de movimientos por producto y tipo en un almacen y periodo: [variantId, type, suma]. */
    @org.springframework.data.jpa.repository.Query("""
            select m.variantId, m.type, sum(m.quantity) from StockMovement m
            where m.warehouseId = :warehouseId and m.createdAt >= :from and m.createdAt < :to
            group by m.variantId, m.type
            """)
    java.util.List<Object[]> sumByVariantAndType(
            @org.springframework.data.repository.query.Param("warehouseId") UUID warehouseId,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to);

    /** Ultimo proveedor de cada producto: [variantId, supplierId]. */
    @org.springframework.data.jpa.repository.Query(value = """
            select distinct on (variant_id) variant_id, supplier_id from stock_movements
            where tenant_id = :tenantId and supplier_id is not null
            order by variant_id, created_at desc
            """, nativeQuery = true)
    java.util.List<Object[]> lastSupplierByVariant(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);

    /** Lo que ha traido cada proveedor de cada producto (en su unidad de inventario): [supplierId, variantId, suma]. */
    @org.springframework.data.jpa.repository.Query("""
            select m.supplierId, m.variantId, sum(m.quantity) from StockMovement m
            where m.tenantId = :tenantId and m.supplierId is not null
              and m.type = com.bloop.inventory.entity.StockMovementType.IN
            group by m.supplierId, m.variantId
            """)
    java.util.List<Object[]> sumReceivedBySupplier(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);

    /** Fecha del ultimo recuento de un almacen antes de "before" (para el periodo por defecto del control). */
    @org.springframework.data.jpa.repository.Query("""
            select max(m.createdAt) from StockMovement m
            where m.warehouseId = :warehouseId and m.createdAt < :before
              and m.type = com.bloop.inventory.entity.StockMovementType.ADJUSTMENT
            """)
    java.time.Instant lastCountBefore(
            @org.springframework.data.repository.query.Param("warehouseId") UUID warehouseId,
            @org.springframework.data.repository.query.Param("before") java.time.Instant before);
}
