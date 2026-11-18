package com.bloop.customers.mapper;

import com.bloop.customers.dto.response.CustomerDetailResponse;
import com.bloop.customers.dto.response.CustomerResponse;
import com.bloop.customers.entity.Customer;
import com.bloop.customers.entity.CustomerConsent;
import com.bloop.customers.entity.CustomerSegment;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.reservations.entity.Reservation;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer c, Instant now) {
        return new CustomerResponse(c.getId(), c.getEmail(), c.getFirstName(), c.getLastName(), c.getPhone(),
                c.getTags(), c.isMarketingConsent(), c.getOrdersCount(), c.getTicketsCount(), c.getTotalSpent(),
                c.getFirstPurchaseAt(), c.getLastPurchaseAt(), CustomerSegment.of(c, now), c.getUserId() != null,
                c.getCreatedAt());
    }

    public CustomerDetailResponse.OrderSummary toOrderSummary(TicketOrder o) {
        var session = o.getSession();
        return new CustomerDetailResponse.OrderSummary(o.getId(), o.getReference(), session.getEvent().getName(),
                session.getStartsAt(), o.getStatus(), o.getTicketCount(), o.getTotalAmount(), o.getCurrency(),
                o.getCreatedAt());
    }

    public CustomerDetailResponse.ReservationSummary toReservationSummary(Reservation r) {
        return new CustomerDetailResponse.ReservationSummary(r.getId(), r.getReference(),
                r.getTable().getZone().getName(), r.getTable().getName(), r.getStartsAt(), r.getGuests(),
                r.getStatus());
    }

    public CustomerDetailResponse.ConsentInfo toConsent(CustomerConsent c) {
        return new CustomerDetailResponse.ConsentInfo(c.getType(), c.isGranted(), c.getSource(), c.getCreatedAt());
    }
}
