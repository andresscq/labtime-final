// Cliente HTTP unico para todo el backend. Como nginx unifica rooms-service y
// bookings-service bajo un solo origen (VITE_API_BASE_URL), la app movil no
// necesita saber que son dos microservicios distintos — esa es justamente la
// ventaja del reverse proxy.

import { getAccessToken, logout } from "./auth";
import type { Booking, EquipmentCatalogItem, EquipmentRequestItem, Room, TimeSlot } from "../types/models";

const BASE_URL = import.meta.env.VITE_API_BASE_URL as string;

class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const response = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (response.status === 401) {
    // El access_token expiro o es invalido: no adivinamos, mandamos a login de nuevo.
    logout();
    throw new ApiError(401, "Sesion expirada");
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }));
    throw new ApiError(response.status, body.message ?? `Error ${response.status}`);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

// ---- rooms-service (publico para GET, STAFF para escritura) ----
export const RoomsApi = {
  list: () => request<Room[]>("/rooms"),
  get: (id: number) => request<Room>(`/rooms/${id}`),
  create: (data: Omit<Room, "id" | "timeSlots">) =>
    request<Room>("/rooms", { method: "POST", body: JSON.stringify(data) }),
  update: (id: number, data: Omit<Room, "id" | "timeSlots">) =>
    request<Room>(`/rooms/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  remove: (id: number) => request<void>(`/rooms/${id}`, { method: "DELETE" }),

  // Horario disponible (solo lo libre): GET /rooms/{id} ya trae el horario
  // COMPLETO embebido en room.timeSlots; esto queda para cuando de verdad
  // solo se necesita lo reservable, sin lo ocupado.
  availableSlots: (roomId: number) =>
    request<TimeSlot[]>(`/rooms/${roomId}/slots/available`),
  createSlot: (data: { roomId: number; startsAt: string; endsAt: string }) =>
    request<TimeSlot>("/slots", { method: "POST", body: JSON.stringify(data) }),
  updateSlot: (id: number, data: { roomId: number; startsAt: string; endsAt: string }) =>
    request<TimeSlot>(`/slots/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  removeSlot: (id: number) => request<void>(`/slots/${id}`, { method: "DELETE" }),
};

// ---- bookings-service (REQUESTER crea/gestiona lo suyo, STAFF aprueba) ----
export const BookingsApi = {
  create: (data: { slotId: number; purpose: string }) =>
    request<Booking>("/bookings", { method: "POST", body: JSON.stringify(data) }),
  mine: () => request<Booking[]>("/bookings/me"),
  get: (id: number) => request<Booking>(`/bookings/${id}`),
  all: () => request<Booking[]>("/bookings"), // solo STAFF (filtrado server-side)
  update: (id: number, data: { purpose: string }) =>
    request<Booking>(`/bookings/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  cancel: (id: number) => request<void>(`/bookings/${id}`, { method: "DELETE" }),
  approve: (id: number, status: "APPROVED" | "REJECTED") =>
    request<Booking>(`/bookings/${id}/approve`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  markAttended: (id: number) =>
    request<Booking>(`/bookings/${id}/attended`, { method: "PATCH" }),
};

export const EquipmentApi = {
  // GET publico: el frontend arma el dropdown con esto, nunca deja escribir el nombre.
  catalog: () => request<EquipmentCatalogItem[]>("/equipment-catalog"),
  create: (data: { bookingId: number; equipment: string; quantity: number }) =>
    request<EquipmentRequestItem>("/equipment-requests", {
      method: "POST",
      body: JSON.stringify(data),
    }),
  byBooking: (bookingId: number) =>
    request<EquipmentRequestItem[]>(`/equipment-requests/booking/${bookingId}`),
  remove: (id: number) => request<void>(`/equipment-requests/${id}`, { method: "DELETE" }),
};

export { ApiError };
