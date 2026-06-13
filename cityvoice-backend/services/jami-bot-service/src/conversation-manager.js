'use strict';

/**
 * Gestionnaire des conversations Jami
 *
 * Gère le flux de conversation pour la création de signalements:
 * 1. Salutation + demande description du problème
 * 2. Récupération description
 * 3. Demande localisation
 * 4. Récupération localisation
 * 5. Envoi vers backend CityVoice + confirmation
 */

const axios = require('axios');

const CONVERSATION_TIMEOUT_MS = (parseInt(process.env.CONVERSATION_TIMEOUT) || 120) * 1000;

// États de la conversation
const STATE = {
    IDLE: 'idle',
    WAITING_DESCRIPTION: 'waiting_description',
    WAITING_LOCATION: 'waiting_location',
    PROCESSING: 'processing',
    DONE: 'done'
};

// Messages du bot (français)
const MESSAGES = {
    WELCOME: `Bonjour ! Je suis l'assistant vocal CityVoice. 👋

Pour créer un signalement, répondez à mes 2 questions.

❓ *Question 1/2* — Quel est le problème que vous souhaitez signaler ?
(Ex: "lampadaire cassé rue principale", "poubelle non collectée", "nid de poule dangereux")`,

    ASK_LOCATION: `Merci pour votre signalement ! 📍

❓ *Question 2/2* — Indiquez l'adresse ou un point de repère:
(Ex: "angle rue de la Paix et avenue centrale, Tunis", "en face de l'école ibn khaldoun")`,

    PROCESSING: `⏳ Je traite votre signalement... Veuillez patienter quelques secondes.`,

    SUCCESS: (id, description, adresse) => `✅ *Signalement créé avec succès !*

📋 *Numéro:* #${id}
📝 *Description:* ${description}
📍 *Adresse:* ${adresse}

Votre signalement a été transmis aux services municipaux. Vous serez informé de son traitement. Merci !`,

    ERROR: `❌ Une erreur s'est produite lors de la création du signalement.
Veuillez réessayer en écrivant "signaler".`,

    TIMEOUT: `⏱️ La session a expiré. Écrivez "signaler" pour recommencer.`,

    ALREADY_ACTIVE: `🔄 Une session est déjà en cours. Répondez à la question ou écrivez "annuler" pour recommencer.`,

    CANCELLED: `Session annulée. Écrivez "signaler" pour recommencer.`,

    UNKNOWN: `Je ne comprends pas. Écrivez "signaler" pour créer un signalement municipal. 🏛️`
};

class ConversationManager {

    constructor(jamiClient, cityvoiceApiUrl) {
        this.jami = jamiClient;
        this.apiUrl = cityvoiceApiUrl || 'http://localhost:8080';

        // Map: contactUri → session
        this.sessions = new Map();
    }

    /**
     * Point d'entrée principal: traite un message reçu
     */
    async handleMessage(accountId, contactUri, text) {
        const normalized = (text || '').toLowerCase().trim();
        const session = this.sessions.get(contactUri);

        console.log(`[BOT] Message de ${contactUri}: "${text}" | état: ${session?.state || 'none'}`);

        // Commande d'annulation (prioritaire)
        if (normalized === 'annuler' || normalized === 'cancel' || normalized === 'stop') {
            this._clearSession(contactUri);
            await this.jami.sendMessage(accountId, contactUri, MESSAGES.CANCELLED);
            return;
        }

        // Démarrage d'une nouvelle session
        if (!session || session.state === STATE.DONE || normalized === 'signaler' || normalized === 'signalement') {
            await this._startSession(accountId, contactUri);
            return;
        }

        // Conversation active
        switch (session.state) {
            case STATE.WAITING_DESCRIPTION:
                await this._receiveDescription(accountId, contactUri, text, session);
                break;

            case STATE.WAITING_LOCATION:
                await this._receiveLocation(accountId, contactUri, text, session);
                break;

            case STATE.PROCESSING:
                await this.jami.sendMessage(accountId, contactUri, MESSAGES.PROCESSING);
                break;

            default:
                await this.jami.sendMessage(accountId, contactUri, MESSAGES.UNKNOWN);
        }
    }

    /**
     * Démarre une nouvelle session de signalement
     */
    async _startSession(accountId, contactUri) {
        const sessionId = `jami-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

        const session = {
            state: STATE.WAITING_DESCRIPTION,
            sessionId,
            accountId,
            contactUri,
            description: null,
            location: null,
            startedAt: Date.now()
        };

        this.sessions.set(contactUri, session);

        // Timeout automatique
        session.timeoutHandle = setTimeout(() => {
            const currentSession = this.sessions.get(contactUri);
            if (currentSession?.sessionId === sessionId && currentSession.state !== STATE.DONE) {
                this._clearSession(contactUri);
                this.jami.sendMessage(accountId, contactUri, MESSAGES.TIMEOUT)
                    .catch(e => console.error('[BOT] Erreur envoi timeout:', e.message));
            }
        }, CONVERSATION_TIMEOUT_MS);

        await this.jami.sendMessage(accountId, contactUri, MESSAGES.WELCOME);
    }

    /**
     * Reçoit la description du problème
     */
    async _receiveDescription(accountId, contactUri, text, session) {
        if (!text || text.trim().length < 5) {
            await this.jami.sendMessage(accountId, contactUri,
                '⚠️ Veuillez décrire le problème plus précisément (minimum 5 caractères).');
            return;
        }

        session.description = text.trim();
        session.state = STATE.WAITING_LOCATION;

        await this.jami.sendMessage(accountId, contactUri, MESSAGES.ASK_LOCATION);
    }

    /**
     * Reçoit la localisation
     */
    async _receiveLocation(accountId, contactUri, text, session) {
        if (!text || text.trim().length < 5) {
            await this.jami.sendMessage(accountId, contactUri,
                '⚠️ Veuillez indiquer une adresse ou un point de repère (minimum 5 caractères).');
            return;
        }

        session.location = text.trim();
        session.state = STATE.PROCESSING;

        await this.jami.sendMessage(accountId, contactUri, MESSAGES.PROCESSING);

        // Créer le signalement via l'API CityVoice
        await this._createSignalement(accountId, contactUri, session);
    }

    /**
     * Envoie le signalement au backend CityVoice
     */
    async _createSignalement(accountId, contactUri, session) {
        try {
            console.log(`[BOT] Création signalement: desc="${session.description}", loc="${session.location}"`);

            // Option A: Utiliser l'endpoint Jami dédié
            const response = await axios.post(
                `${this.apiUrl}/api/v1/hybrid-voice/jami-message`,
                {
                    sessionId: session.sessionId,
                    contactUri: contactUri,
                    description: session.description,
                    location: session.location
                },
                { timeout: 30000 }
            );

            const data = response.data;
            session.state = STATE.DONE;

            // Nettoyer le timer
            if (session.timeoutHandle) {
                clearTimeout(session.timeoutHandle);
            }

            // Envoyer confirmation
            const msg = MESSAGES.SUCCESS(
                data.signalementId || '???',
                data.description || session.description,
                data.adresse || session.location
            );

            await this.jami.sendMessage(accountId, contactUri, msg);

            console.log(`[BOT] Signalement #${data.signalementId} créé pour ${contactUri}`);

        } catch (error) {
            console.error('[BOT] Erreur création signalement:', error.message);
            session.state = STATE.DONE;

            await this.jami.sendMessage(accountId, contactUri, MESSAGES.ERROR);
        }

        // Nettoyer la session après 30s
        setTimeout(() => this._clearSession(contactUri), 30000);
    }

    /**
     * Nettoie une session
     */
    _clearSession(contactUri) {
        const session = this.sessions.get(contactUri);
        if (session?.timeoutHandle) {
            clearTimeout(session.timeoutHandle);
        }
        this.sessions.delete(contactUri);
    }

    /**
     * Statistiques (pour monitoring)
     */
    getStats() {
        const sessions = [...this.sessions.values()];
        return {
            activeSessions: sessions.filter(s => s.state !== STATE.DONE).length,
            totalSessions: sessions.length,
            states: sessions.reduce((acc, s) => {
                acc[s.state] = (acc[s.state] || 0) + 1;
                return acc;
            }, {})
        };
    }
}

module.exports = { ConversationManager, MESSAGES };
