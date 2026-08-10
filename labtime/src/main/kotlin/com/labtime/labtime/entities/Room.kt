package com.labtime.labtime.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "rooms", uniqueConstraints = [UniqueConstraint(columnNames = ["name"])])
class Room(

    @Column(unique = true)
    var name: String,

    var roomType: String, // "LAB" o "AULA"

    var capacity: Int,

    var building: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
