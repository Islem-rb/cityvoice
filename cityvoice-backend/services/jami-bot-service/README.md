# CityVoice Jami Bot Service

Bot Jami pour créer des signalements municipaux via messages texte.

## Comment ça marche

```
Utilisateur (app Jami mobile)
        │
        │ écrit "signaler"
        ▼
Bot CityVoice (ce service)
        │
        │ "Quel est le problème ?"
        │ ← réponse utilisateur
        │ "Quelle est l'adresse ?"
        │ ← réponse utilisateur
        │
        │ POST /api/v1/hybrid-voice/jami-message
        ▼
Backend Spring Boot
        │
        │ Analyse IA (Ollama/fallback)
        │ Création signalement
        ▼
Confirmation envoyée à l'utilisateur ✅
```

---

## Installation

### 1. Prérequis

- Node.js 18+
- Jami installé sur votre PC : https://jami.net/download/
- (Optionnel) Whisper + Ollama pour l'IA

### 2. Activer l'API REST du daemon Jami

**Windows** : Jami installe `jamid.exe` en arrière-plan.
Pour activer le REST, relancer avec les options :

```cmd
# Trouver l'emplacement de jamid
where jamid

# Lancer le daemon avec REST
jamid.exe --rest --rest-port 8888
```

Ou via PowerShell (tuer l'instance existante puis relancer) :
```powershell
Stop-Process -Name "jamid" -Force
Start-Process "C:\Program Files\Jami\jamid.exe" -ArgumentList "--rest --rest-port 8888"
```

**Linux** :
```bash
jamid --rest --rest-port 8888 &
```

### 3. Créer le compte bot

```bash
# Vérifier que l'API répond
curl http://localhost:8888/accounts

# Créer un compte Jami pour le bot
curl -X POST http://localhost:8888/accounts \
  -H "Content-Type: application/json" \
  -d '{"type":"RING","alias":"CityVoiceBot","upnpEnabled":true}'
```

La commande retourne un **Account ID** (chaîne hexadécimale). Copiez-le.

### 4. Récupérer l'URI Jami du bot

```bash
# Remplacez YOUR_ACCOUNT_ID par l'ID obtenu
curl http://localhost:8888/accounts/YOUR_ACCOUNT_ID
```

Notez la valeur de `Account.username` — c'est l'adresse Jami du bot (format `ring:xxxxxxxx`).

### 5. Configurer le bot

```bash
cd services/jami-bot-service

# Copier la config exemple
cp .env.example .env

# Éditer le fichier .env
notepad .env   # Windows
nano .env      # Linux
```

```env
JAMI_DAEMON_URL=http://localhost:8888
JAMI_BOT_ACCOUNT_ID=YOUR_ACCOUNT_ID_ICI
CITYVOICE_API_URL=http://localhost:8080
BOT_PORT=3001
```

### 6. Installer et démarrer

```bash
npm install
npm start
```

Vous devriez voir :
```
[BOT] ✅ Connecté ! URI Jami du bot: ring:xxxxxxxxxxxxxxxx
[BOT] → Ajoutez ce contact dans l'app Jami pour tester
```

---

## Tester depuis l'app Jami mobile

1. Installez **Jami** sur votre téléphone : https://jami.net/download/
2. Créez un compte
3. Ajoutez le contact bot (adresse `ring:xxxx` affichée au démarrage)
4. Envoyez le message : **`signaler`**
5. Suivez les instructions du bot

---

## Messages du bot

| Déclencheur | Action |
|-------------|--------|
| `signaler` ou `signalement` | Démarre une nouvelle session |
| `annuler` | Annule la session en cours |
| Tout autre message | Répond à la question en cours |

---

## Endpoints API (monitoring)

| Endpoint | Description |
|----------|-------------|
| `GET http://localhost:3001/health` | État du bot |
| `GET http://localhost:3001/stats` | Statistiques des sessions |

---

## Architecture des fichiers

```
jami-bot-service/
├── src/
│   ├── index.js              ← Point d'entrée + serveur Express
│   ├── jami-client.js        ← Client daemon Jami (REST + WebSocket)
│   └── conversation-manager.js ← Logique du bot (états + messages)
├── .env.example
├── package.json
└── README.md
```
