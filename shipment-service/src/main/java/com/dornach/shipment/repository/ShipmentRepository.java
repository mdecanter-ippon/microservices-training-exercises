package com.dornach.shipment.repository;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.domain.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    List<Shipment> findByOrderId(UUID orderId);

    List<Shipment> findByStatus(ShipmentStatus status);
}
