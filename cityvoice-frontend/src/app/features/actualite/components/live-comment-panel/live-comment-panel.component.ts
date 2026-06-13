import {
  AfterViewChecked,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { UserService } from '../../../../core/services/user.service';
import { LiveCommentService } from '../../services/live-comment.service';
import { LiveComment } from '../../models/live-comment.model';

/**
 * Panneau de commentaires "Instagram Live".
 *
 * Apparence :
 *  - overlay absolu sur la vidéo (padding bas-gauche)
 *  - derniers messages qui glissent par le bas, plus anciens qui fondent en haut
 *  - barre d'envoi en pilule + bouton cœur
 *
 * Tech :
 *  - composant STANDALONE (Angular 18) → importable sans module.
 *  - les messages transitent par le LiveCommentService (REST + STOMP).
 *  - on ne garde en mémoire que les N derniers visibles (MAX_VISIBLE) — les
 *    anciens restent dans `comments` pour l'historique complet éventuel mais
 *    disparaissent de l'UI (effet IG).
 */
@Component({
  selector: 'app-live-comment-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './live-comment-panel.component.html',
  styleUrls: ['./live-comment-panel.component.css']
})
export class LiveCommentPanelComponent implements OnInit, OnDestroy, OnChanges, AfterViewChecked {

  @Input() roomName!: string;
  @Input() currentUserName = 'Spectateur';

  @ViewChild('messagesBox') messagesBox?: ElementRef<HTMLDivElement>;

  /** Palette pour colorer les avatars quand il n'y a pas de photo. */
  private static readonly AVATAR_COLORS = [
    'linear-gradient(135deg,#f97316,#ef4444)',
    'linear-gradient(135deg,#3b82f6,#8b5cf6)',
    'linear-gradient(135deg,#10b981,#06b6d4)',
    'linear-gradient(135deg,#ec4899,#f43f5e)',
    'linear-gradient(135deg,#eab308,#f97316)',
    'linear-gradient(135deg,#8b5cf6,#ec4899)',
    'linear-gradient(135deg,#06b6d4,#3b82f6)'
  ];

  comments: LiveComment[] = [];
  draft = '';
  sending = false;
  loadingHistory = true;
  error: string | null = null;

  /** Cœurs flottants (style Instagram Live) — disparaissent automatiquement. */
  floatingHearts: { id: number; left: number; hue: number; color: string }[] = [];
  private heartIdSeq = 0;
  /** Palette de couleurs pour varier les cœurs comme sur Instagram. */
  private static readonly HEART_COLORS = [
    '#ef4444', '#ec4899', '#f43f5e', '#f97316', '#fb7185', '#f43f5e', '#e11d48'
  ];
  /** Compteur total de cœurs reçus (affichable comme likes count). */
  heartsCount = 0;

  private currentUserId: string | null = null;
  private currentUserPhoto: string | null = null;
  private sub: Subscription | null = null;

  /** True quand il faut scroller tout en bas au prochain cycle de rendu. */
  private pendingScrollToBottom = true;

  constructor(
    private liveCommentService: LiveCommentService,
    private auth: AuthService,
    private userService: UserService
  ) {}

  async ngOnInit(): Promise<void> {
    const user = this.auth.getCurrentUser();
    this.currentUserId = user?.userId || null;

    if (user?.userId) {
      try {
        const u: any = await this.userService.getById(user.userId).toPromise();
        if (u?.photo) this.currentUserPhoto = u.photo;
        if (!this.currentUserName || this.currentUserName === 'Spectateur') {
          this.currentUserName = u?.nom || u?.prenom || user.email?.split('@')[0] || 'Spectateur';
        }
      } catch { /* fallback déjà en place */ }
    }

    this.initForRoom(this.roomName);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['roomName'] && !changes['roomName'].firstChange) {
      this.initForRoom(this.roomName);
    }
  }

  ngAfterViewChecked(): void {
    if (this.pendingScrollToBottom) {
      this.scrollToBottom();
      this.pendingScrollToBottom = false;
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.liveCommentService.unsubscribeFromRoom();
  }

  /**
   * Retourne `true` si l'utilisateur est déjà proche du bas de la liste.
   * Utilisé pour n'auto-scroller que s'il "suit" le flux, sinon on le laisse
   * lire tranquillement les anciens messages.
   */
  private isNearBottom(): boolean {
    const el = this.messagesBox?.nativeElement;
    if (!el) return true;
    const delta = el.scrollHeight - el.scrollTop - el.clientHeight;
    return delta < 80; // tolérance de 80px
  }

  private scrollToBottom(): void {
    const el = this.messagesBox?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  // ───────── Envoi ─────────

  send(): void {
    const contenu = (this.draft || '').trim();
    if (!contenu || this.sending || !this.roomName) return;

    this.sending = true;
    this.liveCommentService.post(this.roomName, {
      contenu,
      auteurId: this.currentUserId || undefined,
      auteurNom: this.currentUserName,
      auteurPhoto: this.currentUserPhoto || undefined
    }).subscribe({
      next: () => {
        // Pas de push manuel : le backend broadcaste via STOMP et tout le monde
        // (y compris l'auteur) reçoit le message par ce canal → évite les doublons.
        this.draft = '';
        this.sending = false;
      },
      error: (err) => {
        console.error('[LiveCommentPanel] post KO:', err);
        this.error = "Impossible d'envoyer le commentaire.";
        this.sending = false;
      }
    });
  }

  /** Réaction rapide "❤️" — comme le bouton cœur d'Instagram Live. */
  sendHeart(): void {
    if (!this.roomName) return;
    // Feedback immédiat : on spawn un cœur visible dès le clic, sans attendre le
    // retour serveur (évite le délai réseau et donne le feeling Instagram).
    this.spawnFloatingHeart();

    // On poste quand même côté serveur pour que les autres spectateurs voient
    // également un cœur flottant. Le marqueur spécial "❤️" sera intercepté côté
    // réception pour générer un cœur flottant et PAS un message de commentaire.
    this.liveCommentService.post(this.roomName, {
      contenu: '❤️',
      auteurId: this.currentUserId || undefined,
      auteurNom: this.currentUserName,
      auteurPhoto: this.currentUserPhoto || undefined
    }).subscribe({
      next: () => {},
      error: () => {}
    });
  }

  /**
   * Détecte si un commentaire est une simple réaction cœur (vs un vrai texte).
   * Ex: "❤️" tout seul, ou éventuellement plusieurs cœurs consécutifs.
   */
  private isHeartReaction(c: LiveComment): boolean {
    const txt = (c?.contenu || '').trim();
    if (!txt) return false;
    // Uniquement des cœurs (un ou plusieurs) → réaction, pas un commentaire
    return /^(\u2764\uFE0F?|❤|❤️|♥)+$/.test(txt);
  }

  /** Ajoute un cœur flottant qui monte et s'efface automatiquement. */
  private spawnFloatingHeart(): void {
    const id = ++this.heartIdSeq;
    // Position horizontale aléatoire (0-60px de variation depuis le bouton)
    const left = Math.round(Math.random() * 60);
    const hue = Math.floor(Math.random() * 40) - 20;
    const palette = LiveCommentPanelComponent.HEART_COLORS;
    const color = palette[Math.floor(Math.random() * palette.length)];

    this.floatingHearts.push({ id, left, hue, color });
    this.heartsCount++;

    // Retirer après la fin de l'animation (≈3s)
    setTimeout(() => {
      this.floatingHearts = this.floatingHearts.filter(h => h.id !== id);
    }, 3200);
  }

  trackHeart = (_: number, h: { id: number }) => h.id;

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  // ───────── Helpers de rendu ─────────

  trackById(_i: number, c: LiveComment): number | string {
    return c.id ?? `${c.auteurId}-${c.date}-${c.contenu}`;
  }

  /**
   * Retourne TOUS les commentaires (plus de cap artificiel : l'utilisateur
   * peut scroller pour relire l'historique comme sur Instagram Live).
   * L'effet de fondu visuel sur les vieux messages est géré par le mask-image
   * CSS sur `.ig-messages`.
   */
  visibleComments(): LiveComment[] {
    return this.comments;
  }

  /** Couleur d'avatar déterministe à partir du nom/id. */
  avatarColor(c: LiveComment): string {
    const key = c.auteurId || c.auteurNom || 'x';
    let h = 0;
    for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
    const palette = LiveCommentPanelComponent.AVATAR_COLORS;
    return palette[h % palette.length];
  }

  /** Première lettre du nom, pour l'avatar sans photo. */
  initial(c: LiveComment): string {
    const name = (c.auteurNom || 'S').trim();
    return name.charAt(0).toUpperCase();
  }

  // ───────── Init / subscriptions ─────────

  private initForRoom(roomName: string | undefined): void {
    if (!roomName) return;

    this.comments = [];
    this.loadingHistory = true;
    this.error = null;

    this.liveCommentService.getHistory(roomName).subscribe({
      next: (list) => {
        // Historique : on filtre les réactions cœur (elles n'ont pas de sens
        // en "replay" — elles étaient éphémères lors du live) et on compte.
        const items = list || [];
        const hearts = items.filter(c => this.isHeartReaction(c));
        this.heartsCount = hearts.length;
        this.comments = items.filter(c => !this.isHeartReaction(c));
        this.loadingHistory = false;
        this.pendingScrollToBottom = true;          // position initiale en bas
      },
      error: (err) => {
        console.warn('[LiveCommentPanel] historique KO:', err);
        this.loadingHistory = false;
      }
    });

    this.sub?.unsubscribe();
    this.sub = this.liveCommentService.incoming$.subscribe((c) => {
      if (!c || (c.roomName && c.roomName !== this.roomName)) return;

      if (this.isHeartReaction(c)) {
        // Réaction cœur d'un autre viewer → afficher un cœur flottant
        // (pour l'émetteur, le cœur a déjà été spawn localement dans sendHeart,
        // mais ré-afficher un deuxième est OK — ça ressemble à Instagram).
        this.spawnFloatingHeart();
        return;
      }

      // N'auto-scroll que si l'utilisateur est déjà près du bas (sinon on
      // ne gêne pas sa lecture des anciens messages).
      const wasAtBottom = this.isNearBottom();
      this.comments.push(c);
      if (wasAtBottom) this.pendingScrollToBottom = true;
    });
    this.liveCommentService.subscribeToRoom(roomName);
  }
}
