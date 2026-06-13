import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { Post } from '../../models/post.model';

@Component({
  selector: 'app-saved-posts',
  templateUrl: './saved-posts.component.html',
  styleUrls: ['./saved-posts.component.css']
})
export class SavedPostsComponent implements OnInit {

  savedPosts:   Post[] = [];
  selectedPost: Post | null = null;
  currentUserId = '';
  loading = true;
  filterType = '';

  /** Exposer Math pour le template */
  Math = Math;

  // Palette de couleurs pour les avatars auteur (par hash)
  private authorColors = [
    'linear-gradient(135deg, #E85D24, #f0a060)',
    'linear-gradient(135deg, #667eea, #764ba2)',
    'linear-gradient(135deg, #11998e, #38ef7d)',
    'linear-gradient(135deg, #f7971e, #ffd200)',
    'linear-gradient(135deg, #ee0979, #ff6a00)',
    'linear-gradient(135deg, #4568dc, #b06ab3)',
    'linear-gradient(135deg, #093637, #50a7c2)',
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (!user?.userId) { this.router.navigate(['/auth/signin']); return; }
    this.currentUserId = user.userId;
    this.loadSavedPosts();
  }

  private loadSavedPosts(): void {
    this.loading = true;
    try {
      const key = `cv_saved_posts_data_${this.currentUserId}`;
      const raw = localStorage.getItem(key);
      this.savedPosts = raw ? JSON.parse(raw) : [];
    } catch { this.savedPosts = []; }
    this.loading = false;
    if (this.savedPosts.length > 0) {
      this.selectedPost = this.savedPosts[0];
    }
  }

  get filteredPosts(): Post[] {
    if (!this.filterType) return this.savedPosts;
    return this.savedPosts.filter(p => p.type === this.filterType);
  }

  selectPost(post: Post): void {
    this.selectedPost = post;
  }

  unsavePost(postId: number): void {
    const idsKey  = `cv_saved_${this.currentUserId}`;
    const dataKey = `cv_saved_posts_data_${this.currentUserId}`;
    try {
      const rawIds = localStorage.getItem(idsKey);
      const ids: number[] = rawIds ? JSON.parse(rawIds) : [];
      localStorage.setItem(idsKey, JSON.stringify(ids.filter(id => id !== postId)));
    } catch {}
    try {
      const raw  = localStorage.getItem(dataKey);
      const posts: Post[] = raw ? JSON.parse(raw) : [];
      localStorage.setItem(dataKey, JSON.stringify(posts.filter(p => p.id !== postId)));
    } catch {}
    this.savedPosts = this.savedPosts.filter(p => p.id !== postId);
    if (this.selectedPost?.id === postId) {
      this.selectedPost = this.filteredPosts[0] ?? null;
    }
  }

  /** Navigue vers le post dans le fil avec highlight */
  goToPost(id: number): void {
    this.router.navigate(['/actualites'], { queryParams: { postId: id } });
  }

  goToFeed(): void { this.router.navigate(['/actualites']); }

  /** Couleur déterministe pour l'avatar de l'auteur */
  getAuthorColor(authorId?: string): string {
    if (!authorId) return this.authorColors[0];
    let hash = 0;
    for (let i = 0; i < authorId.length; i++) {
      hash = authorId.charCodeAt(i) + ((hash << 5) - hash);
    }
    return this.authorColors[Math.abs(hash) % this.authorColors.length];
  }

  getInitials(nom: string): string {
    if (!nom) return '?';
    return nom.trim().charAt(0).toUpperCase();
  }

  getImageUrl(url: string): string {
    if (!url) return '';
    return url.startsWith('http') ? url : 'http://localhost:8083' + url;
  }

  isVideo(url: string): boolean {
    if (!url) return false;
    const lower = url.toLowerCase();
    return lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.ogg')
        || lower.endsWith('.mov') || lower.endsWith('.avi');
  }
}
