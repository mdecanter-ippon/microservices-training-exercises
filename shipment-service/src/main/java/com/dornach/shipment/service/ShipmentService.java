package com.dornach.shipment.service;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.domain.ShipmentStatus;
import com.dornach.shipment.dto.CreateShipmentRequest;
import com.dornach.shipment.exception.InvalidStatusTransitionException;
import com.dornach.shipment.exception.ShipmentNotFoundException;
import com.dornach.shipment.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment createShipment(CreateShipmentRequest request) {
        log.info("Creating shipment for order: {}", request.orderId());

        Shipment shipment = new Shipment(
                request.orderId(),
                request.recipientName(),
                request.recipientAddress()
        );

        Shipment saved = shipmentRepository.save(shipment);
        log.info("Shipment created with tracking number: {}", saved.getTrackingNumber());

        return saved;
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentById(UUID id) {
        log.debug("Fetching shipment by id: {}", id);

        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByTrackingNumber(String trackingNumber) {
        log.debug("Fetching shipment by tracking number: {}", trackingNumber);

        return shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ShipmentNotFoundException(null));
    }

    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByOrderId(UUID orderId) {
        log.debug("Fetching shipments for order: {}", orderId);

        return shipmentRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<Shipment> getAllShipments() {
        log.debug("Fetching all shipments");

        return shipmentRepository.findAll();
    }

    public Shipment updateShipmentStatus(UUID id, ShipmentStatus newStatus) {
        log.info("Updating shipment {} status to {}", id, newStatus);

        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id));

        validateStatusTransition(shipment.getStatus(), newStatus);

        switch (newStatus) {
            case SHIPPED -> shipment.markAsShipped();
            case DELIVERED -> shipment.markAsDelivered();
            case CANCELLED -> shipment.markAsCancelled();
            default -> throw new InvalidStatusTransitionException(shipment.getStatus(), newStatus);
        }

        return shipmentRepository.save(shipment);
    }

    private void validateStatusTransition(ShipmentStatus current, ShipmentStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == ShipmentStatus.SHIPPED || target == ShipmentStatus.CANCELLED;
            case SHIPPED -> target == ShipmentStatus.IN_TRANSIT || target == ShipmentStatus.DELIVERED;
            case IN_TRANSIT -> target == ShipmentStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };

        if (!valid) {
            throw new InvalidStatusTransitionException(current, target);
        }
    }
}
