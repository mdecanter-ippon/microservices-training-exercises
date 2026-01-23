package com.dornach.shipment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant shippedAt;

    private Instant deliveredAt;

    protected Shipment() {
    }

    public Shipment(UUID orderId, String recipientName, String recipientAddress) {
        this.orderId = orderId;
        this.recipientName = recipientName;
        this.recipientAddress = recipientAddress;
        this.trackingNumber = generateTrackingNumber();
        this.status = ShipmentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private String generateTrackingNumber() {
        return "SHIP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markAsShipped() {
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    public void markAsDelivered() {
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void markAsCancelled() {
        this.status = ShipmentStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public void setRecipientAddress(String recipientAddress) {
        this.recipientAddress = recipientAddress;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
