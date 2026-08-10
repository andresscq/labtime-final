package com.labtime.labtime.services

import com.labtime.labtime.dto.RoomRequest
import com.labtime.labtime.dto.RoomResponse
import com.labtime.labtime.exceptions.RoomHasTimeSlotsException
import com.labtime.labtime.exceptions.RoomNameAlreadyExistsException
import com.labtime.labtime.exceptions.RoomNotFoundException
import com.labtime.labtime.mappers.toEntity
import com.labtime.labtime.mappers.toResponse
import com.labtime.labtime.repositories.RoomRepository
import com.labtime.labtime.repositories.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val timeSlotRepository: TimeSlotRepository
) {
    private val logger = LoggerFactory.getLogger(RoomService::class.java)

    // ---- Lectura publica ----
    // Cada sala trae embebido su horario COMPLETO (ocupado y libre), para que
    // una sola llamada a GET /rooms o GET /rooms/{id} muestre todo el detalle
    // de la sala sin tener que pedir los slots aparte con otra llamada.
    fun findAll(): List<RoomResponse> =
        roomRepository.findAll().map { room ->
            room.toResponse(timeSlotRepository.findByRoom_IdOrderByStartsAtAsc(room.id).map { it.toResponse() })
        }

    fun findById(id: Long): RoomResponse {
        val room = findOrThrow(id)
        return room.toResponse(timeSlotRepository.findByRoom_IdOrderByStartsAtAsc(id).map { it.toResponse() })
    }

    // ---- Escritura: solo STAFF llega aqui (lo filtra SecurityConfig) ----
    fun create(request: RoomRequest): RoomResponse {
        if (roomRepository.existsByNameIgnoreCase(request.name)) {
            logger.warn("event=room.rejected | msg=Room name already exists | name=\"${request.name}\"")
            throw RoomNameAlreadyExistsException("A room named '${request.name}' already exists")
        }
        val saved = roomRepository.save(request.toEntity())
        logger.info("event=room.created | msg=Room created | roomId=${saved.id} name=\"${saved.name}\"")
        return saved.toResponse()
    }

    fun update(id: Long, request: RoomRequest): RoomResponse {
        val room = findOrThrow(id)
        if (roomRepository.existsByNameIgnoreCaseAndIdNot(request.name, id)) {
            logger.warn("event=room.rejected | msg=Room name already exists | name=\"${request.name}\"")
            throw RoomNameAlreadyExistsException("A room named '${request.name}' already exists")
        }
        room.name = request.name
        room.roomType = request.roomType
        room.capacity = request.capacity
        room.building = request.building
        val saved = roomRepository.save(room)
        logger.info("event=room.updated | msg=Room updated | roomId=${saved.id}")
        return saved.toResponse()
    }

    fun delete(id: Long) {
        val room = findOrThrow(id)
        // Guarda de integridad: la FK real de time_slots.room_id impediria el
        // borrado en Postgres igual, pero aqui se rechaza con un 409 legible
        // en vez de dejar que suba un error crudo de base de datos.
        if (timeSlotRepository.existsByRoom_Id(id)) {
            logger.warn("event=room.rejected | msg=Room still has time slots | roomId=$id")
            throw RoomHasTimeSlotsException("Room $id still has time slots and cannot be deleted")
        }
        roomRepository.delete(room)
        logger.info("event=room.deleted | msg=Room deleted | roomId=${room.id}")
    }

    private fun findOrThrow(id: Long) =
        roomRepository.findById(id)
            .orElseThrow {
                logger.warn("event=room.not_found | msg=Room not found | roomId=$id")
                RoomNotFoundException("Room $id does not exist")
            }
}
