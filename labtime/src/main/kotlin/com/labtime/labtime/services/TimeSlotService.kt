package com.labtime.labtime.services

import com.labtime.labtime.dto.TimeSlotRequest
import com.labtime.labtime.dto.TimeSlotResponse
import com.labtime.labtime.exceptions.InvalidTimeRangeException
import com.labtime.labtime.exceptions.RoomNotFoundException
import com.labtime.labtime.exceptions.TimeSlotHasBookingsException
import com.labtime.labtime.exceptions.TimeSlotNotFoundException
import com.labtime.labtime.mappers.toEntity
import com.labtime.labtime.mappers.toResponse
import com.labtime.labtime.repositories.BookingRepository
import com.labtime.labtime.repositories.RoomRepository
import com.labtime.labtime.repositories.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class TimeSlotService(
    private val timeSlotRepository: TimeSlotRepository,
    private val roomRepository: RoomRepository,
    private val bookingRepository: BookingRepository
) {
    private val logger = LoggerFactory.getLogger(TimeSlotService::class.java)

    // Regla de negocio: ningun horario puede durar menos de 1 hora.
    private val minimumSlotDuration = Duration.ofHours(1)

    private fun validateSlotWindow(startsAt: LocalDateTime, endsAt: LocalDateTime) {
        if (!endsAt.isAfter(startsAt)) {
            throw InvalidTimeRangeException("endsAt must be after startsAt")
        }
        if (!startsAt.isAfter(LocalDateTime.now())) {
            logger.warn("event=slot.rejected | msg=startsAt is in the past | startsAt=$startsAt")
            throw InvalidTimeRangeException("startsAt must be in the future, cannot create a time slot in the past")
        }
        if (Duration.between(startsAt, endsAt) < minimumSlotDuration) {
            logger.warn("event=slot.rejected | msg=Slot shorter than minimum duration | startsAt=$startsAt endsAt=$endsAt")
            throw InvalidTimeRangeException("A time slot must be at least 1 hour long")
        }
        // Limite diario: un horario de laboratorio/aula es un bloque DENTRO de
        // un mismo dia (ej. 8:00-9:00), no una sala bloqueada por 2 dias
        // seguidos. Sin este limite, "startsAt en el futuro" + "minimo 1 hora"
        // igual dejaban pasar un slot de 08:00 del dia 10 a 08:00 del dia 12.
        if (startsAt.toLocalDate() != endsAt.toLocalDate()) {
            logger.warn("event=slot.rejected | msg=Slot crosses calendar days | startsAt=$startsAt endsAt=$endsAt")
            throw InvalidTimeRangeException("A time slot must start and end on the same calendar day")
        }
    }

    fun findById(id: Long): TimeSlotResponse = findOrThrow(id).toResponse()

    // ---- Lectura publica: horarios libres de una sala ----
    fun findAvailableByRoom(roomId: Long): List<TimeSlotResponse> {
        if (!roomRepository.existsById(roomId)) {
            logger.warn("event=room.not_found | msg=Room not found | roomId=$roomId")
            throw RoomNotFoundException("Room $roomId does not exist")
        }
        return timeSlotRepository.findByRoom_IdAndAvailableTrue(roomId).map { it.toResponse() }
    }

    // ---- Escritura: solo STAFF ----
    fun create(request: TimeSlotRequest): TimeSlotResponse {
        val room = roomRepository.findById(request.roomId)
            .orElseThrow { RoomNotFoundException("Room ${request.roomId} does not exist") }
        validateSlotWindow(request.startsAt, request.endsAt)
        val saved = timeSlotRepository.save(request.toEntity(room))
        logger.info("event=slot.created | msg=Time slot created | slotId=${saved.id} roomId=${saved.room.id}")
        return saved.toResponse()
    }

    fun update(id: Long, request: TimeSlotRequest): TimeSlotResponse {
        val slot = findOrThrow(id)
        validateSlotWindow(request.startsAt, request.endsAt)
        slot.startsAt = request.startsAt
        slot.endsAt = request.endsAt
        val saved = timeSlotRepository.save(slot)
        logger.info("event=slot.updated | msg=Time slot updated | slotId=${saved.id}")
        return saved.toResponse()
    }

    fun delete(id: Long) {
        val slot = findOrThrow(id)
        // Guarda de integridad: la FK real de bookings.slot_id impediria el
        // borrado en Postgres igual, pero aqui se rechaza con un 409 legible.
        if (bookingRepository.existsBySlot_Id(id)) {
            logger.warn("event=slot.rejected | msg=Time slot still has bookings | slotId=$id")
            throw TimeSlotHasBookingsException("Time slot $id still has bookings and cannot be deleted")
        }
        timeSlotRepository.delete(slot)
        logger.info("event=slot.deleted | msg=Time slot deleted | slotId=${slot.id}")
    }

    // Llamado EN PROCESO por BookingService cuando se confirma una reserva
    // (antes era una llamada HTTP a rooms-service; ahora es el mismo microservicio).
    fun markUnavailable(id: Long) {
        val slot = findOrThrow(id)
        slot.available = false
        timeSlotRepository.save(slot)
        logger.info("event=slot.marked_unavailable | msg=Time slot marked unavailable | slotId=$id")
    }

    private fun findOrThrow(id: Long) =
        timeSlotRepository.findById(id)
            .orElseThrow {
                logger.warn("event=slot.not_found | msg=Time slot not found | slotId=$id")
                TimeSlotNotFoundException("Time slot $id does not exist")
            }
}
