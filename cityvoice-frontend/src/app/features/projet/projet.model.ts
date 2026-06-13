export interface CollecteFinancement {
  id?: number;
  montantCible: number;
  montantCollecte: number;
  statut?: string;
  dateDebut?: string;
  dateFin?: string;
}

export interface VoteProjet {
  id?: number;
  userId: string;
  valeur: boolean; 
  date?: string;
}

export interface Projet {
  id?: number;
  titre: string;
  description: string;
  categorie: string;
  image?: string;
  location?: string;
  tags?: string;
  adminId?: string;
  adminNom?: string;
  statut?: StatutProjet;
  votePour?: number;
  voteContre?: number;
  totalVotes?: number;
  dureeDays?: number;
  dateDebut?: string;
  dateFin?: string;
  createdAt?: string;
  collecte?: CollecteFinancement;
  votes?: VoteProjet[];
}

export type StatutProjet =
  | 'EN_VOTE'
  | 'APPROUVE'
  | 'EN_FINANCEMENT'
  | 'EN_COURS'
  | 'TERMINE'
  | 'REJETE';

export type MethodePaiement =
  | 'CARTE_BANCAIRE'
  | 'VIREMENT_BANCAIRE'
  | 'PAIEMENT_MOBILE'
  | 'PAIEMENT_EN_LIGNE'
  | 'ESPECES';

export interface Paiement {
  userId?: string;
  montant: number;
  anonymous?: boolean;
  email?: string;
  methode: MethodePaiement;
}