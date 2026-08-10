import React, { useEffect, useState } from "react";
import {
  IonBackButton, IonButtons, IonContent, IonHeader, IonItem, IonLabel,
  IonList, IonPage, IonTitle, IonToolbar, IonBadge, IonButton, IonAlert,
  IonItemSliding, IonItemOptions, IonItemOption, IonModal, IonTextarea, IonText,
  IonSelect, IonSelectOption
} from "@ionic/react";
import { BookingsApi, EquipmentApi } from "../services/api";
import type { Booking, EquipmentCatalogItem, EquipmentCode } from "../types/models";

const statusColor: Record<string, string> = {
  PENDING: "warning", APPROVED: "primary", REJECTED: "danger",
  ATTENDED: "success", CANCELLED: "medium",
};

// Pantalla "Mis reservas" — GET /bookings/me, PUT/DELETE /bookings/{id},
// y el punto de entrada a "Pedir equipo" (POST /equipment-requests).
// Aqui se completa el CRUD del lado del REQUESTER: crear ya paso en
// BookingPage, aqui estan Read, Update y Delete.
const MyBookingsPage: React.FC = () => {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [toCancel, setToCancel] = useState<number | null>(null);
  const [editing, setEditing] = useState<Booking | null>(null);
  // Antes era un string libre; ahora se elige del catalogo fijo del backend
  // (GET /equipment-catalog), nunca se escribe a mano.
  const [catalog, setCatalog] = useState<EquipmentCatalogItem[]>([]);
  const [selectedEquipment, setSelectedEquipment] = useState<EquipmentCode | undefined>(undefined);

  const load = () => BookingsApi.mine().then(setBookings);
  useEffect(() => { load(); }, []);
  useEffect(() => { EquipmentApi.catalog().then(setCatalog); }, []);

  const savePurpose = async () => {
    if (!editing) return;
    await BookingsApi.update(editing.id, { purpose: editing.purpose });
    setEditing(null);
    load();
  };

  const requestEquipment = async (bookingId: number) => {
    if (!selectedEquipment) return;
    await EquipmentApi.create({ bookingId, equipment: selectedEquipment, quantity: 1 });
    setSelectedEquipment(undefined);
  };

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start"><IonBackButton defaultHref="/rooms" /></IonButtons>
          <IonTitle>Mis reservas</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonList>
          {bookings.map((b) => (
            <IonItemSliding key={b.id}>
              <IonItem>
                <IonLabel>
                  <h2>{b.purpose}</h2>
                  <p>{new Date(b.slot.startsAt).toLocaleString()} · creada {new Date(b.createdAt).toLocaleDateString()}</p>
                </IonLabel>
                <IonBadge color={statusColor[b.status] ?? "medium"}>{b.status}</IonBadge>
              </IonItem>
              <IonItemOptions side="end">
                <IonItemOption onClick={() => setEditing(b)}>Editar</IonItemOption>
                <IonItemOption color="danger" onClick={() => setToCancel(b.id)}>Cancelar</IonItemOption>
              </IonItemOptions>
            </IonItemSliding>
          ))}
          {bookings.length === 0 && (
            <IonItem lines="none"><IonText color="medium">Todavia no tienes reservas.</IonText></IonItem>
          )}
        </IonList>

        {/* Update: editar el proposito de la reserva */}
        <IonModal isOpen={editing !== null} onDidDismiss={() => setEditing(null)}>
          <IonContent className="ion-padding">
            <h2>Editar reserva</h2>
            <IonTextarea
              value={editing?.purpose ?? ""}
              onIonInput={(e) => editing && setEditing({ ...editing, purpose: e.detail.value ?? "" })}
            />
            <IonButton expand="block" onClick={savePurpose}>Guardar</IonButton>

            <h3 className="ion-margin-top">Pedir equipo para esta reserva</h3>
            <IonItem>
              <IonLabel position="stacked">Equipo</IonLabel>
              <IonSelect
                value={selectedEquipment}
                placeholder="Selecciona un equipo"
                onIonChange={(e) => setSelectedEquipment(e.detail.value)}
              >
                {catalog.map((item) => (
                  <IonSelectOption key={item.code} value={item.code}>
                    {item.displayName} (disponibles: {item.totalStock})
                  </IonSelectOption>
                ))}
              </IonSelect>
            </IonItem>
            <IonButton expand="block" fill="outline"
              onClick={() => editing && requestEquipment(editing.id)}>
              Solicitar equipo
            </IonButton>
          </IonContent>
        </IonModal>

        {/* Delete: cancelar reserva propia */}
        <IonAlert
          isOpen={toCancel !== null}
          onDidDismiss={() => setToCancel(null)}
          header="Cancelar reserva"
          message="Esta accion no se puede deshacer."
          buttons={[
            { text: "Volver", role: "cancel" },
            {
              text: "Cancelar reserva", role: "destructive",
              handler: async () => { if (toCancel) { await BookingsApi.cancel(toCancel); load(); } },
            },
          ]}
        />
      </IonContent>
    </IonPage>
  );
};

export default MyBookingsPage;
