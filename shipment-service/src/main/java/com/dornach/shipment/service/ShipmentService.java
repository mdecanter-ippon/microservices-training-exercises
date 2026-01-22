package com.dornach.shipment.service;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.dto.CreateShipmentRequest;
import com.dornach.shipment.repository.ShipmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    public Shipment getShipmentById(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shipment not found: " + id));
    }

    public Shipment getShipmentByTrackingNumber(String trackingNumber) {
        return shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new RuntimeException("Shipment not found: " + trackingNumber));
    }

    public Shipment createShipment(CreateShipmentRequest request) {
        Shipment shipment = new Shipment(
                request.orderId(),
                request.recipientName(),
                request.recipientAddress()
        );
        return shipmentRepository.save(shipment);
    }
}
