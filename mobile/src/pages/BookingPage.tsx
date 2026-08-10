import React, { useState } from "react";
import {
  IonBackButton, IonButton, IonButtons, IonContent, IonHeader, IonInput,
  IonItem, IonLabel, IonPage, IonTitle, IonToolbar, IonText, IonTextarea
} from "@ionic/react";
import { useHistory, useParams } from "react-router-dom";
import { BookingsApi, ApiError } from "../services/api";

// Pantalla "Reservar" — POST /bookings. slotId viene de la ruta (se toco un
// horario libre en SearchRoomsPage); requesterUsername NO se pide aqui: el
// backend lo saca del JWT, nunca del formulario.
const BookingPage: React.FC = () => {
  const { slotId } = useParams<{ slotId: string }>();
  const [purpose, setPurpose] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const history = useHistory();

  const submit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      await BookingsApi.create({ slotId: Number(slotId), purpose });
      history.replace("/bookings/me");
    } catch (e) {
      // 409 = alguien mas reservo este horario primero (choque de concurrencia).
      if (e instanceof ApiError && e.status === 409) {
        setError("Este horario acaba de ser reservado por otra persona. Vuelve a buscar.");
      } else {
        setError(e instanceof Error ? e.message : "No se pudo reservar");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start"><IonBackButton defaultHref="/rooms" /></IonButtons>
          <IonTitle>Reservar horario</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent className="ion-padding">
        <IonItem>
          <IonLabel position="stacked">Motivo de la reserva</IonLabel>
          <IonTextarea
            value={purpose}
            onIonInput={(e) => setPurpose(e.detail.value ?? "")}
            placeholder="Ej. Practica de laboratorio de POO, grupo 3"
          />
        </IonItem>
        {error && <IonText color="danger"><p>{error}</p></IonText>}
        <IonButton expand="block" className="ion-margin-top" disabled={submitting || !purpose}
          onClick={submit}>
          Confirmar reserva
        </IonButton>
      </IonContent>
    </IonPage>
  );
};

export default BookingPage;
