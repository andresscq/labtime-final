package com.labtime.users.repositories

import com.labtime.users.entities.UserProfile
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileRepository : JpaRepository<UserProfile, Long> {
    fun findByCognitoSub(cognitoSub: String): UserProfile?
    fun existsByCognitoSub(cognitoSub: String): Boolean
}
