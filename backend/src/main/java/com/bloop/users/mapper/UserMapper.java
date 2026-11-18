package com.bloop.users.mapper;

import com.bloop.users.dto.response.RoleResponse;
import com.bloop.users.dto.response.UserResponse;
import com.bloop.users.entity.Permission;
import com.bloop.users.entity.Role;
import com.bloop.users.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getStatus(), user.getRoleCodes(), Set.copyOf(user.getVenueIds()),
                user.getLastLoginAt(), user.getCreatedAt());
    }

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getCode(), role.getName(), role.getDescription(),
                role.getPermissions().stream().map(Permission::getCode).sorted().toList());
    }
}
