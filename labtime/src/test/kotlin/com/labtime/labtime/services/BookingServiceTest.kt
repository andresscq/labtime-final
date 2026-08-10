package com.labtime.labtime.services

import com.labtime.labtime.dto.BookingRequest
import com.labtime.labtime.dto.BookingUpdateRequest
import com.labtime.labtime.entities.Booking
import com.labtime.labtime.entities.Room
import com.labtime.labtime.entities.TimeSlot
import com.labtime.labtime.exceptions.BookingHasEquipmentRequestsException
import com.labtime.labtime.exceptions.BookingNotFoundException
import com.labtime.labtime.exceptions.NotYourBookingException
import com.labtime.labtime.exceptions.SlotAlreadyBookedException
import com.labtime.labtime.exceptions.SlotNotAvailableException
import com.labtime.labtime.repositories.BookingRepository
import com.labtime.labtime.repositories.EquipmentRequestRepository
import com.labtime.labtime.repositories.TimeSlotRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class BookingServiceTest {

    private lateinit var bookingRepository: BookingRepository
    private lateinit var timeSlotRepository: TimeSlotRepository
    private lateinit var timeSlotService: TimeSlotService
    private lateinit var equipmentRequestRepository: EquipmentRequestRepository
    private lateinit var bookingService: BookingService

    @BeforeEach
    fun setUp() {
        bookingRepository = mock()
        timeSlotRepository = mock()
        // TimeSlotService se mockea completo: BookingService solo lo usa para
        // confirmar la reserva (markUnavailable), no para leer.
        timeSlotService = mock()
        equipmentRequestRepository = mock()
        bookingService = BookingService(bookingRepository, timeSlotRepository, timeSlotService, equipmentRequestRepository)
    }

    private fun labA() = Room(name = "Lab A", roomType = "LAB", capacity = 25, building = "Bloque C", id = 1)

    private fun slotDisponible() = TimeSlot(
        room = labA(),
        startsAt = java.time.LocalDateTime.now().plusHours(2),
        endsAt = java.time.LocalDateTime.now().plusHours(3),
        available = true,
        id = 10
    )

    private fun reservaDeAna() = Booking(
        slot = slotDisponible(), requesterUsername = "ana", purpose = "Practica de POO", id = 1
    )

    // ---------- Camino feliz ----------

    @Test
    fun `crear una reserva la firma con el requester recibido y confirma el slot`() {
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slotDisponible()))
        whenever(bookingRepository.existsBySlot_IdAndStatusIn(eq(10), any())).thenReturn(false)
        whenever(bookingRepository.save(any<Booking>())).thenAnswer { it.arguments[0] as Booking }

        val reserva = bookingService.create(BookingRequest(slotId = 10, purpose = "Practica de POO"), "ana")

        assertEquals("ana", reserva.requesterUsername)
        assertEquals("PENDING", reserva.status)
        verify(timeSlotService).markUnavailable(10)
    }

    @Test
    fun `listar mis reservas consulta SOLO por mi username`() {
        whenever(bookingRepository.findByRequesterUsernameOrderByCreatedAtDesc("ana"))
            .thenReturn(listOf(reservaDeAna()))

        val resultado = bookingService.findMine("ana")

        assertEquals(1, resultado.size)
        verify(bookingRepository, never()).findAll()
    }

    @Test
    fun `puedo editar y borrar mi propia reserva sin equipo pedido`() {
        // OJO: se guarda en una variable y se reutiliza la MISMA instancia en el
        // stub y en el verify. Booking es una clase normal (no data class), asi
        // que equals() es por referencia: dos llamadas separadas a reservaDeAna()
        // NUNCA son "iguales" para Mockito, aunque tengan los mismos datos.
        val reserva = reservaDeAna()
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reserva))
        whenever(bookingRepository.save(any<Booking>())).thenAnswer { it.arguments[0] as Booking }
        whenever(equipmentRequestRepository.existsByBooking_Id(1)).thenReturn(false)

        val editada = bookingService.update(1, BookingUpdateRequest("Nuevo motivo"), "ana")
        assertEquals("Nuevo motivo", editada.purpose)

        bookingService.delete(1, "ana")
        verify(bookingRepository).delete(reserva)
    }

    // ---------- 🔒 Autorizacion por propiedad ----------

    @Test
    fun `NO puedo ver la reserva de otro usuario`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))

        assertThrows(NotYourBookingException::class.java) {
            bookingService.findOne(1, "beto", isStaff = false) // 403, no 404: la reserva SI existe
        }
    }

    @Test
    fun `STAFF SI puede ver la reserva de otro usuario`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))

        val reserva = bookingService.findOne(1, "beto", isStaff = true)

        assertEquals("ana", reserva.requesterUsername)
    }

    @Test
    fun `NO puedo editar ni borrar la reserva de otro usuario`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))

        assertThrows(NotYourBookingException::class.java) {
            bookingService.update(1, BookingUpdateRequest("hackeada"), "beto")
        }
        assertThrows(NotYourBookingException::class.java) {
            bookingService.delete(1, "beto")
        }
        verify(bookingRepository, never()).save(any<Booking>())
        verify(bookingRepository, never()).delete(any<Booking>())
    }

    // ---------- 404 de verdad ----------

    @Test
    fun `una reserva que no existe da 404, no 403`() {
        whenever(bookingRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(BookingNotFoundException::class.java) {
            bookingService.findOne(999, "ana", isStaff = false)
        }
    }

    // ---------- Concurrencia ----------

    @Test
    fun `NO se puede reservar un slot que no existe`() {
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.empty())

        assertThrows(SlotNotAvailableException::class.java) {
            bookingService.create(BookingRequest(slotId = 10, purpose = "x"), "ana")
        }
        verify(bookingRepository, never()).save(any<Booking>())
    }

    @Test
    fun `NO se puede reservar un slot que ya no esta disponible`() {
        val slotOcupado = TimeSlot(
            room = labA(), startsAt = java.time.LocalDateTime.now(),
            endsAt = java.time.LocalDateTime.now().plusHours(1), available = false, id = 10
        )
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slotOcupado))

        assertThrows(SlotNotAvailableException::class.java) {
            bookingService.create(BookingRequest(slotId = 10, purpose = "x"), "ana")
        }
        verify(bookingRepository, never()).save(any<Booking>())
    }

    @Test
    fun `NO se puede reservar un slot que ya tiene una reserva activa (choque de concurrencia)`() {
        whenever(timeSlotRepository.findById(10)).thenReturn(Optional.of(slotDisponible()))
        whenever(bookingRepository.existsBySlot_IdAndStatusIn(eq(10), any())).thenReturn(true)

        assertThrows(SlotAlreadyBookedException::class.java) {
            bookingService.create(BookingRequest(slotId = 10, purpose = "x"), "beto")
        }
        verify(bookingRepository, never()).save(any<Booking>())
    }

    // ---------- Integridad referencial (FK real booking_id en equipment_requests) ----------

    @Test
    fun `NO puedo borrar una reserva que todavia tiene equipo pedido`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))
        whenever(equipmentRequestRepository.existsByBooking_Id(1)).thenReturn(true)

        assertThrows(BookingHasEquipmentRequestsException::class.java) {
            bookingService.delete(1, "ana")
        }
        verify(bookingRepository, never()).delete(any<Booking>())
    }
}
