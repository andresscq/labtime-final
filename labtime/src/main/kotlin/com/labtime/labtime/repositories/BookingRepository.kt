package com.labtime.labtime.repositories

import com.labtime.labtime.entities.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BookingRepository : JpaRepository<Booking, Long> {

    // Para LISTAR "mis reservas" nunca usamos findAll(): la consulta exige el dueno.
    fun findByRequesterUsernameOrderByCreatedAtDesc(requesterUsername: String): List<Booking>

    // Usado por la restriccion de concurrencia: ¿ya hay una reserva activa sobre este slot?
    fun existsBySlot_IdAndStatusIn(slotId: Long, statuses: List<String>): Boolean

    // Guarda de integridad: ¿este horario todavia tiene reservas (de cualquier estado)? Si las tiene, no se puede borrar.
    fun existsBySlot_Id(slotId: Long): Boolean
}
