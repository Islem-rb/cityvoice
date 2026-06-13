// src/app/features/facture.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Facture {
  id?: number;
  demandeId: number;
  description: string;
  ressourceNom?: string;
  typeIntervention: string;
  dureeEstimee: string;
  coutTotal: number;
  statut: 'EN_ATTENTE' | 'PAYEE' | 'ANNULEE';
  dateEmission: string;
  datePaiement?: string;
  technicienId: string;
  chefId: number;
}

@Injectable({
  providedIn: 'root'
})
export class FactureService {
  private apiUrl = 'http://localhost:8085/api/factures';

  constructor(private http: HttpClient) { }

  // Créer une facture
  creerFacture(facture: Facture): Observable<Facture> {
    return this.http.post<Facture>(this.apiUrl, facture);
  }

  // Envoyer la facture au chef d'équipe
  envoyerFactureAuChef(factureId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${factureId}/envoyer`, {});
  }

  // Marquer comme payée
  marquerPayee(factureId: number): Observable<Facture> {
    return this.http.put<Facture>(`${this.apiUrl}/${factureId}/payer`, {});
  }

  // Récupérer les factures d'un technicien
  getFacturesByTechnicien(technicienId: string): Observable<Facture[]> {
    return this.http.get<Facture[]>(`${this.apiUrl}/technicien/${technicienId}`);
  }

  // Récupérer les factures d'une demande
  getFacturesByDemande(demandeId: number): Observable<Facture[]> {
    return this.http.get<Facture[]>(`${this.apiUrl}/demande/${demandeId}`);
  }

  payerFacturesGroupe(ids: number[]): Observable<any> {
  return this.http.post(`${this.apiUrl}/paiement-groupe`, { ids });
}

getFacturesByChef(chefId: number): Observable<Facture[]> {
    return this.http.get<Facture[]>(`${this.apiUrl}/chef/${chefId}`);
  }


  // facture.service.ts
payerFacture(factureId: number): Observable<Facture> {
  return this.http.put<Facture>(`${this.apiUrl}/${factureId}/payer`, {});
}
getAll(): Observable<Facture[]> {
  return this.http.get<Facture[]>(this.apiUrl);
}
}