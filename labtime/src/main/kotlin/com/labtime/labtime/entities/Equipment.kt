package com.labtime.labtime.entities

// Catalogo fijo del equipo que LabTime realmente tiene disponible para
// prestar. Vive en el codigo (no en la base de datos) porque asi lo pidio
// el profesor: el usuario ya NO escribe el nombre del equipo a mano, elige
// una de estas opciones. Cambiar el inventario implica un nuevo deploy.
enum class Equipment(val displayName: String, val totalStock: Int) {
    PROJECTOR("Proyector", 10),
    LAPTOP("Laptop", 15),
    HDMI_CABLE("Cable HDMI", 20),
    WHITEBOARD_MARKER("Marcador de pizarra", 50),
    EXTENSION_CORD("Extension electrica", 8),
    SPEAKER("Parlante", 6)
}
