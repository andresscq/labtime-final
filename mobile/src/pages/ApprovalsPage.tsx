import React, { useEffect, useState } from "react";
import {
  IonBackButton, IonBadge, IonButton, IonButtons, IonContent, IonHeader,
  IonItem, IonLabel, IonList, IonPage, IonTitle, IonToolbar
} from "@ionic/react";
import { BookingsApi } from "../services/api";
import type { Booking } from "../types/models";

const statusColor: Record<string, string> = {
  PENDING: "warning", APPROVED: "primary", REJECTED: "danger",
  ATTENDED: "success", CANCELLED: "medium",
};

// Pantalla "Aprobar reservas (STAFF)" — GET /bookings (admin),
// PATCH /bookings/{id}/approve, PATCH /bookings/{id}/attended.
const ApprovalsPage: React.FC = () => {
  const [bookings, setBookings] = useState<Booking[]>([]);

  const load = () => BookingsApi.all().then(setBookings);
  useEffect(() => { load(); }, []);

  const decide = async (id: number, status: "APPROVED" | "REJECTED") => {
    await BookingsApi.approve(id, status);
    load();
  };

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start"><IonBackButton defaultHref="/rooms" /></IonButtons>
          <IonTitle>Aprobar reservas</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonList>
          {bookings.map((b) => (
            <IonItem key={b.id}>
              <IonLabel>
                <h2>{b.purpose}</h2>
                <p>{b.requesterUsername} · {new Date(b.slot.startsAt).toLocaleString()}</p>
              </IonLabel>
              <IonBadge color={statusColor[b.status] ?? "medium"} slot="end">{b.status}</IonBadge>
              {b.status === "PENDING" && (
                <>
                  <IonButton size="small" onClick={() => decide(b.id, "APPROVED")}>Aprobar</IonButton>
                  <IonButton size="small" color="danger" onClick={() => decide(b.id, "REJECTED")}>Rechazar</IonButton>
                </>
              )}
              {b.status === "APPROVED" && (
                <IonButton size="small" onClick={() => BookingsApi.markAttended(b.id).then(load)}>
                  Marcar atendida
                </IonButton>
              )}
            </IonItem>
          ))}
        </IonList>
      </IonContent>
    </IonPage>
  );
};

export default ApprovalsPage;
