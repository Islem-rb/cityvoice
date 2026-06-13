// src/app/features/message/message.service.ts

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Message {
  id: number;
  expediteurId: string;
  expediteurNom: string;
  expediteurRole: string;
  destinataireId: string;
  destinataireRole: string;
  contenu: string;
  imageUrl?: string;
  demandeId?: number;
  dateEnvoi: string;
  lu: boolean;
  dateLecture?: string;
}

@Injectable({
  providedIn: 'root'
})
export class MessageService {
  
  private apiUrl = 'http://localhost:8085/api/messages';

  constructor(private http: HttpClient) { }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('cv_token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * Envoyer un nouveau message
   */
  envoyerMessage(message: Partial<Message>): Observable<Message> {
    console.log('📨 Envoi du message:', message);
    return this.http.post<Message>(this.apiUrl, message, { headers: this.getHeaders() });
  }

  /**
   * Récupérer la conversation entre deux utilisateurs
   */
  getConversation(userId1: string, userId2: string): Observable<Message[]> {
    console.log('💬 Récupération conversation entre:', userId1, 'et', userId2);
    return this.http.get<Message[]>(`${this.apiUrl}/conversation/${userId1}/${userId2}`, { headers: this.getHeaders() });
  }

  /**
   * Récupérer tous les messages d'une demande spécifique
   */
  getMessagesByDemande(demandeId: number): Observable<Message[]> {
    console.log('💬 Récupération messages pour demande:', demandeId);
    return this.http.get<Message[]>(`${this.apiUrl}/demande/${demandeId}`, { headers: this.getHeaders() });
  }

  /**
   * Récupérer les messages non lus d'un utilisateur
   */
  getMessagesNonLus(userId: string): Observable<Message[]> {
    console.log('🔔 Récupération messages non lus pour:', userId);
    return this.http.get<Message[]>(`${this.apiUrl}/non-lus/${userId}`, { headers: this.getHeaders() });
  }

  /**
   * Marquer un message comme lu
   */
  marquerCommeLu(id: number): Observable<void> {
    console.log('✅ Marquage message comme lu:', id);
    return this.http.put<void>(`${this.apiUrl}/${id}/lu`, null, { headers: this.getHeaders() });
  }
}