package com.labtime.users.mappers

import com.labtime.users.dto.UserProfileRequest
import com.labtime.users.dto.UserProfileResponse
import com.labtime.users.entities.UserProfile

fun UserProfileRequest.toEntity(cognitoSub: String) = UserProfile(
    cognitoSub = cognitoSub,
    fullName = this.fullName,
    email = this.email,
    phone = this.phone,
    faculty = this.faculty
)

fun UserProfile.toResponse() = UserProfileResponse(
    id = this.id,
    cognitoSub = this.cognitoSub,
    fullName = this.fullName,
    email = this.email,
    phone = this.phone,
    faculty = this.faculty,
    createdAt = this.createdAt
)
