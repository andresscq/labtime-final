// Tipos que reflejan 1:1 los DTOs Response del backend (rooms-service / bookings-service).

export interface Room {
  id: number;
  name: string;
  roomType: string; // "LAB" | "AULA"
  capacity: number;
  building: string;
  // Horario COMPLETO embebido por el backend (ocupado y libre) — una sola
  // llamada a GET /rooms trae todo el detalle, sin pedir los slots aparte.
  timeSlots: TimeSlot[];
}

export interface TimeSlot {
  id: number;
  roomId: number;
  startsAt: string;  // ISO LocalDateTime
  endsAt: string;
  available: boolean;
}

export type BookingStatus = "PENDING" | "APPROVED" | "REJECTED" | "ATTENDED" | "CANCELLED";

export interface Booking {
  id: number;
  // Antes era solo slotId (number); el backend ahora manda el TimeSlot completo.
  slot: TimeSlot;
  requesterUsername: string;
  purpose: string;
  status: BookingStatus;
  createdAt: string;
}

// Codigos del catalogo fijo de equipos (enum Equipment en el backend). Si se
// agrega un equipo nuevo alla, hay que agregarlo aqui tambien.
export type EquipmentCode =
  | "PROJECTOR"
  | "LAPTOP"
  | "HDMI_CABLE"
  | "WHITEBOARD_MARKER"
  | "EXTENSION_CORD"
  | "SPEAKER";

export interface EquipmentCatalogItem {
  code: EquipmentCode;
  displayName: string;
  totalStock: number;
}

export interface EquipmentRequestItem {
  id: number;
  bookingId: number;
  equipment: EquipmentCode;
  equipmentDisplayName: string;
  quantity: number;
}

export interface CognitoIdTokenClaims {
  sub: string;
  username?: string;
  "cognito:groups"?: string[];
  email?: string;
  exp: number;
}
