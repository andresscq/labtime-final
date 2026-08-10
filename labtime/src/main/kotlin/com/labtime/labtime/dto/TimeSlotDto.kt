package com.labtime.labtime.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class TimeSlotRequest(
    @field:NotNull(message = "roomId is required")
    val roomId: Long,
    @field:NotNull(message = "startsAt is required")
    val startsAt: LocalDateTime,
    @field:NotNull(message = "endsAt is required")
    val endsAt: LocalDateTime
)

data class TimeSlotResponse(
    val id: Long,
    val roomId: Long,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val available: Boolean
)
