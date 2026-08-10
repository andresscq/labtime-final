# LabTime — Computación en la Nube

Cubre la sección 4 de la rúbrica P01: virtualización/contenedores y arquitectura de
infraestructura en la nube.

## 1. Contenedores (ya implementado en `labtime-backend/`)

Cada microservicio tiene su propio `Dockerfile` multi-stage (build con Gradle, runtime solo con
el JAR — imagen final más liviana):

```
rooms-service/Dockerfile      -> imagen ~200MB, expone :8081
bookings-service/Dockerfile   -> imagen ~200MB, expone :8082
nginx (imagen oficial 1.27-alpine) -> expone :8888 (único puerto público)
```

`docker-compose.yml` orquesta los 3 contenedores en una red interna de Docker: `nginx` es el
único que publica puerto al host; `rooms-service` y `bookings-service` solo son alcanzables
dentro de la red de Docker (`expose`, no `ports`) — esto es una decisión de seguridad: nadie
puede pegarle a `rooms-service:8081` directo desde fuera, todo pasa por el reverse proxy.

## 2. Ventajas y desventajas de escalar cada microservicio independientemente

| | Ventaja | Desventaja / costo |
|---|---|---|
| **Escalar `bookings-service` solo** (más carga: temporada de reservas) | No se gasta recursos escalando `rooms-service`, que cambia poco | Más instancias de `bookings-service` compitiendo por llamar a `rooms-service` — puede saturarlo si no se escala junto |
| **Escalar `rooms-service` solo** (más lecturas públicas: catálogo muy consultado) | Las lecturas públicas (`GET /rooms`) no compiten con la carga de escritura de reservas | `bookings-service` sigue limitado si el cuello de botella real es la validación cruzada de slots |
| **Bases de datos separadas por servicio** | Cada servicio escala su BD independientemente; un pico en `bookings` no satura la BD de `rooms` | No hay transacciones ACID entre ambos — la consistencia entre "slot marcado no disponible" y "reserva creada" es *eventual*, no atómica (ver ADR-001 y ADR-004 en el documento de Análisis de Diseño) |
| **Nginx como único punto de entrada** | Un solo certificado TLS, un solo dominio público, más fácil de proteger | Es un punto único de fallo si no se replica también (mitigable con un load balancer delante de varias instancias de nginx) |

## 3. Arquitectura de despliegue propuesta (más allá del sandbox local)

Para llevar esto a una nube real (AWS, ya que el curso usa Cognito):

```
                         ┌────────────────────────┐
                         │   AWS Cognito           │
                         │   User Pool + Hosted UI │
                         └────────────┬────────────┘
                                      │ JWT
                                      ▼
        Internet ──► Application Load Balancer (HTTPS, ACM cert)
                                      │
                         ┌────────────┴────────────┐
                         │   ECS Fargate Cluster    │
                         │                          │
                    ┌────┴─────┐            ┌───────┴──────┐
                    │  rooms-  │            │  bookings-   │
                    │  service │◄───────────│  service     │
                    │ (N tasks)│  HTTP int.  │  (N tasks)   │
                    └────┬─────┘            └───────┬──────┘
                         │                           │
                    ┌────┴─────┐            ┌────────┴─────┐
                    │  RDS     │            │  RDS         │
                    │ PostgreSQL│            │ PostgreSQL   │
                    │ (rooms)  │            │ (bookings)   │
                    └──────────┘            └──────────────┘
```

- **ECS Fargate** en vez de EC2: no administramos servidores, cada microservicio es una task
  definition independiente — coincide con RNF03 (escalar cada uno por separado).
- **Application Load Balancer** con reglas de path (`/rooms*`, `/slots*` → rooms-service;
  `/bookings*`, `/equipment-requests*` → bookings-service) — reemplaza a nginx en producción,
  mismo rol que cumple en el sandbox local.
- **RDS PostgreSQL** separado por servicio, reemplazando el H2 en memoria — persistencia real
  y confirma que las bases de datos siguen sin compartirse entre microservicios.
- **Auto Scaling** basado en CPU/memoria de cada servicio ECS de forma independiente — así se
  materializa la ventaja de la tabla de la sección 2.

## 4. Qué falta para producción (a mencionar en la defensa como trabajo futuro)

- Reemplazar H2 en memoria por RDS/PostgreSQL real (los `application.yaml` ya usan Hibernate
  `ddl-auto: update`, que migra el esquema automáticamente al cambiar el datasource).
- Autenticación servicio-a-servicio real para la llamada interna
  `PATCH /slots/{id}/mark-unavailable` (hoy simplificada como pública para el sandbox —
  ver el comentario en `rooms-service/SecurityConfig.kt`), usando client-credentials de Cognito.
- CI/CD: pipeline que construya y publique las imágenes Docker a ECR en cada merge a `main`
  (conecta con la estrategia de GitFlow del documento de Análisis de Diseño).
