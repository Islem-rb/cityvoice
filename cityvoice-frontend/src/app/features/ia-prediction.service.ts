import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface IAPrediction {
  success: boolean;
  description: string;
  duree_heures: number;
  duree_formatee: string;
  type_intervention: string;
  cout_main_oeuvre: number;
  prime: number;
  cout_total: number;
  taux_horaire: number;
  mot_cle_trouve: string;
}

@Injectable({
  providedIn: 'root'
})
export class IaPredictionService {
  // URL de votre service IA (port 5001)
  private apiUrl = 'http://localhost:5001';

  constructor(private http: HttpClient) { }

  /**
   * Prédire la durée et le coût à partir de la description de la panne
   * @param description La description de la panne ou de la maintenance
   */
  predireDepuisDescription(description: string): Observable<IAPrediction> {
    return this.http.post<IAPrediction>(`${this.apiUrl}/predict-from-description`, { description });
  }
}