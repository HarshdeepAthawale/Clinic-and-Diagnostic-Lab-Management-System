package com.cdlms.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Paginated list shape from Docs/API.md: {@code { content, page, size, totalElements }}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
