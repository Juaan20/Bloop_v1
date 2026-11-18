package com.bloop.notifications.repository;

import com.bloop.notifications.entity.AdminAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AdminAlertRepository extends JpaRepository<AdminAlert, UUID> {

    Page<AdminAlert> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    long countByTenantIdAndReadAtIsNull(UUID tenantId);

    /**
     * Inserta la alerta salvo que ya exista una del mismo tipo para la misma entidad
     * (p. ej. "pocas entradas" de un tipo de entrada solo se avisa una vez).
     * Con ON CONFLICT DO NOTHING no se produce ningun error que deshaga la operacion principal.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into admin_alerts (id, tenant_id, type, title, message, link, entity_id, created_at)
            values (gen_random_uuid(), :tenantId, :type, :title, :message, :link, :entityId, :now)
            on conflict (tenant_id, type, entity_id) where entity_id is not null do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("tenantId") UUID tenantId, @Param("type") String type, @Param("title") String title,
                       @Param("message") String message, @Param("link") String link,
                       @Param("entityId") UUID entityId, @Param("now") Instant now);

    /** Borra la alerta de una entidad (p. ej. al reponer stock) para que pueda volver a avisar. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AdminAlert a where a.tenantId = :tenantId and a.type = :type and a.entityId = :entityId")
    int deleteForEntity(@Param("tenantId") UUID tenantId, @Param("type") com.bloop.notifications.entity.AlertType type,
                        @Param("entityId") UUID entityId);

    @Modifying
    @Query("update AdminAlert a set a.readAt = :now where a.tenantId = :tenantId and a.readAt is null")
    int markAllRead(@Param("tenantId") UUID tenantId, @Param("now") Instant now);
}
