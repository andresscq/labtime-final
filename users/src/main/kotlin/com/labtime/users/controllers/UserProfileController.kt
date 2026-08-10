package com.labtime.users.controllers

import com.labtime.users.dto.UserProfileRequest
import com.labtime.users.dto.UserProfileResponse
import com.labtime.users.services.UserProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserProfileController(private val userProfileService: UserProfileService) {

    // ---- CREATE ---- (cualquier usuario autenticado, una sola vez para si mismo)
    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: UserProfileRequest, @AuthenticationPrincipal jwt: Jwt): UserProfileResponse =
        userProfileService.create(request, jwt.subject)

    // ---- READ ---- (el propio perfil)
    @GetMapping("/me")
    fun mine(@AuthenticationPrincipal jwt: Jwt): UserProfileResponse =
        userProfileService.findMine(jwt.subject)

    // ---- UPDATE ---- (el propio perfil)
    @PutMapping("/me")
    fun updateMine(@Valid @RequestBody request: UserProfileRequest, @AuthenticationPrincipal jwt: Jwt): UserProfileResponse =
        userProfileService.updateMine(request, jwt.subject)

    // ---- READ ---- (solo STAFF; control de acceso a nivel de metodo, Criterio 8)
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    fun all(): List<UserProfileResponse> = userProfileService.findAll()
}
