package com.labtime.users.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

// El cognitoSub JAMAS se acepta del cliente: sale del JWT en el controller.
data class UserProfileRequest(
    @field:NotBlank(message = "fullName is required")
    val fullName: String,
    @field:NotBlank(message = "email is required")
    @field:Email(message = "email must be a valid email address")
    val email: String,
    val phone: String?,
    // Dato adicional: facultad/carrera del docente o estudiante (ej. "Facultad
    // de Ingenieria", "Ingenieria en Sistemas"). Opcional, texto libre por
    // ahora — no hay un catalogo fijo de facultades como si lo hay con Equipment.
    val faculty: String? = null
)

data class UserProfileResponse(
    val id: Long,
    val cognitoSub: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val faculty: String?,
    val createdAt: LocalDateTime
)
