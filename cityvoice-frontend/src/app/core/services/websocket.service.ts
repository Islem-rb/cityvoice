import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Subject, Observable } from 'rxjs';

export interface WsNotification {
  id:           any;
  recipientId?: string;
  receiverId?:  string;
  actorId?:     string;
  senderId?:    string;
  actorName?:   string;
  actorPhoto?:  string;
  type:         string;
  title?:       string;
  message:      string;
  postId?:      number | null;
  read:         boolean;
  createdAt?:   string;
  cvId?:        string;
  fonction?:    string;
}

export interface RealtimeNotification {
  id:          any;
  receiverId?: string;   // optional now
  senderId?:   string;   // optional now
  title?:      string;   // optional now
  message:     string;
  type:        string;
  read:        boolean;
  createdAt?:  string;
  cvId?:       string;
  fonction?:   string;
}

export interface DbNotification extends RealtimeNotification {}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private connectedUserId: string | null = null;

  private notificationSubject = new Subject<WsNotification>();
  notification$ = this.notificationSubject.asObservable();

  // Dedicated subject for call signaling — no replay
  private callSubject = new Subject<any>();
  call$ = this.callSubject.asObservable();

  constructor(private http: HttpClient) {}

  connect(userId: string): void {
    if (!userId) {
      console.warn('[WS] connect() appelé sans userId — ignoré');
      return;
    }

    if (this.client?.active && this.connectedUserId === userId) {
      console.log('[WS] Déjà connecté pour userId:', userId);
      return;
    }

    if (this.client?.active) {
      this.disconnect();
    }

    this.connectedUserId = userId;
    console.log('[WS] Connexion WebSocket pour userId:', userId);

    this.client = new Client({
      brokerURL: 'ws://localhost:8083/ws',
      reconnectDelay: 5000,

      onConnect: () => {
        console.log('[WS] ✅ Connecté. Abonnement à /topic/user.' + userId);

        this.subscription = this.client!.subscribe(
          `/topic/user.${userId}`,
          (msg: IMessage) => {
            try {
              const payload = JSON.parse(msg.body);
              console.log('[WS] 🔔 Message reçu:', payload);

              if (
                payload.type === 'INCOMING_CALL' ||
                payload.type === 'CALL_REJECTED' ||
                payload.type === 'CALL_ENDED'
              ) {
                console.log('[WS] 📞 Call event → call$:', payload.type);
                this.callSubject.next(payload);
              } else {
                this.notificationSubject.next(payload as WsNotification);
              }
            } catch (_) {
              console.warn('[WS] Impossible de parser le message:', msg.body);
            }
          }
        );
      },

      onDisconnect: () => console.log('[WS] Déconnecté du broker STOMP'),

      onStompError: (frame) =>
        console.error('[WS] ❌ Erreur STOMP:', frame.headers['message']),

      onWebSocketError: (event) =>
        console.error('[WS] ❌ Erreur WebSocket:', event),
    });

    this.client.activate();
  }

  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.client?.deactivate();
    this.client = null;
    this.connectedUserId = null;
    console.log('[WS] Déconnexion propre effectuée');
  }

  // ── Methods required by the complex navbar ──────────────────

  getAll(userId: string): Observable<DbNotification[]> {
    return this.http.get<DbNotification[]>(
      `http://localhost:8083/personnel/notifications/user/${userId}`
    );
  }

  markRead(notifId: string | number): Observable<any> {
    return this.http.patch(
      `http://localhost:8083/personnel/notifications/${notifId}/read`, {}
    );
  }

  markAllRead(userId: string): Observable<any> {
    return this.http.patch(
      `http://localhost:8083/personnel/notifications/user/${userId}/read-all`, {}
    );
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
