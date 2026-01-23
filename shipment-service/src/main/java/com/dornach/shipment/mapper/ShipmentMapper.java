package com.dornach.shipment.mapper;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.dto.CreateShipmentRequest;
import com.dornach.shipment.dto.ShipmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShipmentMapper {

    ShipmentResponse toResponse(Shipment shipment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trackingNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Shipment toEntity(CreateShipmentRequest request);
}
