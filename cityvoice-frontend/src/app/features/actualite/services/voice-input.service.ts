import { Injectable, NgZone } from '@angular/core';

/**
 * Service de dictée vocale (Speech-to-Text).
 * Utilise l'API native `SpeechRecognition` / `webkitSpeechRecognition`.
 * Aucune clé API, aucune dépendance externe.
 *
 * Fonctionne sur Chrome, Edge, Safari. Firefox : support limité.
 * Langue par défaut : fr-FR.
 */
@Injectable({ providedIn: 'root' })
export class VoiceInputService {

  private recognition: any = null;

  /** Identifiant du champ actuellement en cours d'écoute (ex: 'title' | 'content') */
  private currentFieldId: string | null = null;

  /** Callback appelé à chaque mise à jour du texte reconnu (interim + final) */
  private onResultCallback: ((text: string, isFinal: boolean) => void) | null = null;

  /** Callback appelé quand l'écoute s'arrête (naturellement ou via stop()) */
  private onEndCallback: (() => void) | null = null;

  /** Texte final cumulé depuis le démarrage de l'écoute */
  private finalTranscript = '';

  constructor(private zone: NgZone) {}

  /** Vérifie si l'API SpeechRecognition est supportée par le navigateur */
  isSupported(): boolean {
    if (typeof window === 'undefined') return false;
    return !!((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition);
  }

  /** Retourne true si le champ donné est en cours d'écoute */
  isListening(fieldId: string): boolean {
    return this.currentFieldId === fieldId;
  }

  /** Retourne l'ID du champ actuellement en écoute (null si rien) */
  getCurrentFieldId(): string | null {
    return this.currentFieldId;
  }

  /**
   * Démarre la dictée vocale pour un champ donné.
   * Si un autre champ est déjà en écoute, l'arrête d'abord.
   *
   * @param fieldId        Identifiant unique du champ ('title', 'content', etc.)
   * @param initialText    Texte déjà présent dans le champ (sera conservé et augmenté)
   * @param onResult       Callback (texteComplet, isFinal) à chaque résultat
   * @param onEnd          Callback quand l'écoute se termine
   */
  start(
    fieldId: string,
    initialText: string,
    onResult: (text: string, isFinal: boolean) => void,
    onEnd?: () => void
  ): void {
    if (!this.isSupported()) {
      console.warn('[VoiceInputService] SpeechRecognition non supporté par ce navigateur.');
      return;
    }

    // Annule une éventuelle écoute précédente
    this.stop();

    const SpeechRecognitionCtor =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    const recognition = new SpeechRecognitionCtor();

    recognition.lang = 'fr-FR';
    recognition.continuous = true;     // Continue après une pause de parole
    recognition.interimResults = true; // Envoie des résultats partiels (feedback visuel)
    recognition.maxAlternatives = 1;

    // Préfixer avec le texte existant + un espace séparateur
    const prefix = initialText && initialText.trim().length > 0
      ? initialText.trimEnd() + ' '
      : '';
    this.finalTranscript = prefix;

    recognition.onresult = (event: any) => {
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          this.finalTranscript += transcript + ' ';
        } else {
          interim += transcript;
        }
      }
      // Angular zone → garantit que la vue se met à jour
      this.zone.run(() => {
        const combined = (this.finalTranscript + interim).replace(/\s+/g, ' ').trimStart();
        if (this.onResultCallback) {
          const isFinal = interim === '';
          this.onResultCallback(combined, isFinal);
        }
      });
    };

    recognition.onerror = (event: any) => {
      console.warn('[VoiceInputService] Erreur:', event.error);
      this.zone.run(() => this.handleEnd());
    };

    recognition.onend = () => {
      this.zone.run(() => this.handleEnd());
    };

    this.recognition = recognition;
    this.currentFieldId = fieldId;
    this.onResultCallback = onResult;
    this.onEndCallback = onEnd ?? null;

    try {
      recognition.start();
    } catch (e) {
      console.warn('[VoiceInputService] Impossible de démarrer la reconnaissance:', e);
      this.handleEnd();
    }
  }

  /** Arrête l'écoute en cours (s'il y en a une) */
  stop(): void {
    if (this.recognition) {
      try { this.recognition.stop(); } catch { /* ignore */ }
    }
    // Reset immédiat au cas où onend ne se déclencherait pas
    this.handleEnd();
  }

  /**
   * Bascule start/stop pour un champ donné :
   *  - Si ce champ est déjà en écoute → arrête
   *  - Sinon → démarre
   */
  toggle(
    fieldId: string,
    initialText: string,
    onResult: (text: string, isFinal: boolean) => void,
    onEnd?: () => void
  ): void {
    if (this.isListening(fieldId)) {
      this.stop();
    } else {
      this.start(fieldId, initialText, onResult, onEnd);
    }
  }

  /** Reset l'état interne + déclenche le callback */
  private handleEnd(): void {
    const cb = this.onEndCallback;
    this.recognition = null;
    this.currentFieldId = null;
    this.onResultCallback = null;
    this.onEndCallback = null;
    this.finalTranscript = '';
    if (cb) cb();
  }
}
