package com.labtime.labtime.mappers

import com.labtime.labtime.dto.BookingRequest
import com.labtime.labtime.dto.BookingResponse
import com.labtime.labtime.entities.Booking
import com.labtime.labtime.entities.TimeSlot

// requesterUsername entra por parametro, JAMAS desde el request.
fun BookingRequest.toEntity(slot: TimeSlot, requesterUsername: String) = Booking(
    slot = slot,
    requesterUsername = requesterUsername,
    purpose = this.purpose
)

fun Booking.toResponse() = BookingResponse(
    id = this.id,
    slot = this.slot.toResponse(),
    requesterUsername = this.requesterUsername,
    purpose = this.purpose,
    status = this.status,
    createdAt = this.createdAt
)
