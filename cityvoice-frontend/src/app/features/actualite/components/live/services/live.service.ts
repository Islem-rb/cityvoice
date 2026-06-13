import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Room,
  RoomEvent,
  Track,
  RemoteTrack,
  RemoteTrackPublication,
  RemoteParticipant,
  LocalParticipant,
  LocalVideoTrack,
  DisconnectReason
} from 'livekit-client';
import { environment } from '../../../../../../environments/environment';
import { AuthService } from '../../../../../core/services/auth.service';

export interface LiveRoom {
  roomName: string;
  /** Conservé pour compat : contient la même valeur que wsUrl. */
  roomUrl: string;
  /** URL WebSocket LiveKit (wss://xxx.livekit.cloud). */
  wsUrl: string;
  /** JWT signé par le backend. Streamer pour /create, viewer pour GET /{roomName}. */
  token: string;
  streamerUsername: string;
  streamerUserId?: string;
  title: string;
  startedAt: string;
  viewerCount: number;
}

@Injectable({ providedIn: 'root' })
export class LiveService {

  private readonly API = `http://localhost:8083/api/live`;

  /** Room LiveKit active (1 seule à la fois). */
  private room: Room | null = null;

  /** Container DOM où on injecte les <video>/<audio>. */
  private videoContainer: HTMLElement | null = null;

  /** État local micro / caméra. */
  private _micOn = true;
  private _camOn = true;

  /** Callback à appeler sur déconnexion / live terminé. */
  private readyToCloseCb: (() => void) | null = null;

  constructor(private http: HttpClient, private auth: AuthService) {
    // Force une déconnexion propre quand l'utilisateur ferme l'onglet / la
    // fenêtre. Sans ça, LiveKit Cloud met ~15-30s à s'apercevoir du départ
    // et le "fantôme" inflate le compteur de spectateurs côté streamer.
    if (typeof window !== 'undefined') {
      window.addEventListener('pagehide', () => {
        if (this.room) {
          try { this.room.disconnect(); } catch { /* noop */ }
        }
      });
      // beforeunload ne suffit pas toujours sur mobile (iOS), donc on double
      // avec pagehide ci-dessus. On l'ajoute quand même pour les desktops.
      window.addEventListener('beforeunload', () => {
        if (this.room) {
          try { this.room.disconnect(); } catch { /* noop */ }
        }
      });
    }
  }

  // ───────── REST ─────────

  createLive(title: string, userName?: string): Observable<LiveRoom> {
    const user = this.auth.getCurrentUser();
    const fallback = user?.email?.split('@')[0] || 'Citoyen';
    const body = {
      title,
      username: userName || fallback,
      userId: user?.userId || ''
    };
    return this.http.post<LiveRoom>(`${this.API}/create`, body, { headers: this.authHeaders() });
  }

  listLives(): Observable<LiveRoom[]> {
    return this.http.get<LiveRoom[]>(`${this.API}/list`, { headers: this.authHeaders() });
  }

  endLive(roomName: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/${roomName}`, { headers: this.authHeaders() });
  }

  /**
   * Récupère la room + un token VIEWER frais. Le backend génère le token
   * avec canPublish=false à chaque appel.
   */
  getLive(roomName: string): Observable<LiveRoom> {
    const user = this.auth.getCurrentUser();
    let params = new HttpParams();
    if (user?.userId) params = params.set('userId', user.userId);
    if (user?.email) {
      const name = user.email.split('@')[0];
      params = params.set('userName', name);
    }
    return this.http.get<LiveRoom>(
      `${this.API}/${roomName}`,
      { headers: this.authHeaders(), params }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.auth.getToken();
    return new HttpHeaders({ Authorization: `Bearer ${token || ''}` });
  }

  // ───────── LiveKit SDK ─────────

  /**
   * Liste les caméras disponibles dans le navigateur. Utile pour afficher
   * un sélecteur avant de démarrer le live (DroidCam crée plusieurs sources
   * virtuelles, il faut laisser le choix).
   */
  async listVideoDevices(): Promise<MediaDeviceInfo[]> {
    try {
      // Il faut avoir au moins une permission média pour que les labels soient exposés
      const devices = await Room.getLocalDevices('videoinput');
      return devices;
    } catch (e) {
      console.warn('[Live] Impossible de lister les caméras :', e);
      return [];
    }
  }

  async listAudioInputs(): Promise<MediaDeviceInfo[]> {
    try {
      return await Room.getLocalDevices('audioinput');
    } catch {
      return [];
    }
  }

  /**
   * Connecte l'utilisateur à la room LiveKit en tant que STREAMER.
   * Active la caméra (DroidCam si c'est la caméra par défaut de Chrome) + le micro,
   * puis injecte la vidéo locale dans `container`.
   *
   * @param videoDeviceId  deviceId de la caméra à utiliser (optionnel — évite le
   *                       "green screen" quand Chrome pioche une mauvaise source
   *                       DroidCam virtuelle).
   * @param audioDeviceId  deviceId du micro à utiliser (optionnel).
   */
  async joinAsStreamer(
    container: HTMLElement,
    liveRoom: LiveRoom,
    userName: string,
    videoDeviceId?: string,
    audioDeviceId?: string
  ): Promise<void> {
    await this.leave();
    this.videoContainer = container;
    this.clearContainer();

    this._micOn = true;
    this._camOn = true;

    // On NE force PAS la résolution — certaines sources (DroidCam) ne supportent
    // pas 720p et renvoient alors une image verte / corrompue. LiveKit s'adapte
    // à ce que renvoie getUserMedia.
    this.room = new Room({
      adaptiveStream: true,
      dynacast: true,
      videoCaptureDefaults: videoDeviceId ? { deviceId: videoDeviceId } : {},
      audioCaptureDefaults: audioDeviceId ? { deviceId: audioDeviceId } : {},
    });

    this.registerRoomListeners(/* isStreamer = */ true);

    await this.room.connect(liveRoom.wsUrl, liveRoom.token);

    // Active caméra + micro, avec deviceId explicite si fourni. Cela évite que
    // Chrome choisisse une source DroidCam virtuelle inactive.
    await this.room.localParticipant.setMicrophoneEnabled(true,
      audioDeviceId ? { deviceId: audioDeviceId } : undefined);
    await this.room.localParticipant.setCameraEnabled(true,
      videoDeviceId ? { deviceId: videoDeviceId } : undefined);

    // Petit délai pour laisser la piste se publier, puis attacher la vidéo locale
    setTimeout(() => {
      if (this.room) this.attachLocalVideo(this.room.localParticipant);
    }, 300);
  }

  /**
   * Connecte l'utilisateur en tant que VIEWER (canPublish=false).
   * Écoute les TrackSubscribed et attache automatiquement la vidéo + l'audio
   * du streamer dans `container`.
   */
  async joinAsViewer(container: HTMLElement, liveRoom: LiveRoom, userName: string): Promise<void> {
    await this.leave();
    this.videoContainer = container;
    this.clearContainer();

    this._micOn = false;
    this._camOn = false;

    this.room = new Room({
      adaptiveStream: true,
      dynacast: true,
    });

    this.registerRoomListeners(/* isStreamer = */ false);

    await this.room.connect(liveRoom.wsUrl, liveRoom.token);

    // Attache toutes les pistes déjà publiées (cas où le viewer arrive après)
    this.room.remoteParticipants.forEach((p: RemoteParticipant) => {
      p.trackPublications.forEach((pub: RemoteTrackPublication) => {
        if (pub.track) this.attachRemoteTrack(pub.track);
      });
    });
  }

  /** Coupe la connexion LiveKit et nettoie le DOM. */
  async leave(): Promise<void> {
    if (this.room) {
      try { await this.room.disconnect(); } catch {}
      this.room = null;
    }
    this.clearContainer();
    this.videoContainer = null;
    this.readyToCloseCb = null;
  }

  async toggleMic(): Promise<boolean> {
    if (!this.room) return this._micOn;
    this._micOn = !this._micOn;
    await this.room.localParticipant.setMicrophoneEnabled(this._micOn);
    return this._micOn;
  }

  async toggleCam(): Promise<boolean> {
    if (!this.room) return this._camOn;
    this._camOn = !this._camOn;
    await this.room.localParticipant.setCameraEnabled(this._camOn);
    // Si on rallume la caméra, il faut ré-attacher la vidéo
    if (this._camOn) this.attachLocalVideo(this.room.localParticipant);
    return this._camOn;
  }

  /**
   * Nombre RÉEL et UNIQUE de spectateurs dans la room (streamer EXCLU).
   *
   * Pourquoi on dédoublonne :
   * Le backend génère une identité aléatoire par connexion :
   *     "viewer-<userId>-<rand4>"      (utilisateur authentifié)
   *     "viewer-<rand8>"               (anonyme, pas d'userId)
   *
   * Quand un spectateur recharge sa page, LiveKit Cloud garde sa SESSION
   * précédente en vie jusqu'à ~15-30s (timeout de détection de déco). La
   * nouvelle connexion a une identité DIFFÉRENTE (nouveau random), donc le
   * même vrai utilisateur peut apparaître comme 2, 3, 5 "zombies" dans
   * `remoteParticipants`. C'est pour ça qu'on voyait "5 spectateurs" alors
   * qu'une seule personne regardait.
   *
   * Solution : on extrait le `userId` de l'identité et on le met dans un
   * Set → un userId = un spectateur, quoi qu'il arrive aux vieilles sessions.
   * Pour les viewers anonymes, on garde l'identité brute (pas de dédoublonnage
   * possible sans identifiant, mais ce cas est rare).
   */
  onParticipantsChange(cb: (count: number) => void): void {
    if (!this.room) return;

    const update = () => {
      if (!this.room) return;
      const uniqueViewers = new Set<string>();
      const add = (identity?: string) => {
        if (!identity || !identity.startsWith('viewer-')) return;
        uniqueViewers.add(this.viewerKey(identity));
      };
      this.room.remoteParticipants.forEach(p => add(p.identity));
      add(this.room.localParticipant?.identity);
      cb(uniqueViewers.size);
    };

    this.room.on(RoomEvent.ParticipantConnected, update);
    this.room.on(RoomEvent.ParticipantDisconnected, update);
    this.room.on(RoomEvent.Reconnected, update);
    // Premier appel immédiat
    update();
  }

  /**
   * Extrait la "clé utilisateur" d'une identité LiveKit viewer.
   *
   *   "viewer-1a2b3c-7f9e"       → "1a2b3c"      (userId pur)
   *   "viewer-abc-def-ghi-xxxx"  → "abc-def-ghi" (userId avec tirets)
   *   "viewer-8charsRand"        → identité brute (anonyme, pas de dedupe)
   */
  private viewerKey(identity: string): string {
    const parts = identity.split('-');
    // Authentifié : viewer + userId (≥1 segment) + random4  ⇒ ≥ 3 parts
    if (parts.length >= 3) {
      return parts.slice(1, -1).join('-');
    }
    return identity;
  }

  /**
   * Appelé quand la room se ferme côté serveur (streamer disconnect) ou
   * que la connexion tombe.
   */
  onReadyToClose(cb: () => void): void {
    this.readyToCloseCb = cb;
  }

  getCall(): Room | null {
    return this.room;
  }

  /**
   * Crée une mini-connexion LiveKit dédiée UNIQUEMENT à l'aperçu vidéo
   * d'un live dans une liste (thumbnail). Ne touche PAS à `this.room` (la
   * room principale du viewer/streamer). Retourne une fonction de cleanup
   * à appeler pour se déconnecter.
   *
   * - Pas d'audio (muted + on s'abonne qu'aux pistes vidéo)
   * - Pas de publication (token viewer)
   * - autoSubscribe=false pour économiser la bande passante
   */
  async createThumbnailPreview(
    liveRoom: LiveRoom,
    container: HTMLElement
  ): Promise<() => Promise<void>> {
    const room = new Room({
      adaptiveStream: true,
      dynacast: true,
    });

    const attachVideo = (track: RemoteTrack) => {
      if (track.kind !== Track.Kind.Video) return;
      const el = track.attach();
      el.setAttribute('autoplay', 'true');
      el.setAttribute('playsinline', 'true');
      (el as HTMLVideoElement).muted = true;
      el.style.width = '100%';
      el.style.height = '100%';
      el.style.objectFit = 'cover';
      el.style.pointerEvents = 'none';
      // Efface d'abord tout <video> précédent dans le container
      container.querySelectorAll('video').forEach(v => v.remove());
      container.appendChild(el);
    };

    room.on(RoomEvent.TrackSubscribed, attachVideo);
    room.on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack) => {
      track.detach().forEach(el => el.remove());
    });

    // Connect sans auto-subscribe pour ne s'abonner qu'à la vidéo
    await room.connect(liveRoom.wsUrl, liveRoom.token, { autoSubscribe: false });

    const subscribeVideoOnly = (p: RemoteParticipant) => {
      p.trackPublications.forEach((pub: RemoteTrackPublication) => {
        if (pub.kind === Track.Kind.Video) {
          pub.setSubscribed(true);
        }
      });
    };

    // Participants déjà présents (streamer)
    room.remoteParticipants.forEach(subscribeVideoOnly);
    // Nouveaux participants
    room.on(RoomEvent.ParticipantConnected, (p: RemoteParticipant) => subscribeVideoOnly(p));
    // Nouvelles pistes publiées (ex : caméra rallumée)
    room.on(RoomEvent.TrackPublished, (_pub, p) => subscribeVideoOnly(p as RemoteParticipant));

    return async () => {
      try { await room.disconnect(); } catch {}
      container.querySelectorAll('video').forEach(v => v.remove());
    };
  }

  // ───────── Helpers internes ─────────

  private registerRoomListeners(isStreamer: boolean): void {
    if (!this.room) return;

    // Côté viewer : attacher automatiquement les pistes reçues
    if (!isStreamer) {
      this.room.on(RoomEvent.TrackSubscribed,
        (track: RemoteTrack, _pub: RemoteTrackPublication, _p: RemoteParticipant) => {
          this.attachRemoteTrack(track);
        });
      this.room.on(RoomEvent.TrackUnsubscribed,
        (track: RemoteTrack) => {
          track.detach().forEach(el => el.remove());
        });
    }

    this.room.on(RoomEvent.Disconnected, (reason?: DisconnectReason) => {
      console.log('[Live] Déconnecté de LiveKit, raison =', reason);
      if (this.readyToCloseCb) {
        try { this.readyToCloseCb(); } catch {}
      }
    });
  }

  private attachLocalVideo(local: LocalParticipant): void {
    if (!this.videoContainer) return;
    const pub = local.getTrackPublication(Track.Source.Camera);
    const track = pub?.track as LocalVideoTrack | undefined;
    if (!track) return;
    // Supprime l'ancienne vidéo locale s'il y en a une
    this.videoContainer.querySelectorAll('video[data-local="true"]')
      .forEach(el => el.remove());
    const el = track.attach();
    el.setAttribute('data-local', 'true');
    el.setAttribute('autoplay', 'true');
    el.setAttribute('playsinline', 'true');
    (el as HTMLVideoElement).muted = true; // éviter larsen avec son propre micro
    el.style.width = '100%';
    el.style.height = '100%';
    el.style.objectFit = 'cover';
    this.videoContainer.appendChild(el);
  }

  private attachRemoteTrack(track: RemoteTrack): void {
    if (!this.videoContainer) return;
    const el = track.attach();
    el.setAttribute('autoplay', 'true');
    el.setAttribute('playsinline', 'true');
    if (track.kind === Track.Kind.Video) {
      el.style.width = '100%';
      el.style.height = '100%';
      el.style.objectFit = 'contain';
      el.style.background = '#0f172a';
    }
    this.videoContainer.appendChild(el);
  }

  private clearContainer(): void {
    if (this.videoContainer) {
      this.videoContainer.innerHTML = '';
    }
  }
}
