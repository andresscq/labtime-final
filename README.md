# LabTime — Proyecto Integrador · Arquitectura Empresarial (2026-01)

**Integrantes:** José Pinduisaca, Andres Criollo · **NRC:** 1462
**Proyecto:** LabTime — reserva de laboratorios y aulas universitarias
**URL desplegada:** _pendiente (agregar si se despliega en cloud)_

Este README está pensado para que **cualquier persona** (compañero de equipo,
docente evaluando la entrega, o tú mismo en otra máquina) pueda clonar el
repositorio y levantar el sistema completo sin contexto previo.

## Índice

1. [Arquitectura](#1-arquitectura)
2. [Prerrequisitos](#2-prerrequisitos)
3. [Cómo levantar el backend (Docker)](#3-cómo-levantar-el-backend-docker)
4. [Cómo levantar el frontend (app web/móvil)](#4-cómo-levantar-el-frontend-app-webmóvil)
5. [Verificar que todo funciona](#5-verificar-que-todo-funciona)
6. [Problemas comunes al levantar el proyecto](#6-problemas-comunes-al-levantar-el-proyecto)
7. [Estándar de logging](#7-estándar-de-logging)
8. [Tests y cobertura](#8-tests-y-cobertura)
9. [Cognito (autenticación y roles)](#9-cognito-autenticación-y-roles)
10. [Postman](#10-postman)
11. [Estructura del repositorio](#11-estructura-del-repositorio)

---

## 1. Arquitectura

```
                    ┌───────────────────────────┐
                    │        AWS Cognito         │
                    │  User Pool: us-east-1_...  │
                    └──────────────┬─────────────┘
                                   │ emite JWT (sub, cognito:groups)
                                   ▼
cliente ──► http://localhost:9090 ──► nginx (unico punto de entrada)
                                          │
                    ┌─────────────────────┴─────────────────────┐
                    │                                            │
                 /users                          /rooms, /slots, /bookings,
                    ▼                                  /equipment-requests
        ┌───────────────────────┐                              ▼
        │   users               │                 ┌────────────────────────┐
        │   Spring Boot :8080   │                 │   labtime              │
        │   PostgreSQL propia   │                 │   Spring Boot :8081    │
        │   (user_profiles)     │                 │   PostgreSQL propia    │
        └───────────────────────┘                 │   (rooms, time_slots,  │
                                                    │    bookings,           │
                                                    │    equipment_requests) │
                                                    └────────────────────────┘
```

`users` y `labtime` **no comparten base de datos**. Ninguno consulta
la tabla del otro: toda necesidad de datos ajenos se resolvería llamando a la API
del otro servicio (hoy no hay ninguna llamada síncrona entre ambos; Cognito ya
resuelve la identidad para los dos).

Dentro de `labtime`, las relaciones `Room → TimeSlot → Booking → EquipmentRequest`
son **FK reales de Postgres** (JPA `@ManyToOne` + `@JoinColumn`, no un `Long`
suelto validado a mano): borrar un recurso que todavía tiene hijos responde
**409 Conflict** en vez de un error crudo de base de datos. Ver
[`docs/03_Arquitectura_RubricaMapeo.md`](docs/03_Arquitectura_RubricaMapeo.md)
para el detalle completo del mapeo contra la rúbrica.

### ADR — Por qué `rooms-service` + `bookings-service` se fusionaron en `labtime`

El diseño original (ver `docs/`) separaba el dominio en dos microservicios que
se hablaban por HTTP (`RoomsServiceClient`), siguiendo el mismo patrón que
`users`/`diary` del sandbox del curso. La entrega final de Arquitectura
Empresarial exige una estructura de monorepo distinta: **exactamente**
`users/` + `nginx/` + **un** microservicio del dominio propio de la pareja.
Fusionar `rooms` y `bookings` en `labtime` (mismas 4 entidades, mismas capas,
mismas reglas de negocio) fue la forma de cumplir esa estructura sin perder
ninguna regla de dominio ya probada: la llamada HTTP
`RoomsServiceClient.markSlotUnavailable()` se volvió una llamada directa en el
mismo proceso a `TimeSlotService.markUnavailable()`, dentro de la misma
transacción `@Transactional` de `BookingService.create()`.

`users` es nuevo: antes la identidad la resolvía Cognito directamente sin un
microservicio propio de perfiles; ahora `users` guarda el **perfil extendido**
(nombre, email de contacto, teléfono) que Cognito no almacena, indexado por el
`sub` del token.

---

## 2. Prerrequisitos

Instalar antes de empezar:

- **[Docker Desktop](https://www.docker.com/products/docker-desktop/)** — debe
  estar **abierto y corriendo** (ícono en la bandeja del sistema; si dice
  "paused", reanudarlo desde ahí).
- **[Node.js](https://nodejs.org/)** 20+ (para el frontend, `mobile/`).
- **Git**.
- Acceso al **User Pool de Cognito** del proyecto (region + user pool id +
  app client). Si no lo tienes, pídelo al resto del equipo.

No hace falta instalar Java/Kotlin/Gradle en la máquina: los dos
microservicios compilan y corren dentro de sus contenedores Docker.

---

## 3. Cómo levantar el backend (Docker)

```bash
git clone https://github.com/JosEPinduisaca/backend-prueba-arquitectura.git
cd backend-prueba-arquitectura

cp .env.example .env
# abrir .env y completar COGNITO_REGION / COGNITO_USER_POOL_ID con los
# valores reales del User Pool. Las contraseñas de las bases ya traen un
# valor por defecto que funciona para desarrollo local, no hace falta tocarlas.

docker compose up -d --build
```

La primera vez tarda varios minutos (descarga las imágenes base y compila
ambos `.jar` dentro de Docker). Las siguientes veces es mucho más rápido
porque reutiliza las capas ya construidas.

Verificar que los 6 servicios quedaron arriba:

```bash
docker compose ps
```

Deberías ver `labtime`, `users`, `labtime-db`, `users-db`, `reverse-proxy` y
`pgadmin`, todos `Up` (las dos bases además deben decir `healthy`).

| Servicio | URL | Para qué |
|---|---|---|
| Gateway (nginx) | http://localhost:9090 | único punto de entrada a la API |
| pgAdmin | http://localhost:5050 | explorar las 2 bases de datos |
| Logs en vivo | `docker compose logs -f` | ver los `event=...` de ambos servicios en tiempo real |

Endpoints de ejemplo a través de nginx:
```bash
curl http://localhost:9090/rooms
curl http://localhost:9090/users/me -H "Authorization: Bearer $TOKEN"
```

Para apagar todo sin perder los datos de las bases: `docker compose down`
(agregar `-v` solo si quieres borrar también los volúmenes de Postgres).

---

## 4. Cómo levantar el frontend (app web/móvil)

El backend por sí solo ya es usable con `curl`/Postman, pero el proyecto
incluye una app Ionic + React que consume la API. Corre aparte del backend,
en una segunda terminal:

```bash
cd mobile
cp .env.example .env   # ya trae los valores reales de Cognito (domain, client id)
npm install
npm run dev
```

Esto deja un servidor de desarrollo (Vite) en **http://localhost:5173**.
Ábrelo en el navegador: verás la pantalla de login de LabTime. Al hacer clic
en "Iniciar sesión" te redirige al Hosted UI real de Cognito y, tras
autenticarte, vuelve ya logueado.

Si vas a empaquetarla como app Android (Capacitor), ver `mobile/package.json`
(`npm run cap:sync`) — fuera del alcance de este README, que cubre el backend.

---

## 5. Verificar que todo funciona

Checklist rápido tras el `docker compose up`:

```bash
docker compose ps                                    # 6 servicios Up (2 healthy)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9090/rooms        # -> 200 (publico)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9090/users/me     # -> 401 (sin token)
```

Para una verificación más completa: correr los tests automatizados (sección 8)
y/o la colección de Postman (sección 10) con un usuario real de Cognito.

---

## 6. Problemas comunes al levantar el proyecto

Estos son los problemas reales que aparecieron al levantar este proyecto en
Windows — si te pasa algo parecido, no es un bug del código, es configuración
del entorno.

**`labtime` y/o `users` se reinician solos (`Exited (1)`) con
`password authentication failed`**
Postgres solo aplica `POSTGRES_PASSWORD` la **primera vez** que inicializa un
volumen vacío. Si cambiaste el `.env` después de un primer `docker compose up`,
el volumen viejo sigue teniendo la contraseña anterior. Arreglo (borra solo
las bases de prueba locales, no afecta el código):
```bash
docker compose down -v
docker compose up -d --build
```

**`pgadmin` sale con `Exited (1)` y el log dice que el email "no es válido"**
Si cambiaste `PGADMIN_DEFAULT_EMAIL` en `docker-compose.yml` a un dominio
reservado (`.local`, `.test`, etc.), pgAdmin lo rechaza. Usa un dominio con
TLD real, p. ej. `admin@labtime.com` (ya viene así en este repo).

**`docker compose up` falla con `"Docker Desktop is manually paused"`**
Abre Docker Desktop y reanúdalo desde el ícono de la bandeja del sistema.

**`./gradlew: not found` / el contenedor no compila**
En Windows, Git puede clonar los scripts de shell (`gradlew`) con saltos de
línea CRLF, lo que rompe el `#!/bin/sh` dentro del contenedor Linux. Este
repo ya trae un `.gitattributes` que fuerza LF en `gradlew`, así que un clon
nuevo no debería sufrirlo. Si aun así pasa:
```bash
git rm --cached labtime/gradlew users/gradlew
git add labtime/gradlew users/gradlew
git commit -m "fix: normalizar gradlew a LF"
```

**`./gradlew test` falla en Windows con
`java.io.IOException: Unable to establish loopback connection`**
Es un bug conocido de JDK 21 en Windows (el NIO `Selector` intenta usar Unix
Domain Sockets para su pipe interno y falla en algunos entornos), sin relación
con este proyecto. Workaround: correr los tests dentro de un contenedor Linux
con el mismo JDK que usa el `Dockerfile`:
```bash
docker run --rm -v "${PWD}/labtime:/app" -w /app eclipse-temurin:21-jdk ./gradlew test --no-daemon
docker run --rm -v "${PWD}/users:/app" -w /app eclipse-temurin:21-jdk ./gradlew test --no-daemon
```
(agregar `-v gradle-cache:/root/.gradle` para no re-descargar Gradle cada vez).

**`DELETE /rooms/{id}` (o `/slots/{id}`, `/bookings/{id}`) da `409 Conflict`**
No es un error: esa sala/horario/reserva todavía tiene hijos dependientes
(horarios, reservas o pedidos de equipo respectivamente) — es la FK real de
Postgres protegiendo la integridad de los datos. Borra primero los hijos.

---

## 7. Estándar de logging

Cada línea de log sigue este formato fijo (una sola línea, sin JSON):

```
<timestamp> | <LEVEL> | <servicio> | sub=<cognito-sub|anonimo> | <logger> | <mensaje>
```

- `timestamp`, `LEVEL`, `<servicio>` y `sub` los pone automáticamente el patrón
  de Logback (`logback-spring.xml`, idéntico en ambos servicios) — nunca se
  escriben a mano.
- `sub` se toma del JWT de Cognito vía `MdcSubFilter`, que corre justo después
  de que Spring Security valida el token y lo mete en el MDC. Si no hay token,
  el patrón usa `anonimo` por defecto.
- El código de aplicación solo escribe la parte final:
  `event=<evento> | msg=<mensaje en ingles> | clave=valor ...`
- `MdcSubFilter` también deja una línea `event=http.request` al entrar y
  `event=http.response` (con el código HTTP) al salir de cada petición, para
  que ningún request pase sin dejar rastro.
- SQL de ambas bases logueado con parámetros: `org.hibernate.SQL=DEBUG` +
  `org.hibernate.orm.jdbc.bind=TRACE` en ambos `logback-spring.xml`.
- Todo a `stdout` (lo captura `docker compose logs -f`); nada se escribe solo
  a un archivo dentro del contenedor.

Ejemplo real (línea acortada):
```
2026-08-05T01:10:22.301Z | INFO  | labtime | sub=a1b2c3d4-... | c.l.l.services.BookingService | event=booking.created | msg=Booking created | bookingId=17 slotId=10
```

---

## 8. Tests y cobertura

Según lo permitido por la rúbrica, se excluyen del cálculo de cobertura:
`*Application.kt` (clase de arranque de Spring Boot), clases de configuración
(`SecurityConfig`, `MdcSubFilter`), DTOs (sin lógica) y entidades `@Entity`
(sin comportamiento propio, solo estructura de datos).

**Tests incluidos:**
- `labtime` (43 tests): `RoomServiceTest`, `TimeSlotServiceTest`,
  `BookingServiceTest`, `EquipmentRequestServiceTest` (unitarios con
  Mockito-Kotlin — camino feliz, autorización por propiedad, 404 real,
  concurrencia, e integridad referencial: borrar algo con hijos da 409) +
  `RoomControllerSecurityTest` (integración `MockMvc`: 401 sin token, 403 con
  rol equivocado, 201 con rol correcto).
- `users` (10 tests): `UserProfileServiceTest` (unitario) +
  `UserProfileControllerSecurityTest` (integración `MockMvc`: mismos tres casos).

Los `*ControllerSecurityTest` usan `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors.jwt()`:
simulan el token de Cognito inyectando directamente las authorities
(`ROLE_STAFF`/`ROLE_REQUESTER`) en el `SecurityContext` de la petición, sin
levantar base de datos ni depender de Cognito real.

`./gradlew test` en cada servicio corre estos tests y además genera el reporte
de Jacoco en `build/reports/jacoco/test/html/index.html` como respaldo del
*Run with Coverage* del IDE. Verificado: **53 tests, 0 fallos** en total
(10 en `users`, 43 en `labtime`). Si en Windows `./gradlew test` falla con
`Unable to establish loopback connection`, ver sección 6.

---

## 9. Cognito (autenticación y roles)

Ambos servicios resuelven el **mismo issuer**, tomado de variables de entorno
(`COGNITO_REGION`, `COGNITO_USER_POOL_ID`) — ver `.env.example`. El rol sale
del claim `cognito:groups` del JWT y se traduce a `ROLE_STAFF`/`ROLE_REQUESTER`
vía un `JwtAuthenticationConverter` idéntico en ambos `SecurityConfig`.

`SecurityConfig` (`@EnableMethodSecurity`) solo decide qué necesita token
(rutas públicas de `/rooms/**` y `/slots/**` vs. el resto autenticado). El
control de acceso por rol vive en cada endpoint con `@PreAuthorize("hasRole('STAFF')")`
(`RoomController`, `TimeSlotController`, `BookingController.all/approve/attended`,
`UserProfileController.all`), tal como exige el Criterio 8. La autorización por
*propiedad* (un REQUESTER solo ve/edita lo suyo) sigue viviendo en el service,
porque Spring Security no sabe de quién es la fila 1.

| Grupo Cognito | Rol interno | Puede |
|---|---|---|
| `STAFF` | `ROLE_STAFF` | Gestionar salas/horarios, ver y aprobar todas las reservas, ver el listado completo de usuarios |
| `REQUESTER` | `ROLE_REQUESTER` | Ver salas/horarios, crear/editar/cancelar sus propias reservas y pedidos de equipo |

---

## 10. Postman

La colección en `postman/` (`LabTime_Postman_Collection.json` +
`LabTime_Postman_Environment.json`) ya apunta a `http://localhost:9090` y
trae, en orden:

1. **Auth (Cognito)** — login STAFF y REQUESTER, guarda los tokens en el
   environment automáticamente.
2. Casos positivos por recurso (`users`, `rooms`, `slots`, `bookings`,
   `equipment-requests`).
3. Casos negativos explícitos: sin token (401), rol equivocado (403), slot ya
   reservado (409).
4. **Cleanup** al final, en el orden correcto para no chocar con las FK
   (equipo → reserva → horario → sala).

Para usarla: importar ambos archivos en Postman, completar
`cognito_staff_username/password` y `cognito_requester_username/password` en
el environment con usuarios reales del User Pool, y correr la colección de
arriba hacia abajo.

---

## 11. Estructura del repositorio

```
users/                -> microservicio de perfiles de usuario
labtime/              -> rooms + time_slots + bookings + equipment_requests
nginx/                -> reverse proxy, único punto de entrada expuesto
pgadmin/              -> conexiones pre-registradas para el explorador de BD
docker-compose.yml
.env.example
.gitattributes        -> fuerza LF en gradlew/*.sh (evita el problema de CRLF en Windows)
postman/               -> colección + environment, ya apuntando a nginx:9090 con flujo de token de Cognito
docs/                  -> mapeo de la implementación contra la rúbrica (arquitectura, RF/RNF, ADRs)
mobile/                -> app Ionic + React + TypeScript; ver sección 4 para levantarla
```
