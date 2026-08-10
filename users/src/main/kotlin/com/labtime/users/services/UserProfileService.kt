package com.labtime.users.services

import com.labtime.users.dto.UserProfileRequest
import com.labtime.users.dto.UserProfileResponse
import com.labtime.users.exceptions.UserProfileAlreadyExistsException
import com.labtime.users.exceptions.UserProfileNotFoundException
import com.labtime.users.mappers.toEntity
import com.labtime.users.mappers.toResponse
import com.labtime.users.repositories.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserProfileService(
    private val userProfileRepository: UserProfileRepository
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)

    // ---- CREATE ---- (se llama una vez, tras el primer login exitoso en Cognito)
    fun create(request: UserProfileRequest, cognitoSub: String): UserProfileResponse {
        if (userProfileRepository.existsByCognitoSub(cognitoSub)) {
            logger.warn("event=user.rejected | msg=Profile already exists")
            throw UserProfileAlreadyExistsException("A profile already exists for this user")
        }
        val saved = userProfileRepository.save(request.toEntity(cognitoSub))
        logger.info("event=user.created | msg=User profile created | userId=${saved.id}")
        return saved.toResponse()
    }

    // ---- READ ---- (el propio perfil, resuelto por el sub del JWT)
    fun findMine(cognitoSub: String): UserProfileResponse =
        findOrThrow(cognitoSub).toResponse()

    // ---- READ ---- (solo STAFF: listado completo, para asignar/consultar roles)
    fun findAll(): List<UserProfileResponse> =
        userProfileRepository.findAll().map { it.toResponse() }

    // ---- UPDATE ---- (el propio perfil)
    fun updateMine(request: UserProfileRequest, cognitoSub: String): UserProfileResponse {
        val profile = findOrThrow(cognitoSub)
        profile.fullName = request.fullName
        profile.email = request.email
        profile.phone = request.phone
        profile.faculty = request.faculty
        val saved = userProfileRepository.save(profile)
        logger.info("event=user.updated | msg=User profile updated | userId=${saved.id}")
        return saved.toResponse()
    }

    private fun findOrThrow(cognitoSub: String) =
        userProfileRepository.findByCognitoSub(cognitoSub)
            ?: run {
                logger.warn("event=user.not_found | msg=User profile not found")
                throw UserProfileNotFoundException("No profile exists for this user")
            }
}
