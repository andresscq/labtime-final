# LabTime — Defensa de Arquitectura: funcionamiento y preguntas

Este documento complementa `06_Guion_Defensa.md` (que cubre la presentación de diseño). Aquí el
foco es **el código real del backend**: cómo funciona internamente y las preguntas técnicas que
pueden hacerte sobre la arquitectura ya implementada.

---

## 1. Cómo funciona el sistema, de punta a punta

### 1.1 Flujo de login (Authorization Code + PKCE)

1. El usuario abre la app y toca "Iniciar sesión".
2. La app genera un `code_verifier` aleatorio (128 caracteres) y calcula su hash SHA-256, el
   `code_challenge`.
3. La app redirige al navegador al Hosted UI de Cognito, mandando el `code_challenge` en la URL.
4. El usuario inicia sesión **en la pantalla de Cognito**, no en una pantalla nuestra — nuestra
   app nunca ve la contraseña.
5. Cognito redirige de vuelta a `/callback?code=...` con un código de un solo uso.
6. La app manda ese código **más el `code_verifier` original** (que solo ella tiene) al endpoint
   `/oauth2/token` de Cognito.
7. Cognito recalcula el hash del `code_verifier` recibido y lo compara con el `code_challenge`
   que guardó en el paso 3. Si coincide, entrega `access_token` + `id_token` + `refresh_token`.
8. La app guarda los tokens y usa el `access_token` como `Authorization: Bearer ...` en cada
   llamada al backend.

**Por qué PKCE y no un client secret:** un client secret no se puede esconder de forma segura en
una app móvil o web (cualquiera puede extraerlo del bundle instalado). PKCE reemplaza ese
secreto fijo por uno de un solo uso, generado en el momento — aunque alguien intercepte el
`code` del paso 5, no puede canjearlo sin el `code_verifier` que nunca viajó por ahí.

### 1.2 Qué pasa en el backend con CADA request privado

1. La petición llega a nginx (o directo al microservicio si estás en desarrollo).
2. Spring Security (configurado como **OAuth2 Resource Server**) intercepta la petición antes de
   que llegue a cualquier controller.
3. Toma el JWT del header `Authorization`, y valida tres cosas contra las llaves públicas del
   User Pool (descargadas una vez al arrancar, vía JWKS — no hay llamada de red por cada
   request):
   - **Firma**: ¿lo firmó realmente Cognito?
   - **Emisor (`iss`)**: ¿es de nuestro User Pool (`us-east-1_4B0CaMgZv`) y no de otro?
   - **Expiración (`exp`)**: ¿el token sigue vigente?
4. Si algo de eso falla → **401**, ni siquiera llega al controller.
5. Si el token es válido, el `JwtAuthenticationConverter` personalizado lee el claim
   `cognito:groups` (ej. `["STAFF"]`) y lo traduce a un `GrantedAuthority` con el formato que
   Spring espera: `ROLE_STAFF`.
6. Spring Security compara esa autoridad contra la regla de la ruta (`hasRole("STAFF")`). Si no
   alcanza → **403**, tampoco llega al controller.
7. Si todo pasa, el controller recibe la petición y extrae el `username` del JWT (nunca del
   body) para pasárselo al service.
8. El service, si la operación toca un recurso específico (una reserva), compara el dueño real
   contra ese `username`. Si no coincide → **403**, pero este SÍ lo lanza nuestro código
   (`NotYourBookingException`), no Spring Security.

### 1.3 Flujo completo: crear una reserva (`POST /bookings`)

1. La app manda `{ slotId, purpose }` con el Bearer token del REQUESTER.
2. `BookingController.create()` extrae `username` del JWT y llama a `BookingService.create()`.
3. El service llama a `RoomsServiceClient.findSlot(slotId)` — **una llamada HTTP real** de
   `bookings-service` hacia `rooms-service` (por eso `bookings-service` depende de que
   `rooms-service` esté corriendo).
4. Si el slot no existe o `available == false` → `SlotNotAvailableException` (400).
5. Si existe y está disponible, se verifica `existsBySlotIdAndStatusIn(slotId, [PENDING,
   APPROVED])` — la guarda de concurrencia. Si ya hay una reserva activa → `SlotAlreadyBookedException`
   (409).
6. Si pasa ambas validaciones, todo lo anterior ocurre dentro de una transacción
   `@Transactional`: se guarda el `Booking` con `status = PENDING`.
7. Después de guardar, se llama a `RoomsServiceClient.markSlotUnavailable(slotId)` — otra
   llamada HTTP, esta vez `PATCH /slots/{id}/mark-unavailable` en `rooms-service`, para que el
   horario deje de aparecer como libre.
8. La respuesta `201 Created` con la reserva completa vuelve a la app.

**Punto débil que hay que saber explicar:** los pasos 6 y 7 no son atómicos entre sí — son dos
sistemas distintos con dos bases de datos distintas. Si el paso 7 falla (rooms-service se cae
justo ahí), la reserva ya quedó creada pero el slot sigue apareciendo como disponible. Es una
limitación real de microservicios con bases de datos separadas, documentada en el ADR-001 y en
`04_Computacion_Nube.md` como algo que en producción se resolvería con un patrón *outbox* o
reintentos.

### 1.4 Por qué CORS está configurado en DOS lugares

- **En `nginx.conf`**: cubre el caso "todo corre con Docker", donde la app le habla al gateway
  único en `localhost:8888`.
- **En cada `SecurityConfig.kt`** (Spring): cubre el caso "desarrollo con IntelliJ", donde la app
  le habla directo a `localhost:8081` / `localhost:8082`, sin pasar por nginx.

Ambos permiten el mismo origen (`http://localhost:5173`, el dev server de Vite) porque son dos
caminos distintos que puede tomar la misma petición según cómo estés corriendo el proyecto.

---

## 2. Preguntas lógicas de arquitectura (con respuesta)

### Sobre las decisiones de diseño

**¿Por qué dos microservicios y no uno solo con las 4 tablas?**
> Separamos por responsabilidad: `rooms-service` es del dominio de "gestión de espacios" (lo
> administra STAFF, cambia poco) y `bookings-service` es del dominio de "gestión de reservas"
> (más volumen de escritura, más lógica de negocio). Así cada uno puede escalar y desplegarse
> por separado — ver ADR-001 en `01_Analisis_Diseno.md`.

**¿Por qué `slotId` en `Booking` no es una foreign key de base de datos?**
> Porque `bookings-service` y `rooms-service` tienen bases de datos completamente separadas —
> ni siquiera están en el mismo motor necesariamente. Una FK de SQL solo puede apuntar dentro de
> la misma base de datos. Por eso `slotId` es solo un número que `bookings-service` valida por
> HTTP contra `rooms-service` (`RoomsServiceClient`), no por un `JOIN`.

**¿Qué pasa si `rooms-service` está caído y alguien intenta reservar?**
> `RoomsServiceClient.findSlot()` captura la excepción de red y devuelve `null`; el service lo
> interpreta como "el slot no existe" y lanza `SlotNotAvailableException` → el usuario ve un
> error 400 claro, en vez de que la app se cuelgue o crashee.

**¿Por qué usan H2 en memoria y no una base de datos real?**
> Para el sandbox de desarrollo es más simple: arranca con la aplicación, sin instalar nada
> aparte. En un despliegue real se cambia a PostgreSQL (RDS) solo tocando la URL del datasource
> en `application.yaml` — Hibernate con `ddl-auto: update` migra el esquema automáticamente, así
> que no hay que tocar código.

### Sobre seguridad

**¿Qué es exactamente un JWT y qué partes tiene?**
> Tres partes separadas por puntos: header (algoritmo de firma), payload (los "claims": quién
> es, qué rol tiene, cuándo expira) y firma (garantiza que nadie modificó el payload). Nuestro
> backend no descifra nada — solo verifica la firma con la llave pública de Cognito.

**¿El backend llama a Cognito en cada request para validar el token?**
> No. Al arrancar, descarga y cachea las llaves públicas del User Pool (JWKS). Validar la firma
> después es matemática local (criptografía de curva elíptica), sin round-trip de red. Solo
> vuelve a consultar si Cognito rota las llaves.

**¿Qué pasa si alguien arma un JWT falso a mano?**
> No pasa la verificación de firma — no tiene forma de firmarlo con la llave privada de Cognito
> (esa nunca sale de AWS). Spring Security lo rechaza con 401 antes de mirar el contenido.

**¿Por qué el App client de Cognito no tiene client secret?**
> Porque es tipo SPA/público — una app móvil o web no puede guardar un secreto de forma segura
> (se puede extraer del bundle). En vez de un secreto fijo, se usa PKCE (ver sección 1.1): un
> secreto de un solo uso generado en tiempo de ejecución.

**¿Qué diferencia hay entre `access_token` e `id_token`?**
> El `access_token` es el que se manda al backend para autorizar cada request (lo que valida
> Spring Security). El `id_token` es para que la propia app sepa quién es el usuario (nombre,
> email, grupos) sin tener que preguntarle al backend — lo usamos en `AuthContext` solo para
> decidir qué botones mostrar en la UI, nunca para autorizar nada de verdad.

### Sobre la comunicación entre microservicios

**¿Cómo se comunican `bookings-service` y `rooms-service`?**
> HTTP simple y síncrono (`RestClient` de Spring), no un bus de mensajes ni gRPC. Es la forma
> más simple de empezar; para un sistema con más carga se consideraría comunicación asíncrona
> (colas) para desacoplar el fallo de un servicio del otro.

**¿Por qué el endpoint interno `PATCH /slots/{id}/mark-unavailable` está sin protección de rol?**
> Simplificación documentada del sandbox: es una llamada de servicio a servicio, no de un
> usuario final, así que no trae el JWT del usuario. En producción se protegería con
> *client credentials grant* (un token de aplicación, no de usuario) — está anotado como
> pendiente en el código y en `RUBRICA_MAPEO.md`.

### Sobre manejo de errores

**¿Por qué cada microservicio tiene su propio `GlobalExceptionHandler` en vez de uno compartido?**
> Porque son proyectos Gradle independientes — no comparten código en tiempo de compilación (a
> propósito, para que cada uno se pueda desplegar solo). Si se repite mucho código entre ambos,
> la alternativa sería extraer una librería compartida publicada internamente, pero eso agrega
> complejidad de versionado que no se justificaba para este alcance.

**¿Qué código HTTP devuelven si mandas un JSON mal formado?**
> `400 Bad Request` — lo maneja Spring automáticamente antes de que el controller lo vea (falla
> al deserializar el body a `BookingRequest`), sin necesidad de una excepción propia.

### Sobre testing

**¿Por qué solo `bookings-service` tiene tests unitarios?**
> Es una limitación real, no una decisión de diseño — priorizamos escribir el test donde vive la
> lógica más crítica (propiedad + concurrencia). `rooms-service` queda pendiente de escribir
> `RoomServiceTest`/`TimeSlotServiceTest` con el mismo patrón antes de la entrega final.

**¿Qué es un mock y por qué lo usan en los tests?**
> `BookingRepository` y `RoomsServiceClient` se reemplazan por objetos falsos (`mock()`) que
> devuelven exactamente lo que el test necesita, sin tocar una base de datos real ni hacer una
> llamada HTTP real. Así el test es rápido y prueba solo la lógica de `BookingService`, no la
> infraestructura de la que depende.

### Sobre despliegue

**¿Por qué nginx y no cada microservicio expuesto directo?**
> Un solo punto de entrada público es más simple de asegurar (un certificado TLS, un dominio) y
> oculta la topología interna — el cliente no necesita saber que hay dos microservicios
> distintos, ni en qué puerto vive cada uno.

**¿Qué pasa si nginx se cae?**
> Todo el sistema queda inaccesible desde afuera — es un punto único de fallo. En producción se
> mitigaría con un Application Load Balancer real (ver `04_Computacion_Nube.md`), que sí se
> replica automáticamente entre zonas de disponibilidad.
