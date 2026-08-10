package com.labtime.labtime.controllers

import com.labtime.labtime.dto.TimeSlotRequest
import com.labtime.labtime.dto.TimeSlotResponse
import com.labtime.labtime.services.TimeSlotService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class TimeSlotController(private val timeSlotService: TimeSlotService) {

    // Publico: GET /rooms/{roomId}/slots/available
    @GetMapping("/rooms/{roomId}/slots/available")
    fun availableByRoom(@PathVariable roomId: Long): List<TimeSlotResponse> =
        timeSlotService.findAvailableByRoom(roomId)

    // Publico: usado tambien por bookings-service para validar un slot antes de reservar.
    @GetMapping("/slots/{id}")
    fun findById(@PathVariable id: Long): TimeSlotResponse = timeSlotService.findById(id)

    // NOTA: ya no existe un endpoint HTTP "mark-unavailable". Ahora que rooms y
    // bookings viven en el mismo microservicio, BookingService llama directo a
    // timeSlotService.markUnavailable(id) en el mismo proceso (ver BookingService).

    // Privado, solo STAFF (Criterio 8: control de acceso a nivel de metodo).
    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STAFF')")
    fun create(@Valid @RequestBody request: TimeSlotRequest): TimeSlotResponse =
        timeSlotService.create(request)

    @PutMapping("/slots/{id}")
    @PreAuthorize("hasRole('STAFF')")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: TimeSlotRequest): TimeSlotResponse =
        timeSlotService.update(id, request)

    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('STAFF')")
    fun delete(@PathVariable id: Long) = timeSlotService.delete(id)
}
