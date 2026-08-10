import React from "react";
import { IonButton, IonContent, IonHeader, IonPage, IonTitle, IonToolbar, IonText } from "@ionic/react";
import { startLogin } from "../services/auth";

// Pantalla "Login" de la presentacion: autenticacion via Cognito.
// No hay formulario propio: el boton redirige al Hosted UI de AWS, que es
// quien realmente pide usuario/contrasena. Nuestra app nunca ve la contrasena.
const LoginPage: React.FC = () => {
  return (
    <IonPage>
      <IonHeader>
        <IonToolbar><IonTitle>LabTime</IonTitle></IonToolbar>
      </IonHeader>
      <IonContent className="ion-padding ion-text-center">
        <IonText>
          <h2>Reserva de laboratorios y aulas</h2>
          <p>Inicia sesion con tu cuenta institucional para reservar o gestionar salas.</p>
        </IonText>
        <IonButton expand="block" onClick={() => startLogin()}>
          Iniciar sesion
        </IonButton>
      </IonContent>
    </IonPage>
  );
};

export default LoginPage;
