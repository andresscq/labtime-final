import React, { useEffect, useState } from "react";
import { IonContent, IonPage, IonSpinner, IonText } from "@ionic/react";
import { useHistory, useLocation } from "react-router-dom";
import { exchangeCodeForTokens } from "../services/auth";
import { useAuth } from "../context/AuthContext";

// A esta pantalla vuelve Cognito con ?code=... despues de un login exitoso
// en el Hosted UI. Aqui se completa el intercambio PKCE por los tokens reales.
const CallbackPage: React.FC = () => {
  const location = useLocation();
  const history = useHistory();
  const { refresh } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const code = params.get("code");
    if (!code) {
      setError("Cognito no devolvio un codigo de autorizacion.");
      return;
    }
    exchangeCodeForTokens(code)
      .then(() => {
        refresh();
        history.replace("/rooms");
      })
      .catch((e) => setError(e.message));
  }, []);

  return (
    <IonPage>
      <IonContent className="ion-padding ion-text-center">
        {error ? <IonText color="danger">{error}</IonText> : <IonSpinner />}
      </IonContent>
    </IonPage>
  );
};

export default CallbackPage;
