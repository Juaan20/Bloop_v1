package com.bloop.pos.controller;

import com.bloop.pos.dto.request.CategoryRequest;
import com.bloop.pos.dto.request.ProductRequest;
import com.bloop.pos.dto.response.CatalogResponses;
import com.bloop.pos.service.PosCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Catalogo del TPV: categorias, productos y variantes (POS_MANAGE). */
@RestController
@RequestMapping("/api/pos")
@PreAuthorize("hasAuthority('POS_MANAGE')")
@RequiredArgsConstructor
public class PosCatalogController {

    private final PosCatalogService catalogService;

    @GetMapping("/categories")
    public List<CatalogResponses.Category> categories() {
        return catalogService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponses.Category createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CatalogResponses.Category updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @GetMapping("/products")
    public List<CatalogResponses.Product> products() {
        return catalogService.listProducts();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponses.Product createProduct(@Valid @RequestBody ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public CatalogResponses.Product updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }
}
