import { Injectable } from '@angular/core';

/**
 * Résultat d'une vérification de modération.
 * - `blocked` : true si au moins une catégorie a été déclenchée.
 * - `categories` : liste des catégories détectées (politique, religion, etc.).
 * - `matchedKeywords` : termes exacts repérés (utile pour debug / logs).
 */
export interface ModerationResult {
  blocked: boolean;
  categories: string[];
  matchedKeywords: string[];
  /** Message prêt à être affiché à l'utilisateur (FR). */
  userMessage: string;
}

/**
 * ContentModerationService — vérifie côté client que le texte d'une
 * publication ne viole pas les règles de la plateforme CityVoice :
 * pas de contenu politique, pas de haine religieuse, pas d'appel à la
 * violence, pas de contenu adulte, pas d'apologie de la drogue.
 *
 * Ce service fait un PRÉ-CONTRÔLE : le backend
 * (ContentModerationService Java) reste l'autorité finale et bloquera
 * à son tour via un HTTP 422 si quelque chose passe à travers.
 *
 * L'objectif du contrôle front est surtout l'expérience utilisateur :
 * afficher un message clair AVANT l'appel réseau plutôt qu'après.
 *
 * Les listes sont alignées sur celles du backend pour que les deux
 * couches restent cohérentes.
 */
@Injectable({ providedIn: 'root' })
export class ContentModerationService {

  // ─── Politique (partis, dirigeants, vocabulaire) ─────────────────────────
  private readonly POLITICAL: string[] = [
    // Partis tunisiens
    'ennahdha', 'ennahda', 'nahdha', 'nahda',
    'nidaa tounes', 'nida tounes', 'nidaa',
    'qalb tounes', '9alb tounes',
    'pdl', 'parti destourien libre', 'destourien',
    'tayyar', 'courant democrate',
    'echaab', 'harakat echaab',
    'afek tounes',
    'ettakatol',
    'cpr', 'congres pour la republique',
    'ettahrir', 'hizb ettahrir',
    // Personnalités
    'kais saied', 'kais said', 'kaies saied',
    'ghannouchi', 'rached ghannouchi',
    'bourguiba', 'ben ali', 'zine el abidine',
    'marzouki', 'moncef marzouki',
    'caid essebsi', 'beji caid',
    'hamma hammami', 'abir moussi',
    // Vocabulaire politique
    'election presidentielle', 'election legislative',
    "coup d'etat", 'coup detat',
    'dictature', 'dictateur', 'regime',
    'revolution', 'contre-revolution',
    'manifestation politique', 'sit-in politique',
    'propagande',
    // Conflits & idéologies
    'communisme', 'communiste',
    'fascisme', 'fasciste',
    'nazisme', 'nazi',
    'zionisme', 'sioniste', 'sionism', 'zionist',
    'hamas', 'hezbollah',
    'daech', 'isis', 'al qaeda', 'al-qaeda',
    // Anglais
    'trump', 'biden', 'putin', 'zelensky', 'netanyahu',
    'political party', 'election fraud',
    // Dialecte translittéré
    'siyesa', 'siyasa', '7okouma', 'hokouma',
    'ra2is', 'rais el joumhouriya',
    'barlamen', 'barlament',
  ];

  // ─── Haine religieuse ────────────────────────────────────────────────────
  private readonly RELIGIOUS_HATE: string[] = [
    'mort aux juifs', 'mort aux musulmans', 'mort aux chretiens',
    'kill the jews', 'kill the muslims', 'kill the christians',
    'islamophobe', 'islamophobie',
    'antisemite', 'antisemitism',
    'kafir', 'kouffar', 'kouffars',
    'mourtad', 'murtad',
    'jihad', 'djihad',
    'takfir', 'takfiri',
    'apostat', 'apostasie',
  ];

  // ─── Violence / appels au meurtre ────────────────────────────────────────
  private readonly VIOLENCE: string[] = [
    'tuer', 'egorger', 'assassiner', 'massacrer',
    'kill', 'murder', 'slaughter', 'behead',
    'bomb', 'bombe', 'explosif', 'attentat',
    'terroriste', 'terrorist', 'terrorism',
    'menace de mort', 'death threat',
    'lynchage', 'lynching',
  ];

  // ─── Contenu adulte ──────────────────────────────────────────────────────
  private readonly ADULT: string[] = [
    'pornographie', 'pornographique', 'porno', 'porn',
    'xxx', 'xnxx', 'pornhub',
    'escort', 'prostituee',
    'nude', 'nudes', 'sextape',
  ];

  // ─── Drogue ──────────────────────────────────────────────────────────────
  private readonly DRUG: string[] = [
    'cocaine', 'heroine',
    'cannabis', 'marijuana', 'zatla', '9anba',
    'ecstasy', 'lsd', 'meth', 'crystal meth',
    'dealer de drogue', 'trafic de drogue',
  ];

  /** Patterns stricts par catégorie : word boundaries. */
  private readonly patternsByCategory: { label: string; strict: RegExp[]; fuzzy: RegExp[] }[];

  constructor() {
    this.patternsByCategory = [
      this.buildCategory('politique',          this.POLITICAL),
      this.buildCategory('haine religieuse',    this.RELIGIOUS_HATE),
      this.buildCategory('violence',            this.VIOLENCE),
      this.buildCategory('contenu adulte',      this.ADULT),
      this.buildCategory('drogue',              this.DRUG),
    ];
  }

  /** Construit les patterns (strict + flou) d'une catégorie. */
  private buildCategory(label: string, words: string[]) {
    const strict: RegExp[] = [];
    const fuzzy: RegExp[] = [];
    for (const raw of words) {
      const norm = this.normalizeWord(raw);
      if (!norm) continue;

      // Strict : le mot entier, entouré de non-lettres.
      const escaped = norm.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      strict.push(new RegExp(`(?<![\\p{L}])${escaped}(?![\\p{L}])`, 'iu'));

      // Fuzzy : répétition autorisée de chaque lettre (ex : "fuuuuck").
      const letters = norm.replace(/[^a-z]/g, '');
      if (letters.length >= 4) {
        const fuzzySrc = letters.split('').map(c => `${c}+`).join('');
        fuzzy.push(new RegExp(fuzzySrc, 'iu'));
      }
    }
    return { label, strict, fuzzy };
  }

  /** Minuscules + suppression des diacritiques d'un mot de référence. */
  private normalizeWord(word: string): string {
    return word.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
  }

  /** Normalise le texte utilisateur (lower + sans diacritiques + leet basique). */
  private normalize(text: string): string {
    const stripped = text.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
    return stripped
      .replace(/0/g, 'o')
      .replace(/1/g, 'i')
      .replace(/3/g, 'e')
      .replace(/4/g, 'a')
      .replace(/5/g, 's')
      .replace(/7/g, 't')
      .replace(/@/g, 'a')
      .replace(/\$/g, 's');
  }

  /**
   * Vérifie un ou plusieurs textes (titre, contenu…).
   * Renvoie un {@link ModerationResult} prêt à l'emploi.
   */
  check(...texts: (string | null | undefined)[]): ModerationResult {
    const joined = texts
      .filter((t): t is string => !!t && t.trim().length > 0)
      .join(' ');

    if (!joined.trim()) {
      return { blocked: false, categories: [], matchedKeywords: [], userMessage: '' };
    }

    const normalized = this.normalize(joined);
    const lettersOnly = normalized.replace(/[^a-z]/g, '');

    const categories: string[] = [];
    const matches: string[] = [];

    for (const cat of this.patternsByCategory) {
      let hit = false;
      for (const re of cat.strict) {
        const m = normalized.match(re);
        if (m) { hit = true; matches.push(m[0]); break; }
      }
      if (!hit) {
        for (const re of cat.fuzzy) {
          const m = lettersOnly.match(re);
          if (m) { hit = true; matches.push(m[0]); break; }
        }
      }
      if (hit) categories.push(cat.label);
    }

    const blocked = categories.length > 0;
    const userMessage = blocked
      ? `⛔ Publication refusée : contenu détecté comme ${categories.join(', ')}. `
        + 'CityVoice est une plateforme citoyenne et ne publie pas de contenu '
        + 'de cette nature. Merci de reformuler votre message.'
      : '';

    return { blocked, categories, matchedKeywords: matches, userMessage };
  }

  /** Raccourci boolean. */
  isBlocked(...texts: (string | null | undefined)[]): boolean {
    return this.check(...texts).blocked;
  }
}
