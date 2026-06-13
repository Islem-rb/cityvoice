import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { LiveService, LiveRoom } from '../../services/live.service';
import { AuthService } from '../../../../../../core/services/auth.service';
import { UserService } from '../../../../../../core/services/user.service';

@Component({
  selector: 'app-start-live',
  templateUrl: './start-live.component.html',
  styleUrls: ['./start-live.component.css']
})
export class StartLiveComponent implements OnInit, OnDestroy {

  @ViewChild('videoContainer') videoContainer!: ElementRef<HTMLDivElement>;

  title = '';
  isLive = false;
  loading = false;
  error: string | null = null;

  room: LiveRoom | null = null;
  viewerCount = 0;
  micOn = true;
  camOn = true;

  currentUserName = 'Citoyen';

  // Devices disponibles (sélecteur pré-live)
  cameras: MediaDeviceInfo[] = [];
  microphones: MediaDeviceInfo[] = [];
  selectedCameraId = '';
  selectedMicId = '';
  devicesLoading = false;

  constructor(
    private liveService: LiveService,
    private auth: AuthService,
    private userService: UserService,
    private router: Router
  ) {}

  async ngOnInit(): Promise<void> {
    const user = this.auth.getCurrentUser();
    if (user?.userId) {
      this.userService.getById(user.userId).subscribe({
        next: (u: any) => {
          this.currentUserName = u?.nom || u?.prenom || user.email?.split('@')[0] || 'Citoyen';
        },
        error: () => {
          this.currentUserName = user.email?.split('@')[0] || 'Citoyen';
        }
      });
    }
    await this.loadDevices();
  }

  async loadDevices(): Promise<void> {
    this.devicesLoading = true;
    try {
      try {
        const tmp = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        tmp.getTracks().forEach(t => t.stop());
      } catch { /* permission refusée — on continue */ }

      this.cameras = await this.liveService.listVideoDevices();
      this.microphones = await this.liveService.listAudioInputs();

      const droidcam = this.cameras.find(c => /droidcam/i.test(c.label));
      this.selectedCameraId = droidcam?.deviceId || this.cameras[0]?.deviceId || '';
      this.selectedMicId = this.microphones[0]?.deviceId || '';
    } finally {
      this.devicesLoading = false;
    }
  }

  async startLive(): Promise<void> {
    if (!this.title.trim()) {
      this.error = 'Donnez un titre à votre live.';
      return;
    }
    this.error = null;
    this.loading = true;

    try {
      const room = await this.liveService.createLive(this.title, this.currentUserName).toPromise();
      if (!room) throw new Error('Impossible de créer la room');
      this.room = room;
      this.isLive = true;

      await new Promise(resolve => setTimeout(resolve, 50));

      if (!this.videoContainer?.nativeElement) {
        throw new Error('Conteneur vidéo introuvable');
      }

      await this.liveService.joinAsStreamer(
        this.videoContainer.nativeElement,
        room,
        this.currentUserName,
        this.selectedCameraId || undefined,
        this.selectedMicId || undefined
      );

      // Nombre RÉEL de spectateurs (identités "viewer-*") — le streamer
      // s'exclut lui-même grâce au filtre côté LiveService.
      this.liveService.onParticipantsChange(count => {
        this.viewerCount = count;
      });
    } catch (e: any) {
      console.error('[Live] startLive error:', e);
      this.error = e?.message || 'Erreur au démarrage du live.';
      this.isLive = false;
    } finally {
      this.loading = false;
    }
  }

  async stopLive(): Promise<void> {
    if (!this.room) return;
    const name = this.room.roomName;
    try { await this.liveService.leave(); } catch {}
    try { await this.liveService.endLive(name).toPromise(); } catch {}
    this.isLive = false;
    this.room = null;
    this.router.navigate(['/actualites']);
  }

  async toggleMic(): Promise<void> {
    this.micOn = await this.liveService.toggleMic();
  }

  async toggleCam(): Promise<void> {
    this.camOn = await this.liveService.toggleCam();
  }

  async ngOnDestroy(): Promise<void> {
    if (this.isLive && this.room) {
      const name = this.room.roomName;
      await this.liveService.leave();
      this.liveService.endLive(name).subscribe({ error: () => {} });
    }
  }
}
