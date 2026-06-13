import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SuiviTechnicien {
  id: number;
  technicienId: string;
  maintenanceId: number;
  statut: string;
  debut: string;
  fin: string;
  dureeSecondes: number;
  estPaye: boolean;
}

@Injectable({ providedIn: 'root' })
export class TechnicienService {
  private apiUrl = 'http://localhost:8085/api/suivi-technicien';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('cv_token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  // Démarrer une intervention
  demarrerIntervention(maintenanceId: number, technicienId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/demarrer?maintenanceId=${maintenanceId}&technicienId=${technicienId}`, null, { headers: this.getHeaders() });
  }

  // Changer de statut
  changerStatut(suiviId: number, nouveauStatut: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/${suiviId}/statut?nouveauStatut=${nouveauStatut}`, null, { headers: this.getHeaders() });
  }

  // Mettre hors ligne
  mettreHorsLigne(suiviId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/${suiviId}/hors-ligne`, null, { headers: this.getHeaders() });
  }

  // Terminer l'intervention
  terminerIntervention(suiviId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/${suiviId}/terminer`, null, { headers: this.getHeaders() });
  }

  // Récupérer le suivi en cours
  getSuiviEnCours(maintenanceId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/en-cours/${maintenanceId}`, { headers: this.getHeaders() });
  }

  // Récupérer le temps total
  getTempsTotal(maintenanceId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/temps-total/${maintenanceId}`, { headers: this.getHeaders() });
  }
}