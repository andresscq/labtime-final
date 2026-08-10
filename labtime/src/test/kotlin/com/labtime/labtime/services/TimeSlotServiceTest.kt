package com.labtime.labtime.services

import com.labtime.labtime.dto.TimeSlotRequest
import com.labtime.labtime.entities.Room
import com.labtime.labtime.entities.TimeSlot
import com.labtime.labtime.exceptions.InvalidTimeRangeException
import com.labtime.labtime.exceptions.RoomNotFoundException
import com.labtime.labtime.exceptions.TimeSlotHasBookingsException
import com.labtime.labtime.exceptions.TimeSlotNotFoundException
import com.labtime.labtime.repositories.BookingRepository
import com.labtime.labtime.repositories.RoomRepository
import com.labtime.labtime.repositories.TimeSlotRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

class TimeSlotServiceTest {

    private lateinit var timeSlotRepository: TimeSlotRepository
    private lateinit var roomRepository: RoomRepository
    private lateinit var bookingRepository: BookingRepository
    private lateinit var timeSlotService: TimeSlotService

    private val startsAt = LocalDateTime.of(2026, 8, 10, 8, 0)
    private val endsAt = LocalDateTime.of(2026, 8, 10, 10, 0)

    @BeforeEach
    fun setUp() {
        timeSlotRepository = mock()
        roomRepository = mock()
        bookingRepository = mock()
        timeSlotService = TimeSlotService(timeSlotRepository, roomRepository, bookingRepository)
    }

    private fun labA() = Room(name = "Lab A", roomType = "LAB", capacity = 25, building = "Bloque C", id = 1)

    private fun slotDisponible() = TimeSlot(room = labA(), startsAt = startsAt, endsAt = endsAt, available = true, id = 10)

    // ---------- Camino feliz ----------

    @Test
    fun `crear un horario para una sala existente lo guarda`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        whenever(timeSlotRepository.save(any<TimeSlot>())).thenAnswer { it.arguments[0] as TimeSlot }

        val slot = timeSlotService.create(TimeSlotRequest(1, startsAt, endsAt))

        assertEquals(1, slot.roomId)
        assertEquals(true, slot.available)
    }

    @Test
    fun `listar horarios disponibles de una sala existente`() {
        whenever(roomRepository.existsById(1)).thenReturn(true)
        whenever(timeSlotRepository.findByRoom_IdAndAvailableTrue(1)).thenReturn(listOf(slotDisponible()))

        val disponibles = timeSlotService.findAvailableByRoom(1)

        assertEquals(1, disponibles.size)
    }

    @Test
    fun `actualizar un horario reemplaza sus fechas`() {
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slotDisponible()))
        whenever(timeSlotRepository.save(any<TimeSlot>())).thenAnswer { it.arguments[0] as TimeSlot }

        val nuevoInicio = startsAt.plusHours(1)
        val nuevoFin = endsAt.plusHours(1)
        val actualizado = timeSlotService.update(10, TimeSlotRequest(1, nuevoInicio, nuevoFin))

        assertEquals(nuevoInicio, actualizado.startsAt)
    }

    @Test
    fun `marcar un horario como no disponible lo deja available=false`() {
        val slot = slotDisponible()
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slot))
        whenever(timeSlotRepository.save(any<TimeSlot>())).thenAnswer { it.arguments[0] as TimeSlot }

        timeSlotService.markUnavailable(10)

        assertFalse(slot.available)
        verify(timeSlotRepository).save(slot)
    }

    @Test
    fun `borrar un horario sin reservas lo elimina`() {
        val slot = slotDisponible()
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slot))
        whenever(bookingRepository.existsBySlot_Id(10)).thenReturn(false)

        timeSlotService.delete(10)

        verify(timeSlotRepository).delete(slot)
    }

    // ---------- Errores ----------

    @Test
    fun `crear un horario para una sala inexistente da 404 y no guarda nada`() {
        whenever(roomRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(RoomNotFoundException::class.java) {
            timeSlotService.create(TimeSlotRequest(999, startsAt, endsAt))
        }
        verify(timeSlotRepository, never()).save(any<TimeSlot>())
    }

    @Test
    fun `crear un horario con endsAt antes de startsAt da 400 y no guarda nada`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))

        assertThrows(InvalidTimeRangeException::class.java) {
            timeSlotService.create(TimeSlotRequest(1, endsAt, startsAt)) // invertidos a proposito
        }
        verify(timeSlotRepository, never()).save(any<TimeSlot>())
    }

    @Test
    fun `crear un horario en el pasado da 400 y no guarda nada`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        val ayer = LocalDateTime.now().minusDays(1)

        assertThrows(InvalidTimeRangeException::class.java) {
            timeSlotService.create(TimeSlotRequest(1, ayer, ayer.plusHours(2)))
        }
        verify(timeSlotRepository, never()).save(any<TimeSlot>())
    }

    @Test
    fun `crear un horario de menos de 1 hora da 400 y no guarda nada`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        val manana = LocalDateTime.now().plusDays(1)

        assertThrows(InvalidTimeRangeException::class.java) {
            timeSlotService.create(TimeSlotRequest(1, manana, manana.plusMinutes(30)))
        }
        verify(timeSlotRepository, never()).save(any<TimeSlot>())
    }

    @Test
    fun `crear un horario que cruza de un dia a otro da 400 y no guarda nada`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        val hoyALasOcho = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0)
        val mananaALasOcho = hoyALasOcho.plusDays(2) // 48 horas seguidas, cruza varios dias

        assertThrows(InvalidTimeRangeException::class.java) {
            timeSlotService.create(TimeSlotRequest(1, hoyALasOcho, mananaALasOcho))
        }
        verify(timeSlotRepository, never()).save(any<TimeSlot>())
    }

    @Test
    fun `listar horarios de una sala inexistente da 404`() {
        whenever(roomRepository.existsById(999)).thenReturn(false)

        assertThrows(RoomNotFoundException::class.java) {
            timeSlotService.findAvailableByRoom(999)
        }
    }

    @Test
    fun `buscar un horario inexistente da 404`() {
        whenever(timeSlotRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(TimeSlotNotFoundException::class.java) {
            timeSlotService.findById(999)
        }
    }

    // ---------- Integridad referencial (FK real slot_id en bookings) ----------

    @Test
    fun `borrar un horario que todavia tiene reservas da 409 y no borra nada`() {
        val slot = slotDisponible()
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slot))
        whenever(bookingRepository.existsBySlot_Id(10)).thenReturn(true)

        assertThrows(TimeSlotHasBookingsException::class.java) {
            timeSlotService.delete(10)
        }
        verify(timeSlotRepository, never()).delete(any<TimeSlot>())
    }
}
