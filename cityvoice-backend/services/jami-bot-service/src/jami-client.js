'use strict';

/**
 * Client Jami Daemon REST API
 *
 * Nécessite: jamid --rest --rest-port 8888
 *
 * Documentation API: https://git.jami.net/savoirfairelinux/jami-daemon/-/blob/master/src/rest/
 */

const axios = require('axios');
const WebSocket = require('ws');
const EventEmitter = require('events');

class JamiClient extends EventEmitter {

    constructor(daemonUrl) {
        super();
        this.daemonUrl = daemonUrl || 'http://localhost:8888';
        this.ws = null;
        this.accountId = null;
        this.connected = false;
    }

    /**
     * Connexion au daemon Jami via WebSocket
     */
    async connect(accountId) {
        this.accountId = accountId;

        // Vérifier que le compte existe
        const accounts = await this.getAccounts();
        if (!accounts.includes(accountId)) {
            throw new Error(`Compte Jami ${accountId} introuvable. Comptes disponibles: ${accounts.join(', ')}`);
        }

        // Connexion WebSocket pour les événements temps réel
        const wsUrl = this.daemonUrl.replace('http', 'ws') + '/';
        console.log(`[JAMI] Connexion WebSocket: ${wsUrl}`);

        this.ws = new WebSocket(wsUrl);

        this.ws.on('open', () => {
            this.connected = true;
            console.log('[JAMI] WebSocket connecté');

            // S'abonner aux événements du compte
            this.ws.send(JSON.stringify({
                type: 'subscribe',
                accountId: this.accountId
            }));

            this.emit('connected');
        });

        this.ws.on('message', (data) => {
            try {
                const event = JSON.parse(data.toString());
                this._handleEvent(event);
            } catch (e) {
                console.error('[JAMI] Erreur parsing événement:', e.message);
            }
        });

        this.ws.on('close', () => {
            this.connected = false;
            console.log('[JAMI] WebSocket déconnecté');
            this.emit('disconnected');

            // Reconnexion automatique après 5s
            setTimeout(() => this.connect(accountId), 5000);
        });

        this.ws.on('error', (err) => {
            console.error('[JAMI] Erreur WebSocket:', err.message);
        });
    }

    /**
     * Gestion des événements Jami
     */
    _handleEvent(event) {
        console.log('[JAMI] Événement reçu:', event.type, JSON.stringify(event).substring(0, 100));

        switch (event.type) {
            case 'messageReceived':
                // Message entrant (texte)
                this.emit('message', {
                    accountId: event.accountId,
                    conversationId: event.conversationId,
                    from: event.author || event.from,
                    message: event.message || event.body,
                    timestamp: event.timestamp || Date.now()
                });
                break;

            case 'incomingCall':
                // Appel entrant
                this.emit('incomingCall', {
                    accountId: event.accountId,
                    callId: event.callId,
                    from: event.from,
                    displayName: event.displayName
                });
                break;

            case 'callStateChanged':
                this.emit('callStateChanged', event);
                break;

            case 'conversationReady':
                this.emit('conversationReady', {
                    accountId: event.accountId,
                    conversationId: event.conversationId
                });
                break;

            default:
                // Ignorer les autres événements
                break;
        }
    }

    // ─── REST API ─────────────────────────────────────────────────────────────

    /**
     * Liste les comptes Jami enregistrés dans le daemon
     */
    async getAccounts() {
        const response = await axios.get(`${this.daemonUrl}/accounts`);
        return response.data || [];
    }

    /**
     * Crée un compte Jami (Ring/IPFS)
     */
    async createAccount(alias) {
        const response = await axios.post(`${this.daemonUrl}/accounts`, {
            type: 'RING',
            alias: alias || 'CityVoiceBot',
            upnpEnabled: true,
            autoAnswer: false
        });
        return response.data;
    }

    /**
     * Récupère les infos d'un compte
     */
    async getAccountDetails(accountId) {
        const response = await axios.get(`${this.daemonUrl}/accounts/${accountId}`);
        return response.data;
    }

    /**
     * Envoie un message texte à un contact
     */
    async sendMessage(accountId, contactId, message) {
        try {
            // Chercher d'abord une conversation existante
            const convId = await this.getOrCreateConversation(accountId, contactId);

            const response = await axios.post(
                `${this.daemonUrl}/accounts/${accountId}/conversations/${convId}/messages`,
                { body: message }
            );

            console.log(`[JAMI] Message envoyé à ${contactId}: "${message.substring(0, 50)}"`);
            return response.data;
        } catch (err) {
            console.error('[JAMI] Erreur envoi message:', err.message);
            throw err;
        }
    }

    /**
     * Récupère ou crée une conversation avec un contact
     */
    async getOrCreateConversation(accountId, contactUri) {
        try {
            // Lister les conversations existantes
            const response = await axios.get(`${this.daemonUrl}/accounts/${accountId}/conversations`);
            const conversations = response.data || {};

            // Chercher une conversation avec ce contact
            for (const [convId, conv] of Object.entries(conversations)) {
                if (conv.members && conv.members.includes(contactUri)) {
                    return convId;
                }
            }

            // Créer une nouvelle conversation
            const createResp = await axios.post(
                `${this.daemonUrl}/accounts/${accountId}/conversations`,
                { members: [contactUri] }
            );
            return createResp.data.id || createResp.data;

        } catch (err) {
            console.error('[JAMI] Erreur gestion conversation:', err.message);
            throw err;
        }
    }

    /**
     * Accepte un appel entrant
     */
    async acceptCall(accountId, callId) {
        await axios.put(`${this.daemonUrl}/accounts/${accountId}/calls/${callId}`, {
            state: 'CURRENT'
        });
    }

    /**
     * Raccroche un appel
     */
    async hangupCall(accountId, callId) {
        await axios.delete(`${this.daemonUrl}/accounts/${accountId}/calls/${callId}`);
    }

    /**
     * Récupère l'URI Jami du compte (pour l'afficher dans l'UI)
     */
    async getAccountUri(accountId) {
        const details = await this.getAccountDetails(accountId);
        return details['Account.username'] || details.username;
    }
}

module.exports = JamiClient;
