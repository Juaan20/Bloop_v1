package com.bloop.customers.repository;

import com.bloop.customers.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findFirstByTenantIdAndEmailIgnoreCaseAndAnonymizedAtIsNull(UUID tenantId, String email);

    /** Fichas del cliente en todas las empresas (zona de privacidad del propio cliente). */
    List<Customer> findByUserIdOrderByCreatedAt(UUID userId);

    /**
     * Crea la ficha del cliente en la empresa SI NO EXISTE, copiando sus datos de la cuenta.
     * "ON CONFLICT DO NOTHING" hace que dos pagos simultaneos del mismo cliente no fallen
     * por la restriccion unica: el segundo simplemente no inserta nada.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into customers (id, tenant_id, user_id, email, first_name, last_name, phone,
                                   created_at, updated_at)
            select gen_random_uuid(), :tenantId, u.id, u.email, u.first_name, u.last_name, u.phone, now(), now()
            from users u where u.id = :userId
            on conflict (tenant_id, user_id) where user_id is not null do nothing
            """, nativeQuery = true)
    int insertIfMissing(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** Ficha con bloqueo de fila: las estadisticas de dos compras simultaneas no se pisan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.tenantId = :tenantId and c.userId = :userId")
    Optional<Customer> findByTenantAndUserForUpdate(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * Listado del CRM. segment vacio = todos; las reglas coinciden con CustomerSegment.
     * Las fichas anonimizadas no aparecen.
     */
    @Query(value = """
            select c from Customer c
            where c.tenantId = :tenantId and c.anonymizedAt is null
              and (:search = '' or lower(c.email) like lower(concat('%', :search, '%'))
                   or lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', :search, '%'))
                   or lower(coalesce(c.tags, '')) like lower(concat('%', :search, '%')))
              and (:segment = ''
                   or (:segment = 'NEW' and c.ordersCount <= 1)
                   or (:segment = 'RECURRING' and c.ordersCount >= 2)
                   or (:segment = 'VIP' and c.totalSpent >= :vipMinSpent)
                   or (:segment = 'INACTIVE' and c.lastPurchaseAt < :inactiveBefore))
              and (:marketingOnly = false or c.marketingConsent = true)
            order by c.lastPurchaseAt desc nulls last, c.createdAt desc
            """,
            countQuery = """
            select count(c) from Customer c
            where c.tenantId = :tenantId and c.anonymizedAt is null
              and (:search = '' or lower(c.email) like lower(concat('%', :search, '%'))
                   or lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', :search, '%'))
                   or lower(coalesce(c.tags, '')) like lower(concat('%', :search, '%')))
              and (:segment = ''
                   or (:segment = 'NEW' and c.ordersCount <= 1)
                   or (:segment = 'RECURRING' and c.ordersCount >= 2)
                   or (:segment = 'VIP' and c.totalSpent >= :vipMinSpent)
                   or (:segment = 'INACTIVE' and c.lastPurchaseAt < :inactiveBefore))
              and (:marketingOnly = false or c.marketingConsent = true)
            """)
    Page<Customer> search(@Param("tenantId") UUID tenantId, @Param("search") String search,
                          @Param("segment") String segment, @Param("vipMinSpent") BigDecimal vipMinSpent,
                          @Param("inactiveBefore") Instant inactiveBefore,
                          @Param("marketingOnly") boolean marketingOnly, Pageable pageable);
}
