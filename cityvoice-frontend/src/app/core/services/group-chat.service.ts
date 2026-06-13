import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, interval, Subscription } from 'rxjs';

export interface ChatGroup {
  id: number;
  name: string;
  creatorId: string;
  memberIds: string[];
  blockedMemberIds?: string[];
  photoUrl?: string;
  createdAt: string;
}

export interface GroupMessage {
  id: number;
  groupId: number;
  senderId: string;
  senderName: string;
  senderPhoto: string;
  content: string;
  sentAt: string;
  isMine?: boolean;
}

@Injectable({ providedIn: 'root' })
export class GroupChatService implements OnDestroy {

  private readonly API = 'http://localhost:8083/api/groups';
  private readonly POLL_MS = 2500;

  // Messages par groupe (key = groupId)
  private messagesMap = new Map<number, BehaviorSubject<GroupMessage[]>>();

  // Groupes actifs pour le polling
  private activeGroups: { groupId: number; lastId: number }[] = [];
  private currentUserId = '';
  private pollSub?: Subscription;

  constructor(private http: HttpClient) {}

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  // ===== INIT =====
  init(userId: string): void {
    this.currentUserId = userId;
    this.startPolling();
  }

  get userId(): string {
    return this.currentUserId;
  }

  // ===== GROUPES API =====

  getGroupsForUser(userId: string): Observable<ChatGroup[]> {
    return this.http.get<ChatGroup[]>(`${this.API}/user/${userId}`);
  }

  createGroup(name: string, creatorId: string, memberIds: string[]): Observable<ChatGroup> {
    return this.http.post<ChatGroup>(this.API, { name, creatorId, memberIds });
  }

  leaveGroup(groupId: number, userId: string): Observable<ChatGroup> {
    return this.http.delete<ChatGroup>(`${this.API}/${groupId}/members/${userId}`);
  }

  addMembers(groupId: number, memberIds: string[]): Observable<ChatGroup> {
    return this.http.put<ChatGroup>(`${this.API}/${groupId}/members`, { memberIds });
  }

  kickMember(groupId: number, userId: string, requesterId: string): Observable<ChatGroup> {
    return this.http.delete<ChatGroup>(`${this.API}/${groupId}/kick/${userId}?requesterId=${requesterId}`);
  }

  blockMember(groupId: number, userId: string, requesterId: string): Observable<ChatGroup> {
    return this.http.post<ChatGroup>(`${this.API}/${groupId}/block/${userId}`, { requesterId });
  }

  unblockMember(groupId: number, userId: string, requesterId: string): Observable<ChatGroup> {
    return this.http.post<ChatGroup>(`${this.API}/${groupId}/unblock/${userId}`, { requesterId });
  }

  // ===== MESSAGES =====

  getMessages$(groupId: number): Observable<GroupMessage[]> {
    if (!this.messagesMap.has(groupId)) {
      this.messagesMap.set(groupId, new BehaviorSubject<GroupMessage[]>([]));
    }
    return this.messagesMap.get(groupId)!.asObservable();
  }

  loadHistory(groupId: number): void {
    this.http.get<GroupMessage[]>(`${this.API}/${groupId}/messages`).subscribe({
      next: (msgs) => {
        const enriched = this.enrich(msgs);
        this.getOrCreate(groupId).next(enriched);
        const lastId = msgs.length > 0 ? Math.max(...msgs.map(m => m.id)) : 0;
        this.registerGroup(groupId, lastId);
      },
      error: (err) => console.error('[GroupChatService] Erreur historique:', err)
    });
  }

  send(groupId: number, content: string, senderName: string, senderPhoto: string): void {
    const payload = {
      senderId: this.currentUserId,
      senderName,
      senderPhoto,
      content
    };
    this.http.post<GroupMessage>(`${this.API}/${groupId}/messages`, payload).subscribe({
      next: (msg) => {
        const enriched = { ...msg, isMine: true };
        const subj = this.getOrCreate(groupId);
        const current = subj.getValue();
        subj.next([...current, enriched]);
        const grp = this.activeGroups.find(g => g.groupId === groupId);
        if (grp) grp.lastId = msg.id;
        else this.activeGroups.push({ groupId, lastId: msg.id });
      },
      error: (err) => console.error('[GroupChatService] Erreur envoi:', err)
    });
  }

  // ===== POLLING =====
  private startPolling(): void {
    this.pollSub = interval(this.POLL_MS).subscribe(() => {
      this.activeGroups.forEach(grp => {
        this.http.get<GroupMessage[]>(`${this.API}/${grp.groupId}/messages/new/${grp.lastId}`)
          .subscribe({
            next: (newMsgs) => {
              if (newMsgs.length === 0) return;
              const enriched = this.enrich(newMsgs);
              const subj = this.getOrCreate(grp.groupId);
              const current = subj.getValue();
              const existingIds = new Set(current.map(m => m.id));
              const toAdd = enriched.filter(m => !existingIds.has(m.id));
              if (toAdd.length > 0) {
                subj.next([...current, ...toAdd]);
                grp.lastId = Math.max(...newMsgs.map(m => m.id));
              }
            },
            error: () => {}
          });
      });
    });
  }

  private registerGroup(groupId: number, lastId: number): void {
    const existing = this.activeGroups.find(g => g.groupId === groupId);
    if (existing) existing.lastId = lastId;
    else this.activeGroups.push({ groupId, lastId });
  }

  private getOrCreate(groupId: number): BehaviorSubject<GroupMessage[]> {
    if (!this.messagesMap.has(groupId)) {
      this.messagesMap.set(groupId, new BehaviorSubject<GroupMessage[]>([]));
    }
    return this.messagesMap.get(groupId)!;
  }

  private enrich(msgs: GroupMessage[]): GroupMessage[] {
    return msgs.map(m => ({ ...m, isMine: m.senderId === this.currentUserId }));
  }
}
