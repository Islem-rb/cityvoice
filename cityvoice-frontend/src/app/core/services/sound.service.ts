import { Injectable } from '@angular/core';

/**
 * SoundService — sons UI smooth via Web Audio API
 * Aucun fichier audio externe requis.
 */
@Injectable({ providedIn: 'root' })
export class SoundService {
  private ctx: AudioContext | null = null;
  private _enabled = true;

  private get audioCtx(): AudioContext {
    if (!this.ctx) {
      this.ctx = new (window.AudioContext || (window as any).webkitAudioContext)();
    }
    // ← Résumer si suspendu (après interaction utilisateur)
    if (this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
    return this.ctx;
  }

  get isEnabled(): boolean { return this._enabled; }
  toggle(): void { this._enabled = !this._enabled; }

  /** Boutons d'action principaux */
  click(): void {
    if (!this._enabled) return;
    try {
      const ctx = this.audioCtx, t = ctx.currentTime;
      const osc = ctx.createOscillator(), gain = ctx.createGain();
      const flt = ctx.createBiquadFilter();
      flt.type = 'lowpass'; flt.frequency.value = 2400;
      osc.connect(flt); flt.connect(gain); gain.connect(ctx.destination);
      osc.type = 'sine';
      osc.frequency.setValueAtTime(660, t);
      osc.frequency.exponentialRampToValueAtTime(820, t + .04);
      osc.frequency.exponentialRampToValueAtTime(680, t + .12);
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(.07, t + .01);
      gain.gain.exponentialRampToValueAtTime(.001, t + .18);
      osc.start(t); osc.stop(t + .18);
    } catch {}
  }

  /** Navigation, onglets, steps */
  nav(): void {
    if (!this._enabled) return;
    try {
      const ctx = this.audioCtx, t = ctx.currentTime;
      const osc = ctx.createOscillator(), gain = ctx.createGain();
      osc.connect(gain); gain.connect(ctx.destination);
      osc.type = 'sine';
      osc.frequency.setValueAtTime(520, t);
      osc.frequency.exponentialRampToValueAtTime(640, t + .06);
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(.05, t + .008);
      gain.gain.exponentialRampToValueAtTime(.001, t + .12);
      osc.start(t); osc.stop(t + .12);
    } catch {}
  }

  /** Succès — accord do-mi-sol */
  success(): void {
    if (!this._enabled) return;
    try {
      const ctx = this.audioCtx;
      [523, 659, 784].forEach((freq, i) => {
        const t = ctx.currentTime + i * .1;
        const osc = ctx.createOscillator(), gain = ctx.createGain();
        osc.connect(gain); gain.connect(ctx.destination);
        osc.type = 'sine'; osc.frequency.value = freq;
        gain.gain.setValueAtTime(0, t);
        gain.gain.linearRampToValueAtTime(.07, t + .01);
        gain.gain.exponentialRampToValueAtTime(.001, t + .24);
        osc.start(t); osc.stop(t + .24);
      });
    } catch {}
  }

  notification(): void {
    if (!this._enabled) return;
    try {
      const ctx = this.audioCtx;
      [880, 1174].forEach((freq, i) => {
        const t = ctx.currentTime + i * .08;
        const osc = ctx.createOscillator(), gain = ctx.createGain();
        const flt = ctx.createBiquadFilter();
        flt.type = 'lowpass';
        flt.frequency.setValueAtTime(2600, t);
        osc.connect(flt);
        flt.connect(gain);
        gain.connect(ctx.destination);
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, t);
        osc.frequency.exponentialRampToValueAtTime(freq * .96, t + .18);
        gain.gain.setValueAtTime(0, t);
        gain.gain.linearRampToValueAtTime(.06, t + .01);
        gain.gain.exponentialRampToValueAtTime(.001, t + .22);
        osc.start(t);
        osc.stop(t + .22);
      });
    } catch {}
  }

  /**
   * Son "nouveau message" — style Messenger/Facebook (double pop doux).
   * Deux oscillateurs rapides : un "ding" clair + un léger harmonique.
   */
  message(): void {
    if (!this._enabled) return;
    try {
      const ctx = this.audioCtx;
      const t0 = ctx.currentTime;

      // Premier "pop" aigu (880 Hz → 1320 Hz, très court)
      const osc1  = ctx.createOscillator();
      const gain1 = ctx.createGain();
      osc1.type = 'sine';
      osc1.connect(gain1); gain1.connect(ctx.destination);
      osc1.frequency.setValueAtTime(880, t0);
      osc1.frequency.exponentialRampToValueAtTime(1320, t0 + .05);
      gain1.gain.setValueAtTime(0, t0);
      gain1.gain.linearRampToValueAtTime(.12, t0 + .01);
      gain1.gain.exponentialRampToValueAtTime(.001, t0 + .16);
      osc1.start(t0); osc1.stop(t0 + .18);

      // Second "pop" plus grave légèrement décalé (effet Messenger)
      const t1 = t0 + .08;
      const osc2  = ctx.createOscillator();
      const gain2 = ctx.createGain();
      osc2.type = 'sine';
      osc2.connect(gain2); gain2.connect(ctx.destination);
      osc2.frequency.setValueAtTime(660, t1);
      osc2.frequency.exponentialRampToValueAtTime(990, t1 + .05);
      gain2.gain.setValueAtTime(0, t1);
      gain2.gain.linearRampToValueAtTime(.09, t1 + .01);
      gain2.gain.exponentialRampToValueAtTime(.001, t1 + .18);
      osc2.start(t1); osc2.stop(t1 + .2);
    } catch {}
  }

  /** Toggle switch on/off */
  toggle2(on: boolean): void {
    if (!this._enabled) return;
    try {
      const ctx = this.audioCtx, t = ctx.currentTime;
      const osc = ctx.createOscillator(), gain = ctx.createGain();
      osc.connect(gain); gain.connect(ctx.destination);
      osc.type = 'sine'; osc.frequency.value = on ? 740 : 480;
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(.05, t + .008);
      gain.gain.exponentialRampToValueAtTime(.001, t + .09);
      osc.start(t); osc.stop(t + .09);
    } catch {}
  }
  keypress(): void {
  if (!this._enabled) return;
  try {
    const ctx = this.audioCtx, t = ctx.currentTime;
    const osc = ctx.createOscillator(), gain = ctx.createGain();
    osc.connect(gain); gain.connect(ctx.destination);
    osc.type = 'sine';
    osc.frequency.value = 400 + Math.random() * 80;
    gain.gain.setValueAtTime(0, t);
    gain.gain.linearRampToValueAtTime(.02, t + .005);
    gain.gain.exponentialRampToValueAtTime(.001, t + .06);
    osc.start(t); osc.stop(t + .06);
  } catch {}
}
}
