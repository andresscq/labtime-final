package com.labtime.labtime.controllers

import tools.jackson.databind.ObjectMapper
import com.labtime.labtime.config.MdcSubFilter
import com.labtime.labtime.config.SecurityConfig
import com.labtime.labtime.dto.RoomRequest
import com.labtime.labtime.dto.RoomResponse
import com.labtime.labtime.services.RoomService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Pruebas de integracion de seguridad (Criterio 6: casos de autorizacion 401/403;
 * Criterio 8: valida que el @PreAuthorize de RoomController realmente bloquee).
 *
 * No levanta base de datos ni Cognito real: RoomService se mockea y el token se
 * simula con SecurityMockMvcRequestPostProcessors.jwt(), que inyecta directamente
 * las authorities en el SecurityContext de la request (mismo mecanismo con el que
 * SecurityConfig traduce "cognito:groups" -> "ROLE_STAFF"/"ROLE_REQUESTER").
 *
 *  - Sin token            -> 401 (lo genera Spring Security al exigir autenticacion)
 *  - Token, rol equivocado -> 403 (lo genera @PreAuthorize("hasRole('STAFF')"))
 *  - Token, rol correcto   -> 2xx
 */
@WebMvcTest(controllers = [RoomController::class])
@Import(SecurityConfig::class, MdcSubFilter::class)
class RoomControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var roomService: RoomService

    private val roomRequest = RoomRequest(
        name = "Lab 301",
        roomType = "COMPUTER_LAB",
        capacity = 30,
        building = "Block A"
    )

    private val roomResponse = RoomResponse(
        id = 1L,
        name = "Lab 301",
        roomType = "COMPUTER_LAB",
        capacity = 30,
        building = "Block A"
    )

    // ---- GET /rooms es publico: no requiere token ----
    @Test
    fun `GET rooms is public and returns 200 without a token`() {
        whenever(roomService.findAll()).thenReturn(listOf(roomResponse))

        mockMvc.perform(get("/rooms"))
            .andExpect(status().isOk)
    }

    // ---- POST /rooms sin token -> 401 ----
    @Test
    fun `POST rooms without a token returns 401`() {
        mockMvc.perform(
            post("/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roomRequest))
        ).andExpect(status().isUnauthorized)
    }

    // ---- POST /rooms con rol REQUESTER -> 403 (STAFF requerido) ----
    @Test
    fun `POST rooms with REQUESTER role returns 403`() {
        mockMvc.perform(
            post("/rooms")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_REQUESTER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roomRequest))
        ).andExpect(status().isForbidden)
    }

    // ---- POST /rooms con nombre vacio -> 400, con mensaje claro de "obligatorio" ----
    @Test
    fun `POST rooms with blank name returns 400 with a clear message`() {
        val invalido = roomRequest.copy(name = "")

        mockMvc.perform(
            post("/rooms")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalido))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name is required")))
    }

    // ---- POST /rooms con rol STAFF -> 201 ----
    @Test
    fun `POST rooms with STAFF role returns 201`() {
        whenever(roomService.create(any())).thenReturn(roomResponse)

        mockMvc.perform(
            post("/rooms")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roomRequest))
        ).andExpect(status().isCreated)
    }
}
