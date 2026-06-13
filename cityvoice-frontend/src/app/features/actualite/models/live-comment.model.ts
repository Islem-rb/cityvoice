/**
 * Commentaire diffusé en direct pendant un live (équivalent du "chat du stream").
 *
 * Les messages transitent par :
 *   - POST  /api/live/{roomName}/comments        → créer + broadcast
 *   - GET   /api/live/{roomName}/comments        → historique
 *   - STOMP /topic/live.{roomName}.comments      → flux temps réel
 *
 * `id` et `date` sont remplis par le backend.
 */
export interface LiveComment {
  id?: number;
  roomName?: string;
  auteurId?: string;
  auteurNom?: string;
  auteurPhoto?: string;
  contenu: string;
  date?: string;
}
