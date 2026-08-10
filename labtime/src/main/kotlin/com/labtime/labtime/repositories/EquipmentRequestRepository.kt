package com.labtime.labtime.repositories

import com.labtime.labtime.entities.EquipmentRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EquipmentRequestRepository : JpaRepository<EquipmentRequest, Long> {

    fun findByBooking_Id(bookingId: Long): List<EquipmentRequest>

    // Guarda de integridad: ¿esta reserva todavia tiene equipo pedido? Si lo tiene, no se puede borrar.
    fun existsByBooking_Id(bookingId: Long): Boolean
}
