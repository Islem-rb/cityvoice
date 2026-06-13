import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface FriendDto {
  id: string;
  nom: string;
  photo?: string;
  ville?: string;
  gouvernorat?: string;
  status: 'FRIEND' | 'PENDING_SENT' | 'PENDING_RECEIVED' | 'NONE' | 'BLOCKED';
  requestedAt?: string;
}

export type FriendshipStatus = 'FRIEND' | 'PENDING_SENT' | 'PENDING_RECEIVED' | 'NONE' | 'BLOCKED';

@Injectable({ providedIn: 'root' })
export class FriendService {

  // L'endpoint /api/friends a été déplacé du user-service (8081) vers le actualite-service (8083).
  private readonly API = 'http://localhost:8083/api/friends';

  constructor(private http: HttpClient) {}

  /** Liste des amis acceptés */
  getFriends(userId: string): Observable<FriendDto[]> {
    return this.http.get<FriendDto[]>(`${this.API}/${userId}`);
  }

  /** Demandes d'amis reçues (PENDING) */
  getPendingRequests(userId: string): Observable<FriendDto[]> {
    return this.http.get<FriendDto[]>(`${this.API}/requests/${userId}`);
  }

  /** Nombre de demandes en attente */
  getPendingCount(userId: string): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.API}/requests/count/${userId}`);
  }

  /** Statut entre deux users */
  getStatus(userId: string, otherId: string): Observable<{ status: FriendshipStatus }> {
    return this.http.get<{ status: FriendshipStatus }>(`${this.API}/status/${userId}/${otherId}`);
  }

  /** Envoyer une demande d'ami */
  sendRequest(requesterId: string, addresseeId: string): Observable<any> {
    return this.http.post(`${this.API}/request/${requesterId}/${addresseeId}`, {});
  }

  /** Accepter une demande */
  acceptRequest(requesterId: string, addresseeId: string): Observable<any> {
    return this.http.put(`${this.API}/accept/${requesterId}/${addresseeId}`, {});
  }

  /** Refuser une demande */
  rejectRequest(requesterId: string, addresseeId: string): Observable<any> {
    return this.http.put(`${this.API}/reject/${requesterId}/${addresseeId}`, {});
  }

  /** Supprimer un ami */
  removeFriend(userId: string, friendId: string): Observable<any> {
    return this.http.delete(`${this.API}/remove/${userId}/${friendId}`);
  }

  /** Bloquer un utilisateur */
  blockUser(userId: string, targetId: string): Observable<any> {
    return this.http.put(`${this.API}/block/${userId}/${targetId}`, {});
  }

  /** Débloquer un utilisateur */
  unblockUser(userId: string, targetId: string): Observable<any> {
    return this.http.put(`${this.API}/unblock/${userId}/${targetId}`, {});
  }

  /** Liste des utilisateurs bloqués */
  getBlockedUsers(userId: string): Observable<FriendDto[]> {
    return this.http.get<FriendDto[]>(`${this.API}/blocked/${userId}`);
  }
}
