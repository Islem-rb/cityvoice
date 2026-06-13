import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { LiveService, LiveRoom } from '../../services/live.service';
import { AuthService } from '../../../../../../core/services/auth.service';

/**
 * Affiche le bouton "Démarrer un Live" + la liste des lives en cours.
 * Le streamer peut terminer son propre live directement depuis cette liste.
 *
 * Chaque carte affiche un APERÇU VIDÉO en direct (mini LiveKit viewer,
 * muet, vidéo uniquement) au lieu d'un carré noir.
 */
@Component({
  selector: 'app-live-list',
  templateUrl: './live-list.component.html',
  styleUrls: ['./live-list.component.css']
})
export class LiveListComponent implements OnInit, OnDestroy, AfterViewChecked {

  lives: LiveRoom[] = [];
  loading = true;
  stoppingRoom: string | null = null; // roomName en cours de suppression
  private refreshSub?: Subscription;

  /** Références aux containers vidéo (un par live). */
  @ViewChildren('previewBox') previewBoxes!: QueryList<ElementRef<HTMLElement>>;

  /** Cleanups des mini-players LiveKit, clé = roomName. */
  private previews = new Map<string, () => Promise<void>>();

  constructor(
    private liveService: LiveService,
    private auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.refresh();
    this.refreshSub = interval(10000).subscribe(() => this.refresh());
  }

  ngAfterViewChecked(): void {
    // Appelé après chaque cycle de rendu Angular. On connecte les aperçus
    // pour les lives qui n'en ont pas encore, et on purge les anciens.
    this.syncPreviews();
  }

  /** trackBy pour *ngFor — évite qu'Angular recrée les DOM nodes à chaque refresh. */
  trackByRoomName = (_: number, live: LiveRoom) => live.roomName;

  isLoggedIn(): boolean {
    return this.auth.isLoggedIn();
  }

  /** Renvoie l'userId de l'utilisateur connecté. */
  currentUserId(): string | null {
    return this.auth.getCurrentUser()?.userId || null;
  }

  /** Vrai si l'utilisateur connecté est le streamer de ce live. */
  isMyLive(live: LiveRoom): boolean {
    const uid = this.currentUserId();
    return !!uid && !!live.streamerUserId && uid === live.streamerUserId;
  }

  refresh(): void {
    this.liveService.listLives().subscribe({
      next: (data) => { this.lives = data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  join(live: LiveRoom): void {
    this.router.navigate(['/live/watch', live.roomName]);
  }

  startLive(): void {
    if (!this.isLoggedIn()) {
      this.router.navigate(['/auth/login']);
      return;
    }
    this.router.navigate(['/live/start']);
  }

  /** Termine le live directement depuis la liste (pour le streamer). */
  stopLive(live: LiveRoom, event: Event): void {
    event.stopPropagation(); // Empêche la navigation vers watch-live
    this.stoppingRoom = live.roomName;
    this.liveService.endLive(live.roomName).subscribe({
      next: () => {
        // Déconnecte l'aperçu
        const cleanup = this.previews.get(live.roomName);
        if (cleanup) { cleanup(); this.previews.delete(live.roomName); }
        this.lives = this.lives.filter(l => l.roomName !== live.roomName);
        this.stoppingRoom = null;
      },
      error: () => { this.stoppingRoom = null; }
    });
  }

  // ───── Aperçus vidéo ─────

  /**
   * Synchronise les aperçus LiveKit avec la liste actuelle.
   * - Déconnecte ceux qui ne sont plus dans la liste
   * - Connecte ceux qui viennent d'apparaître
   */
  private async syncPreviews(): Promise<void> {
    if (!this.previewBoxes) return;

    const currentNames = new Set(this.lives.map(l => l.roomName));

    // 1) Purge les anciens qui ne sont plus listés
    for (const [name, cleanup] of Array.from(this.previews.entries())) {
      if (!currentNames.has(name)) {
        try { await cleanup(); } catch {}
        this.previews.delete(name);
      }
    }

    // 2) Connecte les nouveaux
    for (const boxRef of this.previewBoxes.toArray()) {
      const el = boxRef.nativeElement;
      const roomName = el.getAttribute('data-room');
      if (!roomName) continue;
      if (this.previews.has(roomName)) continue;
      const live = this.lives.find(l => l.roomName === roomName);
      if (!live) continue;

      // Marque l'intention (évite double-connexion pendant l'await)
      this.previews.set(roomName, async () => {});

      try {
        // Récupère un token viewer frais (le /list strippe les tokens)
        const withToken = await this.liveService.getLive(roomName).toPromise();
        if (!withToken) { this.previews.delete(roomName); continue; }
        const cleanup = await this.liveService.createThumbnailPreview(withToken, el);
        this.previews.set(roomName, cleanup);
      } catch (e) {
        console.warn('[LiveList] Aperçu impossible pour', roomName, e);
        this.previews.delete(roomName);
      }
    }
  }

  async ngOnDestroy(): Promise<void> {
    this.refreshSub?.unsubscribe();
    // Déconnecte tous les aperçus
    for (const cleanup of this.previews.values()) {
      try { await cleanup(); } catch {}
    }
    this.previews.clear();
  }
}
