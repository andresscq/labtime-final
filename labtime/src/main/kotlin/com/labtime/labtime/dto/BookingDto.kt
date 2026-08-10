package com.labtime.labtime.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

// No hay "requesterUsername" aqui: ese dato jamas se acepta del cliente, sale del JWT.
data class BookingRequest(
    @field:NotNull(message = "slotId is required")
    val slotId: Long,
    @field:NotBlank(message = "purpose is required")
    val purpose: String
)

// Para editar solo se permite cambiar el proposito; slot y status tienen su propio flujo.
data class BookingUpdateRequest(
    @field:NotBlank(message = "purpose is required")
    val purpose: String
)

data class BookingStatusRequest(
    @field:NotBlank(message = "status is required")
    val status: String // APPROVED, REJECTED, ATTENDED
)

data class BookingResponse(
    val id: Long,
    // Antes era solo slotId (Long); ahora viene el slot completo (con su sala
    // adentro) para que el cliente no tenga que pedirlo aparte con otra llamada.
    val slot: TimeSlotResponse,
    val requesterUsername: String,
    val purpose: String,
    val status: String,
    val createdAt: LocalDateTime
)
