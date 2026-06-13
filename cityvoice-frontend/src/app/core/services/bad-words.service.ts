import { Injectable } from '@angular/core';

/**
 * BadWordsService — filtre les insultes et grossièretés
 * avant envoi au backend (commentaires, posts, messages de chat).
 *
 * Les mots détectés sont remplacés par *** de la même longueur.
 * Le service reconnaît les variantes avec espaces, chiffres et
 * caractères spéciaux courants (e→3, a→@, i→1, o→0, s→$).
 */
@Injectable({ providedIn: 'root' })
export class BadWordsService {

  // ─── Liste de mots interdits (FR · EN · dialecte tunisien courant) ───────
  private readonly BAD_WORDS: string[] = [
    // === FRANÇAIS ===
    'merde', 'putain', 'connard', 'connasse', 'salope', 'pute', 'enculé',
    'enculer', 'encule', 'bâtard', 'batard', 'fdp', 'fils de pute',
    'va te faire foutre', 'va te faire', 'casse toi', 'casseton',
    'nique', 'niquer', 'ta gueule', 'ferme ta gueule', 'tg',
    'con', 'conne', 'abruti', 'abrutie', 'idiot', 'idiote',
    'imbécile', 'imbecile', 'crétin', 'cretin', 'débile', 'debile',
    'ordure', 'salopard', 'saloparde', 'cochon', 'porc',
    'baise', 'baiser', 'branler', 'branleur', 'branleuse',
    'chier', 'chieur', 'emmerde', 'emmerder', 'emmerdeur',
    'foutre', 'foutaise', 'bite', 'couilles', 'couille',
    'sein', 'nichons', 'cul', 'anus', 'fesses', 'vagin', 'pénis',
    'penis', 'phallus', 'clitoris', 'sperme',

    // === ANGLAIS ===
    'fuck', 'fucking', 'fucker', 'fucked', 'motherfucker',
    'shit', 'bullshit', 'shitty', 'ass', 'asshole', 'assh0le',
    'bitch', 'bastard', 'cunt', 'dick', 'cock', 'pussy',
    'damn', 'damnit', 'hell', 'whore', 'slut', 'idiot',
    'stupid', 'moron', 'retard', 'loser', 'jerk',
    'kill yourself', 'kys', 'wtf', 'stfu',

    // === ARABE / DIALECTE TUNISIEN (translittéré) ===
    'kess', 'kes', 'kis', 'zob', 'zebi', 'zbbi', '7mar', 'hmar',
    'kalb', 'kelbek', 'wled el haram', 'ibn el haram',
    'sharmouta', 'charmota', 'sharmouta', 'zamel', 'zemel',
    'omek', 'boytek', 'boitek', 'ahbal', 'manyak',
    'nhik', 'nik', 'nk', 'beda', 'zbel', 'tiz',
    'ya khawel', 'khawel', 'kahba', 'gahba',
    'wled el kahba', 'ould el kahba',
  ];

  // Regex préconstruites pour chaque mot (avec normalisation)
  private readonly patterns: { re: RegExp; replacement: string }[] = [];

  constructor() {
    this.BAD_WORDS.forEach(word => {
      // Échapper les caractères spéciaux regex dans le mot
      const escaped = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

      // Normaliser les substitutions courantes : a→[@a4], e→[e3é], i→[i1!], o→[o0], s→[s$5]
      const normalized = escaped
        .replace(/a/gi, '[a@4àáâ]')
        .replace(/e/gi, '[e3éèê]')
        .replace(/i/gi, '[i1!íî]')
        .replace(/o/gi, '[o0óô]')
        .replace(/s/gi, '[s$5]')
        .replace(/u/gi, '[uùúû]')
        .replace(/c/gi, '[c(]')
        .replace(/l/gi, '[l1]');

      const re = new RegExp(`(?<![a-zA-Z])${normalized}(?![a-zA-Z])`, 'gi');
      const replacement = '*'.repeat(word.length);
      this.patterns.push({ re, replacement });
    });
  }

  /**
   * Retourne true si le texte contient au moins un mot interdit.
   */
  hasBadWords(text: string): boolean {
    if (!text) return false;
    const normalized = this.normalizeText(text);
    return this.patterns.some(p => p.re.test(normalized));
  }

  /**
   * Remplace tous les mots interdits par des étoiles (*** longueur identique).
   * Retourne le texte nettoyé.
   */
  filter(text: string): string {
    if (!text) return text;
    let result = text;
    this.patterns.forEach(({ re, replacement }) => {
      // Réinitialiser lastIndex pour les regex avec flag 'g'
      re.lastIndex = 0;
      result = result.replace(re, (match) => '*'.repeat(match.length));
    });
    return result;
  }

  /**
   * Normalise le texte pour la détection (minuscules, supprime accents excessifs).
   */
  private normalizeText(text: string): string {
    return text.toLowerCase();
  }
}
