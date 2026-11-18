package com.bloop.reservations.mapper;

import com.bloop.reservations.dto.response.ReservationResponse;
import com.bloop.reservations.dto.response.TableAvailabilityResponse;
import com.bloop.reservations.dto.response.VipZoneResponse;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.entity.VipZone;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class ReservationMapper {

    private final com.bloop.reservations.service.FloorPlanService floorPlans;

    public VipZoneResponse toZoneResponse(VipZone z) {
        var tables = z.getTables().stream()
                .map(t -> new VipZoneResponse.Table(t.getId(), t.getName(), t.getCapacity(), t.getMinSpend(),
                        t.getDepositAmount(), t.isActive(), t.getPosX(), t.getPosY(), t.getWidth(), t.getHeight(),
                        t.getShape(), t.getPackageItems().stream()
                                .map(p -> new VipZoneResponse.PackageItem(p.getVariant().getId(),
                                        variantLabel(p.getVariant()), p.getQuantity(), p.getVariant().getPrice()))
                                .toList()))
                .toList();
        return new VipZoneResponse(z.getId(), z.getVenueId(), z.getName(), z.getDescription(), z.isActive(),
                z.getSortOrder(), tables, z.getCapacity(), z.getColor(), floorPlans.parse(z.getLayout()));
    }

    public TableAvailabilityResponse toAvailability(VipTable t, boolean available) {
        return new TableAvailabilityResponse(t.getId(), t.getZone().getName(), t.getZone().getDescription(),
                t.getName(), t.getCapacity(), t.getMinSpend(), t.getDepositAmount(), available);
    }

    /** venueName lo resuelve el servicio (la reserva solo guarda el id del local). */
    public ReservationResponse toResponse(Reservation r, String venueName) {
        var table = r.getTable();
        var session = r.getSession();
        return new ReservationResponse(r.getId(), r.getReference(), r.getVenueId(), venueName,
                table.getZone().getName(), table.getId(), table.getName(),
                session == null ? null : session.getId(), session == null ? null : session.getEvent().getName(),
                r.getContactName(), r.getContactEmail(), r.getContactPhone(), r.getGuests(), r.getStartsAt(),
                r.getEndsAt(), r.getStatus(), r.getMinSpend(), r.getDepositAmount(), r.getDepositPaidAt(),
                r.isDepositPending(), r.getNotes(), r.getSource(), r.getCustomerId(), r.getCreatedAt());
    }

    /** "Ginebra Premium · Botella" */
    public static String variantLabel(com.bloop.pos.entity.ProductVariant v) {
        return v.getProduct().getName() + " · " + v.getName();
    }
}
