import { Injectable } from '@angular/core';
import AgoraRTC, {
  IAgoraRTCClient,
  IMicrophoneAudioTrack,
  IRemoteAudioTrack
} from 'agora-rtc-sdk-ng';

export interface CallState {
  channelName: string;
  contactName: string;
  isMuted: boolean;
  isConnected: boolean;
  duration: number; // secondes
}

@Injectable({ providedIn: 'root' })
export class CallService {

  private client: IAgoraRTCClient | null = null;
  private localAudioTrack: IMicrophoneAudioTrack | null = null;
  private durationInterval?: any;

  state: CallState | null = null;

  /** Rejoindre un canal vocal Agora */
  async joinChannel(
    appId: string,
    channelName: string,
    token: string | null,
    uid: number | string
  ): Promise<void> {
    this.client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });

    // Écouter les flux audio entrants
    this.client.on('user-published', async (user, mediaType) => {
      if (mediaType === 'audio') {
        await this.client!.subscribe(user, mediaType);
        const remoteAudio: IRemoteAudioTrack = user.audioTrack!;
        remoteAudio.play();
      }
    });

    // Rejoindre le canal
    await this.client.join(appId, channelName, token, uid);

    // Créer et publier le micro local
    this.localAudioTrack = await AgoraRTC.createMicrophoneAudioTrack();
    await this.client.publish([this.localAudioTrack]);

    this.state = { channelName, contactName: '', isMuted: false, isConnected: true, duration: 0 };

    // Timer durée d'appel
    this.durationInterval = setInterval(() => {
      if (this.state) this.state.duration++;
    }, 1000);
  }

  /** Mute / Unmute le micro */
  async toggleMute(): Promise<void> {
    if (!this.localAudioTrack || !this.state) return;
    this.state.isMuted = !this.state.isMuted;
    await this.localAudioTrack.setEnabled(!this.state.isMuted);
  }

  /** Quitter le canal et libérer les ressources */
  async leaveChannel(): Promise<void> {
    clearInterval(this.durationInterval);
    this.localAudioTrack?.close();
    this.localAudioTrack = null;
    await this.client?.leave();
    this.client = null;
    this.state = null;
  }

  /** Formater la durée en MM:SS */
  formatDuration(seconds: number): string {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }
}
