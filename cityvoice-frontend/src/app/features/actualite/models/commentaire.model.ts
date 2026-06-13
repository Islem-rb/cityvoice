export interface Commentaire {
  id: number;
  contenu: string;
  date: string;
  auteurId: string;
  auteurNom?: string;
  auteurPhoto?: string;
  postId: number;
  parentId?: number;       // null = commentaire racine, sinon = réponse
  replies?: Commentaire[]; // rempli côté frontend en groupant par parentId
}

export interface CommentaireRequest {
  contenu: string;
  auteurId: string;
  auteurNom?: string;
  auteurPhoto?: string;
  parentId?: number;       // optionnel : uniquement pour les réponses
}
