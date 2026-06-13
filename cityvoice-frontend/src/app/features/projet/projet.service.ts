import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Projet, VoteProjet, Paiement, StatutProjet } from './projet.model';

@Injectable({ providedIn: 'root' })
export class ProjetService {

  private url = `${environment.apiUrl}/api/projets`;
  private collecteUrl = `${environment.apiUrl}/api/collectes`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Projet[]> {
    return this.http.get<Projet[]>(this.url);
  }

  getById(id: number): Observable<Projet> {
    return this.http.get<Projet>(`${this.url}/${id}`);
  }

  getByStatut(statut: StatutProjet): Observable<Projet[]> {
    return this.http.get<Projet[]>(`${this.url}/statut/${statut}`);
  }

  getByCategorie(categorie: string): Observable<Projet[]> {
    return this.http.get<Projet[]>(`${this.url}/categorie/${categorie}`);
  }

  create(projet: Projet): Observable<Projet> {
    return this.http.post<Projet>(this.url, projet);
  }

  update(id: number, projet: Projet): Observable<Projet> {
    return this.http.put<Projet>(`${this.url}/${id}`, projet);
  }

  updateStatut(id: number, statut: StatutProjet): Observable<Projet> {
    return this.http.patch<Projet>(
      `${this.url}/${id}/statut?statut=${statut}`, {}
    );
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  // ── VOTE ─────────────────────────────────────────────
  vote(projetId: number, vote: VoteProjet): Observable<Projet> {
    return this.http.post<Projet>(`${this.url}/${projetId}/vote`, vote);
  }

  // ── PAIEMENT ─────────────────────────────────────────
  pay(collecteId: number, paiement: Paiement): Observable<any> {
    return this.http.post(`${this.collecteUrl}/${collecteId}/payer`, paiement);
  }
  

  initiateStripe(payload: {
  collecteId: number;
  montant:    number;
  userId:     string;
  email:      string;
  phone:       string;
  anonymous:  boolean;
  description: string;
}): Observable<{ checkoutUrl: string; paiementId: string; sessionId: string }> {
  return this.http.post<any>(
    `${environment.apiUrl}/api/stripe/initiate`, payload
  );
}

verifyStripe(payload: {
  paiementId: string;
  sessionId:  string;
}): Observable<{ success: boolean; montant?: number }> {
  return this.http.post<any>(
    `${environment.apiUrl}/api/stripe/verify`, payload
  );
}
predictPrice(payload: {
  description: string;
  categorie:   string;
  location:    string;
  titre:       string;
}): Observable<{ result: string }> {
  return this.http.post<{ result: string }>(
    `${environment.apiUrl}/api/ai/predict-price`, payload
  );
}
getPaymentHistory(userId: string): Observable<any[]> {
  return this.http.get<any[]>(
    `${environment.apiUrl}/api/stripe/history`,
    { params: { userId } }   
  );
}
getStatsByGouvernorat(): Observable<any[]> {
  return this.http.get<any[]>(
    `${environment.apiUrl}/api/stats/by-gouvernorat`
  );
}

getStatsByCategory(gouvernorat?: string): Observable<any[]> {
  const params = gouvernorat
    ? `?gouvernorat=${encodeURIComponent(gouvernorat)}` : '';
  return this.http.get<any[]>(
    `${environment.apiUrl}/api/stats/by-category${params}`
  );
}

getTopDonators(gouvernorat?: string): Observable<any[]> {
  const params = gouvernorat ? `?gouvernorat=${encodeURIComponent(gouvernorat)}` : '';
  return this.http.get<any[]>(
    `${environment.apiUrl}/api/stats/top-donators${params}`
  );
}

validateImage(payload: {
  image:       string;
  description: string;
  titre:       string;
}): Observable<{ matches: boolean; confidence: string; reason: string }> {
  return this.http.post<any>(
    `${environment.apiUrl}/api/ai/validate-image`, payload
  );
}

suggestProject(gouvernorat: string): Observable<{ result: any }> {
  return this.http.post<any>(
    `${environment.apiUrl}/api/ai/suggest-project`, { gouvernorat }
  );
}



initiateKonnect(payload: {
  collecteId:  number;
  montant:     number;
  userId:      string;
  phone:       string;
  anonymous:   boolean;
  description: string;
}): Observable<{ checkoutUrl: string; paiementId: string; paymentRef: string }> {
  return this.http.post<any>(
    `${environment.apiUrl}/api/konnect/initiate`, payload
  );
}

verifyKonnect(payload: {
  paiementId: string;
  paymentRef: string;
}): Observable<{ success: boolean; montant?: number; points?: number }> {
  return this.http.post<any>(
    `${environment.apiUrl}/api/konnect/verify`, payload
  );
}
}