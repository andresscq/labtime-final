import React, { createContext, useContext, useEffect, useState } from "react";
import { jwtDecode } from "jwt-decode";
import { getIdToken, isLoggedIn, logout as doLogout } from "../services/auth";
import type { CognitoIdTokenClaims } from "../types/models";

interface AuthState {
  loggedIn: boolean;
  username: string | null;
  roles: string[];
  isStaff: boolean;
  logout: () => void;
  refresh: () => void;
}

const AuthContext = createContext<AuthState>({
  loggedIn: false,
  username: null,
  roles: [],
  isStaff: false,
  logout: () => {},
  refresh: () => {},
});

// Lee los claims del id_token SOLO para decidir que mostrar en la UI
// (ej. ocultar el boton "Aprobar reservas" si no es STAFF). Esto es
// cosmetico: la autorizacion de verdad la hace el backend en cada request,
// nunca confiamos en el cliente para eso.
function readClaims(): CognitoIdTokenClaims | null {
  const token = getIdToken();
  if (!token) return null;
  try {
    return jwtDecode<CognitoIdTokenClaims>(token);
  } catch {
    return null;
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [claims, setClaims] = useState<CognitoIdTokenClaims | null>(readClaims());

  const refresh = () => setClaims(readClaims());

  useEffect(() => {
    refresh();
  }, []);

  const roles = claims?.["cognito:groups"] ?? [];

  const value: AuthState = {
    loggedIn: isLoggedIn(),
    username: claims?.username ?? claims?.sub ?? null,
    roles,
    isStaff: roles.includes("STAFF"),
    logout: doLogout,
    refresh,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => useContext(AuthContext);
