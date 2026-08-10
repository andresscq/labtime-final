package com.labtime.labtime.mappers

import com.labtime.labtime.dto.RoomRequest
import com.labtime.labtime.dto.RoomResponse
import com.labtime.labtime.dto.TimeSlotResponse
import com.labtime.labtime.entities.Room

fun RoomRequest.toEntity() = Room(
    name = this.name,
    roomType = this.roomType,
    capacity = this.capacity,
    building = this.building
)

// timeSlots ya viene mapeado por el service (necesita TimeSlotRepository,
// que este mapper no tiene) — por defecto vacio para los casos que no lo piden.
fun Room.toResponse(timeSlots: List<TimeSlotResponse> = emptyList()) = RoomResponse(
    id = this.id,
    name = this.name,
    roomType = this.roomType,
    capacity = this.capacity,
    building = this.building,
    timeSlots = timeSlots
)
