package com.bloop.orders.service;

import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.entity.TicketCategory;
import com.bloop.events.entity.TicketType;
import com.bloop.events.repository.TicketTypeRepository;
import com.bloop.events.service.StockAlertService;
import com.bloop.promoters.service.PromoCodeService;
import com.bloop.promoters.service.PromoterService;
import com.bloop.orders.dto.request.CheckoutItemRequest;
import com.bloop.orders.dto.request.CheckoutRequest;
import com.bloop.orders.mapper.OrderMapper;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.service.PaymentService;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.venues.entity.Venue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test unitario con Mockito: reglas del checkout sin base de datos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:00:00Z");

    @Mock TicketOrderRepository orderRepository;
    @Mock TicketTypeRepository ticketTypeRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @Mock OrderPaymentService orderPaymentService;
    @Mock PromoCodeService promoCodeService;
    @Mock PromoterService promoterService;
    @Mock StockAlertService stockAlertService;
    @Mock CurrentUserProvider currentUserProvider;

    private CheckoutService service;
    private final UUID customerId = UUID.randomUUID();
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        var properties = new BloopProperties(null, null, new BloopProperties.Orders(15),
                new BloopProperties.Payments(true, null), null, null, null, null);
        service = new CheckoutService(orderRepository, ticketTypeRepository, userRepository, paymentService,
                orderPaymentService, promoCodeService, promoterService, stockAlertService, new OrderMapper(),
                currentUserProvider, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(currentUserProvider.get()).thenReturn(new CurrentUser(customerId, "c@test.local", null,
                Set.of("CUSTOMER"), Set.of()));
        when(orderRepository.findByCustomerUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        User buyer = new User();
        buyer.setEmail("c@test.local");
        buyer.setFirstName("C");
        buyer.setLastName("T");
        when(userRepository.findById(customerId)).thenReturn(Optional.of(buyer));
        ticketType = publishedTicketType();
        when(ticketTypeRepository.findAllWithSessionByIdIn(any())).thenReturn(List.of(ticketType));
    }

    @Test
    void rejectsMoreThanMaxPerOrderWithoutTouchingStock() {
        var request = new CheckoutRequest(ticketType.getSession().getId(),
                List.of(new CheckoutItemRequest(ticketType.getId(), 5)), null, null, false, null, null);

        assertThatThrownBy(() -> service.checkout(request, "key-12345678"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Maximo 4");
        verify(ticketTypeRepository, never()).reserveStock(any(), anyInt());
    }

    @Test
    void conflictWhenAtomicReservationFails() {
        when(ticketTypeRepository.reserveStock(ticketType.getId(), 2)).thenReturn(0);
        var request = new CheckoutRequest(ticketType.getSession().getId(),
                List.of(new CheckoutItemRequest(ticketType.getId(), 2)), null, null, false, null, null);

        assertThatThrownBy(() -> service.checkout(request, "key-12345678"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("No quedan suficientes");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidIdempotencyKey() {
        var request = new CheckoutRequest(UUID.randomUUID(), List.of(new CheckoutItemRequest(UUID.randomUUID(), 1)),
                null, null, false, null, null);
        assertThatThrownBy(() -> service.checkout(request, "x"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Idempotency-Key");
    }

    @Test
    void rejectsDraftEvents() {
        ticketType.getSession().getEvent().setStatus(EventStatus.DRAFT);
        var request = new CheckoutRequest(ticketType.getSession().getId(),
                List.of(new CheckoutItemRequest(ticketType.getId(), 1)), null, null, false, null, null);
        assertThatThrownBy(() -> service.checkout(request, "key-12345678"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("no esta a la venta");
    }

    private static TicketType publishedTicketType() {
        Venue venue = new Venue();
        Event event = new Event();
        event.setStatus(EventStatus.PUBLISHED);
        event.setVenue(venue);
        EventSession session = new EventSession();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.setStartsAt(NOW.plus(1, ChronoUnit.DAYS));
        session.setEndsAt(NOW.plus(1, ChronoUnit.DAYS).plus(6, ChronoUnit.HOURS));
        event.addSession(session);
        TicketType type = new TicketType();
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        type.setName("General");
        type.setCategory(TicketCategory.GENERAL);
        type.setPrice(new BigDecimal("10.00"));
        type.setQuantityTotal(100);
        type.setMaxPerOrder(4);
        session.addTicketType(type);
        return type;
    }
}
