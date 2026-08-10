package com.labtime.labtime.entities

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

// Relacion N:1 con TimeSlot (muchas bookings referencian historicamente un slot), FK real en Postgres.
@Entity
@Table(name = "bookings")
class Booking(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    var slot: TimeSlot,

    var requesterUsername: String,

    var purpose: String,

    var status: String = "PENDING", // PENDING | APPROVED | REJECTED | ATTENDED

    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
