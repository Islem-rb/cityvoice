import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ReactionSummaryDto, TypeReaction } from '../models/reaction.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class ReactionService {

  private readonly URL = `http://localhost:8083/api/reactions`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private get userId(): string {
    return this.authService.getCurrentUser()?.userId ?? '';
  }

  /** GET /api/reactions/post/{postId}?userId={userId} */
  getSummary(postId: string): Observable<ReactionSummaryDto> {
    const uid = this.userId;
    const params = uid ? `?userId=${uid}` : '';
    return this.http.get<ReactionSummaryDto>(`${this.URL}/post/${postId}${params}`);
  }

  /** POST /api/reactions/post/{postId}  body: {userId, userName, userPhoto, type} */
  react(postId: string, type: TypeReaction, userName: string, userPhoto?: string): Observable<ReactionSummaryDto> {
    return this.http.post<ReactionSummaryDto>(`${this.URL}/post/${postId}`, {
      userId: this.userId,
      userName,
      userPhoto: userPhoto ?? null,
      type
    });
  }

  /** DELETE /api/reactions/post/{postId}?userId={userId} */
  unreact(postId: string): Observable<ReactionSummaryDto> {
    return this.http.delete<ReactionSummaryDto>(`${this.URL}/post/${postId}?userId=${this.userId}`);
  }
}
