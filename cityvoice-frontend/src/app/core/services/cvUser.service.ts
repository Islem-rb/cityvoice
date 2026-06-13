import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CandidatureEquipe } from './candidature.service';
import { HttpClient } from '@angular/common/http';

export interface CvUser {
  id?: string;
  fileName?: string;
  fileType?: string;
  data?: Blob;
  candidature?: CandidatureEquipe;
  userId?: string;
}

export interface QuizResultRow {
  resultId: string;
  userId: string;
  cvId: string;
  score: number | null;
  totalQuestions: number | null;
  fonction: string;
  timeExpired: boolean;
  passedAt: string;
  nom: string;
  email: string;
  telephone: string;
  photo: string;
}

/** Body envoyé au endpoint POST /{cvId}/preselect */
export interface PreselectionRequest {
  userId:       string;
  poste:        string;
  equipeNom:    string;
  chefEquipeId: string;   // ← NOUVEAU : UUID du chef d'équipe connecté
}

@Injectable({ providedIn: 'root' })
export class CvUserService {

  private baseUrl = `${environment.apiUrl}/personnel/cvuser`;

  constructor(private http: HttpClient) {}

  /** Upload CV */
  uploadCV(candidatureId: string, idUser: string, file: File): Observable<CvUser> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', idUser);
    return this.http.post<CvUser>(`${this.baseUrl}/${candidatureId}`, formData);
  }

  hasApplied(candidatureId: string, userId: string): Observable<boolean> {
    return this.http.get<boolean>(
      `${this.baseUrl}/${candidatureId}/hasApplied/${userId}`
    );
  }

  /** Get CV by ID */
  getCV(cvId: string): Observable<CvUser> {
    return this.http.get<CvUser>(`${this.baseUrl}/${cvId}`);
  }

  /** Get all CVs by candidature */
  getCVsByCandidature(candidatureId: string): Observable<CvUser[]> {
    return this.http.get<CvUser[]>(
      `${this.baseUrl}/candidature/${candidatureId}/cvs`
    );
  }

  /** Delete CV */
  deleteCV(cvId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${cvId}`);
  }

  /** Update CV */
  updateCV(cvId: string, file: File): Observable<CvUser> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.put<CvUser>(`${this.baseUrl}/${cvId}`, formData);
  }

  /** Get CVs with user info */
  getCvsWithUsers(candidatureId: string): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/candidature/${candidatureId}/with-users`
    );
  }

  /** Download CV as Blob (pour preview + téléchargement) */
  downloadCvBlob(cvId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${cvId}/download`, { responseType: 'blob' });
  }

  /** Envoyer un email */
  sendEmail(payload: { to: string; subject: string; body: string }): Observable<any> {
    return this.http.post(`${this.baseUrl}/send-email`, payload);
  }

  /**
   * Score IA d'un CV.
   */
  scoreCv(cvId: string, description: string): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/${cvId}/score-smart`,
      { description }
    );
  }


  /**
   * Préselectionner un candidat.
   * Envoie une notification temps réel + email au candidat.
   */
  preselectCv(cvId: string, req: PreselectionRequest): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/${cvId}/preselect`, req);
  }

  getQuizResultsByCandidature(candidatureId: string): Observable<QuizResultRow[]> {
    return this.http.get<QuizResultRow[]>(
      `${environment.apiUrl}/personnel/quiz/candidature/${candidatureId}/results`
    );
  }

  hireFromQuizResult(
    candidatureId: string,
    resultId: string,
    fonctionMembre = 'TECHNICIEN'
  ): Observable<any> {
    return this.http.post<any>(
      `${environment.apiUrl}/personnel/quiz/candidature/${candidatureId}/hire`,
      { resultId, fonctionMembre }
    );
  }
  genererCvParIa(candidatureId: string, userId: string): Observable<Blob> {
    return this.http.post(
        `${this.baseUrl}/generer-ia/${candidatureId}?userId=${userId}`,
        {},
        { responseType: 'blob' }
    );
  }
}
