package com.labtime.labtime.mappers

import com.labtime.labtime.dto.TimeSlotRequest
import com.labtime.labtime.dto.TimeSlotResponse
import com.labtime.labtime.entities.Room
import com.labtime.labtime.entities.TimeSlot

fun TimeSlotRequest.toEntity(room: Room) = TimeSlot(
    room = room,
    startsAt = this.startsAt,
    endsAt = this.endsAt
)

fun TimeSlot.toResponse() = TimeSlotResponse(
    id = this.id,
    roomId = this.room.id,
    startsAt = this.startsAt,
    endsAt = this.endsAt,
    available = this.available
)
