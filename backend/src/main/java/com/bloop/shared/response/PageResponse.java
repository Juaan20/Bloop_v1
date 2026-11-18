package com.bloop.shared.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Pagina de resultados con un formato JSON estable (no exponemos el Page de Spring). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
