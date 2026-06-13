import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LiveService, LiveRoom } from '../../services/live.service';
import { AuthService } from '../../../../../../core/services/auth.service';
import { UserService } from '../../../../../../core/services/user.service';

@Component({
  selector: 'app-watch-live',
  templateUrl: './watch-live.component.html',
  styleUrls: ['./watch-live.component.css']
})
export class WatchLiveComponent implements OnInit, OnDestroy {

  @ViewChild('videoContainer') videoContainer!: ElementRef<HTMLDivElement>;

  room: LiveRoom | null = null;
  loading = true;
  error: string | null = null;
  viewerCount = 0;
  liveEnded = false;   // true quand le streamer a terminé le live

  currentUserName = 'Spectateur';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private liveService: LiveService,
    private auth: AuthService,
    private userService: UserService
  ) {}

  async ngOnInit(): Promise<void> {
    // Récupérer le vrai nom du citoyen
    const user = this.auth.getCurrentUser();
    if (user?.userId) {
      try {
        const u: any = await this.userService.getById(user.userId).toPromise();
        this.currentUserName = u?.nom || u?.prenom || user.email?.split('@')[0] || 'Spectateur';
      } catch {
        this.currentUserName = user.email?.split('@')[0] || 'Spectateur';
      }
    }

    const roomName = this.route.snapshot.paramMap.get('roomName');
    if (!roomName) {
      this.error = 'Live introuvable';
      this.loading = false;
      return;
    }

    try {
      const room = await this.liveService.getLive(roomName).toPromise();
      if (!room) throw new Error('Live introuvable');
      this.room = room;
      this.loading = false;

      // Attendre 1 tick pour que le container soit dans le DOM
      await new Promise(resolve => setTimeout(resolve, 50));

      if (!this.videoContainer?.nativeElement) {
        throw new Error('Conteneur vidéo introuvable');
      }

      // Connexion LiveKit en viewer (token viewer fraîchement généré par le backend
      // avec canPublish=false). La vidéo du streamer sera auto-attachée.
      await this.liveService.joinAsViewer(
        this.videoContainer.nativeElement,
        room,
        this.currentUserName
      );

      // Compteur de spectateurs (count filtre déjà les "streamer-*" côté service)
      this.liveService.onParticipantsChange(count => {
        this.viewerCount = count;
      });

      // Quand LiveKit ferme la session (streamer raccroché ou bouton "Quitter")
      this.liveService.onReadyToClose(async () => {
        this.liveEnded = true;
        await this.liveService.leave();
        // Retour automatique après 3 secondes
        setTimeout(() => this.router.navigate(['/actualites']), 3000);
      });

    } catch (e: any) {
      console.error('[Watch] error:', e);
      this.error = e?.message || 'Impossible de rejoindre le live.';
      this.loading = false;
    }
  }

  async leave(): Promise<void> {
    await this.liveService.leave();
    this.router.navigate(['/actualites']);
  }

  async ngOnDestroy(): Promise<void> {
    await this.liveService.leave();
  }
}
