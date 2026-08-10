package com.labtime.users.controllers

import tools.jackson.databind.ObjectMapper
import com.labtime.users.config.MdcSubFilter
import com.labtime.users.config.SecurityConfig
import com.labtime.users.dto.UserProfileRequest
import com.labtime.users.dto.UserProfileResponse
import com.labtime.users.services.UserProfileService
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
import java.time.LocalDateTime

/**
 * Pruebas de integracion de seguridad (Criterio 6: casos 401/403; Criterio 8:
 * valida que el @PreAuthorize de UserProfileController.all() realmente bloquee).
 *
 *  - Sin token             -> 401
 *  - Token, rol equivocado -> 403 (GET /users es solo STAFF)
 *  - Token, rol correcto   -> 200
 */
@WebMvcTest(controllers = [UserProfileController::class])
@Import(SecurityConfig::class, MdcSubFilter::class)
class UserProfileControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var userProfileService: UserProfileService

    private val profileResponse = UserProfileResponse(
        id = 1L,
        cognitoSub = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        fullName = "Jane Doe",
        email = "jane@example.com",
        phone = null,
        faculty = null,
        createdAt = LocalDateTime.now()
    )

    // ---- GET /users/me: cualquier usuario autenticado, pero requiere token ----
    @Test
    fun `GET users me without a token returns 401`() {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET users me with a valid token returns 200`() {
        whenever(userProfileService.findMine(any())).thenReturn(profileResponse)

        mockMvc.perform(
            get("/users/me").with(jwt().authorities(SimpleGrantedAuthority("ROLE_REQUESTER")))
        ).andExpect(status().isOk)
    }

    // ---- GET /users (listado completo): solo STAFF ----
    @Test
    fun `GET users without a token returns 401`() {
        mockMvc.perform(get("/users"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET users with REQUESTER role returns 403`() {
        mockMvc.perform(
            get("/users").with(jwt().authorities(SimpleGrantedAuthority("ROLE_REQUESTER")))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `GET users with STAFF role returns 200`() {
        whenever(userProfileService.findAll()).thenReturn(listOf(profileResponse))

        mockMvc.perform(
            get("/users").with(jwt().authorities(SimpleGrantedAuthority("ROLE_STAFF")))
        ).andExpect(status().isOk)
    }

    // ---- POST /users/me con fullName vacio -> 400, con mensaje claro de "obligatorio" ----
    @Test
    fun `POST users me with blank fullName returns 400 with a clear message`() {
        val invalido = UserProfileRequest(fullName = "", email = "jane@example.com", phone = null)

        mockMvc.perform(
            post("/users/me")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_REQUESTER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalido))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("fullName is required")))
    }
}
