package com.labtime.labtime.mappers

import com.labtime.labtime.dto.EquipmentRequestRequest
import com.labtime.labtime.dto.EquipmentRequestResponse
import com.labtime.labtime.entities.Booking
import com.labtime.labtime.entities.EquipmentRequest

fun EquipmentRequestRequest.toEntity(booking: Booking) = EquipmentRequest(
    booking = booking,
    equipment = this.equipment,
    quantity = this.quantity
)

fun EquipmentRequest.toResponse() = EquipmentRequestResponse(
    id = this.id,
    bookingId = this.booking.id,
    equipment = this.equipment,
    equipmentDisplayName = this.equipment.displayName,
    quantity = this.quantity
)
