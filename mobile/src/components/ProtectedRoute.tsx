import React from "react";
import { Redirect, Route, RouteProps } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

interface Props extends RouteProps {
  component: React.ComponentType<any>;
  staffOnly?: boolean;
}

// Guarda de UI: si no hay sesion, manda a /login. Si la ruta es staffOnly y
// el usuario no tiene el rol, lo manda a /rooms. Es solo conveniencia visual
// -- el backend rechaza igual con 401/403 si alguien se salta esto a mano.
const ProtectedRoute: React.FC<Props> = ({ component: Component, staffOnly, ...rest }) => {
  const { loggedIn, isStaff } = useAuth();

  return (
    <Route
      {...rest}
      render={(props) => {
        if (!loggedIn) return <Redirect to="/login" />;
        if (staffOnly && !isStaff) return <Redirect to="/rooms" />;
        return <Component {...props} />;
      }}
    />
  );
};

export default ProtectedRoute;
