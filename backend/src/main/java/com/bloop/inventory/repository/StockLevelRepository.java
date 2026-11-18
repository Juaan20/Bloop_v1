package com.bloop.inventory.repository;

import com.bloop.inventory.entity.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

    List<StockLevel> findByWarehouseId(UUID warehouseId);

    List<StockLevel> findByWarehouseIdAndVariantIdIn(UUID warehouseId, Collection<UUID> variantIds);

    Optional<StockLevel> findByWarehouseIdAndVariantId(UUID warehouseId, UUID variantId);

    /**
     * Crea la fila de existencias (a 0) si no existe. ON CONFLICT DO NOTHING evita errores
     * si dos peticiones la crean a la vez.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into stock_levels (id, tenant_id, warehouse_id, variant_id, quantity, min_quantity, updated_at)
            values (gen_random_uuid(), :tenantId, :warehouseId, :variantId, 0, 0, :now)
            on conflict (warehouse_id, variant_id) do nothing
            """, nativeQuery = true)
    int insertIfMissing(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId,
                        @Param("variantId") UUID variantId, @Param("now") Instant now);

    /**
     * Resta existencias de forma ATOMICA solo si hay suficientes (mismo patron que el stock de entradas):
     * dos ventas simultaneas de la ultima botella nunca pueden dejar el almacen en negativo.
     *
     * @return 1 si se desconto, 0 si no habia suficientes
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update StockLevel s set s.quantity = s.quantity - :quantity, s.updatedAt = :now
            where s.warehouseId = :warehouseId and s.variantId = :variantId and s.quantity >= :quantity
            """)
    int decrease(@Param("warehouseId") UUID warehouseId, @Param("variantId") UUID variantId,
                 @Param("quantity") int quantity, @Param("now") Instant now);

    /** Venta del TPV: descuenta siempre (nunca bloquea). El stock puede quedar en negativo hasta el recuento. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update StockLevel s set s.quantity = s.quantity - :quantity, s.updatedAt = :now
            where s.warehouseId = :warehouseId and s.variantId = :variantId
            """)
    int decreaseAllowingNegative(@Param("warehouseId") UUID warehouseId, @Param("variantId") UUID variantId,
                                 @Param("quantity") int quantity, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update StockLevel s set s.quantity = s.quantity + :quantity, s.updatedAt = :now
            where s.warehouseId = :warehouseId and s.variantId = :variantId
            """)
    int increase(@Param("warehouseId") UUID warehouseId, @Param("variantId") UUID variantId,
                 @Param("quantity") int quantity, @Param("now") Instant now);

    /** Recuento: deja la cantidad exacta contada. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update StockLevel s set s.quantity = :quantity, s.updatedAt = :now
            where s.warehouseId = :warehouseId and s.variantId = :variantId
            """)
    int setQuantity(@Param("warehouseId") UUID warehouseId, @Param("variantId") UUID variantId,
                    @Param("quantity") int quantity, @Param("now") Instant now);

    /** Cantidad actual leida de la BD (tras un UPDATE atomico). */
    @Query("select s.quantity from StockLevel s where s.warehouseId = :warehouseId and s.variantId = :variantId")
    Optional<Integer> findQuantity(@Param("warehouseId") UUID warehouseId, @Param("variantId") UUID variantId);
}
