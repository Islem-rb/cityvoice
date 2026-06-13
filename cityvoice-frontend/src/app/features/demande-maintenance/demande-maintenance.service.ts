import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

// ✅ EXPORTEZ l'interface (c'est ce qui manquait)
export interface DemandeMaintenance {
  id: number;
  ressourceId: number;
  chefId: number;
  motif: string;
  urgence: 'BASSE' | 'MOYENNE' | 'HAUTE' | 'CRITIQUE';
  dateRemiseSouhaitee: string;
  dateDemande: string;
  statut: 'EN_ATTENTE' | 'ACCEPTEE' | 'REFUSEE' | 'TERMINEE'| 'EN_COURS';
  maintenanceId?: number;
  ressourceImageUrl?: string;
  ressourceMatricule?: string;
  ressourceNom?: string;
  ressourceType?: string;
  technicienId?: string;
}

@Injectable({ providedIn: 'root' })
export class DemandeMaintenanceService {
  private apiUrl = 'http://localhost:8085/api/demandes-maintenance';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('cv_token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  getAll(): Observable<DemandeMaintenance[]> {
    console.log('📞 getAll appelé');
    return this.http.get<DemandeMaintenance[]>(this.apiUrl, { headers: this.getHeaders() });
  }

  getEnAttente(): Observable<DemandeMaintenance[]> {
    return this.http.get<DemandeMaintenance[]>(`${this.apiUrl}/en-attente`, { headers: this.getHeaders() });
  }

  create(demande: Partial<DemandeMaintenance>): Observable<DemandeMaintenance> {
    return this.http.post<DemandeMaintenance>(this.apiUrl, demande, { headers: this.getHeaders() });
  }

  updateStatut(id: number, statut: string, maintenanceId?: number): Observable<DemandeMaintenance> {
  // 🔥 Construction de l'URL exactement comme dans le fetch
  let url = `${this.apiUrl}/${id}/statut?statut=${statut}`;
  if (maintenanceId) {
    url += `&maintenanceId=${maintenanceId}`;
  }
  console.log('📞 updateStatut - URL:', url);
  
  // 🔥 Envoyer null comme body (pas de body)
  return this.http.put<DemandeMaintenance>(url, null, { headers: this.getHeaders() });
}









  // ========== NOUVEAUX ==========

  /**
   * 🔍 Récupérer une demande par son ID (pour modification)
   */
  getById(id: number): Observable<DemandeMaintenance> {
    console.log(`🔍 getById appelé pour ID: ${id}`);
    return this.http.get<DemandeMaintenance>(`${this.apiUrl}/${id}`, { headers: this.getHeaders() });
  }

  /**
   * 📋 Récupérer les demandes d'un chef spécifique
   */
  getByChef(chefId: number): Observable<DemandeMaintenance[]> {
    console.log(`📋 getByChef appelé pour chefId: ${chefId}`);
    return this.http.get<DemandeMaintenance[]>(`${this.apiUrl}/chef/${chefId}`, { headers: this.getHeaders() });
  }

  /**
   * ✏️ MODIFIER une demande (chef d'équipe)
   */
  update(id: number, demande: Partial<DemandeMaintenance>): Observable<DemandeMaintenance> {
    console.log(`✏️ update appelé pour ID: ${id}`, demande);
    return this.http.put<DemandeMaintenance>(`${this.apiUrl}/${id}`, demande, { headers: this.getHeaders() });
  }

  /**
   * 🗑️ SUPPRIMER une demande (chef d'équipe)
   */
  delete(id: number): Observable<void> {
    console.log(`🗑️ delete appelé pour ID: ${id}`);
    return this.http.delete<void>(`${this.apiUrl}/${id}`, { headers: this.getHeaders() });
  }


assignerTechnicien(id: number, technicienId: string): Observable<DemandeMaintenance> {
  return this.http.put<DemandeMaintenance>(`${this.apiUrl}/${id}/assigner-technicien?technicienId=${technicienId}`, null, { headers: this.getHeaders() });
}


}
