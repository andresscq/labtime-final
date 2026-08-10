package com.labtime.labtime.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class RoomRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    @field:NotBlank(message = "roomType is required")
    val roomType: String,
    @field:Positive(message = "capacity must be greater than 0")
    val capacity: Int,
    @field:NotBlank(message = "building is required")
    val building: String
)

data class RoomResponse(
    val id: Long,
    val name: String,
    val roomType: String,
    val capacity: Int,
    val building: String,
    // Antes solo traia lo DISPONIBLE embebido; ahora trae el horario
    // COMPLETO de la sala (ocupado y libre) para que una sola llamada a
    // GET /rooms o GET /rooms/{id} muestre todo el detalle de la sala, sin
    // tener que pedir los slots aparte con otra llamada.
    val timeSlots: List<TimeSlotResponse> = emptyList()
)
