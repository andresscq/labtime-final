# LabTime — Mapeo del backend contra la rúbrica P01

Este documento explica **qué archivo de código cubre cada criterio** de la rúbrica de
Evaluación de Proyecto Integrador (PUCE TEC), con énfasis en **3. Arquitectura Empresarial (/8)**,
que es la sección que corresponde a este backend.

Estructura: dos microservicios Kotlin + Spring Boot 4, siguiendo el mismo patrón del sandbox
`demo_microservices` (mismo stack, mismas capas, mismo estilo de autorización por propiedad),
cada uno con su propia base de datos Postgres, detrás de un reverse proxy nginx:

```
                    ┌──────────────────────────┐
                    │      AWS Cognito         │
                    │  User Pool: us-east-1_4B0CaMgZv │
                    └────────────┬─────────────┘
                                 │ emite JWT
                                 ▼
cliente ──► http://localhost:9090 ──► nginx (reverse proxy)
                                         │
                    ┌────────────────────┴────────────────────┐
                    │                                          │
                 /users                              /rooms, /slots, /bookings,
                    ▼                                 /equipment-requests
        ┌───────────────────────┐                  ┌────────────────────────┐
        │   users               │                  │   labtime              │
        │   Spring Boot :8080   │                  │   Spring Boot :8081    │
        │   Postgres (usersdb)  │                  │   Postgres (labtimedb) │
        │   user_profiles       │                  │   rooms, time_slots,   │
        │                       │                  │   bookings,            │
        │                       │                  │   equipment_requests   │
        └───────────────────────┘                  └────────────────────────┘
```

**Nota de arquitectura (ver comentario en `BookingService.kt`):** en una iteración anterior
`rooms` y `bookings` eran dos microservicios separados que se hablaban por HTTP
(`RoomsServiceClient`) y usaban H2 en memoria. Se fusionaron en un solo microservicio `labtime`
con Postgres persistente para cumplir la estructura exigida por la rúbrica (un microservicio
`users` + un microservicio propio del dominio). Esa llamada HTTP entre servicios se volvió una
llamada normal en el mismo proceso (`BookingService` → `TimeSlotRepository`/`TimeSlotService`).
Al fusionarse, las relaciones se migraron de `Long` planos a **FK reales de JPA** (`@ManyToOne`
+ `@JoinColumn`, con constraint `FOREIGN KEY` en Postgres) — ver 3.1.

---

## 3. Arquitectura Empresarial — /8 (el foco de este documento)

### 3.1 Modelo de datos y dominio — /1
*"Entidades bien identificadas, relaciones correctas (1:N, N:M) y coherencia entre diseño e implementación."*

- 4 entidades de dominio en el microservicio `labtime` (el microservicio `users` aporta la quinta,
  `UserProfile`, ligada a Cognito por `cognitoSub`, no por FK de dominio): `Room`, `TimeSlot`,
  `Booking`, `EquipmentRequest`.
- Relaciones 1:N implementadas con JPA real (`@ManyToOne(fetch = LAZY)` + `@JoinColumn`), con
  constraint `FOREIGN KEY` verificado en Postgres (`\d time_slots` muestra
  `FOREIGN KEY (room_id) REFERENCES rooms(id)`, y análogo para las otras dos):
  - `rooms` 1 → N `time_slots` (`TimeSlot.room`, columna `room_id`)
  - `time_slots` 1 → N `bookings` (`Booking.slot`, columna `slot_id`)
  - `bookings` 1 → N `equipment_requests` (`EquipmentRequest.booking`, columna `booking_id`)
- Los DTOs (`RoomResponse`, `BookingResponse`, etc.) siguen exponiendo `roomId`/`slotId`/`bookingId`
  como `Long` plano — el contrato HTTP no cambia, solo la modelación interna en JPA.
- Integridad referencial en el `delete` de cada entidad "1": antes de borrar, el service consulta
  si todavía tiene hijos (`existsByRoom_Id`, `existsBySlot_Id`, `existsByBooking_Id`) y responde
  **409** con una excepción propia (`RoomHasTimeSlotsException`, etc.) en vez de dejar que suba un
  error crudo de la base de datos cuando la FK lo bloquee.
- Archivos: `entities/Room.kt`, `entities/TimeSlot.kt`, `entities/Booking.kt`, `entities/EquipmentRequest.kt`

### 3.2 Organización en capas — /2
*"Separación correcta en controller, service, repository, con DTOs, mappers y entities. Cada capa asume solo su responsabilidad."*

Ambos microservicios replican la misma estructura de carpetas que el sandbox:

```
controllers/   -> reciben el HTTP, extraen el JWT, delegan al service. CERO lógica de negocio aquí.
services/      -> TODA la lógica de negocio y las reglas de autorización por propiedad viven aquí.
repositories/  -> interfaces JpaRepository, sin lógica.
entities/      -> clases @Entity, solo estructura de datos.
dto/           -> Request/Response, nunca se exponen las entities directamente.
mappers/       -> funciones de extensión toEntity()/toResponse(), la única frontera entre DTO y Entity.
exceptions/    -> excepciones propias + GlobalExceptionHandler (@RestControllerAdvice).
config/        -> SecurityConfig (JWT + roles vía @PreAuthorize) + MdcSubFilter (logging).
```

No existe una capa `clients/`: al fusionar `rooms` y `bookings` en el microservicio `labtime`
(ver nota de arquitectura arriba), la llamada HTTP entre microservicios que antes hacía ese rol
desapareció — `BookingService` ahora depende directo de `TimeSlotRepository`/`TimeSlotService`
dentro del mismo proceso.

Ejemplo de la separación en la práctica: `BookingController.create()` no valida nada — solo
extrae `jwt.username()` y llama a `bookingService.create(...)`. Toda la validación (slot existe,
está disponible, no hay choque de concurrencia) vive en `BookingService.create()`.

### 3.3 Lógica de negocio y manejo de errores — /2
*"Cumplimiento de las reglas del dominio, validaciones y manejo de excepciones consistente (excepciones propias + manejador global). Respuestas HTTP adecuadas."*

Cada microservicio tiene su propio `GlobalExceptionHandler` con `@RestControllerAdvice`, igual
que `users`/`diary` en el sandbox:

| Excepción | HTTP | Dónde se lanza |
|---|---|---|
| `RoomNotFoundException` / `TimeSlotNotFoundException` | 404 | `RoomService` / `TimeSlotService` (`labtime`) |
| `InvalidTimeRangeException` | 400 | `TimeSlotService` (endsAt debe ser posterior a startsAt) |
| `BookingNotFoundException` | 404 | `BookingService` |
| `NotYourBookingException` | **403** | `BookingService.findOwnedOrThrow()` — el corazón de la autorización por propiedad |
| `SlotAlreadyBookedException` | **409** | `BookingService.create()` — guarda de concurrencia |
| `SlotNotAvailableException` | 400 | `BookingService.create()` — el slot no existe o ya no está libre |
| `EquipmentRequestNotFoundException` | 404 | `EquipmentRequestService` |
| `RoomHasTimeSlotsException` | **409** | `RoomService.delete()` — la sala todavía tiene horarios (FK real) |
| `TimeSlotHasBookingsException` | **409** | `TimeSlotService.delete()` — el horario todavía tiene reservas (FK real) |
| `BookingHasEquipmentRequestsException` | **409** | `BookingService.delete()` — la reserva todavía tiene equipo pedido (FK real) |

La regla de negocio central del proyecto —evitar que dos personas reserven el mismo horario—
vive en `BookingService.create()`: consulta `existsBySlotIdAndStatusIn(slotId, [PENDING, APPROVED])`
antes de guardar, dentro de una transacción `@Transactional`.

### 3.4 Calidad y legibilidad del código — /1
*"Código limpio, nombres expresivos, sin duplicación, uso idiomático de Kotlin y buenas prácticas de Spring Boot."*

- Mappers como funciones de extensión (`fun BookingRequest.toEntity(...)`), no clases `@Component`
  innecesarias, siguiendo el estilo idiomático que ya usa `users-service` en el sandbox.
- Un solo punto de verdad para "traer un recurso propio": `findOwnedOrThrow()` en `BookingService`,
  igual que `findMineOrThrow()` en `EntryService` del sandbox — evita repetir el `if` de propiedad
  en cada método.
- DTOs separados de entities siempre: ningún controller recibe ni devuelve una `@Entity` directamente.

### 3.5 Pruebas unitarias y funcionales — /1
*"Cobertura de la lógica relevante (services, mappers, validaciones) con casos válidos e inválidos. Uso correcto de mocks/stubs y aserciones significativas."*

Dos niveles de test, ambos con JUnit 5 + Mockito-Kotlin (`mock()` + `whenever()` + `verify()`):

- **`services/*ServiceTest.kt`** (`BookingServiceTest`, `RoomServiceTest`, `TimeSlotServiceTest`,
  `EquipmentRequestServiceTest` en `labtime`; `UserProfileServiceTest` en `users`):
  - **Camino feliz**: crear, listar, editar y borrar el recurso propio.
  - **🔒 Autorización por propiedad**: no puedo ver/editar/borrar el recurso de otro (403), STAFF sí puede.
  - **404 real**: un recurso inexistente da 404, no 403 (para no confundir "no existe" con "no es tuyo").
  - **Concurrencia** (`BookingServiceTest`): no se puede reservar un slot ya ocupado, ni uno con una reserva activa (409).
- **`controllers/*ControllerSecurityTest.kt`** (`RoomControllerSecurityTest` en `labtime`,
  `UserProfileControllerSecurityTest` en `users`), Criterio 6 y Criterio 8: `@WebMvcTest` +
  `MockMvc`, con `SecurityMockMvcRequestPostProcessors.jwt()` para simular el token de Cognito sin
  levantar base de datos ni Cognito real:
  - Sin token → 401.
  - Token con rol equivocado → 403 (confirma que el `@PreAuthorize` del controller bloquea de verdad).
  - Token con rol correcto → 200/201.

### 3.6 Autenticación y Autorización — /1
*"Acceso protegido correctamente (JWT/Cognito). Endpoints públicos y privados bien delimitados; tokens validados. Restricción según rol/permiso. Un usuario sin permiso recibe 401/403 adecuado."*

- Ambos servicios son **OAuth2 Resource Server**: `SecurityConfig` valida el JWT contra el
  `issuer-uri` del User Pool de Cognito (`us-east-1_4B0CaMgZv`), exactamente como en el sandbox.
- **Autorización por rol**: `cognito:groups` del JWT se traduce a `ROLE_STAFF` / `ROLE_REQUESTER`
  vía un `JwtAuthenticationConverter` personalizado (`cognitoGroupsConverter()`). `SecurityConfig`
  solo decide qué es público (`GET /rooms/**`, `GET /slots/**`); el resto de las reglas de rol
  vive como `@PreAuthorize("hasRole('STAFF')")` en cada endpoint del controller (`@EnableMethodSecurity`),
  más cerca de lo que protege y más fácil de auditar (Criterio 8).
- **Autorización por propiedad**: no la hace Spring Security — la hace el service
  (`findOwnedOrThrow`), exactamente como advierte el comentario del sandbox: *"Spring Security
  verifica la firma, el emisor y la expiración del token, y dice 'adelante'. Saber de quién es la
  fila 1 no es su trabajo: es el tuyo."*
- 401 vs 403 quedan diferenciados en la práctica: sin token → 401 (lo genera Spring Security al
  fallar la validación del JWT); token válido pero rol equivocado → 403 (Spring Security, vía
  `hasRole`); token y rol correctos pero recurso ajeno → 403 (nuestro `GlobalExceptionHandler`,
  vía `NotYourBookingException`).

---

## Sobre "CRUD N:1 completo"

La entidad `Booking` es el lado **N** de la relación **N:1** con `TimeSlot` (muchas reservas
pueden apuntar históricamente al mismo horario). `BookingController` + `BookingService`
implementan el CRUD completo sobre esa entidad:

| Operación | Endpoint | Método HTTP |
|---|---|---|
| **C**reate | `POST /bookings` | Crea la reserva, valida el slot, aplica la guarda de concurrencia |
| **R**ead | `GET /bookings/me`, `GET /bookings/{id}`, `GET /bookings` (STAFF) | Lectura propia, por id (con propiedad), y admin |
| **U**pdate | `PUT /bookings/{id}` | Solo el dueño puede editar el propósito |
| **D**elete | `DELETE /bookings/{id}` | Solo el dueño puede cancelar |

Más las transiciones de estado exclusivas de STAFF (`PATCH /bookings/{id}/approve`,
`PATCH /bookings/{id}/attended`), que no forman parte del CRUD básico pero sí de la regla de
negocio de aprobación.

---

## Otras asignaturas (fuera del alcance de este backend, pero para no perderlas de vista)

- **1. Análisis de Diseño de Sistemas** (/8): RF/RNF + casos de uso, GitFlow, pruebas unitarias
  (ya cubierto arriba), y un ADR justificando decisiones como "por qué dos microservicios en vez
  de uno" o "por qué autorización combinada rol+propiedad".
- **2. Desarrollo Móvil** (/8): la app Android/Ionic que consuma estos endpoints — las pantallas
  ya están definidas en la presentación (`LoginScreen`, `SearchRoomsScreen`, `BookingScreen`, etc.).
- **4. Computación en la Nube** (/8): este `docker-compose.yml` + los `Dockerfile` de cada
  servicio ya cubren 4.2 (contenedores). Falta 4.1 (documentar ventajas/desventajas de escalar
  cada microservicio independientemente — escalamiento horizontal) y 4.3 (diagrama de despliegue,
  por ejemplo llevándolo a ECS/EC2 o un Docker host único).
- **5. Emprendimiento** (/8): Business Model Canvas de LabTime como producto (quién paga, quién
  usa, propuesta de valor frente a "un Excel compartido").
