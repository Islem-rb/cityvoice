import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MembreEquipe } from './membre.service';
import { Fonction } from './membre.service';
export type Etat= 'EN_ATTENTE' | 'EN_EXECUTION' | 'LIBRE'


export interface Equipe{
id?: string;
  name: string;
  specialite: string;
  etat?: Etat;
  membresEquipe?: MembreEquipe[];
  gouvernorat ?:string



}

@Injectable({ providedIn: 'root' })
export class EquipeService {

  private base = `${environment.apiUrl}/personnel/equipe`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Equipe[]> {
    return this.http.get<Equipe[]>(`${this.base}/get`);
  }

  getById(id: string): Observable<Equipe> {
    return this.http.get<Equipe>(`${this.base}/${id}`);
  }

  getByNom(nom: string): Observable<Equipe> {
    return this.http.get<Equipe>(`${this.base}/nom/${nom}`);
  }

  getBySpecialite(specialite: string): Observable<Equipe[]> {
    return this.http.get<Equipe[]>(`${this.base}/specialite/${specialite}`);
  }

  add(equipe: Equipe): Observable<Equipe> {
    return this.http.post<Equipe>(`${this.base}/add`, equipe);
  }

  update(id: string, equipe: Equipe): Observable<string> {
    return this.http.put(`${this.base}/${id}`, equipe, { responseType: 'text' });
  }

  delete(id: string): Observable<string> {
    return this.http.delete(`${this.base}/${id}`, { responseType: 'text' });
  }

  updateStatut(id: string, etat: Etat): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/${etat}`, {});
  }

   addMembre(equipeId: string, membre: MembreEquipe): Observable<string> {
    return this.http.post(
      `${this.base}/${equipeId}/membre`,
      membre,
      { responseType: 'text' }
    );
  }

  removeMembre(equipeId: string, membreId: string): Observable<string> {
    return this.http.delete(`${this.base}/${equipeId}/membre/${membreId}`, { responseType: 'text' });
  }
   hasFonction(id: string, fonction: Fonction): Observable<boolean> {
  return this.http.get<boolean>(
    `${this.base}/${id}/has-fonction/${fonction}`
  );
}

}
