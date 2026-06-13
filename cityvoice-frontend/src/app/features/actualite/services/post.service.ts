import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Post } from '../models/post.model';

@Injectable({ providedIn: 'root' })
export class PostService {

  private apiUrl = 'http://localhost:8083/api/posts';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Post[]> {
    return this.http.get<Post[]>(this.apiUrl);
  }

  getById(id: number): Observable<Post> {
    return this.http.get<Post>(`${this.apiUrl}/${id}`);
  }

  // ✅ Sans image → JSON
  create(data: Partial<Post>): Observable<Post> {
    return this.http.post<Post>(this.apiUrl, data);
  }

  // ✅ Avec image ou vidéo → multipart sur /with-image
  createWithMedia(
    fields: { title: string; content: string; type: string; auteurId: string },
    media: File
  ): Observable<Post> {
    const fd = new FormData();
    fd.append(
      'post',
      new Blob([JSON.stringify(fields)], { type: 'application/json' })
    );
    fd.append('file', media, media.name);
    return this.http.post<Post>(`${this.apiUrl}/with-image`, fd);
  }

  // @deprecated – alias pour rétrocompatibilité
  createWithImage(
    fields: { title: string; content: string; type: string; auteurId: string },
    image: File
  ): Observable<Post> {
    return this.createWithMedia(fields, image);
  }

  update(id: number, data: Partial<Post>): Observable<Post> {
    return this.http.put<Post>(`${this.apiUrl}/${id}`, data);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /**
   * Partager un post — crée une nouvelle entrée liée à l'original
   * et déclenche une notification WebSocket à l'auteur original
   */
  sharePost(
    postId: number,
    payload: { sharerId: string; sharerNom: string; sharerPhoto?: string; commentaire?: string }
  ): Observable<Post> {
    return this.http.post<Post>(`${this.apiUrl}/${postId}/share`, payload);
  }
}