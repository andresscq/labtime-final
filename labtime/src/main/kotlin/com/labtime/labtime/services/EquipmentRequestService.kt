package com.labtime.labtime.services

import com.labtime.labtime.dto.EquipmentRequestRequest
import com.labtime.labtime.dto.EquipmentRequestResponse
import com.labtime.labtime.exceptions.BookingNotFoundException
import com.labtime.labtime.exceptions.EquipmentRequestNotFoundException
import com.labtime.labtime.exceptions.InvalidEquipmentQuantityException
import com.labtime.labtime.exceptions.NotYourBookingException
import com.labtime.labtime.mappers.toEntity
import com.labtime.labtime.mappers.toResponse
import com.labtime.labtime.repositories.BookingRepository
import com.labtime.labtime.repositories.EquipmentRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EquipmentRequestService(
    private val equipmentRequestRepository: EquipmentRequestRepository,
    private val bookingRepository: BookingRepository
) {
    private val logger = LoggerFactory.getLogger(EquipmentRequestService::class.java)

    // Solo el dueno de la reserva puede pedir equipo para ella.
    fun create(request: EquipmentRequestRequest, requesterUsername: String): EquipmentRequestResponse {
        val booking = bookingRepository.findById(request.bookingId)
            .orElseThrow { BookingNotFoundException("Booking ${request.bookingId} does not exist") }

        if (booking.requesterUsername != requesterUsername) {
            logger.warn("event=equipment.forbidden | msg=Booking does not belong to requester | bookingId=${request.bookingId}")
            throw NotYourBookingException("Booking ${request.bookingId} does not belong to you")
        }

        // El catalogo ya limita QUE se puede pedir (enum); esto limita CUANTO:
        // no tiene sentido pedir mas de lo que existe en todo el inventario.
        if (request.quantity < 1 || request.quantity > request.equipment.totalStock) {
            logger.warn("event=equipment.rejected | msg=Invalid quantity | equipment=${request.equipment} quantity=${request.quantity}")
            throw InvalidEquipmentQuantityException(
                "Requested quantity for ${request.equipment.displayName} must be between 1 and ${request.equipment.totalStock}"
            )
        }

        val saved = equipmentRequestRepository.save(request.toEntity(booking))
        logger.info("event=equipment.created | msg=Equipment request created | equipmentRequestId=${saved.id} bookingId=${saved.booking.id}")
        return saved.toResponse()
    }

    fun findByBooking(bookingId: Long, requesterUsername: String, isStaff: Boolean): List<EquipmentRequestResponse> {
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { BookingNotFoundException("Booking $bookingId does not exist") }

        if (!isStaff && booking.requesterUsername != requesterUsername) {
            logger.warn("event=equipment.forbidden | msg=Booking does not belong to requester | bookingId=$bookingId")
            throw NotYourBookingException("Booking $bookingId does not belong to you")
        }

        return equipmentRequestRepository.findByBooking_Id(bookingId).map { it.toResponse() }
    }

    fun delete(id: Long, requesterUsername: String) {
        val equipment = equipmentRequestRepository.findById(id)
            .orElseThrow {
                logger.warn("event=equipment.not_found | msg=Equipment request not found | equipmentRequestId=$id")
                EquipmentRequestNotFoundException("Equipment request $id does not exist")
            }

        // La FK real garantiza que equipment.booking siempre existe: ya no hace
        // falta una segunda consulta a bookingRepository para revalidarlo.
        if (equipment.booking.requesterUsername != requesterUsername) {
            logger.warn("event=equipment.forbidden | msg=Equipment request does not belong to requester | equipmentRequestId=$id")
            throw NotYourBookingException("This equipment request does not belong to you")
        }
        equipmentRequestRepository.delete(equipment)
        logger.info("event=equipment.deleted | msg=Equipment request deleted | equipmentRequestId=$id")
    }
}
