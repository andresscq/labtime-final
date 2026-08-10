# LabTime — Manual completo para levantarlo (Windows + IntelliJ)

Sigue estos pasos en orden. Cada uno termina con una forma de comprobar que funcionó antes de
pasar al siguiente. Este manual ya incluye los tropiezos reales que resolvimos configurando
Cognito y el AWS CLI — si te sale alguno de esos errores, ve directo a la sección que lo cubre.

---

## 0. Prerrequisitos

Instala lo que te falte:

- **JDK 21** — [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21). IntelliJ
  también puede descargarlo solo (ver paso 2).
- **Node.js LTS** (18 o superior) — [nodejs.org](https://nodejs.org)
- **IntelliJ IDEA** — ya lo tienes.
- **AWS CLI v2** — necesario para configurar Cognito y sacar tokens de prueba. Ver paso 4.
- **Docker Desktop** — opcional, solo si quieres correr todo con `docker compose` en vez de
  servicio por servicio. Recomiendo empezar SIN Docker (más fácil de depurar).
- **Postman** — para probar los endpoints con tokens reales (colección incluida en `postman/`).

## 1. Descomprimir el proyecto

Descomprime `labtime-proyecto-integrador.zip` en tu carpeta de proyectos:

```
C:\Users\<tu-usuario>\Downloads\labtime-proyecto-integrador\
```

Deberías ver `backend/`, `mobile/`, `docs/` y `postman/` adentro.

## 2. Abrir y correr `rooms-service`

1. `File → Open...` en IntelliJ → selecciona `backend\rooms-service` (como su propio proyecto).
2. **Trust Project** cuando lo pida.
3. Espera el **Gradle Sync**. La primera vez descarga dependencias — necesita internet.
4. Si pide JDK: `File → Project Structure → Project → SDK` → JDK 21.
5. Clic derecho en `RoomsApplication.kt` → **Run**.

**✅ Comprobación:** consola dice `Tomcat started on port 8081`. En el navegador,
`http://localhost:8081/rooms` debe devolver `[]`.

## 3. Abrir y correr `bookings-service`

Repite el paso 2 pero con `backend\bookings-service`, **en una ventana nueva de IntelliJ**
(necesitas los dos corriendo a la vez — `bookings-service` le habla a `rooms-service` por HTTP
para validar horarios). Ejecuta `BookingsApplication.kt`.

**✅ Comprobación:** consola dice `Tomcat started on port 8082`.

---

## 4. Configurar AWS CLI (esto fue lo que más nos costó — sigue el orden exacto)

### 4.1 Instalar

```powershell
msiexec.exe /i https://awscli.amazonaws.com/AWSCLIV2.msi /qn
```

Espera ~1 minuto (no muestra nada). **Cierra completamente la terminal y abre una nueva** —
si no, no va a reconocer el comando `aws` aunque ya esté instalado.

```powershell
aws --version
```
Debe mostrar `aws-cli/2.x.x ...`.

### 4.2 Configurar región (evita el error `NoRegion`)

```powershell
aws configure set region us-east-1
```

### 4.3 Configurar credenciales (evita el error `NoCredentials`)

Si nunca creaste un Access Key:

1. Consola de AWS → tu nombre de usuario (arriba a la derecha) → **Security credentials**
2. **Access keys** → **Create access key** → elige **Command Line Interface (CLI)** → Next → Create
3. Copia el **Access key ID** y el **Secret access key** — el secret solo se muestra esta vez.

Ponlos directo con comandos (más confiable que `aws configure` interactivo — si el pegado falla
en la terminal, el campo queda vacío sin avisar):

```powershell
aws configure set aws_access_key_id AKIAxxxxxxxxxxxxxx
aws configure set aws_secret_access_key tu_secret_access_key_aqui
```

### 4.4 Confirmar que ya quedó conectado

```powershell
aws sts get-caller-identity
```

**✅ Comprobación:** JSON con `Account`, `UserId`, `Arn`, sin error.

### Errores que ya vimos y cómo se resuelven

| Error exacto | Causa | Solución |
|---|---|---|
| `'aws' no se reconoce como...` | AWS CLI no instalado, o terminal vieja que no refrescó el PATH | Instala (4.1) y **abre una terminal nueva** |
| `NoRegion: You must specify a region` | Falta la región | `aws configure set region us-east-1` (4.2) |
| `NoCredentials: Unable to locate credentials` | El Access Key/Secret nunca se guardó (pegado falló en `aws configure` interactivo) | Usa los comandos directos de 4.3, no el modo interactivo |
| `region ... LOCATION : ~/.aws/configs/config` (con "s" de más) | Poco común — variable de entorno `AWS_CONFIG_FILE` mal puesta | `Remove-Item Env:\AWS_CONFIG_FILE` y abre terminal nueva |

---

## 5. Configurar Cognito (grupos, usuarios, flujo de auth, duración de tokens)

Tu User Pool ya existe: `us-east-1_4B0CaMgZv`, Client ID `1kr72473ofc7lafpri19iq6mkc`, dominio
`https://us-east-14b0camgzv.auth.us-east-1.amazoncognito.com`.

### 5.1 Crear los grupos

```powershell
aws cognito-idp create-group --user-pool-id us-east-1_4B0CaMgZv --group-name STAFF
aws cognito-idp create-group --user-pool-id us-east-1_4B0CaMgZv --group-name REQUESTER
```

### 5.2 Crear usuarios de prueba con contraseña permanente

```powershell
aws cognito-idp admin-set-user-password --user-pool-id us-east-1_4B0CaMgZv --username staff1@labtime.com --password "Staff123!" --permanent

aws cognito-idp admin-set-user-password --user-pool-id us-east-1_4B0CaMgZv --username requester1@labtime.com --password "Requester123!" --permanent
```

(Si el usuario todavía no existe, créalo primero con `aws cognito-idp admin-create-user
--user-pool-id us-east-1_4B0CaMgZv --username staff1@labtime.com --user-attributes
Name=email,Value=staff1@labtime.com Name=email_verified,Value=true`.)

### 5.3 Meter cada usuario a su grupo

**Este paso es el que hace que `cognito:groups` aparezca en el JWT** — sin él, `hasRole("STAFF")`
en el backend nunca va a coincidir con nada, sin importar qué tan bien esté todo lo demás.

```powershell
aws cognito-idp admin-add-user-to-group --user-pool-id us-east-1_4B0CaMgZv --username staff1@labtime.com --group-name STAFF

aws cognito-idp admin-add-user-to-group --user-pool-id us-east-1_4B0CaMgZv --username requester1@labtime.com --group-name REQUESTER
```

### 5.4 Habilitar el flujo `ADMIN_USER_PASSWORD_AUTH`

Necesario para sacar tokens por CLI sin pasar por el navegador. Cognito Console → tu User Pool →
**App integration** → **Clientes de aplicación** → tu App client → **Editar** →
**Authentication flows** → marca `ALLOW_ADMIN_USER_PASSWORD_AUTH` → **Guardar cambios**.

### 5.5 (Opcional pero recomendado) Alargar la duración del access token

Por defecto dura 1 hora — cómodo alargarlo mientras pruebas, para no regenerar tokens cada rato:

```powershell
aws cognito-idp update-user-pool-client --user-pool-id us-east-1_4B0CaMgZv --client-id 1kr72473ofc7lafpri19iq6mkc --access-token-validity 12 --id-token-validity 12 --refresh-token-validity 30 --token-validity-units AccessToken=hours,IdToken=hours,RefreshToken=days
```

Para la defensa, menciona que en producción se dejaría corto (15-30 min) por seguridad — esto es
solo para hacer más cómodas las pruebas.

---

## 6. Sacar tokens y probar con Postman

### 6.1 Sacar un token por rol

```powershell
aws cognito-idp admin-initiate-auth --user-pool-id us-east-1_4B0CaMgZv --client-id 1kr72473ofc7lafpri19iq6mkc --auth-flow ADMIN_USER_PASSWORD_AUTH --auth-parameters USERNAME=staff1@labtime.com,PASSWORD=Staff123!
```
```powershell
aws cognito-idp admin-initiate-auth --user-pool-id us-east-1_4B0CaMgZv --client-id 1kr72473ofc7lafpri19iq6mkc --auth-flow ADMIN_USER_PASSWORD_AUTH --auth-parameters USERNAME=requester1@labtime.com,PASSWORD=Requester123!
```

Copia el campo `AuthenticationResult.AccessToken` de cada respuesta (empieza con `eyJra...`).

### 6.2 Importar la colección de Postman

En Postman: **Import** → arrastra `postman/LabTime_Postman_Collection.json` y
`postman/LabTime_Postman_Environment.json`. Selecciona el environment **"LabTime - Local"**
(desplegable arriba a la derecha).

### 6.3 Pegar los tokens

Ícono del ojo 👁️ junto al desplegable de environment → **Edit** → pega cada `AccessToken` en la
columna **`CURRENT VALUE`** de `staff_token` y `requester_token` → **Save**.

### 6.4 Probar en orden

1. Carpeta **"1. Rooms (público)"** → `GET /rooms` → Send. Sin token, debe dar `200 OK` con `[]`.
2. Carpeta **"2. Rooms + Slots (solo STAFF)"** → `POST /rooms` → Send. Copia el `id` de la
   respuesta a la variable `room_id` del environment.
3. Mismo folder → `POST /slots` → Send. Copia el `id` a `slot_id`.
4. Carpeta **"3. Bookings (REQUESTER)"** → `POST /bookings` → Send. Copia el `id` a `booking_id`.
5. Corre el resto de las carpetas 3, 4 y 5 libremente.
6. Termina con la carpeta **"6. Negativos"** — las 5 requests ahí están armadas para devolver
   401/403/400 a propósito. Es tu evidencia en vivo para la defensa.

**Nota:** el environment apunta por defecto a `rooms_base_url=http://localhost:8081` y
`bookings_base_url=http://localhost:8082` (los microservicios corriendo sueltos en IntelliJ).
Si en cambio usas `docker compose` (paso 8), cambia ambas variables a `http://localhost:8888`.

---

## 7. Correr la app móvil

```powershell
cd mobile
copy .env.example .env
```

Edita `.env` y confirma `VITE_COGNITO_DOMAIN=https://us-east-14b0camgzv.auth.us-east-1.amazoncognito.com`.

```powershell
npm install
npm run dev
```

**✅ Comprobación:** `http://localhost:5173` muestra el login de LabTime. Antes de que el login
funcione de verdad, en Cognito Console → tu App client → **Hosted UI** agrega:
- Allowed callback URLs: `http://localhost:5173/callback`
- Allowed sign-out URLs: `http://localhost:5173/`
- OAuth grant types: **Authorization code grant**, scopes: `openid profile email`

---

## 8. (Opcional) Todo junto con Docker

```powershell
cd backend
docker compose up --build
```

Todo queda en `http://localhost:8888`. Cambia `VITE_API_BASE_URL` en `mobile\.env` y las
variables `rooms_base_url`/`bookings_base_url` del environment de Postman a esa misma URL.

---

## 9. Qué revisar para que cumpla la rúbrica completa

| Materia | Ya está | Verifica tú |
|---|---|---|
| 1. Análisis de Diseño | `docs/01_Analisis_Diseno.md` (RF/RNF, casos de uso, GitFlow, ADRs) | — |
| 2. Desarrollo Móvil | `mobile/` completo, 7 pantallas | `npm install && npm run dev` sin errores |
| 3. Arquitectura Empresarial | `backend/` completo + `docs/03_Arquitectura_RubricaMapeo.md` | `./gradlew build` en ambos servicios, tests de `rooms-service` pendientes |
| 4. Computación en la Nube | `docker-compose.yml` + `docs/04_Computacion_Nube.md` | `docker compose up --build` sin errores |
| 5. Emprendimiento | `docs/05_Emprendimiento.md` (Business Model Canvas) | — |
| Sustentación | `docs/06_Guion_Defensa.md`, `docs/07_Defensa_Arquitectura.md`, `docs/08` y `09` (presentaciones) | Practicar en voz alta |

## 10. Problemas comunes (más allá de AWS CLI)

| Síntoma | Causa probable | Solución |
|---|---|---|
| Gradle Sync nunca termina | Sin internet o firewall bloqueando Maven Central | Prueba otra red |
| `401` en TODOS los endpoints, incluso públicos | `issuer-uri` no coincide con tu User Pool | Confirma que sea `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_4B0CaMgZv` en ambos `application.yaml` |
| `403` aunque el usuario es STAFF | No está en el grupo, o el token es de antes de agregarlo | Repite 5.3, saca un token nuevo (5.1/6.1) |
| App móvil no redirige tras login | `VITE_REDIRECT_URI` no coincide EXACTO con el callback URL de Cognito | Deben ser idénticos, sin `/` de más |
| Error de CORS en el navegador | Ya corregido en el código (nginx + SecurityConfig) | Si usas Docker, reconstruye con `--build`, no solo `up` |
| `Connection refused` al crear una reserva | `rooms-service` no está corriendo | Repite el paso 2 |

Cuando algo falle y no esté aquí, pégame el error exacto (consola de IntelliJ, terminal, o
navegador) y lo resolvemos igual que hicimos con AWS CLI.
