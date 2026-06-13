import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SuggestContentResponse {
  content: string | null;
  success: boolean;
  error: string | null;
}

export interface PhotoResult {
  url: string;
  photographer: string;
  photographerUrl: string;
}

@Injectable({ providedIn: 'root' })
export class AiService {

  private readonly API = 'http://localhost:8083/api/ai';

  constructor(private http: HttpClient) {}

  // ─── Image search (backend → Pexels API → URL) ───────────────────

  /**
   * Le backend cherche une photo sur Pexels et retourne son URL.
   * Le browser Angular télécharge ensuite l'image directement (pas de blocage Cloudflare).
   */
  generateImage(prompt: string): Observable<PhotoResult> {
    const encodedPrompt = encodeURIComponent(prompt);
    return this.http.get<PhotoResult>(
      `${this.API}/generate-image?prompt=${encodedPrompt}`
    );
  }

  // ─── Content suggestion (via backend → Pollinations text API) ────

  /**
   * Appelle le backend Spring Boot qui appelle Pollinations text API.
   * Passer par le backend évite le message "deprecated for authenticated users".
   */
  suggestContent(titre: string, type: string): Observable<SuggestContentResponse> {
    return this.http.post<SuggestContentResponse>(`${this.API}/suggest-content`, { titre, type });
  }
}
