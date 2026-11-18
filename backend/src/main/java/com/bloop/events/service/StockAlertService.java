package com.bloop.events.service;

import com.bloop.events.entity.TicketType;
import com.bloop.events.repository.TicketStock;
import com.bloop.events.repository.TicketTypeRepository;
import com.bloop.notifications.entity.AlertType;
import com.bloop.notifications.service.AdminAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisa al equipo cuando un tipo de entrada se esta agotando (90 %) o se ha agotado.
 * Se llama justo despues de reservar stock en el checkout.
 */
@Service
@RequiredArgsConstructor
public class StockAlertService {

    static final int LOW_STOCK_PERCENT = 90;

    private final TicketTypeRepository ticketTypeRepository;
    private final AdminAlertService alertService;

    @Transactional
    public void checkStock(TicketType type) {
        // Leemos las cantidades de la BD: el UPDATE atomico de reserveStock no actualiza la entidad en memoria.
        TicketStock stock = ticketTypeRepository.findStock(type.getId()).orElse(null);
        if (stock == null || stock.quantityTotal() <= 0) {
            return;
        }
        var event = type.getSession().getEvent();
        String link = "/admin/eventos/" + event.getId();
        String label = "'" + type.getName() + "' de " + event.getName();
        if (stock.quantitySold() >= stock.quantityTotal()) {
            alertService.raise(type.getTenantId(), AlertType.SOLD_OUT, "Entradas agotadas",
                    label + " se ha agotado.", link, type.getId());
        } else if (stock.quantitySold() * 100L >= (long) stock.quantityTotal() * LOW_STOCK_PERCENT) {
            alertService.raise(type.getTenantId(), AlertType.LOW_STOCK, "Quedan pocas entradas",
                    label + ": vendidas " + stock.quantitySold() + " de " + stock.quantityTotal() + ".",
                    link, type.getId());
        }
    }
}
