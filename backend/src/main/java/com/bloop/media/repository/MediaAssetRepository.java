package com.bloop.media.repository;

import com.bloop.media.entity.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Optional<MediaAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MediaAsset> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndUrl(UUID tenantId, String url);

    Optional<MediaAsset> findByProviderAndStorageKey(com.bloop.media.entity.MediaProvider provider, String key);

    /**
     * Donde se usa una imagen dentro de la empresa (eventos, dias de evento, productos y locales).
     * Antes de borrarla se comprueba que no aparezca en ninguno.
     */
    @Query(value = """
            select (select count(*) from events where tenant_id = :tenantId and image_url = :url)
                 + (select count(*) from event_sessions where tenant_id = :tenantId and image_url = :url)
                 + (select count(*) from products where tenant_id = :tenantId and image_url = :url)
                 + (select count(*) from venues where tenant_id = :tenantId and image_url = :url)
            """, nativeQuery = true)
    long countUsages(@Param("tenantId") UUID tenantId, @Param("url") String url);
}
