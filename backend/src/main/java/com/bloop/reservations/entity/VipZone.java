package com.bloop.reservations.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sala de un local (Sala principal, Terraza, Sala privada...). Tiene su propio plano y sus mesas.
 * En el codigo se sigue llamando "zona" (VipZone) por compatibilidad con la API.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vip_zones")
public class VipZone extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "venue_id", nullable = false, updatable = false)
    private UUID venueId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Forma de la sala (JSON de FloorPlanRequest: rejilla pintada + elementos). null = sin disenar. */
    private String layout;

    /** Aforo de la sala (informativo). */
    private Integer capacity;

    /** Color con el que se distingue la sala en el selector (violet, sky, emerald, amber, rose, zinc). */
    @Column(nullable = false)
    private String color = "violet";

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL)
    @OrderBy("name ASC")
    @BatchSize(size = 50)
    private List<VipTable> tables = new ArrayList<>();

    public void addTable(VipTable table) {
        table.setZone(this);
        table.setTenantId(tenantId);
        tables.add(table);
    }
}
