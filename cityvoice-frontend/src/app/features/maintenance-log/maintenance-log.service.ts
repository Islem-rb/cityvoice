import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MaintenanceLog {
  id: number;
  typeIntervention: string;
  description: string;
  date: string;
    dateFin?: string;    // 🔥 AJOUTER - Date de fin (null si non terminée)

  ressourceId: number;
  recurrence?: number;           // ← AJOUTER
  uniteRecurrence?: string;      // ← AJOUTER (jours, mois, ans)
  prochaineMaintenance?: string;
  technicienId?: string;  // ← AJOUTER CETTE LIGNE
  numeroTechnicien?: string;  // ← AJOUTER
  cout?: number; // ← ajouté

}

@Injectable({
  providedIn: 'root'
})
export class MaintenanceLogService {
 getAll(): Observable<MaintenanceLog[]> {
  return this.http.get<MaintenanceLog[]>(this.baseUrl);
}
  private baseUrl = 'http://localhost:8085/api/maintenance-logs';

  constructor(private http: HttpClient) {}

  getByResource(resourceId: number): Observable<MaintenanceLog[]> {
    return this.http.get<MaintenanceLog[]>(`${this.baseUrl}/resource/${resourceId}`);
  }

   // 🔹 Créer une maintenance
  create(log: MaintenanceLog): Observable<MaintenanceLog> {
    return this.http.post<MaintenanceLog>(this.baseUrl, log);
  }

  // 🔹 Mettre à jour une maintenance
  update(id: number, log: MaintenanceLog): Observable<MaintenanceLog> {
    return this.http.put<MaintenanceLog>(`${this.baseUrl}/${id}`, log);
  }

  // 🔹 Supprimer une maintenance
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }


   // 🔥 AJOUTER CETTE MÉTHODE
  getById(id: number): Observable<MaintenanceLog> {
    return this.http.get<MaintenanceLog>(`${this.baseUrl}/${id}`);
  }
}