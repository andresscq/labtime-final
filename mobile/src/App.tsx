import React from "react";
import { IonApp, IonRouterOutlet, setupIonicReact } from "@ionic/react";
import { IonReactRouter } from "@ionic/react-router";
import { Redirect, Route } from "react-router-dom";

import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";

import LoginPage from "./pages/LoginPage";
import CallbackPage from "./pages/CallbackPage";
import SearchRoomsPage from "./pages/SearchRoomsPage";
import BookingPage from "./pages/BookingPage";
import MyBookingsPage from "./pages/MyBookingsPage";
import ManageRoomsPage from "./pages/ManageRoomsPage";
import ApprovalsPage from "./pages/ApprovalsPage";

/* Estilos base de Ionic */
import "@ionic/react/css/core.css";
import "@ionic/react/css/normalize.css";
import "@ionic/react/css/structure.css";
import "@ionic/react/css/typography.css";
import "./theme/variables.css";

setupIonicReact();

// Mapa completo pantalla -> ruta -> endpoint, igual al que se presento en
// la diapositiva "Pantallas de la app movil".
const App: React.FC = () => (
  <IonApp>
    <AuthProvider>
      <IonReactRouter>
        <IonRouterOutlet>
          <Route exact path="/login" component={LoginPage} />
          <Route exact path="/callback" component={CallbackPage} />

          <ProtectedRoute exact path="/rooms" component={SearchRoomsPage} />
          <ProtectedRoute exact path="/book/:slotId" component={BookingPage} />
          <ProtectedRoute exact path="/bookings/me" component={MyBookingsPage} />

          <ProtectedRoute exact path="/staff/rooms" component={ManageRoomsPage} staffOnly />
          <ProtectedRoute exact path="/staff/approvals" component={ApprovalsPage} staffOnly />

          <Route exact path="/">
            <Redirect to="/rooms" />
          </Route>
        </IonRouterOutlet>
      </IonReactRouter>
    </AuthProvider>
  </IonApp>
);

export default App;
