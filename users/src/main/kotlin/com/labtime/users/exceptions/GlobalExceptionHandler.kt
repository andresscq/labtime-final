package com.labtime.users.exceptions

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(UserProfileNotFoundException::class)
    fun handleNotFound(e: UserProfileNotFoundException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, e.message ?: "Profile not found")

    @ExceptionHandler(UserProfileAlreadyExistsException::class)
    fun handleConflict(e: UserProfileAlreadyExistsException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.CONFLICT, e.message ?: "Profile already exists")

    // 400: un @NotBlank/@Email del DTO no se cumplio (ej. fullName="" o un
    // email con formato invalido). Junta todos los campos que fallaron en un
    // solo mensaje legible.
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ExceptionResponse> {
        val detalle = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return respond(HttpStatus.BAD_REQUEST, "Validation failed — $detalle")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, "Request body is missing or has an invalid value for a required field")

    private fun respond(status: HttpStatus, message: String): ResponseEntity<ExceptionResponse> {
        logger.warn("event=request.rejected | msg=$message | source=UserProfileService | status=${status.value()}")
        return ResponseEntity.status(status).body(ExceptionResponse(message))
    }
}

data class ExceptionResponse(val message: String)
