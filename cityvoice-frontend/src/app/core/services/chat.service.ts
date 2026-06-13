import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, interval, Subscription } from 'rxjs';
import { switchMap, distinctUntilChanged } from 'rxjs/operators';

export interface ChatMsg {
  id: number;
  senderId: string;
  receiverId: string;
  content: string;
  sentAt: string;
  isRead: boolean;
  isMine?: boolean;
}

export interface ActiveConversation {
  contactId: string;
  lastId: number;
}

@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {

  private readonly API = 'http://localhost:8083/api/chat';
  private readonly POLL_MS = 2500; // polling toutes les 2.5s

  // Messages par conversation (key = contactId)
  private messagesMap = new Map<string, BehaviorSubject<ChatMsg[]>>();

  // Conversations actives pour le polling
  private activeConvs: ActiveConversation[] = [];
  private currentUserId = '';
  private pollSub?: Subscription;

  constructor(private http: HttpClient) {}

  ngOnDestroy(): void {
    this.stopPolling();
  }

  // ===== INIT =====
  init(userId: string): void {
    this.currentUserId = userId;
    this.startPolling();
  }

  // ===== MESSAGES OBSERVABLE =====
  getMessages$(contactId: string): Observable<ChatMsg[]> {
    if (!this.messagesMap.has(contactId)) {
      this.messagesMap.set(contactId, new BehaviorSubject<ChatMsg[]>([]));
    }
    return this.messagesMap.get(contactId)!.asObservable();
  }

  // ===== CHARGER HISTORIQUE =====
  loadHistory(contactId: string): void {
    this.http.get<ChatMsg[]>(`${this.API}/history/${this.currentUserId}/${contactId}`)
      .subscribe({
        next: (msgs) => {
          const enriched = this.enrichMessages(msgs);
          this.getOrCreate(contactId).next(enriched);

          // Enregistrer la conversation pour le polling
          const lastId = msgs.length > 0 ? Math.max(...msgs.map(m => m.id)) : 0;
          this.registerConversation(contactId, lastId);

          // Marquer comme lus
          this.markRead(contactId);
        },
        error: (err) => { console.error('[ChatService] Erreur historique:', err); }
      });
  }

  // ===== ENVOYER MESSAGE =====
  send(contactId: string, content: string): void {
    const payload = {
      senderId: this.currentUserId,
      receiverId: contactId,
      content
    };
    this.http.post<ChatMsg>(`${this.API}/send`, payload).subscribe({
      next: (msg) => {
        const enriched = { ...msg, isMine: true };
        const subj = this.getOrCreate(contactId);
        const current = subj.getValue();
        subj.next([...current, enriched]);

        // Update lastId
        const conv = this.activeConvs.find(c => c.contactId === contactId);
        if (conv) conv.lastId = msg.id;
        else this.activeConvs.push({ contactId, lastId: msg.id });
      },
      error: (err) => {
        console.error('[ChatService] Erreur envoi message:', err);
      }
    });
  }

  // ===== MARQUER COMME LUS =====
  markRead(contactId: string): void {
    this.http.put(`${this.API}/read/${this.currentUserId}/${contactId}`, {}).subscribe();
  }

  // ===== NOMBRE NON LUS =====
  getUnreadCount(contactId: string): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(
      `${this.API}/unread/${this.currentUserId}/${contactId}`
    );
  }

  // ===== DERNIER MESSAGE VU (pour l'indicateur "Vu" côté expéditeur) =====
  getLastSeenId(receiverId: string): Observable<{ lastSeenId: number }> {
    return this.http.get<{ lastSeenId: number }>(
      `${this.API}/seen/${this.currentUserId}/${receiverId}`
    );
  }

  // ===== POLLING =====
  private startPolling(): void {
    this.pollSub = interval(this.POLL_MS).subscribe(() => {
      this.activeConvs.forEach(conv => {
        this.http.get<ChatMsg[]>(
          `${this.API}/new/${this.currentUserId}/${conv.contactId}/${conv.lastId}`
        ).subscribe({
          next: (newMsgs) => {
            if (newMsgs.length === 0) return;
            const enriched = this.enrichMessages(newMsgs);
            const subj = this.getOrCreate(conv.contactId);
            const current = subj.getValue();
            // Éviter les doublons
            const existingIds = new Set(current.map(m => m.id));
            const toAdd = enriched.filter(m => !existingIds.has(m.id));
            if (toAdd.length > 0) {
              subj.next([...current, ...toAdd]);
              conv.lastId = Math.max(...newMsgs.map(m => m.id));
            }
          },
          error: () => {}
        });
      });
    });
  }

  private stopPolling(): void {
    this.pollSub?.unsubscribe();
  }

  // ===== HELPERS =====
  private registerConversation(contactId: string, lastId: number): void {
    const existing = this.activeConvs.find(c => c.contactId === contactId);
    if (existing) existing.lastId = lastId;
    else this.activeConvs.push({ contactId, lastId });
  }

  private getOrCreate(contactId: string): BehaviorSubject<ChatMsg[]> {
    if (!this.messagesMap.has(contactId)) {
      this.messagesMap.set(contactId, new BehaviorSubject<ChatMsg[]>([]));
    }
    return this.messagesMap.get(contactId)!;
  }

  private enrichMessages(msgs: ChatMsg[]): ChatMsg[] {
    return msgs.map(m => ({ ...m, isMine: m.senderId === this.currentUserId }));
  }
}
