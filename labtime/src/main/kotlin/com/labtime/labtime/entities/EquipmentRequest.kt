package com.labtime.labtime.entities

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

// Relacion N:1 con Booking (una reserva puede pedir varios equipos), FK real en Postgres.
@Entity
@Table(name = "equipment_requests")
class EquipmentRequest(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    var booking: Booking,

    // Antes era un String libre (equipmentName); ahora es una entrada del
    // catalogo fijo (enum Equipment), guardada como texto legible en la BD
    // (EnumType.STRING) para que la tabla no sea ilegible con solo numeros.
    @Enumerated(EnumType.STRING)
    var equipment: Equipment,

    var quantity: Int,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
