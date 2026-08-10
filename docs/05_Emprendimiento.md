# LabTime — Modelo de negocio (Business Model Canvas)

Cubre la sección 5 de la rúbrica P01: Emprendimiento.

## Los 9 bloques

### 1. Segmentos de clientes
- **Universidades y politécnicos** con laboratorios/aulas de uso compartido entre carreras
  (el cliente que paga: la institución, vía su dirección de bienestar estudiantil o
  administración académica).
- **Usuarios finales**: profesores y estudiantes que reservan (REQUESTER), y el personal
  administrativo que gestiona el espacio (STAFF).

### 2. Propuesta de valor
- Elimina el "Excel compartido por WhatsApp" y las llamadas telefónicas para coordinar salas.
- Evita choques de horario mediante la guarda de concurrencia (RF05) — algo que un
  calendario compartido manual no garantiza.
- Trazabilidad: STAFF ve quién reservó qué, cuándo, y para qué (campo `purpose`).
- Autenticación institucional vía Cognito: no hay que crear cuentas nuevas ni gestionar
  contraseñas propias.

### 3. Canales
- Integración directa con el correo/SSO institucional de la universidad (Cognito puede
  federarse con un proveedor SAML/OIDC institucional a futuro).
- App móvil (Android/iOS vía Capacitor) + versión web (PWA) desde la misma base de código.
- Onboarding a través de bienestar estudiantil / coordinación académica al inicio de semestre.

### 4. Relación con el cliente
- Soporte directo con el área de TI de la institución (la que despliega y mantiene el backend).
- Auto-servicio para el usuario final: no necesita hablar con nadie para reservar, solo con
  STAFF si su solicitud es rechazada.

### 5. Fuentes de ingreso
- Licenciamiento SaaS por institución (tarifa anual o por número de espacios gestionados),
  no por usuario final — evita fricción para adopción masiva de estudiantes.
- Alternativa: modelo freemium — gratis hasta N salas gestionadas, de pago para instituciones
  grandes con múltiples campus/edificios.

### 6. Recursos clave
- El backend (este proyecto): rooms-service, bookings-service, infraestructura en la nube.
- Integración con el proveedor de identidad de la institución (Cognito u otro IdP).
- Conocimiento del dominio (flujos de aprobación típicos de una universidad).

### 7. Actividades clave
- Desarrollo y mantenimiento del producto (backend + app móvil).
- Onboarding de nuevas instituciones (configurar su User Pool, sus salas iniciales).
- Soporte técnico y monitoreo de disponibilidad (SLA con la institución cliente).

### 8. Socios clave
- **AWS** (o el proveedor cloud elegido) para Cognito, cómputo y base de datos.
- La propia universidad, como piloto y caso de referencia para vender a otras instituciones.
- Posibles integraciones futuras: sistemas académicos existentes (SIIU, Banner, etc.) para
  sincronizar horarios de clases automáticamente en vez de crearlos a mano.

### 9. Estructura de costos
- Infraestructura cloud (ECS/Fargate, RDS, ALB) — variable según número de instituciones activas.
- Desarrollo y mantenimiento del equipo (los 2 integrantes, a futuro un equipo más grande).
- Costo de Cognito por usuario activo mensual (MAU) — relevante al escalar a varias
  universidades con miles de estudiantes.

## Validación rápida del problema (para la defensa)

Pregunta que probablemente hagan: *"¿por qué pagaría una universidad por esto en vez de sysadmin
del OneDrive/Excel que ya usa gratis?"*

Respuesta lista: un Excel compartido no valida permisos (cualquiera con el link edita cualquier
fila), no previene choques de horario automáticamente, y no dsitingue quién puede aprobar de
quién puede solicitar. LabTime cambia eso por una regla de negocio validada en cada request
(RF05, RF12, RF13) — el valor no es "tener un calendario", es "tener un calendario que no se
puede hacer trampa".
