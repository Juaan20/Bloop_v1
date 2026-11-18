package com.bloop.users.repository;

import com.bloop.users.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, String> {

    List<Role> findByAssignableByTenantTrueOrderByCode();
}
