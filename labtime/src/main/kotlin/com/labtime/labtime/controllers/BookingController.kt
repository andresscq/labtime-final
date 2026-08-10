package com.labtime.labtime.controllers

import com.labtime.labtime.dto.BookingRequest
import com.labtime.labtime.dto.BookingResponse
import com.labtime.labtime.dto.BookingStatusRequest
import com.labtime.labtime.dto.BookingUpdateRequest
import com.labtime.labtime.services.BookingService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * CRUD completo sobre `bookings` (entidad N de la relacion N:1 con time_slots):
 * Create (POST), Read (GET /me, GET /{id}, GET admin), Update (PUT), Delete (DELETE).
 * Mas dos transiciones de estado exclusivas de STAFF (PATCH).
 */
@RestController
@RequestMapping("/bookings")
class BookingController(private val bookingService: BookingService) {

    // ---- CREATE ---- (REQUESTER)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: BookingRequest, @AuthenticationPrincipal jwt: Jwt): BookingResponse =
        bookingService.create(request, jwt.username())

    // ---- READ ---- (REQUESTER: solo las suyas)
    @GetMapping("/me")
    fun mine(@AuthenticationPrincipal jwt: Jwt): List<BookingResponse> =
        bookingService.findMine(jwt.username())

    // ---- READ ---- (REQUESTER dueno o STAFF; filtrado dentro del service)
    @GetMapping("/{id}")
    fun one(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): BookingResponse =
        bookingService.findOne(id, jwt.username(), jwt.isStaff())

    // ---- READ ---- (solo STAFF; Criterio 8: control de acceso a nivel de metodo)
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    fun all(): List<BookingResponse> = bookingService.findAll()

    // ---- UPDATE ---- (REQUESTER, solo el dueno)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: BookingUpdateRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): BookingResponse = bookingService.update(id, request, jwt.username())

    // ---- DELETE ---- (REQUESTER, solo el dueno)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt) =
        bookingService.delete(id, jwt.username())

    // ---- Transiciones exclusivas de STAFF ----
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('STAFF')")
    fun approve(@PathVariable id: Long, @Valid @RequestBody request: BookingStatusRequest): BookingResponse =
        bookingService.changeStatus(id, request)

    @PatchMapping("/{id}/attended")
    @PreAuthorize("hasRole('STAFF')")
    fun markAttended(@PathVariable id: Long): BookingResponse =
        bookingService.changeStatus(id, BookingStatusRequest("ATTENDED"))

    // LA LINEA. Aqui se decide quien eres, en toda la aplicacion.
    private fun Jwt.username(): String = getClaimAsString("username") ?: subject

    private fun Jwt.isStaff(): Boolean =
        (getClaimAsStringList("cognito:groups") ?: emptyList()).contains("STAFF")
}
