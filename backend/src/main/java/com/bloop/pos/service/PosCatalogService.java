package com.bloop.pos.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.pos.dto.request.CategoryRequest;
import com.bloop.pos.dto.request.ProductRequest;
import com.bloop.pos.dto.response.CatalogResponses;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductCategory;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.StockUnit;
import com.bloop.pos.entity.VariantRecipeItem;
import com.bloop.pos.repository.ProductVariantRepository;
import com.bloop.pos.mapper.PosMapper;
import com.bloop.pos.repository.ProductCategoryRepository;
import com.bloop.pos.repository.ProductRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogo del TPV: categorias, productos y variantes.
 * Nada se borra (hay ventas y existencias que lo referencian): se desactiva.
 */
@Service
@RequiredArgsConstructor
public class PosCatalogService {

    private final ProductVariantRepository variantRepository;
    private final com.bloop.media.service.MediaUrlValidator mediaUrls;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PosMapper posMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CatalogResponses.Category> listCategories() {
        return categoryRepository.findByTenantIdOrderBySortOrderAscNameAsc(currentUserProvider.requireTenantId())
                .stream().map(posMapper::toResponse).toList();
    }

    @Transactional
    public CatalogResponses.Category createCategory(CategoryRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        if (categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, request.name().trim())) {
            throw new ConflictException("Ya existe la categoria '" + request.name().trim() + "'");
        }
        ProductCategory category = new ProductCategory();
        category.setTenantId(tenantId);
        apply(category, request);
        category = categoryRepository.save(category);
        auditService.record(AuditAction.POS_CATALOG_CHANGED, "ProductCategory", category.getId(), category.getName());
        return posMapper.toResponse(category);
    }

    @Transactional
    public CatalogResponses.Category updateCategory(UUID id, CategoryRequest request) {
        ProductCategory category = findCategory(id);
        boolean renamed = !category.getName().equalsIgnoreCase(request.name().trim());
        if (renamed && categoryRepository.existsByTenantIdAndNameIgnoreCase(category.getTenantId(), request.name().trim())) {
            throw new ConflictException("Ya existe la categoria '" + request.name().trim() + "'");
        }
        apply(category, request);
        auditService.record(AuditAction.POS_CATALOG_CHANGED, "ProductCategory", id, category.getName()
                + " activa=" + category.isActive());
        return posMapper.toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CatalogResponses.Product> listProducts() {
        return productRepository.findAllOfTenant(currentUserProvider.requireTenantId()).stream()
                .map(posMapper::toResponse).toList();
    }

    @Transactional
    public CatalogResponses.Product createProduct(ProductRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Product product = new Product();
        product.setTenantId(tenantId);
        applyProduct(product, request);
        for (ProductRequest.Variant v : request.variants()) {
            if (v.id() != null) {
                throw new BadRequestException("Un producto nuevo no puede tener variantes con id");
            }
            ProductVariant variant = new ProductVariant();
            product.addVariant(variant);
            applyVariant(variant, v);
        }
        product = productRepository.save(product);
        auditService.record(AuditAction.POS_CATALOG_CHANGED, "Product", product.getId(), product.getName());
        return posMapper.toResponse(product);
    }

    @Transactional
    public CatalogResponses.Product updateProduct(UUID id, ProductRequest request) {
        Product product = productRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Producto"));
        applyProduct(product, request);
        // Se guardan los objetos (no los ids): una consulta de la receta puede hacer flush y dar id
        // a las variantes nuevas antes de terminar el bucle.
        Set<ProductVariant> keep = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<ProductVariant> existing = List.copyOf(product.getVariants());
        for (ProductRequest.Variant v : request.variants()) {
            if (v.id() == null) {
                ProductVariant variant = new ProductVariant();
                product.addVariant(variant);
                applyVariant(variant, v);
                keep.add(variant);
            } else {
                ProductVariant variant = existing.stream().filter(x -> x.getId().equals(v.id()))
                        .findFirst().orElseThrow(() -> NotFoundException.of("Variante"));
                applyVariant(variant, v);
                keep.add(variant);
            }
        }
        // Las variantes que ya no vienen en la peticion se desactivan (pueden tener ventas).
        existing.stream().filter(v -> !keep.contains(v)).forEach(v -> v.setActive(false));
        // flush (no save): el producto ya esta gestionado; las variantes nuevas se persisten en cascada.
        productRepository.flush();
        auditService.record(AuditAction.POS_CATALOG_CHANGED, "Product", id, product.getName() + " activo="
                + product.isActive());
        return posMapper.toResponse(product);
    }

    private ProductCategory findCategory(UUID id) {
        return categoryRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Categoria"));
    }

    private void applyProduct(Product product, ProductRequest r) {
        product.setCategory(findCategory(r.categoryId()));
        product.setName(r.name().trim());
        product.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        product.setImageUrl(mediaUrls.requireOwnUrl(r.imageUrl(), product.getImageUrl()));
        product.setTrackStock(r.trackStock());
        product.setMixerSurcharge(r.mixerSurcharge());
        product.setSortOrder(r.sortOrder());
        product.setActive(r.active());
    }

    private static void apply(ProductCategory category, CategoryRequest r) {
        category.setName(r.name().trim());
        category.setColor(r.color() == null || r.color().isBlank() ? "violet" : r.color());
        category.setSortOrder(r.sortOrder());
        category.setActive(r.active());
        category.setKind(r.kind() == null ? com.bloop.pos.entity.CategoryKind.BAR : r.kind());
    }

    private void applyVariant(ProductVariant variant, ProductRequest.Variant v) {
        variant.setName(v.name().trim());
        variant.setPrice(v.price());
        variant.setSku(v.sku() == null || v.sku().isBlank() ? null : v.sku().trim());
        variant.setSortOrder(v.sortOrder());
        variant.setActive(v.active());
        StockUnit unit = v.stockUnit() == null ? StockUnit.UNIT : v.stockUnit();
        if (unit == StockUnit.ML && v.unitSizeMl() == null) {
            throw new BadRequestException("Indica los ml de la botella de " + v.name().trim());
        }
        variant.setStockUnit(unit);
        variant.setUnitSizeMl(unit == StockUnit.ML ? v.unitSizeMl() : null);
        variant.setCost(v.cost());
        variant.setRequiresMixer(Boolean.TRUE.equals(v.requiresMixer()));
        variant.setReturnable(Boolean.TRUE.equals(v.returnable()));
        applyRecipe(variant, v.recipe() == null ? List.of() : v.recipe());
    }

    /**
     * Sustituye la receta de una variante. Los componentes deben ser de la misma empresa, contar
     * existencias y no tener receta propia (un nivel: copa -> botella, nunca copa -> copa).
     */
    private void applyRecipe(ProductVariant variant, List<ProductRequest.RecipeItem> items) {
        variant.getRecipe().clear();
        if (items.isEmpty()) {
            return;
        }
        UUID tenantId = currentUserProvider.requireTenantId();
        Set<UUID> ids = new HashSet<>();
        items.forEach(i -> {
            if (!ids.add(i.componentVariantId())) {
                throw new BadRequestException("Un componente aparece dos veces en la receta");
            }
        });
        Map<UUID, ProductVariant> components = new HashMap<>();
        variantRepository.findAllWithProduct(ids, tenantId).forEach(c -> components.put(c.getId(), c));
        for (ProductRequest.RecipeItem item : items) {
            ProductVariant component = components.get(item.componentVariantId());
            if (component == null) {
                throw NotFoundException.of("Componente de la receta");
            }
            if (component == variant || (variant.getId() != null && variant.getId().equals(component.getId()))) {
                throw new BadRequestException("Una variante no puede ser componente de si misma");
            }
            if (!component.getRecipe().isEmpty()) {
                throw new BadRequestException(component.getProduct().getName() + " ya tiene receta: usa la botella "
                        + "o la lata como componente");
            }
            if (!component.getProduct().isTrackStock()) {
                throw new BadRequestException(component.getProduct().getName() + " no controla existencias");
            }
            VariantRecipeItem line = new VariantRecipeItem();
            line.setTenantId(tenantId);
            line.setVariant(variant);
            line.setComponent(component);
            line.setQuantity(item.quantity());
            variant.getRecipe().add(line);
        }
    }
}
