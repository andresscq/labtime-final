import React, { useEffect, useState } from "react";
import {
  IonBackButton, IonButton, IonButtons, IonContent, IonFab, IonFabButton,
  IonHeader, IonIcon, IonInput, IonItem, IonItemOption, IonItemOptions,
  IonItemSliding, IonLabel, IonList, IonModal, IonPage, IonTitle, IonToolbar
} from "@ionic/react";
import { add } from "ionicons/icons";
import { RoomsApi } from "../services/api";
import type { Room } from "../types/models";

const emptyRoom = { name: "", roomType: "LAB", capacity: 20, building: "" };

// Pantalla "Gestionar salas (STAFF)" — CRUD completo sobre `rooms`:
// POST /rooms, PUT /rooms/{id}, DELETE /rooms/{id}. Ruta protegida por
// ProtectedRoute(staffOnly) en el router, y por hasRole("STAFF") en el backend.
const ManageRoomsPage: React.FC = () => {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [editing, setEditing] = useState<Room | typeof emptyRoom | null>(null);

  const load = () => RoomsApi.list().then(setRooms);
  useEffect(() => { load(); }, []);

  const save = async () => {
    if (!editing) return;
    if ("id" in editing) {
      await RoomsApi.update(editing.id, editing);
    } else {
      await RoomsApi.create(editing);
    }
    setEditing(null);
    load();
  };

  const remove = async (id: number) => {
    await RoomsApi.remove(id);
    load();
  };

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start"><IonBackButton defaultHref="/rooms" /></IonButtons>
          <IonTitle>Gestionar salas</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonList>
          {rooms.map((r) => (
            <IonItemSliding key={r.id}>
              <IonItem button onClick={() => setEditing(r)}>
                <IonLabel>
                  <h2>{r.name}</h2>
                  <p>{r.roomType} · {r.building} · capacidad {r.capacity}</p>
                </IonLabel>
              </IonItem>
              <IonItemOptions side="end">
                <IonItemOption color="danger" onClick={() => remove(r.id)}>Eliminar</IonItemOption>
              </IonItemOptions>
            </IonItemSliding>
          ))}
        </IonList>

        <IonFab vertical="bottom" horizontal="end" slot="fixed">
          <IonFabButton onClick={() => setEditing(emptyRoom)}>
            <IonIcon icon={add} />
          </IonFabButton>
        </IonFab>

        <IonModal isOpen={editing !== null} onDidDismiss={() => setEditing(null)}>
          <IonContent className="ion-padding">
            <h2>{editing && "id" in editing ? "Editar sala" : "Nueva sala"}</h2>
            <IonItem>
              <IonLabel position="stacked">Nombre</IonLabel>
              <IonInput value={editing?.name} onIonInput={(e) =>
                editing && setEditing({ ...editing, name: e.detail.value ?? "" })} />
            </IonItem>
            <IonItem>
              <IonLabel position="stacked">Tipo (LAB / AULA)</IonLabel>
              <IonInput value={editing?.roomType} onIonInput={(e) =>
                editing && setEditing({ ...editing, roomType: e.detail.value ?? "" })} />
            </IonItem>
            <IonItem>
              <IonLabel position="stacked">Capacidad</IonLabel>
              <IonInput type="number" value={editing?.capacity} onIonInput={(e) =>
                editing && setEditing({ ...editing, capacity: Number(e.detail.value) })} />
            </IonItem>
            <IonItem>
              <IonLabel position="stacked">Edificio</IonLabel>
              <IonInput value={editing?.building} onIonInput={(e) =>
                editing && setEditing({ ...editing, building: e.detail.value ?? "" })} />
            </IonItem>
            <IonButton expand="block" className="ion-margin-top" onClick={save}>Guardar</IonButton>
          </IonContent>
        </IonModal>
      </IonContent>
    </IonPage>
  );
};

export default ManageRoomsPage;
