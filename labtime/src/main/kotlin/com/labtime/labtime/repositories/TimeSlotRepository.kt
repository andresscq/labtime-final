package com.labtime.labtime.repositories

import com.labtime.labtime.entities.TimeSlot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TimeSlotRepository : JpaRepository<TimeSlot, Long> {

    fun findByRoom_IdAndAvailableTrue(roomId: Long): List<TimeSlot>

    // Horario completo de una sala (ocupados y libres) — antes solo se podia
    // ver un slot puntual si ya sabias su id, o los disponibles filtrados.
    fun findByRoom_IdOrderByStartsAtAsc(roomId: Long): List<TimeSlot>

    // Guarda de integridad: ¿esta sala todavia tiene horarios? Si los tiene, no se puede borrar.
    fun existsByRoom_Id(roomId: Long): Boolean
}
