import {
  Component, Input, OnInit, OnChanges, OnDestroy,
  SimpleChanges, Output, EventEmitter
} from '@angular/core';
import { ReactionService } from '../../../core/services/reaction.service';
import { TypeReaction, ReactionSummaryDto } from '../../../core/models/reaction.model';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';

export interface ReactionConfig {
  type:     TypeReaction;
  emoji:    string;
  label:    string;
  color:    string;
  bgActive: string;
}

@Component({
  selector: 'app-reaction-bar',
  templateUrl: './reaction-bar.component.html',
  styleUrls: ['./reaction-bar.component.css']
  // ✅ Pas de OnPush — Default change detection pour garantir le re-rendu
})
export class ReactionBarComponent implements OnInit, OnChanges, OnDestroy {

  @Input() set postId(val: string | number) { this._postId = String(val); }
  get postId(): string { return this._postId; }
  private _postId = '';

  /** true si l'utilisateur connecté est l'auteur du post (pour masquer le bouton Partager) */
  @Input() isOwner = false;

  /** Compteur de partages à afficher */
  @Input() shareCount = 0;

  /** Indique qu'un partage est en cours */
  @Input() sharing = false;

  /** Indique que le partage vient de réussir */
  @Input() shareSuccess = false;

  @Output() commentToggle = new EventEmitter<void>();
  @Output() shareClick    = new EventEmitter<void>();

  readonly reactions: ReactionConfig[] = [
    { type: TypeReaction.JAIME,   emoji: '👍', label: "J'aime",  color: '#1877F2', bgActive: 'rgba(24,119,242,.1)' },
    { type: TypeReaction.UTILE,   emoji: '💡', label: 'Utile',   color: '#F7B928', bgActive: 'rgba(247,185,40,.1)'  },
    { type: TypeReaction.BRAVO,   emoji: '👏', label: 'Bravo',   color: '#0D9B76', bgActive: 'rgba(13,155,118,.1)'  },
    { type: TypeReaction.SOUTIEN, emoji: '❤️', label: 'Soutien', color: '#E0385C', bgActive: 'rgba(224,56,92,.1)'   },
  ];

  summary: ReactionSummaryDto = { counts: [], userReaction: null, total: 0, reactors: [] };
  loading          = true;
  showPopup        = false;
  showWhoTooltip   = false;
  hoveredReaction: TypeReaction | null = null;
  animating: TypeReaction | null = null;

  /** Nom affiché de l'utilisateur connecté (chargé depuis le profil public) */
  currentUserName  = '';
  currentUserPhoto = '';

  private enterTimer:    any = null;
  private leaveTimer:    any = null;
  private whoEnterTimer: any = null;
  private whoLeaveTimer: any = null;

  constructor(
    private reactionSvc: ReactionService,
    public  authService: AuthService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (user?.userId) {
      // ✅ Email immédiatement comme fallback (avant que la requête HTTP revienne)
      this.currentUserName = user.email || '';
      // Puis charger le vrai nom via le profil public
      this.userService.getPublicProfile(user.userId).subscribe({
        next:  u   => {
          this.currentUserName  = u?.nom   || user.email || '';
          this.currentUserPhoto = u?.photo || '';
        },
        error: ()  => { /* garde l'email comme fallback */ }
      });
    }
    if (this.postId) this.load();
  }

  ngOnChanges(c: SimpleChanges): void {
    if (c['postId'] && !c['postId'].firstChange) this.load();
  }

  ngOnDestroy(): void {
    clearTimeout(this.enterTimer);
    clearTimeout(this.leaveTimer);
    clearTimeout(this.whoEnterTimer);
    clearTimeout(this.whoLeaveTimer);
  }

  // ── Charger le résumé depuis le backend ─────────────────────
  private load(): void {
    this.loading = true;
    this.reactionSvc.getSummary(this.postId).subscribe({
      next:  s  => { this.summary = s; this.loading = false; },
      error: (err) => {
        console.error('[ReactionBar] Erreur chargement réactions:', err);
        this.loading = false;
      }
    });
  }

  // ── Helpers ─────────────────────────────────────────────────
  getCount(type: TypeReaction): number {
    return this.summary.counts.find(r => r.type === type)?.count ?? 0;
  }

  isActive(type: TypeReaction): boolean {
    return this.summary.userReaction === type;
  }

  get activeConfig(): ReactionConfig | null {
    if (!this.summary.userReaction) return null;
    return this.reactions.find(r => r.type === this.summary.userReaction) ?? null;
  }

  get topReactions(): ReactionConfig[] {
    return [...this.summary.counts]
      .filter(c => c.count > 0)
      .sort((a, b) => b.count - a.count)
      .slice(0, 3)
      .map(c => this.reactions.find(r => r.type === c.type)!)
      .filter(Boolean);
  }

  /** Texte tooltip "Qui a réagi" — style Facebook */
  get whoReactedText(): string {
    const rs = this.summary.reactors ?? [];
    if (rs.length === 0) return '';
    const currentUserId = this.authService.getCurrentUser()?.userId ?? '';
    const sorted = [...rs].sort((a, b) =>
      a.userId === currentUserId ? -1 : b.userId === currentUserId ? 1 : 0
    );
    const names = sorted.map(r =>
      r.userId === currentUserId ? 'Vous' : r.userName
    );
    if (names.length === 1) return names[0];
    if (names.length === 2) return `${names[0]} et ${names[1]}`;
    const shown = names.slice(0, 2).join(', ');
    const rest  = names.length - 2;
    return `${shown} et ${rest} autre${rest > 1 ? 's' : ''}`;
  }

  // ── Hover popup (bouton J'aime) ──────────────────────────────
  onMainEnter(): void {
    clearTimeout(this.leaveTimer);
    this.enterTimer = setTimeout(() => { this.showPopup = true; }, 500);
  }

  onMainLeave(): void {
    clearTimeout(this.enterTimer);
    this.leaveTimer = setTimeout(() => {
      this.showPopup = false;
      this.hoveredReaction = null;
    }, 300);
  }

  onPopupEnter(): void { clearTimeout(this.leaveTimer); }

  onPopupLeave(): void {
    clearTimeout(this.enterTimer);
    this.leaveTimer = setTimeout(() => {
      this.showPopup = false;
      this.hoveredReaction = null;
    }, 200);
  }

  // ── Hover tooltip "Qui a réagi" ──────────────────────────────
  onCountEnter(): void {
    clearTimeout(this.whoLeaveTimer);
    this.whoEnterTimer = setTimeout(() => { this.showWhoTooltip = true; }, 300);
  }

  onCountLeave(): void {
    clearTimeout(this.whoEnterTimer);
    this.whoLeaveTimer = setTimeout(() => { this.showWhoTooltip = false; }, 200);
  }

  // ── Click handlers ───────────────────────────────────────────
  onMainClick(event: MouseEvent): void {
    event.stopPropagation();
    this.showPopup = false;
    if (this.summary.userReaction) {
      this.doUnreact();
    } else {
      this.doReact(TypeReaction.JAIME);
    }
  }

  onReact(event: MouseEvent, type: TypeReaction): void {
    event.stopPropagation();
    this.animating = type;
    setTimeout(() => { this.animating = null; }, 400);
    this.showPopup = false;
    this.doReact(type);
  }

  private doReact(type: TypeReaction): void {
    // Si même réaction → annuler (toggle off)
    if (this.isActive(type)) {
      this.doUnreact();
      return;
    }

    // ✅ Mise à jour optimiste IMMÉDIATE — l'utilisateur voit le résultat avant la réponse serveur
    const prev = this.cloneSummary();
    this.updateOptimisticReact(type);

    // Appel backend
    this.reactionSvc.react(this.postId, type, this.currentUserName, this.currentUserPhoto).subscribe({
      next:  s  => { this.summary = s; },         // ✅ Confirmer avec données réelles
      error: (err) => {
        console.error('[ReactionBar] Erreur react:', err);
        this.summary = prev;                       // ↩ Rollback si erreur
      }
    });
  }

  private doUnreact(): void {
    const prev = this.cloneSummary();
    this.updateOptimisticUnreact();

    this.reactionSvc.unreact(this.postId).subscribe({
      next:  s  => { this.summary = s; },
      error: (err) => {
        console.error('[ReactionBar] Erreur unreact:', err);
        this.summary = prev;
      }
    });
  }

  // ── Mises à jour optimistes ──────────────────────────────────
  private cloneSummary(): ReactionSummaryDto {
    return {
      ...this.summary,
      counts:   this.summary.counts.map(c => ({ ...c })),
      reactors: (this.summary.reactors ?? []).map(r => ({ ...r }))
    };
  }

  private updateOptimisticReact(type: TypeReaction): void {
    const counts   = this.summary.counts.map(c => ({ ...c }));
    const reactors = (this.summary.reactors ?? []).map(r => ({ ...r }));
    const uid      = this.authService.getCurrentUser()?.userId ?? '';

    // Retirer l'ancienne réaction si elle existe
    if (this.summary.userReaction) {
      const old = counts.findIndex(c => c.type === this.summary.userReaction);
      if (old > -1) counts[old].count = Math.max(0, counts[old].count - 1);
      const ri = reactors.findIndex(r => r.userId === uid);
      if (ri > -1) reactors.splice(ri, 1);
    }

    // Ajouter la nouvelle réaction
    const found = counts.find(c => c.type === type);
    if (found) { found.count++; } else { counts.push({ type, count: 1 }); }
    reactors.unshift({ userId: uid, userName: this.currentUserName || 'Vous', type });

    const delta = this.summary.userReaction ? 0 : 1;
    this.summary = { counts, userReaction: type, total: this.summary.total + delta, reactors };
  }

  private updateOptimisticUnreact(): void {
    const counts   = this.summary.counts.map(c => ({ ...c }));
    const reactors = (this.summary.reactors ?? []).map(r => ({ ...r }));
    const uid      = this.authService.getCurrentUser()?.userId ?? '';

    const idx = counts.findIndex(c => c.type === this.summary.userReaction);
    if (idx > -1) counts[idx].count = Math.max(0, counts[idx].count - 1);
    const ri = reactors.findIndex(r => r.userId === uid);
    if (ri > -1) reactors.splice(ri, 1);

    this.summary = { counts, userReaction: null, total: Math.max(0, this.summary.total - 1), reactors };
  }

  /** ✅ Vrai si userId correspond à l'utilisateur connecté */
  isCurrentUser(userId: string): boolean {
    const uid = this.authService.getCurrentUser()?.userId ?? '';
    return !!uid && uid === userId;
  }

  /** ✅ Emoji correspondant à un type de réaction */
  getEmojiForType(type: string): string {
    return this.reactions.find(r => r.type === type)?.emoji ?? '👍';
  }

  onCommentClick(): void { this.commentToggle.emit(); }
  trackByType(_: number, r: ReactionConfig): string { return r.type; }
}
