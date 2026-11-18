package com.bloop.users.repository;

import com.bloop.users.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Busca un usuario SOLO dentro del tenant indicado (aislamiento multi-tenant). */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select u from User u
            where u.tenantId = :tenantId
              and (:search = ''
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%', :search, '%')))
            """)
    Page<User> searchByTenant(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);

    long countByTenantId(UUID tenantId);

    /** Empleados de la empresa que tienen un rol concreto (p. ej. PROMOTER). */
    @Query("""
            select u from User u join u.roles r
            where u.tenantId = :tenantId and r.code = :roleCode
            order by u.firstName, u.lastName
            """)
    java.util.List<User> findByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("roleCode") String roleCode);
}
