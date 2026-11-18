package com.bloop.events.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fecha concreta de un evento, con su aforo y sus tipos de entrada. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "event_sessions")
public class EventSession extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "doors_open_at")
    private Instant doorsOpenAt;

    @Column(nullable = false)
    private int capacity;

    /** Titulo propio del dia ("Viernes - Techno"). Si es null se usa el nombre del evento. */
    private String title;

    private String description;

    /** Cartel especifico de este dia. Si es null se usa la imagen general del evento. */
    @Column(name = "image_url")
    private String imageUrl;

    /** Venta pausada solo para este dia (los tipos de entrada no se tocan). */
    @Column(name = "sales_paused", nullable = false)
    private boolean salesPaused;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, price ASC")
    @BatchSize(size = 50)
    private List<TicketType> ticketTypes = new ArrayList<>();

    public void addTicketType(TicketType ticketType) {
        ticketType.setSession(this);
        ticketType.setTenantId(tenantId);
        ticketTypes.add(ticketType);
    }

    public int getTicketsSold() {
        return ticketTypes.stream().mapToInt(TicketType::getQuantitySold).sum();
    }

    public int getTicketsConfigured() {
        return ticketTypes.stream().mapToInt(TicketType::getQuantityTotal).sum();
    }

    public boolean isUpcoming(Instant now) {
        return status == SessionStatus.SCHEDULED && endsAt.isAfter(now);
    }
}
