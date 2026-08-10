package com.labtime.labtime.exceptions

// --- rooms / time_slots ---
class RoomNotFoundException(message: String) : RuntimeException(message)
class TimeSlotNotFoundException(message: String) : RuntimeException(message)
class InvalidTimeRangeException(message: String) : RuntimeException(message)

// 409: ya existe una sala con ese nombre (comparacion sin distinguir mayusculas/minusculas).
class RoomNameAlreadyExistsException(message: String) : RuntimeException(message)

// 400: el purpose de la reserva llego vacio.
class InvalidBookingPurposeException(message: String) : RuntimeException(message)

// 400: el slot referenciado ya paso (startsAt anterior a ahora).
class SlotInThePastException(message: String) : RuntimeException(message)

// 409: hay time_slots/bookings/equipment_requests que todavia dependen de este
// recurso (FK real en Postgres) — borrar rompería la relacion 1:N.
class RoomHasTimeSlotsException(message: String) : RuntimeException(message)
class TimeSlotHasBookingsException(message: String) : RuntimeException(message)
class BookingHasEquipmentRequestsException(message: String) : RuntimeException(message)

// --- bookings ---
// 404: esa reserva no existe. Ni para ti, ni para nadie.
class BookingNotFoundException(message: String) : RuntimeException(message)

// 403: la reserva existe perfectamente, sabemos quien eres, y no es tuya.
class NotYourBookingException(message: String) : RuntimeException(message)

// 409: alguien ya reservo ese horario (choque de concurrencia).
class SlotAlreadyBookedException(message: String) : RuntimeException(message)

// 400: el slot referenciado no existe o no esta disponible.
class SlotNotAvailableException(message: String) : RuntimeException(message)

// --- equipment_requests ---
class EquipmentRequestNotFoundException(message: String) : RuntimeException(message)

// 400: la cantidad pedida es <1 o supera el stock total de ese equipo en el catalogo.
class InvalidEquipmentQuantityException(message: String) : RuntimeException(message)
