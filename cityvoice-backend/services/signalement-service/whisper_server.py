from flask import Flask, request, jsonify
import whisper, tempfile, os, uuid

app = Flask(__name__)

# "medium" : bon compromis qualite/vitesse sur CPU
# Passer a "large-v3" pour meilleure precision (plus lent : ~8-15s par segment)
MODEL_NAME = os.getenv("WHISPER_MODEL", "medium")
print(f"[Whisper] Chargement du modele '{MODEL_NAME}'...")
model = whisper.load_model(MODEL_NAME)
print(f"[Whisper] Modele '{MODEL_NAME}' pret ! Serveur sur http://localhost:9000")

# ─────────────────────────────────────────────────────────────────────────
# Prompt initial ENRICHI — guide Whisper vers le vocabulaire infrastructure
# et la toponymie tunisienne. Plus c'est exhaustif, plus Whisper "s'accroche"
# aux bons mots plutot que d'inventer.
# ─────────────────────────────────────────────────────────────────────────
INITIAL_PROMPT = (
    # Contexte
    "Signalement municipal CityVoice à Tunis, Tunisie. "
    "Le citoyen décrit un problème urbain à l'oral puis indique sa localisation. "
    # Types de problèmes (toutes variantes naturelles — répéter pour renforcer le biais)
    "Problèmes rencontrés : un trou dans la rue, un trou dans la chaussée, "
    "il y a un trou, nid-de-poule, trou profond, trou sur la route, "
    "lampadaire cassé, lampadaire en panne, éclairage public, poteau électrique, "
    "fuite d'eau, canalisation, égout, eau qui coule, "
    "déchets non collectés, ordures, poubelle qui déborde, sacs poubelle, "
    "poteau endommagé, poteau cassé, poteau tombé, "
    "signalisation manquante, panneau de signalisation, stop, feu tricolore, "
    "caniveau bouché, évacuation, flaque d'eau, eaux usées, "
    "espace vert dégradé, jardin public, arbre tombé, branches, "
    "graffiti, vandalisme, dégâts, urgent, dangereux, bloqué, cassé, endommagé, réparé. "
    # Grandes villes de Tunisie (24 gouvernorats)
    "Villes : Tunis, Ariana, Ben Arous, Manouba, Nabeul, Zaghouan, Bizerte, "
    "Béja, Jendouba, Kef, Siliana, Kairouan, Kasserine, Sidi Bouzid, Sousse, "
    "Monastir, Mahdia, Sfax, Gafsa, Tozeur, Kebili, Gabès, Medenine, Tataouine. "
    # Quartiers et zones populaires du Grand Tunis
    "Quartiers : El Menzah, Ennasr, Hay Ennasr, La Marsa, Gammarth, Carthage, "
    "Le Bardo, Lafayette, Passage, Berges du Lac, Lac 1, Lac 2, Belvédère, "
    "Mutuelleville, Le Kram, Mornag, Ezzahra, Hammam Lif, La Goulette, El Manar, "
    "Cité Olympique, El Menzah 6, El Menzah 9, Montplaisir, Bab Souika, Bab El Khadra, "
    "Medina, Centre-ville, Cité Ettadhamen, Raoued, Soukra, Charguia, Aouina, "
    "El Omrane, Ibn Khaldoun, Khéireddine, Manouba, Cité Ghazala. "
    # Voirie — structure d'adresse
    "Noms de voies : avenue Habib Bourguiba, rue Ibn Khaldoun, boulevard du 7 novembre, "
    "place de la Kasbah, route de la Marsa, rue de Carthage, impasse, cité, "
    "résidence, lotissement, près de, à côté de, en face de, devant, derrière. "
    # Vocabulaire de proximité utile pour la localisation
    "Repères : à côté de la mosquée, près de l'école, devant le lycée, "
    "en face de l'hôpital, à côté de la pharmacie, près de la boulangerie, "
    "à côté du café, devant le supermarché, près de la station, "
    "arrêt de bus, gare, taxi, parking, rond-point, carrefour, "
    "mosquée, école, lycée, collège, hôpital, pharmacie, marché."
)

# Phrases que Whisper hallucine souvent sur du silence ou du bruit
HALLUCINATION_PHRASES = [
    "merci d'avoir regardé",
    "sous-titres réalisés",
    "sous-titres par",
    "abonnez-vous",
    "n'oubliez pas",
    "à bientôt",
    "merci de votre attention",
    "musique",
    "applaudissements",
    "rires",
    "[musique]",
    "[applaudissements]",
    "you",
    "thank you",
    "thanks for watching",
    "like and subscribe",
    "merci pour votre visionnage",
]

# Mots courts VALIDES qui doivent passer malgre la regle "len < 4"
# (sinon "eau", "trou", "feu" sont rejetes a tort)
SHORT_VALID_WORDS = {
    "eau", "feu", "trou", "fuite", "route", "rue", "bus", "taxi",
    "bruit", "fumée", "arbre", "oui", "non", "ok", "lac", "bab",
    "hay", "cité"
}


def is_hallucination(text: str) -> bool:
    """
    Détecte les hallucinations courantes de Whisper sur silence/bruit.
    Tolere les mots courts du vocabulaire urbain (eau, trou, fuite, etc.).
    """
    if not text:
        return True
    lower = text.lower().strip()

    # Trop court pour être une vraie transcription — sauf si c'est un mot valide
    if len(lower) < 4:
        # Laisser passer les mots courts reconnus
        words = lower.split()
        if any(w in SHORT_VALID_WORDS for w in words):
            return False
        return True

    # Phrases typiques d'hallucination
    for phrase in HALLUCINATION_PHRASES:
        if phrase in lower:
            return True
    return False


def _run_whisper(path: str, no_speech_threshold: float, logprob_threshold: float):
    """Helper : appel Whisper avec parametres configurables pour double-passe."""
    return model.transcribe(
        path,
        language="fr",                                   # Forcer le francais
        temperature=0,                                   # Desactiver sampling
        initial_prompt=INITIAL_PROMPT,                   # Guide vocabulaire
        no_speech_threshold=no_speech_threshold,         # Seuil silence (passe 1: 0.55 ; passe 2: 0.25)
        condition_on_previous_text=False,                # Pas de continuation hallucinee
        compression_ratio_threshold=2.4,                 # Rejeter repetitions
        logprob_threshold=logprob_threshold,             # Confiance minimale
        word_timestamps=False,
        fp16=False,                                      # CPU uniquement
    )


@app.route("/v1/audio/transcriptions", methods=["POST"])
def transcribe():
    f = request.files.get("file")
    if not f:
        return jsonify({"error": "no file"}), 400

    # Chemin manuel — evite les verrous Windows de NamedTemporaryFile
    tmp_path = os.path.join(tempfile.gettempdir(), f"whisper_{uuid.uuid4().hex}.webm")
    text = ""

    try:
        f.save(tmp_path)

        # ── Passe 1 : seuils standards ────────────────────────────────
        result = _run_whisper(tmp_path, no_speech_threshold=0.55, logprob_threshold=-1.0)
        raw_text = result["text"].strip()

        # ── Passe 2 : si passe 1 est vide ou trop courte, retenter en
        #    mode plus permissif (utile pour phrases courtes type "fuite d'eau")
        if is_hallucination(raw_text) or len(raw_text) < 6:
            file_size = os.path.getsize(tmp_path) if os.path.exists(tmp_path) else 0
            # Only retry if the audio file has meaningful content (> 5KB ≈ 0.3s at Opus 128kbps)
            if file_size > 5000:
                print(f"[Whisper] Passe 1 faible ('{raw_text}', {file_size}B) — retry permissif...")
                result2 = _run_whisper(tmp_path, no_speech_threshold=0.25, logprob_threshold=-1.5)
                raw_text2 = result2["text"].strip()
                if raw_text2 and not is_hallucination(raw_text2):
                    raw_text = raw_text2
                    print(f"[Whisper] Passe 2 OK: {raw_text}")

        if is_hallucination(raw_text):
            print(f"[Whisper] Hallucination detectee — ignoree: '{raw_text}'")
            text = ""
        else:
            text = raw_text
            print(f"[Whisper] OK: {text}")

    except Exception as e:
        print(f"[Whisper] Erreur: {e}")

    finally:
        try:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
        except Exception:
            pass

    return jsonify({"text": text})


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "model": MODEL_NAME})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=9000)
