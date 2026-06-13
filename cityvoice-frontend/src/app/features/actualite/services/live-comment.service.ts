import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';
import { LiveComment } from '../models/live-comment.model';

/**
 * Service des commentaires du live.
 *
 * Responsabilités :
 *  - POST un commentaire via REST (le backend fait la persistance + broadcast).
 *  - GET l'historique à l'arrivée dans un live.
 *  - S'abonner en STOMP à /topic/live.{roomName}.comments pour voir les messages
 *    des autres viewers en temps réel.
 *
 * Un seul STOMP Client dédié aux commentaires live est créé (indépendant du
 * WebSocketService global qui sert aux notifications perso). Cela évite de
 * toucher à l'abonnement /topic/user.{userId} existant.
 */
@Injectable({ providedIn: 'root' })
export class LiveCommentService implements OnDestroy {

  private readonly API = `http://localhost:8083/api/live`;
  private readonly WS_URL = environment.apiUrl.replace(/^http/, 'ws') + '/ws';

  private client: Client | null = null;
  private currentSubscription: StompSubscription | null = null;
  private currentRoom: string | null = null;

  /** Stream Rx des nouveaux commentaires reçus via STOMP. */
  private readonly incomingSubject = new Subject<LiveComment>();
  public readonly incoming$ = this.incomingSubject.asObservable();

  constructor(private http: HttpClient, private auth: AuthService) {}

  // ───────── REST ─────────

  /** Historique des commentaires d'une room (les 200 derniers côté serveur). */
  getHistory(roomName: string): Observable<LiveComment[]> {
    return this.http.get<LiveComment[]>(
      `${this.API}/${encodeURIComponent(roomName)}/comments`,
      { headers: this.authHeaders() }
    );
  }

  /**
   * Poste un commentaire dans le live. Le backend renvoie le DTO persistant
   * ET diffuse vers tous les abonnés sur /topic/live.{roomName}.comments —
   * donc le poster recevra aussi son propre message via STOMP.
   */
  post(roomName: string, comment: LiveComment): Observable<LiveComment> {
    return this.http.post<LiveComment>(
      `${this.API}/${encodeURIComponent(roomName)}/comments`,
      comment,
      { headers: this.authHeaders() }
    );
  }

  /** Purge les commentaires côté serveur (ex: à la fin du live, côté streamer). */
  clear(roomName: string): Observable<void> {
    return this.http.delete<void>(
      `${this.API}/${encodeURIComponent(roomName)}/comments`,
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.auth.getToken();
    return new HttpHeaders({ Authorization: `Bearer ${token || ''}` });
  }

  // ───────── STOMP ─────────

  /**
   * Ouvre (si besoin) le client STOMP puis s'abonne au topic de la room.
   * Tous les messages reçus sont émis sur `incoming$`.
   *
   * Si on est déjà abonné à une autre room, on se désabonne proprement avant.
   */
  subscribeToRoom(roomName: string): void {
    if (this.currentRoom === roomName && this.currentSubscription) {
      return; // déjà abonné
    }
    this.unsubscribeFromRoom();
    this.currentRoom = roomName;
    this.ensureClient().then(client => this.doSubscribe(client, roomName));
  }

  /** Désabonne du topic courant (sans fermer la connexion STOMP). */
  unsubscribeFromRoom(): void {
    try {
      this.currentSubscription?.unsubscribe();
    } catch { /* noop */ }
    this.currentSubscription = null;
    this.currentRoom = null;
  }

  /** Ferme complètement la connexion STOMP (à appeler quand on quitte le live). */
  disconnect(): void {
    this.unsubscribeFromRoom();
    if (this.client?.active) {
      try { this.client.deactivate(); } catch { /* noop */ }
    }
    this.client = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  // ───────── private helpers ─────────

  private ensureClient(): Promise<Client> {
    if (this.client?.active) {
      return Promise.resolve(this.client);
    }

    return new Promise((resolve, reject) => {
      const client = new Client({
        brokerURL: this.WS_URL,
        reconnectDelay: 4000,
        onConnect: () => {
          // Si on avait perdu la connexion, on re-souscrit automatiquement
          if (this.currentRoom) {
            this.doSubscribe(client, this.currentRoom);
          }
          resolve(client);
        },
        onStompError: (frame) => {
          console.error('[LiveComment] STOMP error:', frame.headers['message']);
          reject(new Error(frame.headers['message'] || 'STOMP error'));
        },
        onWebSocketError: (ev) => {
          console.error('[LiveComment] WebSocket error — actualite-service (8083) KO ?', ev);
        }
      });
      this.client = client;
      client.activate();
    });
  }

  private doSubscribe(client: Client, roomName: string): void {
    try { this.currentSubscription?.unsubscribe(); } catch { /* noop */ }
    this.currentSubscription = client.subscribe(
      `/topic/live.${roomName}.comments`,
      (msg: IMessage) => {
        try {
          const comment: LiveComment = JSON.parse(msg.body);
          this.incomingSubject.next(comment);
        } catch {
          console.warn('[LiveComment] payload STOMP invalide:', msg.body);
        }
      }
    );
  }
}
