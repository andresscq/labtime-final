package com.labtime.users.exceptions

// 404: no existe perfil para ese usuario todavia (no se ha llamado a /users/me con PUT).
class UserProfileNotFoundException(message: String) : RuntimeException(message)

// 409: ya existe un perfil para ese cognitoSub (create llamado dos veces).
class UserProfileAlreadyExistsException(message: String) : RuntimeException(message)
