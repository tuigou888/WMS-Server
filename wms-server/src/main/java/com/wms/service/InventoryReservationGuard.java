package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.model.entity.Inventory;
import com.wms.model.entity.market.InventoryReservation;
import com.wms.repository.InventoryRepository;
import com.wms.repository.market.InventoryReservationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Locks physical lots before reservation rows so all stock-reducing flows use one lock order. */
@Service
public class InventoryReservationGuard {
    private final InventoryRepository inventories;
    private final InventoryReservationRepository reservations;

    public InventoryReservationGuard(InventoryRepository inventories, InventoryReservationRepository reservations) {
        this.inventories = inventories;
        this.reservations = reservations;
    }

    public Snapshot lock(Long itemId, Long warehouseId) {
        List<Inventory> lots = inventories.findFifoForOut(itemId, warehouseId);
        List<InventoryReservation> heldRows = reservations.findHeldForItemWarehouseForUpdate(itemId, warehouseId, java.time.LocalDateTime.now());
        BigDecimal physical = lots.stream().map(Inventory::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal held = heldRows.stream().map(InventoryReservation::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> byOrder = heldRows.stream().collect(Collectors.groupingBy(r -> r.getOrder().getId(),
                Collectors.mapping(InventoryReservation::getQuantity, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        return new Snapshot(lots, physical, held, byOrder);
    }

    public void requireAvailable(Snapshot snapshot, Long excludingOrderId, BigDecimal requested, String message) {
        if (snapshot.availableExcluding(excludingOrderId).compareTo(requested) < 0) throw new BusinessException(message);
    }

    public void requirePhysicalAfter(Snapshot snapshot, BigDecimal physicalAfter, String message) {
        if (physicalAfter.compareTo(snapshot.held()) < 0) throw new BusinessException(message);
    }

    public record Snapshot(List<Inventory> lots, BigDecimal physical, BigDecimal held, Map<Long, BigDecimal> heldByOrder) {
        public BigDecimal availableExcluding(Long orderId) {
            BigDecimal own = orderId == null ? BigDecimal.ZERO : heldByOrder.getOrDefault(orderId, BigDecimal.ZERO);
            return physical.subtract(held.subtract(own));
        }
    }
}
