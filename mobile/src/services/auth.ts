// Autenticacion contra AWS Cognito Hosted UI usando Authorization Code + PKCE.
// PKCE es obligatorio aqui porque el App client es tipo SPA (sin client secret):
// no hay forma segura de guardar un secreto en una app movil/web, asi que en
// vez de un client secret se usa un "code_verifier" generado en el momento.

const DOMAIN = import.meta.env.VITE_COGNITO_DOMAIN as string;
const CLIENT_ID = import.meta.env.VITE_COGNITO_CLIENT_ID as string;
const REDIRECT_URI = import.meta.env.VITE_REDIRECT_URI as string;

const VERIFIER_KEY = "labtime_pkce_verifier";
const ACCESS_TOKEN_KEY = "labtime_access_token";
const ID_TOKEN_KEY = "labtime_id_token";
const REFRESH_TOKEN_KEY = "labtime_refresh_token";

function base64UrlEncode(bytes: Uint8Array): string {
  let str = btoa(String.fromCharCode(...bytes));
  return str.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomVerifier(): string {
  const bytes = new Uint8Array(64);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes).slice(0, 128);
}

async function challengeFromVerifier(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(new Uint8Array(digest));
}

// Paso 1: redirige al Hosted UI de Cognito (pantalla de login que administra AWS).
export async function startLogin(): Promise<void> {
  const verifier = randomVerifier();
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  const challenge = await challengeFromVerifier(verifier);

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: "code",
    scope: "openid profile email",
    redirect_uri: REDIRECT_URI,
    code_challenge_method: "S256",
    code_challenge: challenge,
  });

  window.location.href = `${DOMAIN}/oauth2/authorize?${params.toString()}`;
}

// Paso 2: la pagina /callback llama esto con el ?code=... que Cognito devolvio.
export async function exchangeCodeForTokens(code: string): Promise<void> {
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  if (!verifier) throw new Error("Falta el code_verifier — reinicia el login");

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: CLIENT_ID,
    code,
    redirect_uri: REDIRECT_URI,
    code_verifier: verifier,
  });

  const response = await fetch(`${DOMAIN}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    throw new Error(`No se pudo intercambiar el codigo por tokens (${response.status})`);
  }

  const tokens = await response.json();
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.access_token);
  localStorage.setItem(ID_TOKEN_KEY, tokens.id_token);
  if (tokens.refresh_token) {
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refresh_token);
  }
  sessionStorage.removeItem(VERIFIER_KEY);
}

// El access_token es el que se manda como Bearer al backend (rooms-service /
// bookings-service lo validan como Resource Server contra el mismo User Pool).
export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getIdToken(): string | null {
  return localStorage.getItem(ID_TOKEN_KEY);
}

export function isLoggedIn(): boolean {
  return getAccessToken() !== null;
}

export function logout(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(ID_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    logout_uri: REDIRECT_URI.replace("/callback", "/"),
  });
  window.location.href = `${DOMAIN}/logout?${params.toString()}`;
}
