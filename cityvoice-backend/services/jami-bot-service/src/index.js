'use strict';

require('dotenv').config();

const express = require('express');
const JamiClient = require('./jami-client');
const { ConversationManager } = require('./conversation-manager');

const JAMI_DAEMON_URL    = process.env.JAMI_DAEMON_URL    || 'http://localhost:8888';
const JAMI_BOT_ACCOUNT_ID = process.env.JAMI_BOT_ACCOUNT_ID;
const CITYVOICE_API_URL  = process.env.CITYVOICE_API_URL  || 'http://localhost:8080';
const BOT_PORT           = parseInt(process.env.BOT_PORT) || 3001;

// ─── Initialisation ──────────────────────────────────────────────────────────

const app = express();
app.use(express.json());

const jamiClient = new JamiClient(JAMI_DAEMON_URL);
const conversationManager = new ConversationManager(jamiClient, CITYVOICE_API_URL);

// ─── Écoute des messages Jami ────────────────────────────────────────────────

jamiClient.on('message', async ({ accountId, from, message }) => {
    if (!message || message.trim() === '') return;

    try {
        await conversationManager.handleMessage(accountId, from, message);
    } catch (err) {
        console.error('[BOT] Erreur traitement message:', err.message);
    }
});

jamiClient.on('incomingCall', async ({ accountId, callId, from, displayName }) => {
    console.log(`[BOT] Appel entrant de ${displayName || from} — appels vocaux pas encore pris en charge`);

    // Pour les appels vocaux, envoyer un message texte à la place
    try {
        await jamiClient.sendMessage(accountId, from,
            `📞 Je ne peux pas répondre aux appels vocaux pour l'instant.\n\nÉcrivez *"signaler"* pour créer un signalement via messages.`
        );
    } catch (e) {
        console.error('[BOT] Erreur envoi message appel entrant:', e.message);
    }
});

jamiClient.on('connected', () => {
    console.log('[BOT] Bot Jami prêt à recevoir des messages');
    console.log(`[BOT] Envoyez "signaler" au compte bot pour démarrer`);
});

jamiClient.on('disconnected', () => {
    console.warn('[BOT] Déconnecté du daemon Jami — tentative de reconnexion...');
});

// ─── API Express (monitoring & webhooks internes) ────────────────────────────

app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        jamiConnected: jamiClient.connected,
        accountId: JAMI_BOT_ACCOUNT_ID,
        ...conversationManager.getStats()
    });
});

app.get('/stats', (req, res) => {
    res.json(conversationManager.getStats());
});

// ─── Démarrage ───────────────────────────────────────────────────────────────

async function start() {
    console.log('═══════════════════════════════════════════════');
    console.log('      CityVoice Jami Bot Service               ');
    console.log('═══════════════════════════════════════════════');
    console.log(`Daemon Jami    : ${JAMI_DAEMON_URL}`);
    console.log(`CityVoice API  : ${CITYVOICE_API_URL}`);
    console.log(`Bot Account ID : ${JAMI_BOT_ACCOUNT_ID || '(non configuré — voir README)'}`);
    console.log('───────────────────────────────────────────────');

    // Démarrer le serveur HTTP
    app.listen(BOT_PORT, () => {
        console.log(`[BOT] Serveur monitoring : http://localhost:${BOT_PORT}/health`);
    });

    // Se connecter au daemon Jami
    if (!JAMI_BOT_ACCOUNT_ID) {
        console.warn('[BOT] ⚠️  JAMI_BOT_ACCOUNT_ID non configuré');
        console.warn('[BOT] → Suivez le README.md pour créer un compte bot');
        console.warn('[BOT] → Le bot fonctionne mais ne peut pas se connecter');
        return;
    }

    try {
        await jamiClient.connect(JAMI_BOT_ACCOUNT_ID);
        const uri = await jamiClient.getAccountUri(JAMI_BOT_ACCOUNT_ID);
        console.log(`[BOT] ✅ Connecté ! URI Jami du bot: ${uri}`);
        console.log(`[BOT] → Ajoutez ce contact dans l'app Jami pour tester`);
    } catch (err) {
        console.error(`[BOT] ❌ Impossible de se connecter au daemon: ${err.message}`);
        console.error('[BOT] → Assurez-vous que le daemon Jami tourne avec --rest');
        console.error('[BOT] → Commande: jamid --rest --rest-port 8888');
    }
}

start().catch(console.error);
