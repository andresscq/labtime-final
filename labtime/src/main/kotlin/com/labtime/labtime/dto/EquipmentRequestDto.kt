package com.labtime.labtime.dto

import com.labtime.labtime.entities.Equipment
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

// equipment llega como el NAME del enum (ej. "PROJECTOR"), no como texto libre.
// Si el cliente manda algo que no esta en el catalogo, Jackson lo rechaza solo
// con un 400 antes de que el request llegue al service.
data class EquipmentRequestRequest(
    @field:NotNull(message = "bookingId is required")
    val bookingId: Long,
    @field:NotNull(message = "equipment is required")
    val equipment: Equipment,
    @field:Min(1, message = "quantity must be at least 1")
    val quantity: Int
)

data class EquipmentRequestResponse(
    val id: Long,
    val bookingId: Long,
    val equipment: Equipment,
    val equipmentDisplayName: String,
    val quantity: Int
)

// Lo que consume el picker del frontend para armar el dropdown: nunca mas un
// campo de texto libre para el nombre del equipo.
data class EquipmentCatalogItem(
    val code: String,
    val displayName: String,
    val totalStock: Int
)
