package com.labtime.labtime.services

import com.labtime.labtime.dto.BookingRequest
import com.labtime.labtime.dto.BookingResponse
import com.labtime.labtime.dto.BookingStatusRequest
import com.labtime.labtime.dto.BookingUpdateRequest
import com.labtime.labtime.entities.Booking
import com.labtime.labtime.exceptions.BookingHasEquipmentRequestsException
import com.labtime.labtime.exceptions.BookingNotFoundException
import com.labtime.labtime.exceptions.InvalidBookingPurposeException
import com.labtime.labtime.exceptions.NotYourBookingException
import com.labtime.labtime.exceptions.SlotAlreadyBookedException
import com.labtime.labtime.exceptions.SlotInThePastException
import com.labtime.labtime.exceptions.SlotNotAvailableException
import com.labtime.labtime.mappers.toEntity
import com.labtime.labtime.mappers.toResponse
import com.labtime.labtime.repositories.BookingRepository
import com.labtime.labtime.repositories.EquipmentRequestRepository
import com.labtime.labtime.repositories.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * CRUD completo sobre `bookings`, la entidad "N" de la relacion N:1 con
 * `time_slots` (muchas bookings referencian historicamente el mismo horario,
 * pero solo una puede estar ACTIVA por slot a la vez).
 *
 * IMPORTANTE — decision de arquitectura (ver ADR en el README raiz): rooms y
 * bookings eran dos microservicios separados que se hablaban por HTTP
 * (RoomsServiceClient). Al fusionarlos en un solo `labtime` para
 * cumplir la estructura exigida (users + UN microservicio propio), esa
 * llamada HTTP se volvio una llamada normal en el mismo proceso a
 * TimeSlotRepository/TimeSlotService. La logica de negocio (guarda de
 * concurrencia, validacion de disponibilidad) es exactamente la misma.
 */
@Service
class BookingService(
    private val bookingRepository: BookingRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val timeSlotService: TimeSlotService,
    private val equipmentRequestRepository: EquipmentRequestRepository
) {
    private val logger = LoggerFactory.getLogger(BookingService::class.java)

    private val activeStatuses = listOf("PENDING", "APPROVED")

    // ---- CREATE ----
    @Transactional
    fun create(request: BookingRequest, requesterUsername: String): BookingResponse {
        if (request.purpose.isBlank()) {
            logger.warn("event=booking.rejected | msg=Purpose is required | slotId=${request.slotId}")
            throw InvalidBookingPurposeException("purpose is required and cannot be blank")
        }

        val slot = timeSlotRepository.findById(request.slotId).orElse(null)
            ?: run {
                logger.warn("event=booking.rejected | msg=Slot does not exist | slotId=${request.slotId}")
                throw SlotNotAvailableException("Time slot ${request.slotId} does not exist")
            }

        if (!slot.available) {
            logger.warn("event=booking.rejected | msg=Slot not available | slotId=${request.slotId}")
            throw SlotNotAvailableException("Time slot ${request.slotId} is no longer available")
        }

        // Segunda capa de seguridad: aunque ya no se puedan CREAR horarios en el
        // pasado (TimeSlotService), un slot creado antes de esa validacion, o uno
        // cuya hora ya se cumplio mientras estaba disponible, no debe poder
        // reservarse retroactivamente.
        if (!slot.startsAt.isAfter(LocalDateTime.now())) {
            logger.warn("event=booking.rejected | msg=Slot already in the past | slotId=${request.slotId} startsAt=${slot.startsAt}")
            throw SlotInThePastException("Time slot ${request.slotId} has already passed and cannot be booked")
        }

        // Guarda de concurrencia: evita dos reservas activas sobre el mismo slot.
        if (bookingRepository.existsBySlot_IdAndStatusIn(request.slotId, activeStatuses)) {
            logger.warn("event=booking.rejected | msg=Slot already booked | slotId=${request.slotId}")
            throw SlotAlreadyBookedException("An active booking already exists for time slot ${request.slotId}")
        }

        val saved = bookingRepository.save(request.toEntity(slot, requesterUsername))
        timeSlotService.markUnavailable(request.slotId)
        logger.info("event=booking.created | msg=Booking created | bookingId=${saved.id} slotId=${saved.slot.id}")
        return saved.toResponse()
    }

    // ---- READ ----
    fun findMine(requesterUsername: String): List<BookingResponse> =
        bookingRepository.findByRequesterUsernameOrderByCreatedAtDesc(requesterUsername)
            .map { it.toResponse() }

    // Un REQUESTER solo puede ver la suya; STAFF puede ver cualquiera.
    fun findOne(id: Long, requesterUsername: String, isStaff: Boolean): BookingResponse =
        (if (isStaff) findByIdOrThrow(id) else findOwnedOrThrow(id, requesterUsername)).toResponse()

    fun findAll(): List<BookingResponse> =
        bookingRepository.findAll().map { it.toResponse() } // solo STAFF llega aqui

    // ---- UPDATE ----
    fun update(id: Long, request: BookingUpdateRequest, requesterUsername: String): BookingResponse {
        if (request.purpose.isBlank()) {
            logger.warn("event=booking.rejected | msg=Purpose is required | bookingId=$id")
            throw InvalidBookingPurposeException("purpose is required and cannot be blank")
        }
        val booking = findOwnedOrThrow(id, requesterUsername)
        booking.purpose = request.purpose
        val saved = bookingRepository.save(booking)
        logger.info("event=booking.updated | msg=Booking updated | bookingId=${saved.id}")
        return saved.toResponse()
    }

    // Solo STAFF: aprobar/rechazar/marcar atendida.
    fun changeStatus(id: Long, request: BookingStatusRequest): BookingResponse {
        val booking = findByIdOrThrow(id)
        booking.status = request.status
        val saved = bookingRepository.save(booking)
        logger.info("event=booking.status_changed | msg=Booking status changed | bookingId=${saved.id} status=${saved.status}")
        return saved.toResponse()
    }

    // ---- DELETE ----
    fun delete(id: Long, requesterUsername: String) {
        val booking = findOwnedOrThrow(id, requesterUsername)
        // Guarda de integridad: la FK real de equipment_requests.booking_id
        // impediria el borrado en Postgres igual, pero aqui se rechaza con un
        // 409 legible en vez de dejar que suba un error crudo de base de datos.
        if (equipmentRequestRepository.existsByBooking_Id(id)) {
            logger.warn("event=booking.rejected | msg=Booking still has equipment requests | bookingId=$id")
            throw BookingHasEquipmentRequestsException("Booking $id still has equipment requests and cannot be deleted")
        }
        bookingRepository.delete(booking)
        logger.info("event=booking.deleted | msg=Booking deleted | bookingId=${booking.id}")
    }

    /**
     * LA UNICA forma de traer una reserva propia en toda la app, y siempre
     * exige el dueno. Este es el `if` de tres lineas que implementa la
     * autorizacion por PROPIEDAD — no lo hace Spring Security, lo hacemos aqui.
     */
    private fun findOwnedOrThrow(id: Long, requesterUsername: String): Booking {
        val booking = findByIdOrThrow(id)
        if (booking.requesterUsername != requesterUsername) {
            logger.warn("event=booking.forbidden | msg=Booking does not belong to requester | bookingId=$id")
            throw NotYourBookingException("Booking $id does not belong to you")
        }
        return booking
    }

    private fun findByIdOrThrow(id: Long): Booking =
        bookingRepository.findById(id)
            .orElseThrow {
                logger.warn("event=booking.not_found | msg=Booking not found | bookingId=$id")
                BookingNotFoundException("Booking $id does not exist")
            }
}
