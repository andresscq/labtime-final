package com.labtime.labtime.services

import com.labtime.labtime.dto.RoomRequest
import com.labtime.labtime.entities.Room
import com.labtime.labtime.entities.TimeSlot
import com.labtime.labtime.exceptions.RoomHasTimeSlotsException
import com.labtime.labtime.exceptions.RoomNameAlreadyExistsException
import com.labtime.labtime.exceptions.RoomNotFoundException
import com.labtime.labtime.repositories.RoomRepository
import com.labtime.labtime.repositories.TimeSlotRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class RoomServiceTest {

    private lateinit var roomRepository: RoomRepository
    private lateinit var timeSlotRepository: TimeSlotRepository
    private lateinit var roomService: RoomService

    @BeforeEach
    fun setUp() {
        roomRepository = mock()
        timeSlotRepository = mock()
        roomService = RoomService(roomRepository, timeSlotRepository)
    }

    private fun labA() = Room(name = "Lab A", roomType = "LAB", capacity = 25, building = "Bloque C", id = 1)

    // ---------- Camino feliz ----------

    @Test
    fun `crear una sala la guarda con los datos del request`() {
        whenever(roomRepository.save(any<Room>())).thenAnswer { it.arguments[0] as Room }

        val sala = roomService.create(RoomRequest("Lab A", "LAB", 25, "Bloque C"))

        assertEquals("Lab A", sala.name)
        assertEquals(25, sala.capacity)
    }

    @Test
    fun `listar todas las salas devuelve el catalogo completo`() {
        whenever(roomRepository.findAll()).thenReturn(listOf(labA()))
        whenever(timeSlotRepository.findByRoom_IdOrderByStartsAtAsc(1)).thenReturn(emptyList())

        val salas = roomService.findAll()

        assertEquals(1, salas.size)
    }

    @Test
    fun `una sala trae su horario completo embebido, ocupado y libre`() {
        val libre = TimeSlot(room = labA(), startsAt = java.time.LocalDateTime.now().plusHours(2), endsAt = java.time.LocalDateTime.now().plusHours(3), available = true, id = 10)
        val ocupado = TimeSlot(room = labA(), startsAt = java.time.LocalDateTime.now().plusHours(4), endsAt = java.time.LocalDateTime.now().plusHours(5), available = false, id = 11)
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        whenever(timeSlotRepository.findByRoom_IdOrderByStartsAtAsc(1)).thenReturn(listOf(libre, ocupado))

        val sala = roomService.findById(1)

        assertEquals(2, sala.timeSlots.size)
        assertEquals(true, sala.timeSlots[0].available)
        assertEquals(false, sala.timeSlots[1].available)
    }

    @Test
    fun `buscar una sala por id la devuelve si existe`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))

        val sala = roomService.findById(1)

        assertEquals("Lab A", sala.name)
    }

    @Test
    fun `actualizar una sala reemplaza sus datos`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        whenever(roomRepository.save(any<Room>())).thenAnswer { it.arguments[0] as Room }

        val actualizada = roomService.update(1, RoomRequest("Lab A Renovado", "LAB", 30, "Bloque C"))

        assertEquals("Lab A Renovado", actualizada.name)
        assertEquals(30, actualizada.capacity)
    }

    @Test
    fun `borrar una sala sin horarios la elimina del repositorio`() {
        val sala = labA()
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(sala))
        whenever(timeSlotRepository.existsByRoom_Id(1)).thenReturn(false)

        roomService.delete(1)

        verify(roomRepository).delete(sala)
    }

    // ---------- Errores ----------

    @Test
    fun `crear una sala con un nombre que ya existe da 409 y no guarda nada`() {
        whenever(roomRepository.existsByNameIgnoreCase("Lab A")).thenReturn(true)

        assertThrows(RoomNameAlreadyExistsException::class.java) {
            roomService.create(RoomRequest("Lab A", "LAB", 25, "Bloque C"))
        }
        verify(roomRepository, never()).save(any<Room>())
    }

    @Test
    fun `renombrar una sala a un nombre ya usado por otra sala da 409 y no guarda nada`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        whenever(roomRepository.existsByNameIgnoreCaseAndIdNot("Lab B", 1)).thenReturn(true)

        assertThrows(RoomNameAlreadyExistsException::class.java) {
            roomService.update(1, RoomRequest("Lab B", "LAB", 25, "Bloque C"))
        }
        verify(roomRepository, never()).save(any<Room>())
    }

    @Test
    fun `buscar una sala inexistente da 404`() {
        whenever(roomRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(RoomNotFoundException::class.java) {
            roomService.findById(999)
        }
    }

    @Test
    fun `actualizar una sala inexistente da 404 y no guarda nada`() {
        whenever(roomRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(RoomNotFoundException::class.java) {
            roomService.update(999, RoomRequest("x", "LAB", 1, "x"))
        }
        verify(roomRepository, never()).save(any<Room>())
    }

    @Test
    fun `borrar una sala inexistente da 404 y no borra nada`() {
        whenever(roomRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(RoomNotFoundException::class.java) {
            roomService.delete(999)
        }
        verify(roomRepository, never()).delete(any<Room>())
    }

    // ---------- Integridad referencial (FK real room_id en time_slots) ----------

    @Test
    fun `borrar una sala que todavia tiene horarios da 409 y no borra nada`() {
        whenever(roomRepository.findById(1)).thenReturn(Optional.of(labA()))
        whenever(timeSlotRepository.existsByRoom_Id(1)).thenReturn(true)

        assertThrows(RoomHasTimeSlotsException::class.java) {
            roomService.delete(1)
        }
        verify(roomRepository, never()).delete(any<Room>())
    }
}
