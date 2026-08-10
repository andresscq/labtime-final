package com.labtime.users.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

// Perfil extendido del usuario. Cognito ya es dueno de la identidad
// (username, password, email verificado, rol via cognito:groups); este
// microservicio guarda SOLO lo que Cognito no guarda: datos de contacto y
// preferencias propias del dominio LabTime.
@Entity
@Table(name = "user_profiles", uniqueConstraints = [UniqueConstraint(columnNames = ["cognitoSub"])])
class UserProfile(

    // El "sub" del token de Cognito: identifica al usuario de forma unica e
    // inmutable. Nunca se usa el username/email como clave, porque esos SI
    // pueden cambiar en Cognito.
    val cognitoSub: String,

    var fullName: String,

    var email: String,

    var phone: String? = null,

    // Dato adicional pedido por el profesor: facultad/carrera del docente o
    // estudiante. Texto libre, opcional — no forma parte de la identidad
    // (eso lo sigue manejando Cognito), es informacion propia de LabTime.
    var faculty: String? = null,

    // El rol NO se guarda aqui. Cognito es la UNICA fuente de verdad del rol
    // (claim "cognito:groups" del JWT) — duplicarlo en esta tabla creaba el
    // riesgo de que quedara desactualizado si alguien cambia de grupo en
    // Cognito. Cualquier endpoint que necesite el rol lo lee del JWT en cada
    // request, nunca de aqui.
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
