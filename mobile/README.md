# LabTime — App móvil (Ionic + React + TypeScript)

Consume el backend de `labtime-backend` (rooms-service + bookings-service, vía el reverse proxy
nginx). Mismo stack que ya usaste en PROG2 (`laboratorios-ionic-JosEPinduisaca`).

## Pantallas (igual a la diapositiva "Pantallas de la app móvil")

| Pantalla | Ruta | Endpoint(s) |
|---|---|---|
| Login | `/login` | Cognito Hosted UI (OAuth2 + PKCE) |
| Buscar salas | `/rooms` | `GET /rooms`, `GET /rooms/{id}/slots/available` |
| Reservar | `/book/:slotId` | `POST /bookings` |
| Mis reservas | `/bookings/me` | `GET /bookings/me`, `PUT/DELETE /bookings/{id}`, `POST /equipment-requests` |
| Gestionar salas (STAFF) | `/staff/rooms` | CRUD completo `/rooms`, `/slots` |
| Aprobar reservas (STAFF) | `/staff/approvals` | `GET /bookings`, `PATCH /bookings/{id}/approve`, `PATCH /bookings/{id}/attended` |

## Configurar Cognito Hosted UI (requisito antes de correr la app)

El User Pool ya existe (`us-east-1_4B0CaMgZv`), pero el Hosted UI necesita configuración
adicional que probablemente aún no está hecha:

1. **Cognito Console → User Pool → App integration → Domain**: crear un dominio (ej.
   `labtime`, queda como `https://labtime.auth.us-east-1.amazoncognito.com`).
2. **App client → Hosted UI settings**:
   - Allowed callback URLs: `http://localhost:5173/callback` (dev) y
     `com.pucetec.labtime://callback` (app empaquetada).
   - Allowed sign-out URLs: `http://localhost:5173/`
   - OAuth flows: **Authorization code grant**
   - OAuth scopes: `openid`, `profile`, `email`
3. **Groups**: crear los grupos `STAFF` y `REQUESTER` (ya lo pedía el backend) y asignar
   usuarios de prueba a cada uno.
4. Copiar el dominio del Hosted UI a `.env` (ver `.env.example`).

## Correr en desarrollo (navegador)

```bash
cp .env.example .env    # y completa VITE_COGNITO_DOMAIN
npm install
npm run dev
```

Abre `http://localhost:5173`. El flujo completo: `/login` → redirige a Cognito → login real →
Cognito redirige a `/callback?code=...` → la app intercambia el código por tokens → `/rooms`.

## Empaquetar como app real (Capacitor)

```bash
npm run build
npx cap add android      # o: npx cap add ios
npx cap sync
npx cap open android
```

Antes de esto, agrega `com.pucetec.labtime://callback` como callback URL en Cognito (ya está
en `.env.example` como referencia) y ajusta `VITE_REDIRECT_URI` para ese build.

## Nota importante

Igual que el backend, este código **no se instaló ni se corrió** en este entorno (sin acceso de
red a npm registry aquí). Se escribió completo y se revisó a mano, pero antes de la defensa
corran `npm install && npm run dev` para confirmar que compila y ajustar cualquier detalle de
versión de dependencias.
