# Twilio Client VoIP -> CityVoice

## Scenario valide

Oui, le scenario est coherent si tu remplaces l'appel PSTN par **Twilio Client (VoIP)**.

Le point cle est le suivant :

- avec **Twilio Client**, l'utilisateur appelle depuis le navigateur ou l'app via `Twilio.Device`
- Twilio ne passe pas par un numero entrant classique
- Twilio envoie la requete au **Voice URL de la TwiML App**
- ce **Voice URL** doit pointer vers ton backend Spring Boot expose publiquement via **ngrok**

Configuration TwiML App attendue pour la demo :

- `TwiML App Name`: `Madina`
- `Voice`: `https://<ton-ngrok>/api/v1/voice/twiml/entry`
- `Messaging`: non configure

## Flux recommande

Pour eviter le probleme "localisation manquante", la demo doit utiliser un **IVR en 2 etapes** :

1. question 1 : "Decrivez le probleme"
2. question 2 : "Indiquez l'adresse, la rue ou un point de repere"

Le backend recoit ensuite deux enregistrements :

- description du probleme
- localisation orale

Puis le pipeline est :

1. Twilio Client obtient un **Access Token** depuis `GET /api/v1/voice/token`
2. le frontend lance `device.connect()`
3. Twilio appelle `POST /api/v1/voice/twiml/entry`
4. le backend renvoie du **TwiML** avec `<Say>` + `<Record>`
5. Twilio poste les callbacks d'enregistrement sur `POST /api/v1/voice/recording-status`
6. le backend telecharge les audios Twilio
7. le backend envoie l'audio a **Whisper**
8. la transcription + la localisation sont envoyees a `madina-ai`
9. `madina-ai` retourne un JSON structure
10. le backend mappe ce JSON vers `SignalementRequest`
11. le signalement est enregistre dans CityVoice

## Configuration backend

Variables d'environnement a renseigner dans `signalement-service` :

- `VOICE_PUBLIC_BASE_URL`
- `TWILIO_ACCOUNT_SID`
- `TWILIO_API_KEY_SID`
- `TWILIO_API_KEY_SECRET`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_TWIML_APP_SID`
- `VOICE_WHISPER_URL`
- `VOICE_WHISPER_API_KEY` si ton endpoint Whisper le demande

Variables utiles pour la demo :

- `VOICE_DEFAULT_IDENTITY=demo-prof`
- `VOICE_DEFAULT_LATITUDE=36.8065`
- `VOICE_DEFAULT_LONGITUDE=10.1815`
- `VOICE_DEFAULT_ADDRESS_LABEL=Localisation a confirmer`

## Endpoints ajoutes

Dans `signalement-service` :

- `GET /api/v1/voice/token`
- `POST /api/v1/voice/twiml/entry`
- `POST /api/v1/voice/twiml/description-complete`
- `POST /api/v1/voice/twiml/location-complete`
- `POST /api/v1/voice/recording-status`
- `GET /api/v1/voice/sessions?callSid=...`

Dans `madina-ai` :

- `POST /api/v1/voice/structure`

## Prompt backend -> LLM

Prompt recommande pour structurer la transcription :

```text
Tu es un assistant de tri pour un systeme de signalement urbain vocal.
Transforme la transcription suivante en JSON strict, sans markdown, sans commentaire, sans texte autour.

Contraintes:
- Reponds avec UN objet JSON valide uniquement.
- Champs obligatoires: type, priorite, description, localisation.
- type doit etre exactement l'une de ces valeurs: voirie, eclairage, dechets, securite, autres
- priorite doit etre exactement l'une de ces valeurs: HAUTE, MOYENNE, BASSE
- description doit etre concise, claire, en francais
- localisation doit etre une chaine si elle est mentionnee, sinon null
- Si le probleme est un trou, une route abimee, une fuite ou un caniveau bouche, choisis "voirie"
- Si la transcription de localisation dit qu'elle est inconnue, mets null

Transcription:
Il y a un trou dangereux dans la rue Bourguiba

Indice de localisation:
rue Bourguiba
```

## Exemple JSON attendu

```json
{
  "type": "voirie",
  "priorite": "HAUTE",
  "description": "Trou dangereux dans la rue Bourguiba pour velos et pietons",
  "localisation": "rue Bourguiba"
}
```

## Remarque importante

Le modele de donnees actuel de CityVoice demande `latitude` et `longitude`.

Pour la demo voix, le backend renseigne temporairement une position par defaut et conserve
la vraie adresse dans `adresse`.

Si tu veux une version production, l'etape suivante logique sera :

- geocodage de l'adresse orale
- ou validation humaine de l'adresse dans le dashboard
