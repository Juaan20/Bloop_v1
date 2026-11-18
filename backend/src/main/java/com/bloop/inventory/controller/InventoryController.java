package com.bloop.inventory.controller;

import com.bloop.inventory.dto.request.InventoryRequests;
import com.bloop.inventory.dto.response.InventoryResponses;
import com.bloop.inventory.service.InventoryService;
import com.bloop.shared.response.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Inventario: almacenes, existencias y movimientos. */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final com.bloop.inventory.service.InventoryControlService controlService;

    // ---- Proveedores, traspasos, recuento, control (merma), pedido sugerido y envases

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<InventoryResponses.Supplier> suppliers() {
        return controlService.suppliers();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public InventoryResponses.Supplier createSupplier(@Valid @RequestBody InventoryRequests.SupplierRequest request) {
        return controlService.createSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public InventoryResponses.Supplier updateSupplier(@PathVariable UUID id,
                                                      @Valid @RequestBody InventoryRequests.SupplierRequest request) {
        return controlService.updateSupplier(id, request);
    }

    /** Traspaso entre almacenes (p. ej. almacen -> barra). */
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public void transfer(@Valid @RequestBody InventoryRequests.Transfer request) {
        controlService.transfer(request);
    }

    /** Recuento de cierre de un almacen (todos los productos contados de una vez). */
    @PostMapping("/warehouses/{id}/count")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public List<InventoryResponses.StockItem> count(@PathVariable UUID id,
                                                    @Valid @RequestBody InventoryRequests.Count request) {
        return controlService.count(id, request);
    }

    /** Control: inicio, recibido, enviado, vendido, merma y final de cada producto en un periodo. */
    @GetMapping("/warehouses/{id}/control")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public InventoryResponses.Control control(@PathVariable UUID id,
                                              @RequestParam(required = false) java.time.Instant from,
                                              @RequestParam(required = false) java.time.Instant to) {
        return controlService.control(id, from, to);
    }

    @GetMapping("/warehouses/{id}/suggested-order")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<InventoryResponses.SuggestedLine> suggestedOrder(@PathVariable UUID id) {
        return controlService.suggestedOrder(id);
    }

    /** Envases retornables por proveedor: recibidos, devueltos y pendientes. */
    @GetMapping("/containers")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<InventoryResponses.ContainerBalance> containers() {
        return controlService.containers();
    }

    @PostMapping("/container-returns")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public List<InventoryResponses.ContainerBalance> returnContainers(
            @Valid @RequestBody InventoryRequests.ContainerReturnRequest request) {
        return controlService.returnContainers(request);
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW', 'POS_MANAGE')")
    public List<InventoryResponses.Warehouse> warehouses() {
        return inventoryService.listWarehouses();
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public InventoryResponses.Warehouse createWarehouse(@Valid @RequestBody InventoryRequests.Warehouse request) {
        return inventoryService.createWarehouse(request);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public InventoryResponses.Warehouse updateWarehouse(@PathVariable UUID id,
                                                        @Valid @RequestBody InventoryRequests.Warehouse request) {
        return inventoryService.updateWarehouse(id, request);
    }

    @GetMapping("/warehouses/{id}/stock")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<InventoryResponses.StockItem> stock(@PathVariable UUID id) {
        return inventoryService.stock(id);
    }

    /** Entrada, salida o ajuste (recuento). Devuelve las existencias actualizadas. */
    @PostMapping("/warehouses/{id}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public List<InventoryResponses.StockItem> registerMovement(@PathVariable UUID id,
                                                               @Valid @RequestBody InventoryRequests.Movement request) {
        return inventoryService.registerMovement(id, request);
    }

    @PutMapping("/warehouses/{id}/stock/{variantId}/minimum")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    public List<InventoryResponses.StockItem> setMinimum(@PathVariable UUID id, @PathVariable UUID variantId,
                                                         @Valid @RequestBody InventoryRequests.MinQuantity request) {
        return inventoryService.setMinimum(id, variantId, request);
    }

    @GetMapping("/warehouses/{id}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public PageResponse<InventoryResponses.Movement> movements(@PathVariable UUID id,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        return inventoryService.movements(id, page, size);
    }
}
