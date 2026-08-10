package com.labtime.labtime.controllers

import com.labtime.labtime.dto.EquipmentRequestRequest
import com.labtime.labtime.dto.EquipmentRequestResponse
import com.labtime.labtime.services.EquipmentRequestService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/equipment-requests")
class EquipmentRequestController(private val equipmentRequestService: EquipmentRequestService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: EquipmentRequestRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): EquipmentRequestResponse = equipmentRequestService.create(request, jwt.username())

    @GetMapping("/booking/{bookingId}")
    fun byBooking(
        @PathVariable bookingId: Long,
        @AuthenticationPrincipal jwt: Jwt
    ): List<EquipmentRequestResponse> =
        equipmentRequestService.findByBooking(bookingId, jwt.username(), jwt.isStaff())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt) =
        equipmentRequestService.delete(id, jwt.username())

    private fun Jwt.username(): String = getClaimAsString("username") ?: subject

    private fun Jwt.isStaff(): Boolean =
        (getClaimAsStringList("cognito:groups") ?: emptyList()).contains("STAFF")
}
