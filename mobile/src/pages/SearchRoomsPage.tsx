import React, { useEffect, useState } from "react";
import {
  IonContent, IonHeader, IonPage, IonTitle, IonToolbar, IonList, IonItem,
  IonLabel, IonBadge, IonButton, IonButtons, IonIcon, IonRefresher,
  IonRefresherContent, IonSpinner, IonText
} from "@ionic/react";
import { logOutOutline, personCircleOutline } from "ionicons/icons";
import { useHistory } from "react-router-dom";
import { RoomsApi } from "../services/api";
import type { Room } from "../types/models";
import { useAuth } from "../context/AuthContext";

// Pantalla "Buscar salas" — GET /rooms.
// Antes hacia GET /rooms + un GET /rooms/{id}/slots/available POR CADA sala
// (patron N+1). Ahora GET /rooms ya trae el horario COMPLETO (timeSlots)
// una sola llamada al backend para toda la pantalla.
const SearchRoomsPage: React.FC = () => {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const { isStaff, logout } = useAuth();
  const history = useHistory();

  const load = async () => {
    setLoading(true);
    setRooms(await RoomsApi.list());
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonTitle>Salas disponibles</IonTitle>
          <IonButtons slot="end">
            {isStaff && (
              <>
                <IonButton onClick={() => history.push("/staff/rooms")}>Gestionar</IonButton>
                <IonButton onClick={() => history.push("/staff/approvals")}>Aprobar</IonButton>
              </>
            )}
            <IonButton onClick={() => history.push("/bookings/me")}>
              <IonIcon icon={personCircleOutline} slot="icon-only" />
            </IonButton>
            <IonButton onClick={logout}>
              <IonIcon icon={logOutOutline} slot="icon-only" />
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonRefresher slot="fixed" onIonRefresh={async (e) => { await load(); e.detail.complete(); }}>
          <IonRefresherContent />
        </IonRefresher>

        {loading && <div className="ion-text-center ion-padding"><IonSpinner /></div>}

        <IonList>
          {rooms.map((room) => (
            <div key={room.id}>
              <IonItem lines="none">
                <IonLabel>
                  <h2>{room.name}</h2>
                  <p>{room.roomType} · {room.building} · capacidad {room.capacity}</p>
                </IonLabel>
              </IonItem>
              {room.timeSlots.length === 0 && (
                <IonItem lines="inset"><IonText color="medium">Sin horarios registrados</IonText></IonItem>
              )}
              {room.timeSlots.map((slot) => (
                <IonItem key={slot.id} button={slot.available} disabled={!slot.available} lines="inset"
                  onClick={() => slot.available && history.push(`/book/${slot.id}`)}>
                  <IonLabel>
                    {new Date(slot.startsAt).toLocaleString()} — {new Date(slot.endsAt).toLocaleTimeString()}
                  </IonLabel>
                  <IonBadge color={slot.available ? "success" : "medium"}>
                    {slot.available ? "Libre" : "Ocupado"}
                  </IonBadge>
                </IonItem>
              ))}
            </div>
          ))}
        </IonList>
      </IonContent>
    </IonPage>
  );
};

export default SearchRoomsPage;
