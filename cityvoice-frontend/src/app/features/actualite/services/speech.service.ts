import { Injectable } from '@angular/core';

/**
 * Service Text-to-Speech (TTS) pour CityVoice.
 * Utilise l'API native du navigateur `SpeechSynthesisUtterance`.
 * Aucune clé API, aucune dépendance externe.
 *
 * Gère un seul post à la fois : lancer la lecture d'un nouveau post
 * arrête automatiquement celle en cours.
 */
@Injectable({ providedIn: 'root' })
export class SpeechService {

  /** ID du post actuellement lu (null si rien ne se lit) */
  private currentPostId: number | null = null;

  /** Référence à l'utterance courante (pour pouvoir l'annuler) */
  private currentUtterance: SpeechSynthesisUtterance | null = null;

  /** Callback pour notifier le composant quand la lecture se termine */
  private onEndCallback: (() => void) | null = null;

  /** Vérifie si l'API SpeechSynthesis est supportée par le navigateur */
  isSupported(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window;
  }

  /** Retourne true si le post donné est en cours de lecture */
  isSpeaking(postId: number): boolean {
    return this.currentPostId === postId;
  }

  /** Retourne l'ID du post en cours de lecture (null si rien) */
  getCurrentPostId(): number | null {
    return this.currentPostId;
  }

  /**
   * Lance la lecture vocale d'un texte en français.
   * Si un autre post est déjà en cours de lecture, l'arrête d'abord.
   *
   * @param postId   ID du post (pour tracker l'état du bouton)
   * @param text     Texte à lire (ex: "${titre}. ${contenu}")
   * @param onEnd    Callback appelé quand la lecture se termine (naturellement ou via stop())
   */
  speak(postId: number, text: string, onEnd?: () => void): void {
    if (!this.isSupported()) {
      console.warn('[SpeechService] SpeechSynthesis non supporté par ce navigateur.');
      return;
    }

    // Toujours annuler la lecture précédente (évite le chevauchement)
    this.stop();

    const cleanText = (text || '').trim();
    if (!cleanText) return;

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.lang = 'fr-FR';
    utterance.rate = 1;     // Vitesse normale
    utterance.pitch = 1;    // Hauteur normale
    utterance.volume = 1;   // Volume max

    // Sélectionner une voix française si disponible
    const voices = window.speechSynthesis.getVoices();
    const frenchVoice = voices.find(v => v.lang === 'fr-FR')
                     || voices.find(v => v.lang.startsWith('fr'));
    if (frenchVoice) {
      utterance.voice = frenchVoice;
    }

    // Fin de lecture → reset état
    utterance.onend = () => {
      this.handleEnd();
    };

    // Erreur → reset état
    utterance.onerror = () => {
      this.handleEnd();
    };

    this.currentPostId = postId;
    this.currentUtterance = utterance;
    this.onEndCallback = onEnd ?? null;

    window.speechSynthesis.speak(utterance);
  }

  /**
   * Arrête immédiatement la lecture en cours (s'il y en a une).
   */
  stop(): void {
    if (!this.isSupported()) return;

    if (window.speechSynthesis.speaking || window.speechSynthesis.pending) {
      window.speechSynthesis.cancel();
    }
    this.handleEnd();
  }

  /**
   * Bascule play/stop pour un post donné :
   *  - Si ce post est en cours → arrête
   *  - Sinon → démarre la lecture
   */
  toggle(postId: number, text: string, onEnd?: () => void): void {
    if (this.isSpeaking(postId)) {
      this.stop();
    } else {
      this.speak(postId, text, onEnd);
    }
  }

  /** Reset l'état interne + déclenche le callback */
  private handleEnd(): void {
    const cb = this.onEndCallback;
    this.currentPostId = null;
    this.currentUtterance = null;
    this.onEndCallback = null;
    if (cb) cb();
  }
}
