import { Component, OnInit } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { PostService } from '../../actualite/services/post.service';
import { UserService } from '../../../core/services/user.service';
import { SoundService } from '../../../core/services/sound.service';
import { Post } from '../../actualite/models/post.model';

@Component({
  selector: 'app-admin-actualites',
  templateUrl: './actualites.component.html',
  styleUrls: ['./actualites.component.css'],
})
export class AdminActualitesComponent implements OnInit {

  // ── Data ────────────────────────────────────────────────────
  allPosts:      Post[] = [];
  filteredPosts: Post[] = [];
  pagedPosts:    Post[] = [];

  loading       = true;
  deleteConfirm: number | null = null;
  deleteLoading  = false;
  selectedPost:  Post | null   = null;

  // ── Filters ─────────────────────────────────────────────────
  search       = '';
  selectedType = 'ALL';
  readonly types = ['ALL', 'ACTUALITE'];

  // ── Pagination ───────────────────────────────────────────────
  currentPage = 0;
  pageSize    = 10;

  // ── Stats ────────────────────────────────────────────────────
  get totalPosts()     { return this.allPosts.length; }
  get withMedia()      { return this.allPosts.filter(p => p.mediaUrls && p.mediaUrls.length > 0).length; }
  get sharedPosts()    { return this.allPosts.filter(p => !!p.sharedFromPostId).length; }
  get filteredTotal()  { return this.filteredPosts.length; }
  get totalPages()     { return Math.ceil(this.filteredPosts.length / this.pageSize); }

  constructor(
    private postService: PostService,
    private userService: UserService,
    public  sound: SoundService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  // ── Load & enrich ────────────────────────────────────────────
  load(): void {
    this.loading = true;
    this.postService.getAll().subscribe({
      next: (posts) => {
        // Sort newest first
        posts.sort((a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        this.enrichWithAuthors(posts);
      },
      error: () => { this.loading = false; }
    });
  }

  private enrichWithAuthors(posts: Post[]): void {
    const ids = [...new Set(
      posts
        .filter(p => p.auteurId && !p.auteurNom)
        .map(p => String(p.auteurId))
    )];

    if (ids.length === 0) {
      this.allPosts = posts;
      this.applyFilters();
      this.loading = false;
      return;
    }

    const reqs = ids.map(id =>
      this.userService.getPublicProfile(id).pipe(catchError(() => of(null)))
    );

    forkJoin(reqs).subscribe(profiles => {
      const map = new Map<string, any>();
      profiles.forEach((p, i) => { if (p) map.set(ids[i], p); });

      this.allPosts = posts.map(post => {
        if (post.auteurId) {
          const u = map.get(String(post.auteurId));
          if (u) return { ...post, auteurNom: u.nom, auteurPhoto: u.photo };
        }
        return post;
      });

      this.applyFilters();
      this.loading = false;
    });
  }

  // ── Filters & pagination ──────────────────────────────────────
  applyFilters(): void {
    const q = this.search.trim().toLowerCase();
    this.filteredPosts = this.allPosts.filter(p => {
      const matchType   = this.selectedType === 'ALL' || p.type === this.selectedType;
      const matchSearch = !q
        || (p.title  ?? '').toLowerCase().includes(q)
        || (p.content ?? '').toLowerCase().includes(q)
        || (p.auteurNom ?? '').toLowerCase().includes(q);
      return matchType && matchSearch;
    });
    this.currentPage = 0;
    this.updatePage();
  }

  updatePage(): void {
    const start = this.currentPage * this.pageSize;
    this.pagedPosts = this.filteredPosts.slice(start, start + this.pageSize);
  }

  setType(t: string): void {
    this.sound.nav();
    this.selectedType = t;
    this.applyFilters();
  }

  onSearch(): void { this.applyFilters(); }

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages) return;
    this.currentPage = p;
    this.updatePage();
  }

  get pages(): number[] {
    const total = this.totalPages;
    const cur   = this.currentPage;
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    const pages: number[] = [];
    for (let i = Math.max(0, cur - 3); i <= Math.min(total - 1, cur + 3); i++) pages.push(i);
    return pages;
  }

  // ── Count per type ────────────────────────────────────────────
  countByType(type: string): number {
    if (type === 'ALL') return this.allPosts.length;
    return this.allPosts.filter(p => p.type === type).length;
  }

  // ── Detail modal ─────────────────────────────────────────────
  viewPost(post: Post): void {
    this.sound.nav();
    this.selectedPost  = post;
    this.deleteConfirm = null;
  }
  closeDetail(): void {
    this.selectedPost  = null;
    this.deleteConfirm = null;
  }

  // ── Delete ───────────────────────────────────────────────────
  confirmDelete(id: number): void {
    this.sound.nav();
    this.deleteConfirm = id;
  }
  cancelDelete(): void { this.deleteConfirm = null; }

  doDelete(id: number): void {
    this.deleteLoading = true;
    this.postService.delete(id).subscribe({
      next: () => {
        this.deleteLoading = false;
        this.deleteConfirm = null;
        this.allPosts      = this.allPosts.filter(p => p.id !== id);
        if (this.selectedPost?.id === id) this.closeDetail();
        this.applyFilters();
      },
      error: () => { this.deleteLoading = false; }
    });
  }

  // ── Helpers ──────────────────────────────────────────────────
  typeColor(type: string): string {
    const m: Record<string, string> = {
      ACTUALITE: '#3B82F6',
      EVENEMENT: '#E85D24',
      ANNONCE:   '#0D9B76',
    };
    return m[type] ?? '#8888A8';
  }

  typeBg(type: string): string {
    const m: Record<string, string> = {
      ACTUALITE: 'rgba(59,130,246,.1)',
      EVENEMENT: 'rgba(232,93,36,.1)',
      ANNONCE:   'rgba(13,155,118,.1)',
    };
    return m[type] ?? 'rgba(136,136,168,.1)';
  }

  typeLabel(type: string): string {
    const m: Record<string, string> = {
      ACTUALITE: 'Actualité',
      EVENEMENT: 'Événement',
      ANNONCE:   'Annonce',
    };
    return m[type] ?? type;
  }

  initials(nom: string = ''): string {
    return nom.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase() || '?';
  }

  formatDate(d: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  getImageUrl(url: string): string {
    return url?.startsWith('http') ? url : 'http://localhost:8083' + url;
  }

  isVideo(url: string): boolean {
    const lower = (url ?? '').toLowerCase();
    return lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.ogg') || lower.endsWith('.mov');
  }

  trackById(_: number, p: Post): number { return p.id; }
}
