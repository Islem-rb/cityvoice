export interface Post {
  id: number;
  title: string;
  content: string;
  type: string;
  createdAt: string;
  mediaUrls?: string[];
  auteurId?: string;
  auteurNom?: string;
  auteurPhoto?: string;
  // Share fields
  sharedFromPostId?:     number;
  sharedFromAuteurId?:   string;
  sharedFromAuteurNom?:  string;
  sharedFromAuteurPhoto?: string;
  sharedFromContent?:    string;
  sharedFromTitre?:      string;
  sharedFromMediaUrls?:  string[];
  shareCount?: number;
  // UI-only state
  sharing?: boolean;
  shareSuccess?: boolean;
}