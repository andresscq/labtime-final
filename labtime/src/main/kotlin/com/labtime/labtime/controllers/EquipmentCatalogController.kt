package com.labtime.labtime.controllers

import com.labtime.labtime.dto.EquipmentCatalogItem
import com.labtime.labtime.entities.Equipment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Publico: el frontend consume esto para armar el dropdown de equipos y NUNCA
// deja que el usuario escriba el nombre a mano. El catalogo real vive en el
// enum Equipment (en el codigo), esto solo lo expone como JSON.
@RestController
@RequestMapping("/equipment-catalog")
class EquipmentCatalogController {

    @GetMapping
    fun list(): List<EquipmentCatalogItem> =
        Equipment.entries.map { EquipmentCatalogItem(it.name, it.displayName, it.totalStock) }
}
