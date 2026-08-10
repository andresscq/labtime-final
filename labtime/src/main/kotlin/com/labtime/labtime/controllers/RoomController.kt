package com.labtime.labtime.controllers

import com.labtime.labtime.dto.RoomRequest
import com.labtime.labtime.dto.RoomResponse
import com.labtime.labtime.services.RoomService
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
@RequestMapping("/rooms")
class RoomController(private val roomService: RoomService) {

    // Publico: catalogo de salas.
    @GetMapping
    fun findAll(): List<RoomResponse> = roomService.findAll()

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): RoomResponse = roomService.findById(id)

    // Privado, solo STAFF (Criterio 8: control de acceso a nivel de metodo).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STAFF')")
    fun create(@Valid @RequestBody request: RoomRequest): RoomResponse = roomService.create(request)

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: RoomRequest): RoomResponse =
        roomService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('STAFF')")
    fun delete(@PathVariable id: Long) = roomService.delete(id)
}
