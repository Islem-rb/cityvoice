import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Commentaire, CommentaireRequest } from '../models/commentaire.model';
import { AuthService } from '../../../core/services/auth.service';
@Injectable({ providedIn: 'root' })
export class CommentaireService {

  private apiUrl = 'http://localhost:8083/api/posts';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  getByPostId(postId: number): Observable<Commentaire[]> {
    return this.http.get<Commentaire[]>(`${this.apiUrl}/${postId}/commentaires`);
  }

  create(postId: number, contenu: string, auteurNom?: string, auteurPhoto?: string, parentId?: number): Observable<Commentaire> {
    const user = this.authService.getCurrentUser();
    const auteurId = user?.userId || '1';

    const data: CommentaireRequest = {
      contenu,
      auteurId,
      auteurNom,
      auteurPhoto,
      parentId  // undefined si commentaire racine, number si réponse
    };

    return this.http.post<Commentaire>(
      `${this.apiUrl}/${postId}/commentaires`,
      data
    );
  }
    update(postId: number, commentId: number, contenu: string): Observable<Commentaire> {
    return this.http.put<Commentaire>(
      `${this.apiUrl}/${postId}/commentaires/${commentId}`,
      { contenu }
    );
  }


  delete(postId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${postId}/commentaires/${id}`);
  }
}