package com.labtime.labtime.exceptions

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Un solo manejador global para las 4 entidades del microservicio (antes eran
 * dos: uno en rooms-service, otro en bookings-service). Cada excepcion propia
 * deja su propia linea de log (event=<algo>.rejected/.not_found/.forbidden)
 * ademas de la respuesta HTTP -- para que ningun error pase sin dejar rastro
 * en los logs (Criterio 4).
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // ---- rooms / time_slots ----
    @ExceptionHandler(RoomNotFoundException::class)
    fun handleRoomNotFound(e: RoomNotFoundException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, e.message ?: "Room not found", "RoomService")

    @ExceptionHandler(TimeSlotNotFoundException::class)
    fun handleSlotNotFound(e: TimeSlotNotFoundException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, e.message ?: "Time slot not found", "TimeSlotService")

    @ExceptionHandler(InvalidTimeRangeException::class)
    fun handleInvalidRange(e: InvalidTimeRangeException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, e.message ?: "Invalid time range", "TimeSlotService")

    @ExceptionHandler(RoomNameAlreadyExistsException::class)
    fun handleRoomNameDuplicate(e: RoomNameAlreadyExistsException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.CONFLICT, e.message ?: "Room name already exists", "RoomService")

    @ExceptionHandler(InvalidBookingPurposeException::class)
    fun handleInvalidPurpose(e: InvalidBookingPurposeException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, e.message ?: "Purpose is required", "BookingService")

    @ExceptionHandler(SlotInThePastException::class)
    fun handleSlotInThePast(e: SlotInThePastException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, e.message ?: "Time slot is in the past", "BookingService")

    // 409: la FK real (room_id/slot_id/booking_id) todavia tiene hijos — borrar
    // rompería la relacion 1:N, asi que se rechaza antes de llegar a Postgres.
    @ExceptionHandler(RoomHasTimeSlotsException::class)
    fun handleRoomHasTimeSlots(e: RoomHasTimeSlotsException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.CONFLICT, e.message ?: "Room still has time slots", "RoomService")

    @ExceptionHandler(TimeSlotHasBookingsException::class)
    fun handleTimeSlotHasBookings(e: TimeSlotHasBookingsException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.CONFLICT, e.message ?: "Time slot still has bookings", "TimeSlotService")

    // ---- bookings ----
    @ExceptionHandler(BookingNotFoundException::class)
    fun handleBookingNotFound(e: BookingNotFoundException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, e.message ?: "Booking not found", "BookingService")

    // Este 403 lo lanzamos NOSOTROS, en el service. Spring Security nunca lo
    // pondria: no sabe de quien es la fila que estas pidiendo, solo valida el token.
    @ExceptionHandler(NotYourBookingException::class)
    fun handleForbidden(e: NotYourBookingException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.FORBIDDEN, e.message ?: "This booking does not belong to you", "BookingService")

    @ExceptionHandler(SlotAlreadyBookedException::class)
    fun handleConflict(e: SlotAlreadyBookedException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.CONFLICT, e.message ?: "This time slot already has an active booking", "BookingService")

    @ExceptionHandler(SlotNotAvailableException::class)
    fun handleSlotNotAvailable(e: SlotNotAvailableException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, e.message ?: "Time slot does not exist or is not available", "BookingService")

    @ExceptionHandler(BookingHasEquipmentRequestsException::class)
    fun handleBookingHasEquipmentRequests(e: BookingHasEquipmentRequestsException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.CONFLICT, e.message ?: "Booking still has equipment requests", "BookingService")

    // ---- equipment_requests ----
    @ExceptionHandler(EquipmentRequestNotFoundException::class)
    fun handleEquipmentNotFound(e: EquipmentRequestNotFoundException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, e.message ?: "Equipment request not found", "EquipmentRequestService")

    @ExceptionHandler(InvalidEquipmentQuantityException::class)
    fun handleInvalidQuantity(e: InvalidEquipmentQuantityException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, e.message ?: "Invalid equipment quantity", "EquipmentRequestService")

    // 400: un @NotBlank/@NotNull/@Min del DTO no se cumplio (ej. name="" en
    // RoomRequest). Junta TODOS los campos que fallaron en un solo mensaje
    // legible, en vez del 400 generico y poco claro que da Spring por defecto.
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ExceptionResponse> {
        val detalle = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return respond(HttpStatus.BAD_REQUEST, "Validation failed — $detalle", "RequestValidation")
    }

    // 400: el JSON llego mal formado o le falta un campo obligatorio de tipo
    // numero/fecha (ej. sin roomId en TimeSlotRequest) -- Jackson lo rechaza
    // ANTES de que la validacion de arriba llegue a correr.
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, "Request body is missing or has an invalid value for a required field", "RequestValidation")

    private fun respond(status: HttpStatus, message: String, source: String): ResponseEntity<ExceptionResponse> {
        logger.warn("event=request.rejected | msg=$message | source=$source | status=${status.value()}")
        return ResponseEntity.status(status).body(ExceptionResponse(message, source))
    }
}

data class ExceptionResponse(
    val message: String,
    val source: String,
)
