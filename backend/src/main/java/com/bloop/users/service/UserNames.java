package com.bloop.users.service;

import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Traduce ids de usuario a nombres para mostrarlos (quien vendio, quien cerro la caja...). */
@Component
@RequiredArgsConstructor
public class UserNames {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<UUID, String> of(Collection<UUID> ids) {
        var distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }
}
