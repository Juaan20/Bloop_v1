package com.bloop.pos.mapper;

import com.bloop.pos.dto.response.CashSessionResponse;
import com.bloop.pos.dto.response.CatalogResponses;
import com.bloop.pos.dto.response.PosSaleResponse;
import com.bloop.pos.entity.CashMovement;
import com.bloop.pos.entity.CashSession;
import com.bloop.pos.entity.PosSale;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductCategory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Conversion de entidades del TPV a DTOs. Los nombres de usuario los resuelve el servicio (mapa id -> nombre). */
@Component
public class PosMapper {

    public CatalogResponses.Category toResponse(ProductCategory c) {
        return new CatalogResponses.Category(c.getId(), c.getName(), c.getColor(), c.getSortOrder(), c.isActive(),
                c.getKind());
    }

    public CatalogResponses.Product toResponse(Product p) {
        var variants = p.getVariants().stream()
                .map(v -> new CatalogResponses.Variant(v.getId(), v.getName(), v.getPrice(), v.getSku(),
                        v.getSortOrder(), v.isActive(), v.getStockUnit(), v.getUnitSizeMl(), v.getCost(),
                        v.isRequiresMixer(), v.getRecipe().stream()
                        .map(r -> new CatalogResponses.RecipeItem(r.getComponent().getId(),
                                r.getComponent().getProduct().getName() + " · " + r.getComponent().getName(),
                                r.getComponent().getStockUnit(), r.getQuantity()))
                        .toList(), v.isReturnable()))
                .toList();
        return new CatalogResponses.Product(p.getId(), p.getCategory().getId(), p.getCategory().getName(), p.getName(),
                p.getDescription(), p.getImageUrl(), p.isTrackStock(), p.getSortOrder(), p.isActive(), variants,
                p.getMixerSurcharge());
    }

    public CatalogResponses.Terminal toResponse(PosTerminal t, String venueName, String warehouseName,
                                                CashSession open, String openedByName) {
        return new CatalogResponses.Terminal(t.getId(), t.getName(), t.getVenueId(), venueName, t.getWarehouseId(),
                warehouseName, t.getType(), t.isActive(), open == null ? null : open.getId(),
                open == null ? null : open.getOpenedAt(), openedByName, t.allowedKindSet());
    }

    public CashSessionResponse toResponse(CashSession s, String terminalName, Map<UUID, String> users,
                                          CashSessionResponse.Summary summary) {
        return new CashSessionResponse(s.getId(), s.getTerminalId(), terminalName, s.getStatus(),
                users.get(s.getOpenedBy()), s.getOpenedAt(), s.getOpeningFloat(), users.get(s.getClosedBy()),
                s.getClosedAt(), s.getCountedCash(), s.getDifference(), s.getClosingNotes(), summary);
    }

    public CashSessionResponse.Movement toResponse(CashMovement m, Map<UUID, String> users) {
        return new CashSessionResponse.Movement(m.getId(), m.getType(), m.getAmount(), m.getReason(),
                users.get(m.getUserId()), m.getCreatedAt());
    }

    public PosSaleResponse toResponse(PosSale s, Map<UUID, String> users) {
        var items = s.getItems().stream()
                .map(i -> new PosSaleResponse.Item(i.getVariantId(), i.getProductName(), i.getVariantName(),
                        i.getQuantity(), i.getUnitPrice(), i.getLineTotal(), i.getMixerName()))
                .toList();
        return new PosSaleResponse(s.getId(), s.getReference(), s.getStatus(), s.getTerminalId(), s.getCashSessionId(),
                s.getSubtotalAmount(), s.getDiscountAmount(), s.getTotalAmount(), s.getPaymentMethod(),
                s.getCashReceived(), s.getChangeGiven(), s.getDiscountReason(),
                users.get(s.getDiscountAuthorizedBy()), users.get(s.getSoldBy()), users.get(s.getVoidedBy()),
                s.getVoidedAt(), s.getVoidReason(), s.getCreatedAt(), items);
    }
}
