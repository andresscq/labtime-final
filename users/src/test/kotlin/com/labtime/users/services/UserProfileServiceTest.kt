package com.labtime.users.services

import com.labtime.users.dto.UserProfileRequest
import com.labtime.users.entities.UserProfile
import com.labtime.users.exceptions.UserProfileAlreadyExistsException
import com.labtime.users.exceptions.UserProfileNotFoundException
import com.labtime.users.repositories.UserProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserProfileServiceTest {

    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var userProfileService: UserProfileService

    @BeforeEach
    fun setUp() {
        userProfileRepository = mock()
        userProfileService = UserProfileService(userProfileRepository)
    }

    private fun perfilDeAna() = UserProfile(
        cognitoSub = "sub-ana", fullName = "Ana Perez", email = "ana@puce.edu.ec",
        phone = null, id = 1
    )

    // ---------- Camino feliz ----------

    @Test
    fun `crear un perfil lo firma con el sub del token, nunca del body`() {
        whenever(userProfileRepository.existsByCognitoSub("sub-ana")).thenReturn(false)
        whenever(userProfileRepository.save(any<UserProfile>())).thenAnswer { it.arguments[0] as UserProfile }

        val perfil = userProfileService.create(
            UserProfileRequest("Ana Perez", "ana@puce.edu.ec", null), "sub-ana"
        )

        assertEquals("sub-ana", perfil.cognitoSub)
    }

    @Test
    fun `leer mi perfil lo busca por el sub, no por id`() {
        whenever(userProfileRepository.findByCognitoSub("sub-ana")).thenReturn(perfilDeAna())

        val perfil = userProfileService.findMine("sub-ana")

        assertEquals("Ana Perez", perfil.fullName)
    }

    @Test
    fun `puedo actualizar mi propio perfil`() {
        whenever(userProfileRepository.findByCognitoSub("sub-ana")).thenReturn(perfilDeAna())
        whenever(userProfileRepository.save(any<UserProfile>())).thenAnswer { it.arguments[0] as UserProfile }

        val actualizado = userProfileService.updateMine(
            UserProfileRequest("Ana P.", "ana.p@puce.edu.ec", "0999999999"), "sub-ana"
        )

        assertEquals("Ana P.", actualizado.fullName)
        assertEquals("0999999999", actualizado.phone)
    }

    // ---------- Errores ----------

    @Test
    fun `crear un perfil que ya existe da 409`() {
        whenever(userProfileRepository.existsByCognitoSub("sub-ana")).thenReturn(true)

        assertThrows(UserProfileAlreadyExistsException::class.java) {
            userProfileService.create(UserProfileRequest("Ana", "a@x.com", null), "sub-ana")
        }
    }

    @Test
    fun `leer un perfil que no existe da 404`() {
        whenever(userProfileRepository.findByCognitoSub("sub-fantasma")).thenReturn(null)

        assertThrows(UserProfileNotFoundException::class.java) {
            userProfileService.findMine("sub-fantasma")
        }
    }
}
