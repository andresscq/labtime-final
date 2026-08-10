package com.labtime.labtime.services

import com.labtime.labtime.dto.EquipmentRequestRequest
import com.labtime.labtime.entities.Booking
import com.labtime.labtime.entities.Equipment
import com.labtime.labtime.entities.EquipmentRequest
import com.labtime.labtime.entities.Room
import com.labtime.labtime.entities.TimeSlot
import com.labtime.labtime.exceptions.BookingNotFoundException
import com.labtime.labtime.exceptions.EquipmentRequestNotFoundException
import com.labtime.labtime.exceptions.InvalidEquipmentQuantityException
import com.labtime.labtime.exceptions.NotYourBookingException
import com.labtime.labtime.repositories.BookingRepository
import com.labtime.labtime.repositories.EquipmentRequestRepository
import org.junit.jupiter.api.Assertions.assertEquals
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

class EquipmentRequestServiceTest {

    private lateinit var equipmentRequestRepository: EquipmentRequestRepository
    private lateinit var bookingRepository: BookingRepository
    private lateinit var equipmentRequestService: EquipmentRequestService

    @BeforeEach
    fun setUp() {
        equipmentRequestRepository = mock()
        bookingRepository = mock()
        equipmentRequestService = EquipmentRequestService(equipmentRequestRepository, bookingRepository)
    }

    private fun labA() = Room(name = "Lab A", roomType = "LAB", capacity = 25, building = "Bloque C", id = 1)

    private fun slotDeLabA() = TimeSlot(
        room = labA(), startsAt = LocalDateTime.now(), endsAt = LocalDateTime.now().plusHours(1),
        available = false, id = 10
    )

    private fun reservaDeAna() = Booking(slot = slotDeLabA(), requesterUsername = "ana", purpose = "Practica", id = 1)
    private fun proyectorParaReserva1() = EquipmentRequest(booking = reservaDeAna(), equipment = Equipment.PROJECTOR, quantity = 1, id = 5)

    // ---------- Camino feliz ----------

    @Test
    fun `pedir equipo para mi propia reserva lo guarda`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))
        whenever(equipmentRequestRepository.save(any<EquipmentRequest>())).thenAnswer { it.arguments[0] as EquipmentRequest }

        val equipo = equipmentRequestService.create(EquipmentRequestRequest(1, Equipment.PROJECTOR, 1), "ana")

        assertEquals(Equipment.PROJECTOR, equipo.equipment)
    }

    @Test
    fun `listar equipo de mi propia reserva`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))
        whenever(equipmentRequestRepository.findByBooking_Id(1)).thenReturn(listOf(proyectorParaReserva1()))

        val equipos = equipmentRequestService.findByBooking(1, "ana", isStaff = false)

        assertEquals(1, equipos.size)
    }

    @Test
    fun `STAFF puede listar equipo de la reserva de otro usuario`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))
        whenever(equipmentRequestRepository.findByBooking_Id(1)).thenReturn(listOf(proyectorParaReserva1()))

        val equipos = equipmentRequestService.findByBooking(1, "beto", isStaff = true)

        assertEquals(1, equipos.size)
    }

    @Test
    fun `borrar mi propia solicitud de equipo la elimina`() {
        val equipo = proyectorParaReserva1()
        whenever(equipmentRequestRepository.findById(5)).thenReturn(Optional.of(equipo))

        equipmentRequestService.delete(5, "ana")

        verify(equipmentRequestRepository).delete(equipo)
    }

    // ---------- 🔒 Autorizacion por propiedad ----------

    @Test
    fun `NO puedo pedir equipo para la reserva de otro usuario`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))

        assertThrows(NotYourBookingException::class.java) {
            equipmentRequestService.create(EquipmentRequestRequest(1, Equipment.PROJECTOR, 1), "beto")
        }
        verify(equipmentRequestRepository, never()).save(any<EquipmentRequest>())
    }

    @Test
    fun `NO puedo listar equipo de la reserva de otro usuario si no soy STAFF`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))

        assertThrows(NotYourBookingException::class.java) {
            equipmentRequestService.findByBooking(1, "beto", isStaff = false)
        }
    }

    @Test
    fun `NO puedo borrar una solicitud de equipo de la reserva de otro usuario`() {
        whenever(equipmentRequestRepository.findById(5)).thenReturn(Optional.of(proyectorParaReserva1()))

        assertThrows(NotYourBookingException::class.java) {
            equipmentRequestService.delete(5, "beto")
        }
        verify(equipmentRequestRepository, never()).delete(any<EquipmentRequest>())
    }

    // ---------- Cantidad vs stock del catalogo ----------

    @Test
    fun `pedir mas proyectores de los que hay en el catalogo da 400`() {
        whenever(bookingRepository.findById(1)).thenReturn(Optional.of(reservaDeAna()))

        assertThrows(InvalidEquipmentQuantityException::class.java) {
            equipmentRequestService.create(EquipmentRequestRequest(1, Equipment.PROJECTOR, 999), "ana")
        }
        verify(equipmentRequestRepository, never()).save(any<EquipmentRequest>())
    }

    // ---------- 404 ----------

    @Test
    fun `pedir equipo para una reserva inexistente da 404`() {
        whenever(bookingRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(BookingNotFoundException::class.java) {
            equipmentRequestService.create(EquipmentRequestRequest(999, Equipment.PROJECTOR, 1), "ana")
        }
    }

    @Test
    fun `borrar una solicitud de equipo inexistente da 404`() {
        whenever(equipmentRequestRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(EquipmentRequestNotFoundException::class.java) {
            equipmentRequestService.delete(999, "ana")
        }
    }
}
