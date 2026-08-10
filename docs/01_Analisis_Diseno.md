# LabTime — Análisis de Diseño de Sistemas

Cubre la sección 1 de la rúbrica P01: requisitos, casos de uso, estrategia de control de
versiones y decisiones de arquitectura documentadas (ADR).

---

## 1. Requisitos funcionales (RF)

| # | Requisito | Actor |
|---|---|---|
| RF01 | El sistema debe permitir a un STAFF publicar una sala (nombre, tipo, capacidad, edificio) | STAFF |
| RF02 | El sistema debe permitir a un STAFF publicar horarios disponibles para una sala | STAFF |
| RF03 | El sistema debe permitir a cualquier visitante consultar salas y horarios disponibles sin autenticarse | Público |
| RF04 | El sistema debe permitir a un REQUESTER autenticado reservar un horario disponible | REQUESTER |
| RF05 | El sistema debe impedir que dos reservas activas existan sobre el mismo horario | Sistema |
| RF06 | El sistema debe permitir a un REQUESTER ver únicamente sus propias reservas | REQUESTER |
| RF07 | El sistema debe permitir a un REQUESTER editar el propósito de su propia reserva | REQUESTER |
| RF08 | El sistema debe permitir a un REQUESTER cancelar su propia reserva | REQUESTER |
| RF09 | El sistema debe permitir a un REQUESTER solicitar equipo adicional para una reserva propia | REQUESTER |
| RF10 | El sistema debe permitir a un STAFF aprobar o rechazar una reserva pendiente | STAFF |
| RF11 | El sistema debe permitir a un STAFF marcar una reserva aprobada como atendida | STAFF |
| RF12 | El sistema debe rechazar cualquier operación de escritura sin un token JWT válido de Cognito | Sistema |
| RF13 | El sistema debe rechazar operaciones sobre un recurso que no pertenece al usuario autenticado, aunque su rol sea correcto | Sistema |

## 2. Requisitos no funcionales (RNF)

| # | Requisito | Categoría |
|---|---|---|
| RNF01 | Los endpoints de escritura deben responder en menos de 500ms bajo carga normal (sin llamadas externas) | Rendimiento |
| RNF02 | El sistema debe validar la firma, emisor y expiración de cada JWT en cada request (Resource Server, sin sesión de servidor) | Seguridad |
| RNF03 | Cada microservicio debe poder desplegarse y escalar de forma independiente | Escalabilidad |
| RNF04 | El código debe seguir una arquitectura en capas (controller/service/repository/entity/dto/mapper) consistente entre ambos microservicios | Mantenibilidad |
| RNF05 | Las contraseñas de usuario nunca deben ser gestionadas por el backend propio (delegado 100% a Cognito) | Seguridad |
| RNF06 | La app móvil debe funcionar tanto en navegador (PWA) como empaquetada (Android/iOS) desde la misma base de código | Portabilidad |
| RNF07 | El sistema debe registrar (log) cada operación de creación/modificación de reservas para trazabilidad | Auditoría |

## 3. Casos de uso principales

### CU01 — Reservar un horario
**Actor:** REQUESTER
**Precondición:** el usuario tiene sesión iniciada; existe al menos un horario disponible.
**Flujo principal:**
1. El REQUESTER busca salas y ve horarios libres (RF03).
2. Selecciona un horario y escribe el propósito de la reserva.
3. El sistema valida que el horario sigue disponible (consulta a `rooms-service`).
4. El sistema valida que no exista ya una reserva activa sobre ese horario (RF05).
5. El sistema crea la reserva en estado `PENDING` y marca el horario como no disponible.

**Flujo alterno (3a):** el horario ya no existe o no está disponible → error 400, se pide reintentar.
**Flujo alterno (4a):** ya existe una reserva activa sobre el horario (choque de concurrencia) → error 409.

### CU02 — Cancelar una reserva propia
**Actor:** REQUESTER
**Precondición:** el usuario tiene al menos una reserva propia.
**Flujo principal:**
1. El REQUESTER abre "Mis reservas" (RF06).
2. Selecciona una reserva propia y confirma cancelación.
3. El sistema valida que el `requesterUsername` de la reserva coincide con el username del JWT.
4. El sistema elimina la reserva.

**Flujo alterno (3a):** la reserva no pertenece al usuario → error 403 (`NotYourBookingException`).

### CU03 — Aprobar una reserva
**Actor:** STAFF
**Precondición:** existe al menos una reserva en estado `PENDING`.
**Flujo principal:**
1. El STAFF abre "Aprobar reservas" (requiere rol STAFF, RF10).
2. Revisa la lista de reservas pendientes.
3. Aprueba o rechaza cada una.

**Flujo alterno (1a):** el usuario no tiene rol STAFF → error 403 (Spring Security, `hasRole`).

## 4. Estrategia de control de versiones (GitFlow)

Ramas:

- `main` — siempre desplegable; solo recibe merges desde `release/*` o `hotfix/*`.
- `develop` — integración continua de features; base para nuevas ramas de feature.
- `feature/<nombre>` — una por historia de usuario (ej. `feature/booking-crud`,
  `feature/cognito-auth`, `feature/staff-approvals`). Se abre desde `develop`, se cierra
  con Pull Request hacia `develop`.
- `release/<version>` — congela `develop` para pruebas finales antes de una entrega
  (ej. `release/p01-diseño`, `release/p01-final`).
- `hotfix/<nombre>` — corrección urgente directo sobre `main`.

Convención de commits: `tipo(alcance): descripción` — ej. `feat(bookings): valida choque de
concurrencia al reservar`, `fix(rooms): corrige rango de horario invalido`,
`test(bookings): agrega casos de propiedad`.

Cada integrante trabaja en su propia rama `feature/*` y abre PR para que el otro revise antes
de mergear a `develop` — así ambos conocen todo el código, como pide la rúbrica de sustentación.

## 5. ADR — Registro de decisiones de arquitectura

### ADR-001: Dos microservicios en vez de un monolito
**Contexto:** el dominio tiene dos responsabilidades claras: gestión de espacios (STAFF) y
gestión de reservas (REQUESTER + STAFF).
**Decisión:** separar en `rooms-service` y `bookings-service`, cada uno con su propia base de
datos, comunicados por HTTP (no comparten esquema).
**Alternativa descartada:** un solo servicio con las 4 tablas. Se descartó porque el enunciado
del curso (`demo_microservices`) exige practicar el patrón de microservicios con Resource
Server independiente por servicio, y porque separa el ciclo de vida de despliegue de cada
dominio (RNF03).
**Consecuencia:** `slotId` en `bookings` no es una FK real; se valida por HTTP
(`RoomsServiceClient`), lo que introduce latencia de red y un caso de fallo adicional
(rooms-service caído) que se documenta en `RUBRICA_MAPEO.md`.

### ADR-002: Autorización combinada (rol + propiedad), no solo rol
**Contexto:** con solo roles, un REQUESTER podría cancelar la reserva de otro REQUESTER.
**Decisión:** Spring Security valida rol vía `hasRole()`; la propiedad del recurso específico
se valida a mano dentro del service (`findOwnedOrThrow`).
**Alternativa descartada:** roles más granulares tipo `REQUESTER_OWN` — se descartó por ser
innecesariamente complejo para Cognito Groups, que no soporta ese nivel de granularidad
nativamente.

### ADR-003: PKCE en vez de client secret para la app móvil
**Contexto:** el App client de Cognito es tipo SPA (sin client secret).
**Decisión:** usar Authorization Code + PKCE en la app Ionic.
**Motivo:** un client secret no se puede proteger en una app cliente (móvil o web) — cualquiera
podría extraerlo del bundle. PKCE resuelve esto generando un secreto de un solo uso en tiempo
de ejecución.

### ADR-004: Guarda de concurrencia con transacción + verificación explícita
**Contexto:** dos REQUESTER podrían reservar el mismo horario casi simultáneamente.
**Decisión:** `BookingService.create()` verifica `existsBySlotIdAndStatusIn` dentro de una
transacción `@Transactional` antes de guardar.
**Limitación conocida:** en una base de datos real (PostgreSQL) esto debería reforzarse con una
restricción `UNIQUE` parcial a nivel de esquema (`WHERE status IN ('PENDING','APPROVED')`) para
cerrar la ventana de carrera por completo; con H2 en memoria para la demo, la verificación en
el service es suficiente para la defensa pero se documenta como pendiente para producción.
