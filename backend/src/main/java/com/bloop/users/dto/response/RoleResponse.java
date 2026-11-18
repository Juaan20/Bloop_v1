package com.bloop.users.dto.response;

import java.util.List;

public record RoleResponse(String code, String name, String description, List<String> permissions) {
}
