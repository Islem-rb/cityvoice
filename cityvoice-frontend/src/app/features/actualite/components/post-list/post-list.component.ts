import { Component, OnDestroy, OnInit, ViewChild, ElementRef } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, Subscription, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { PostService } from '../../services/post.service';
import { CommentaireService } from '../../services/commentaire.service';
import { AuthService } from '../../../../core/services/auth.service';
import { UserService } from '../../../../core/services/user.service';
import { BadWordsService } from '../../../../core/services/bad-words.service';
import { ContentModerationService } from '../../services/content-moderation.service';
import { AiService } from '../../services/ai.service';
import { SpeechService } from '../../services/speech.service';
import { VoiceInputService } from '../../services/voice-input.service';
import { Post } from '../../models/post.model';
import { Commentaire } from '../../models/commentaire.model';

@Component({
  selector: 'app-post-list',
  templateUrl: './post-list.component.html',
  styleUrls: ['./post-list.component.css']
})
export class PostListComponent implements OnInit, OnDestroy {

  posts: Post[] = [];
  loading = true;
  currentUserName = '';
  currentUserPhoto = '';
  currentUserId = '';

  /** ID du post à mettre en évidence (depuis une notification) */
  highlightedPostId: number | null = null;

  /** Abonnement aux changements de queryParams (pour réagir aux clics sur notification) */
  private queryParamsSub?: Subscription;

  // =================== COMMENTS ===================
  openComments: { [postId: number]: boolean } = {};
  comments: { [postId: number]: Commentaire[] } = {};
  loadingComments: { [postId: number]: boolean } = {};
  newComment: { [postId: number]: string } = {};
  badWordWarning: { [postId: number]: boolean } = {};

  editingCommentId: number | null = null;
  editCommentContent: { [commentId: number]: string } = {};
  editCommentLoading = false;

  // =================== REPLIES ===================
  replyingTo: { [postId: number]: { commentId: number; authorName: string } | null } = {};
  newReply: { [postId: number]: string } = {};
  replyBadWordWarning: { [postId: number]: boolean } = {};

  // =================== CREATE POST ===================
  @ViewChild('createFileInput') createFileInput?: ElementRef<HTMLInputElement>;

  showCreateForm = false;
  createForm!: FormGroup;
  createLoading = false;

  // Media (image ou vidéo)
  createMediaFile: File | null = null;
  createMediaPreviewUrl: string | null = null;
  createMediaType: 'image' | 'video' | null = null;
  createMediaError = '';
  createSubmitError = '';
  createSubmitWarning = '';

  // =================== EDIT POST ===================
  @ViewChild('editFileInput') editFileInput?: ElementRef<HTMLInputElement>;

  editingPostId: number | null = null;
  editForm!: FormGroup;
  editLoading = false;

  editMediaFile: File | null = null;
  editMediaPreviewUrl: string | null = null;
  editMediaType: 'image' | 'video' | null = null;
  editMediaError = '';
  editExistingUrls: string[] = [];

  // =================== SAVED POSTS ===================
  savedPostIds = new Set<number>();
  private savedKey = '';

  // =================== DETAIL MODAL ===================
  selectedPost: Post | null = null;
  detailComments: any[] = [];
  detailLoading = false;

  // =================== "LIRE LA SUITE" =====================
  /** Nombre max de caractères affichés par défaut pour le contenu d'un post. */
  readonly CONTENT_PREVIEW_LIMIT = 280;
  /** IDs des posts dont la description est actuellement "dépliée". */
  private expandedPostIds = new Set<number>();

  readonly types = ['ACTUALITE', 'EVENEMENT', 'ANNONCE'];

  // =================== AI FEATURES ===================
  aiImageUrl: string | null = null;       // URL de l'image générée par Pollinations
  aiImageLoading = false;                 // Chargement image en cours
  aiImageError = '';                      // Message d'erreur image

  aiContentLoading = false;               // Chargement suggestion contenu en cours
  aiContentError = '';                    // Message d'erreur contenu

  // =================== UNIFIED SHARE MENU ===================
  /** Post dont le menu de partage est ouvert (null = fermé) */
  shareMenuPost: Post | null = null;
  shareMenuCopied = false;         // Toast "Copié !"
  shareLinkCopied = false;

  constructor(
    private fb: FormBuilder,
    private postService: PostService,
    private commentaireService: CommentaireService,
    private authService: AuthService,
    private userService: UserService,
    private router: Router,
    private route: ActivatedRoute,
    private badWords: BadWordsService,
    private moderation: ContentModerationService,
    private aiService: AiService,
    private speechService: SpeechService,
    private voiceInput: VoiceInputService
  ) {}

  ngOnInit(): void {
    this.createForm = this.fb.group({
      title:   ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
      content: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(1000)]],
      type:    ['ACTUALITE', Validators.required]
    });
    this.loadPosts();
    const user = this.authService.getCurrentUser();
    if (user?.userId) {
      this.currentUserId = user.userId;
      this.loadMyPostIds();
      this.loadSavedPostIds();
      this.userService.getById(user.userId).subscribe(u => {
        this.currentUserName = u.nom;
        this.currentUserPhoto = u.photo;
      });
    }

    // Réagir aux changements de queryParams (ex: l'utilisateur clique sur une notification
    // alors qu'il est DÉJÀ sur /actualites → ngOnInit ne se relance pas, mais queryParams change).
    this.queryParamsSub = this.route.queryParams.subscribe(params => {
      const pid = params['postId'];
      if (!pid) return;
      const postId = Number(pid);
      if (isNaN(postId)) return;

      // Si les posts sont déjà chargés → scroll direct ; sinon loadPosts() s'en occupera.
      if (this.posts.length > 0) {
        this.scrollToPost(postId);
      }
    });
  }

  ngOnDestroy(): void {
    this.revokeCreatePreview();
    this.revokeEditPreview();
    // Stopper la lecture vocale si le composant est détruit
    this.speechService.stop();
    // Stopper la dictée vocale si le composant est détruit
    this.voiceInput.stop();
    this.queryParamsSub?.unsubscribe();
  }

  // =================== TEXT-TO-SPEECH ===================
  /**
   * Bascule la lecture vocale du post (play/stop).
   * Lit : "titre. contenu" en français.
   */
  toggleSpeak(post: Post): void {
    const text = `${post.title}. ${post.content}`;
    this.speechService.toggle(post.id, text);
  }

  /** Retourne true si ce post est en cours de lecture vocale */
  isSpeaking(postId: number): boolean {
    return this.speechService.isSpeaking(postId);
  }

  // =================== VOICE INPUT (Speech-to-Text) ===================
  /**
   * Active/désactive la dictée vocale sur un champ du formulaire de création.
   * @param fieldName  Nom du contrôle dans createForm ('title' ou 'content')
   */
  toggleVoiceInput(fieldName: 'title' | 'content'): void {
    if (!this.voiceInput.isSupported()) {
      alert('La dictée vocale n\'est pas supportée par votre navigateur. Utilisez Chrome, Edge ou Safari.');
      return;
    }

    const control = this.createForm.get(fieldName);
    if (!control) return;

    const initial = (control.value ?? '').toString();

    this.voiceInput.toggle(
      fieldName,
      initial,
      (text: string) => {
        // Respecter les maxlength du formulaire (100 pour title, 1000 pour content)
        const max = fieldName === 'title' ? 100 : 1000;
        const truncated = text.length > max ? text.substring(0, max) : text;
        control.setValue(truncated);
        control.markAsDirty();
      }
    );
  }

  /** Retourne true si ce champ est en cours de dictée */
  isListening(fieldName: string): boolean {
    return this.voiceInput.isListening(fieldName);
  }

  /** Retourne true si la dictée vocale est disponible dans ce navigateur */
  isVoiceInputSupported(): boolean {
    return this.voiceInput.isSupported();
  }

  // =================== MY POST IDS ===================
  private myPostIdsKey = '';
  private myPostIds = new Set<number>();

  private loadMyPostIds(): void {
    this.myPostIdsKey = `cv_my_posts_${this.currentUserId}`;
    try {
      const raw = localStorage.getItem(this.myPostIdsKey);
      this.myPostIds = raw ? new Set(JSON.parse(raw)) : new Set();
    } catch { this.myPostIds = new Set(); }
  }

  private saveMyPostIds(): void {
    if (!this.myPostIdsKey) return;
    localStorage.setItem(this.myPostIdsKey, JSON.stringify([...this.myPostIds]));
  }

  // =================== SAVED POSTS ===================
  private loadSavedPostIds(): void {
    this.savedKey = `cv_saved_${this.currentUserId}`;
    try {
      const raw = localStorage.getItem(this.savedKey);
      this.savedPostIds = raw ? new Set(JSON.parse(raw)) : new Set();
    } catch { this.savedPostIds = new Set(); }
  }

  private persistSavedIds(): void {
    if (!this.savedKey) return;
    localStorage.setItem(this.savedKey, JSON.stringify([...this.savedPostIds]));
  }

  isPostSaved(postId: number): boolean {
    return this.savedPostIds.has(postId);
  }

  toggleSave(post: Post): void {
    if (!this.currentUserId) return;
    if (this.savedPostIds.has(post.id)) {
      this.savedPostIds.delete(post.id);
      // Retirer aussi du localStorage complet des posts sauvegardés
      this.removeSavedPostData(post.id);
    } else {
      this.savedPostIds.add(post.id);
      // Stocker aussi les données du post pour affichage ultérieur
      this.addSavedPostData(post);
    }
    this.persistSavedIds();
  }

  private addSavedPostData(post: Post): void {
    const key = `cv_saved_posts_data_${this.currentUserId}`;
    try {
      const raw = localStorage.getItem(key);
      const saved: Post[] = raw ? JSON.parse(raw) : [];
      const exists = saved.find(p => p.id === post.id);
      if (!exists) saved.push(post);
      localStorage.setItem(key, JSON.stringify(saved));
    } catch {}
  }

  private removeSavedPostData(postId: number): void {
    const key = `cv_saved_posts_data_${this.currentUserId}`;
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return;
      const saved: Post[] = JSON.parse(raw);
      localStorage.setItem(key, JSON.stringify(saved.filter(p => p.id !== postId)));
    } catch {}
  }

  // =================== POSTS ===================
  loadPosts(): void {
    this.loading = true;
    // Lire le postId de la query string AVANT de charger (pour scroller après)
    const targetPostId = this.route.snapshot.queryParams['postId'];

    this.postService.getAll().subscribe({
      next: (data) => {
        this.loading = false;
        this.enrichPostsWithAuteurs(data, targetPostId ? Number(targetPostId) : null);
      },
      error: () => this.loading = false
    });
  }

  /** Scroll vers le post et l'illuminer brièvement */
  scrollToPost(postId: number): void {
    this.highlightedPostId = postId;
    // Attendre que Angular ait rendu le DOM
    setTimeout(() => {
      const el = document.getElementById('post-' + postId);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      // Retirer le highlight après 2.5s
      setTimeout(() => { this.highlightedPostId = null; }, 2500);
    }, 150);
  }

  private enrichPostsWithAuteurs(posts: Post[], scrollToPostId: number | null = null): void {
    // Collect all user IDs to resolve (auteurId + sharedFromAuteurId)
    const allIds = new Set<string>();
    posts.forEach(p => {
      if (p.auteurId) allIds.add(String(p.auteurId));
      if (p.sharedFromAuteurId && !p.sharedFromAuteurNom) allIds.add(String(p.sharedFromAuteurId));
    });
    const missingIds = [...allIds];

    const applyAndScroll = (enrichedPosts: Post[]) => {
      this.posts = enrichedPosts;
      // Scroller après que Angular ait rendu le DOM
      if (scrollToPostId) {
        setTimeout(() => this.scrollToPost(scrollToPostId), 200);
      }
    };

    if (missingIds.length === 0) { applyAndScroll(posts); return; }
    const requests = missingIds.map(id =>
      this.userService.getPublicProfile(id).pipe(catchError(() => of(null)))
    );
    forkJoin(requests).subscribe(profiles => {
      const userMap = new Map<string, any>();
      profiles.forEach((profile, i) => { if (profile) userMap.set(missingIds[i], profile); });
      applyAndScroll(posts.map(post => {
        let enriched = { ...post };
        if (post.auteurId && !post.auteurNom) {
          const u = userMap.get(String(post.auteurId));
          if (u) { enriched.auteurNom = u.nom; enriched.auteurPhoto = u.photo; }
        }
        if (post.sharedFromAuteurId && !post.sharedFromAuteurNom) {
          const u = userMap.get(String(post.sharedFromAuteurId));
          if (u) { enriched.sharedFromAuteurNom = u.nom; enriched.sharedFromAuteurPhoto = u.photo; }
        }
        return enriched;
      }));
    });
  }

  isLoggedIn(): boolean { return this.authService.isLoggedIn(); }

  isOwner(post: Post): boolean {
    if (!this.currentUserId) return false;
    if (post.auteurId && String(post.auteurId) === String(this.currentUserId)) return true;
    return this.myPostIds.has(post.id);
  }

  isCommentOwner(auteurId: string): boolean {
    return !!this.currentUserId && String(auteurId) === String(this.currentUserId);
  }

  openDetailModal(post: Post): void {
    this.selectedPost = post;
    document.body.style.overflow = 'hidden';
    // Charger les commentaires du post
    this.detailLoading = true;
    this.commentaireService.getByPostId(post.id).subscribe({
      next: (data) => {
        this.enrichCommentsWithAuteurs(post.id, data);
        this.detailLoading = false;
      },
      error: () => this.detailLoading = false
    });
  }

  closeDetailModal(): void {
    if (this.selectedPost) {
      this.replyingTo[this.selectedPost.id] = null;
      this.newReply[this.selectedPost.id] = '';
    }
    this.selectedPost = null;
    document.body.style.overflow = '';
  }

  goToDetail(id: number): void {
    const post = this.posts.find(p => p.id === id);
    if (post) this.openDetailModal(post);
  }

  getImageUrl(url: string): string {
    return url?.startsWith('http') ? url : 'http://localhost:8083' + url;
  }

  isVideo(url: string): boolean {
    if (!url) return false;
    const lower = url.toLowerCase();
    return lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.ogg')
      || lower.endsWith('.mov') || lower.endsWith('.avi');
  }

  // ─── "Lire la suite" — gestion du dépliage par post ──────────────────────

  /** Vrai si le contenu dépasse la limite d'aperçu et mérite donc un toggle. */
  needsContentToggle(content: string | null | undefined): boolean {
    return !!content && content.length > this.CONTENT_PREVIEW_LIMIT;
  }

  /** Vrai si l'utilisateur a demandé à voir le contenu complet de ce post. */
  isContentExpanded(postId: number): boolean {
    return this.expandedPostIds.has(postId);
  }

  /** Retourne le contenu à afficher : tronqué + "…" ou complet. */
  displayedContent(post: Post): string {
    const full = post.content || '';
    if (!this.needsContentToggle(full)) return full;
    if (this.isContentExpanded(post.id)) return full;
    // Couper proprement à la dernière espace pour éviter un mot tronqué.
    const cut = full.slice(0, this.CONTENT_PREVIEW_LIMIT);
    const lastSpace = cut.lastIndexOf(' ');
    const clean = lastSpace > 80 ? cut.slice(0, lastSpace) : cut;
    return clean + '…';
  }

  /** Inverse l'état déplié / replié d'un post. */
  toggleContentExpanded(postId: number, ev?: Event): void {
    if (ev) { ev.stopPropagation(); ev.preventDefault(); }
    if (this.expandedPostIds.has(postId)) {
      this.expandedPostIds.delete(postId);
    } else {
      this.expandedPostIds.add(postId);
    }
  }

  fieldMessage(control: AbstractControl | null): string | null {
    if (!control || !control.errors || !(control.touched || control.dirty)) return null;
    const e = control.errors;
    if (e['required']) return 'Ce champ est obligatoire';
    if (e['minlength']) { const n = e['minlength'].requiredLength; return `Minimum ${n} caractère${n > 1 ? 's' : ''}`; }
    if (e['maxlength']) { const n = e['maxlength'].requiredLength; return `Maximum ${n} caractères`; }
    return null;
  }

  // =================== CREATE (MODAL) ===================

  openCreateModal(): void {
    this.showCreateForm = true;
    this.createForm.reset({ title: '', content: '', type: 'ACTUALITE' });
    this.clearCreateMedia();
    this.createSubmitError = '';
    this.createSubmitWarning = '';
    document.body.style.overflow = 'hidden';
  }

  // alias pour rétrocompatibilité HTML
  toggleCreateForm(): void {
    this.showCreateForm ? this.closeCreateModal() : this.openCreateModal();
  }

  /** Navigue vers la page de démarrage d'un live (bouton 🔴 Live du ct-actions). */
  goToLiveStart(): void {
    if (!this.isLoggedIn()) {
      this.router.navigate(['/auth/login']);
      return;
    }
    this.router.navigate(['/live/start']);
  }

  onCreateMediaSelected(event: Event): void {
    this.createMediaError = '';
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const isImg = file.type.startsWith('image/');
    const isVid = file.type.startsWith('video/');

    if (!isImg && !isVid) {
      this.createMediaError = 'Choisissez une image (JPG, PNG…) ou une vidéo (MP4, WebM…)';
      input.value = '';
      return;
    }
    const maxSize = isVid ? 50 * 1024 * 1024 : 5 * 1024 * 1024;
    if (file.size > maxSize) {
      this.createMediaError = isVid ? 'Vidéo trop lourde (max 50 Mo)' : 'Image trop lourde (max 5 Mo)';
      input.value = '';
      return;
    }
    this.revokeCreatePreview();
    this.createMediaFile = file;
    this.createMediaPreviewUrl = URL.createObjectURL(file);
    this.createMediaType = isImg ? 'image' : 'video';
  }

  clearCreateMedia(): void {
    this.revokeCreatePreview();
    this.createMediaFile = null;
    this.createMediaError = '';
    this.createMediaType = null;
    if (this.createFileInput?.nativeElement) this.createFileInput.nativeElement.value = '';
  }

  private revokeCreatePreview(): void {
    if (this.createMediaPreviewUrl) {
      // Ne révoquer que les blob: URLs locales, pas les URLs externes (Pexels, etc.)
      if (this.createMediaPreviewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(this.createMediaPreviewUrl);
      }
      this.createMediaPreviewUrl = null;
    }
  }

  // =================== AI : GÉNÉRATION IMAGE (Pexels URL → browser fetch) ===================

  generateAiImage(): void {
    const titre = (this.createForm.get('title')?.value ?? '').trim();
    if (!titre) return;

    this.aiImageError = '';
    this.aiImageLoading = true;
    this.aiImageUrl = null;
    this.revokeCreatePreview();
    this.createMediaFile = null;
    this.createMediaType = null;

    // 1. Le backend cherche la photo sur Pexels et retourne l'URL (pas les bytes)
    // 2. Le browser télécharge l'image directement → contourne le blocage Cloudflare
    this.aiService.generateImage(titre).subscribe({
      next: async (result) => {
        // Prévisualisation immédiate dès réception de l'URL
        this.createMediaPreviewUrl = result.url;
        this.aiImageUrl = result.url;
        this.createMediaType = 'image';

        try {
          // Téléchargement browser → crée le File pour upload
          const response = await fetch(result.url, { mode: 'cors' });
          if (response.ok) {
            const blob = await response.blob();
            const contentType = blob.type || 'image/jpeg';
            const ext = contentType.includes('png') ? 'png' : 'jpg';
            const file = new File([blob], `pexels-photo.${ext}`, { type: contentType });
            this.createMediaFile = file;
            // Remplace l'URL externe par un ObjectURL local
            this.createMediaPreviewUrl = URL.createObjectURL(blob);
            this.aiImageUrl = this.createMediaPreviewUrl;
          }
          // Si fetch CORS échoue : prévisualisation garde l'URL Pexels, pas d'upload fichier
        } catch {
          // Prévisualisation déjà définie ci-dessus, on garde juste sans File
        }

        this.aiImageLoading = false;
      },
      error: (err) => {
        this.aiImageLoading = false;
        const status = err?.status;
        if (status === 503) {
          this.aiImageError = '🔑 Clé Pexels non configurée — ajoute pexels.api.key dans application.properties.';
        } else if (status === 404) {
          this.aiImageError = '🔍 Aucune photo trouvée pour ce titre. Essaie avec d\'autres mots-clés.';
        } else if (status === 504 || status === 502) {
          this.aiImageError = '⏱️ Timeout. Réessaie dans quelques secondes.';
        } else {
          this.aiImageError = '❌ Erreur de recherche photo. Réessaie.';
        }
      }
    });
  }

  // =================== AI : SUGGESTION CONTENU (Claude Haiku) ===================

  suggestContent(): void {
    const titre = (this.createForm.get('title')?.value ?? '').trim();
    const type  = this.createForm.get('type')?.value ?? 'ACTUALITE';

    if (!titre) return;

    this.aiContentError = '';
    this.aiContentLoading = true;

    this.aiService.suggestContent(titre, type).subscribe({
      next: (res) => {
        this.aiContentLoading = false;
        if (res.success && res.content) {
          this.createForm.get('content')?.setValue(res.content);
        } else {
          this.aiContentError = res.error ?? 'Erreur lors de la suggestion.';
        }
      },
      error: () => {
        this.aiContentLoading = false;
        this.aiContentError = 'Erreur de connexion au service IA.';
      }
    });
  }

  // =================== UNIFIED SHARE MENU ===================

  /** Ouvre le menu de partage unifié (appelé depuis le bouton "Partager" de reaction-bar) */
  openShareMenu(post: Post): void {
    this.shareMenuPost = post;
    this.shareMenuCopied = false;
    this.shareLinkCopied = false;
  }

  /** Ferme le menu de partage */
  closeShareMenu(): void {
    this.shareMenuPost = null;
  }

  /** Partage interne CityVoice (republier dans le fil) */
  shareInternally(): void {
    if (!this.shareMenuPost) return;
    const post = this.shareMenuPost;
    this.closeShareMenu();
    this.sharePost(post);       // logique existante
  }

  /** Construit l'URL publique du post */
  getPostShareUrl(post: Post): string {
    return `${window.location.origin}/posts/${post.id}`;
  }

  /** Partage réel sur Facebook */
  shareToFacebook(): void {
    if (!this.shareMenuPost) return;
    const post  = this.shareMenuPost;
    const url   = encodeURIComponent(this.getPostShareUrl(post));
    const quote = encodeURIComponent(`${post.title}\n\n${(post.content || '').substring(0, 200)}`);
    window.open(
      `https://www.facebook.com/sharer/sharer.php?u=${url}&quote=${quote}`,
      '_blank', 'width=640,height=480,resizable=yes'
    );
    this.closeShareMenu();
  }

  /** Partage réel sur LinkedIn */
  shareToLinkedIn(): void {
    if (!this.shareMenuPost) return;
    const post    = this.shareMenuPost;
    const url     = encodeURIComponent(this.getPostShareUrl(post));
    const title   = encodeURIComponent(post.title);
    const summary = encodeURIComponent((post.content || '').substring(0, 256));
    window.open(
      `https://www.linkedin.com/shareArticle?mini=true&url=${url}&title=${title}&summary=${summary}`,
      '_blank', 'width=640,height=560,resizable=yes'
    );
    this.closeShareMenu();
  }

  /** Partage Instagram : Web Share API sur mobile, copie presse-papiers sur desktop */
  async shareToInstagram(): Promise<void> {
    if (!this.shareMenuPost) return;
    const post = this.shareMenuPost;
    const text = `${post.title}\n\n${(post.content || '').substring(0, 200)}\n\n${this.getPostShareUrl(post)}`;
    if (navigator.share) {
      try { await navigator.share({ title: post.title, text, url: this.getPostShareUrl(post) }); }
      catch { /* annulé */ }
    } else {
      await navigator.clipboard.writeText(text).catch(() => {});
      this.shareMenuCopied = true;
      setTimeout(() => { this.shareMenuCopied = false; }, 3000);
    }
    if (!this.shareMenuCopied) this.closeShareMenu();
  }

  /** Copie le lien dans le presse-papiers */
  async copyShareLink(): Promise<void> {
    if (!this.shareMenuPost) return;
    await navigator.clipboard.writeText(this.getPostShareUrl(this.shareMenuPost)).catch(() => {});
    this.shareLinkCopied = true;
    setTimeout(() => { this.shareLinkCopied = false; this.closeShareMenu(); }, 2000);
  }

  /** Détecte si l'utilisateur est sur mobile */
  isMobileDevice(): boolean {
    return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
  }

  // Réinitialiser l'état IA lors de la fermeture du modal
  closeCreateModal(): void {
    this.showCreateForm = false;
    this.aiImageUrl = null;
    this.aiImageError = '';
    this.aiContentError = '';
    // Stopper la dictée vocale si active
    this.voiceInput.stop();
    document.body.style.overflow = '';
  }

  submitCreate(): void {
    const titleCtrl = this.createForm.get('title');
    const contentCtrl = this.createForm.get('content');
    titleCtrl?.setValue((titleCtrl.value ?? '').trim());
    contentCtrl?.setValue((contentCtrl.value ?? '').trim());
    titleCtrl?.updateValueAndValidity({ emitEvent: false });
    contentCtrl?.updateValueAndValidity({ emitEvent: false });

    if (this.createForm.invalid) { this.createForm.markAllAsTouched(); return; }
    if (!this.currentUserId?.trim()) {
      this.createSubmitError = 'Identifiant utilisateur manquant. Reconnectez-vous.';
      return;
    }

    this.createSubmitError = '';
    this.createSubmitWarning = '';

    const v = this.createForm.value;

    // 🛡️  MODÉRATION — blocage immédiat si contenu politique/haineux/etc.
    //     On ne publie pas sur CityVoice des sujets interdits par la charte.
    const moderationResult = this.moderation.check(v.title ?? '', v.content ?? '');
    if (moderationResult.blocked) {
      this.createSubmitError = moderationResult.userMessage;
      return;
    }

    // Filtrer les mots interdits dans le titre et le contenu
    const filteredTitle   = this.badWords.filter(v.title ?? '');
    const filteredContent = this.badWords.filter(v.content ?? '');
    const hadBadWords = filteredTitle !== v.title || filteredContent !== v.content;
    if (hadBadWords) {
      this.createSubmitError = '⚠️ Votre publication contient des mots inappropriés qui ont été détectés. Veuillez reformuler votre message.';
      return;
    }

    this.createLoading = true;
    const fields = { title: filteredTitle, content: filteredContent, type: v.type, auteurId: this.currentUserId };

    let mediaSkipped415 = false;
    const req = this.createMediaFile
      ? this.postService.createWithMedia(fields, this.createMediaFile).pipe(
        catchError((err: HttpErrorResponse) => {
          if (err.status === 415) { mediaSkipped415 = true; return this.postService.create(fields); }
          return throwError(() => err);
        })
      )
      : this.postService.create(fields);

    req.pipe(finalize(() => { this.createLoading = false; })).subscribe({
      next: (post) => {
        const enriched: Post = {
          ...post,
          auteurId: post.auteurId ?? this.currentUserId,
          auteurNom: post.auteurNom ?? this.currentUserName,
          auteurPhoto: post.auteurPhoto ?? this.currentUserPhoto
        };
        this.myPostIds.add(enriched.id);
        this.saveMyPostIds();
        this.posts.unshift(enriched);
        this.createForm.reset({ title: '', content: '', type: 'ACTUALITE' });
        this.clearCreateMedia();
        if (mediaSkipped415) {
          this.createSubmitWarning = 'Média non envoyé (format non supporté). Seul le texte a été publié.';
        } else {
          this.closeCreateModal();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.createSubmitError = this.httpErrorToMessage(err);
      }
    });
  }

  private httpErrorToMessage(err: HttpErrorResponse): string {
    if (err.status === 0) return 'Serveur injoignable. Vérifiez que le service tourne sur le port 8083.';
    if (err.status === 401) return 'Session expirée : reconnectez-vous.';
    if (err.status === 415) return 'Format non supporté (415).';
    // 422 = modération backend : on remonte le message exact au user.
    if (err.status === 422) {
      const body422 = err.error;
      if (typeof body422 === 'string' && body422.trim()) return body422;
      if (body422?.message) return '⛔ ' + body422.message;
      return '⛔ Publication refusée par la modération.';
    }
    const body = err.error;
    if (typeof body === 'string' && body.trim()) return body.length > 200 ? body.slice(0, 200) + '…' : body;
    if (body?.message) return body.message;
    return err.status ? `Erreur ${err.status}.` : 'Impossible de publier.';
  }

  // =================== EDIT POST ===================

  startEdit(post: Post): void {
    this.editingPostId = post.id;
    this.editForm = this.fb.group({
      title:   [post.title,   [Validators.required, Validators.minLength(3),  Validators.maxLength(100)]],
      content: [post.content, [Validators.required, Validators.minLength(10), Validators.maxLength(1000)]],
      type:    [post.type, Validators.required]
    });
    this.editExistingUrls = post.mediaUrls ? [...post.mediaUrls] : [];
    this.clearEditMedia();
  }

  cancelEdit(): void {
    this.editingPostId = null;
    this.clearEditMedia();
    this.editExistingUrls = [];
  }

  onEditMediaSelected(event: Event): void {
    this.editMediaError = '';
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const isImg = file.type.startsWith('image/');
    const isVid = file.type.startsWith('video/');
    if (!isImg && !isVid) {
      this.editMediaError = 'Choisissez une image ou une vidéo';
      input.value = '';
      return;
    }
    const maxSize = isVid ? 50 * 1024 * 1024 : 5 * 1024 * 1024;
    if (file.size > maxSize) {
      this.editMediaError = isVid ? 'Vidéo trop lourde (max 50 Mo)' : 'Image trop lourde (max 5 Mo)';
      input.value = '';
      return;
    }
    this.revokeEditPreview();
    this.editMediaFile = file;
    this.editMediaPreviewUrl = URL.createObjectURL(file);
    this.editMediaType = isImg ? 'image' : 'video';
  }

  clearEditMedia(): void {
    this.revokeEditPreview();
    this.editMediaFile = null;
    this.editMediaError = '';
    this.editMediaType = null;
    if (this.editFileInput?.nativeElement) this.editFileInput.nativeElement.value = '';
  }

  private revokeEditPreview(): void {
    if (this.editMediaPreviewUrl) {
      URL.revokeObjectURL(this.editMediaPreviewUrl);
      this.editMediaPreviewUrl = null;
    }
  }

  removeExistingImage(index: number): void {
    this.editExistingUrls.splice(index, 1);
  }

  submitEdit(postId: number): void {
    const titleCtrl = this.editForm.get('title');
    const contentCtrl = this.editForm.get('content');
    titleCtrl?.setValue((titleCtrl.value ?? '').trim());
    contentCtrl?.setValue((contentCtrl.value ?? '').trim());
    titleCtrl?.updateValueAndValidity({ emitEvent: false });
    contentCtrl?.updateValueAndValidity({ emitEvent: false });
    if (this.editForm.invalid) { this.editForm.markAllAsTouched(); return; }

    const v = this.editForm.value;

    // 🛡️  MODÉRATION — même règle qu'à la création : pas de contenu
    //     politique/haineux/etc., même lors d'une modification.
    const moderationResult = this.moderation.check(v.title ?? '', v.content ?? '');
    if (moderationResult.blocked) {
      alert(moderationResult.userMessage);
      return;
    }

    this.editLoading = true;

    if (this.editMediaFile) {
      this.postService.createWithMedia(
        { title: v.title, content: v.content, type: v.type, auteurId: this.currentUserId },
        this.editMediaFile
      ).pipe(
        catchError(() => this.postService.update(postId, { title: v.title, content: v.content, type: v.type, mediaUrls: this.editExistingUrls })),
        finalize(() => this.editLoading = false)
      ).subscribe({ next: (updated) => this.applyEdit(postId, updated) });
    } else {
      this.postService.update(postId, { title: v.title, content: v.content, type: v.type, mediaUrls: this.editExistingUrls })
        .pipe(finalize(() => this.editLoading = false))
        .subscribe({ next: (updated) => this.applyEdit(postId, updated) });
    }
  }

  private applyEdit(postId: number, updated: Post): void {
    const idx = this.posts.findIndex(p => p.id === postId);
    if (idx !== -1) {
      this.posts[idx] = {
        ...updated,
        auteurId: this.posts[idx].auteurId,
        auteurNom: this.posts[idx].auteurNom,
        auteurPhoto: this.posts[idx].auteurPhoto
      };
    }
    this.editingPostId = null;
    this.clearEditMedia();
    this.editExistingUrls = [];
  }

  // =================== DELETE POST ===================

  deletePost(postId: number): void {
    if (!confirm('Supprimer ce post définitivement ?')) return;
    this.postService.delete(postId).subscribe(() => {
      this.posts = this.posts.filter(p => p.id !== postId);
      this.myPostIds.delete(postId);
      this.saveMyPostIds();
      // Retirer aussi des favoris
      if (this.savedPostIds.has(postId)) {
        this.savedPostIds.delete(postId);
        this.removeSavedPostData(postId);
        this.persistSavedIds();
      }
    });
  }

  // =================== SHARE POST ===================

  sharePost(post: Post): void {
    if (!this.currentUserId) return;
    if (post.sharing) return; // éviter double-click
    post.sharing = true;

    this.postService.sharePost(post.id, {
      sharerId:    this.currentUserId,
      sharerNom:   this.currentUserName,
      sharerPhoto: this.currentUserPhoto,
      commentaire: ''
    }).subscribe({
      next: (sharedPost) => {
        post.sharing = false;
        post.shareSuccess = true;
        post.shareCount = (post.shareCount ?? 0) + 1;
        // Ajouter le post partagé en tête de liste avec toutes les données de l'original
        const enriched: Post = {
          ...sharedPost,
          auteurNom:           this.currentUserName,
          auteurPhoto:         this.currentUserPhoto,
          // Informations de l'auteur original
          sharedFromAuteurNom:   post.auteurNom,
          sharedFromAuteurPhoto: post.auteurPhoto,
          // Contenu original (fallback si le backend ne les retourne pas encore)
          sharedFromTitre:      sharedPost.sharedFromTitre    ?? post.title,
          sharedFromContent:    sharedPost.sharedFromContent  ?? post.content,
          sharedFromMediaUrls:  sharedPost.sharedFromMediaUrls ?? post.mediaUrls,
        };
        this.posts.unshift(enriched);
        setTimeout(() => { post.shareSuccess = false; }, 2500);
      },
      error: () => {
        post.sharing = false;
      }
    });
  }

  // =================== COMMENTS ===================

  toggleComments(postId: number): void {
    this.openComments[postId] = !this.openComments[postId];
    if (this.openComments[postId] && !this.comments[postId]) {
      this.loadingComments[postId] = true;
      this.commentaireService.getByPostId(postId).subscribe(data => {
        this.enrichCommentsWithAuteurs(postId, data);
        this.loadingComments[postId] = false;
      });
    }
  }

  private enrichCommentsWithAuteurs(postId: number, comments: Commentaire[]): void {
    const missingIds = [...new Set(
      comments.filter(c => c.auteurId && !c.auteurNom).map(c => String(c.auteurId))
    )];
    const doGroup = (enriched: Commentaire[]) => {
      // Construire l'arbre : commentaires racines + leurs réponses imbriquées
      const top = enriched.filter(c => !c.parentId);
      const rep = enriched.filter(c => !!c.parentId);
      top.forEach(c => { c.replies = rep.filter(r => r.parentId === c.id); });
      this.comments[postId] = top;
    };
    if (missingIds.length === 0) { doGroup(comments); return; }
    const requests = missingIds.map(id =>
      this.userService.getPublicProfile(id).pipe(catchError(() => of(null)))
    );
    forkJoin(requests).subscribe(profiles => {
      const userMap = new Map<string, any>();
      profiles.forEach((profile, i) => { if (profile) userMap.set(missingIds[i], profile); });
      const enriched = comments.map(c => {
        if (c.auteurId && !c.auteurNom) {
          const u = userMap.get(String(c.auteurId));
          if (u) return { ...c, auteurNom: u.nom };
        }
        return c;
      });
      doGroup(enriched);
    });
  }

  // ─── Reply methods ─────────────────────────────────────────────────────────
  startReply(postId: number, commentId: number, authorName: string): void {
    this.replyingTo[postId] = { commentId, authorName };
    this.newReply[postId] = '';
  }

  /**
   * Répondre à une réponse (style Facebook) :
   * on reste au MÊME niveau visuel (on ne crée pas de 3e niveau d'imbrication).
   *   - `rootComment` est le commentaire racine (qui porte les `replies`).
   *   - `targetReply` est la réponse qu'on souhaite "taguer".
   * Le contenu est pré-rempli avec "@NomDeCible " comme sur Facebook.
   * Le parentId envoyé au backend reste celui du commentaire RACINE pour garder
   * un arbre à 2 niveaux.
   */
  startReplyToReply(postId: number, rootCommentId: number, targetReply: Commentaire): void {
    const targetName = targetReply.auteurNom || 'Utilisateur';
    this.replyingTo[postId] = { commentId: rootCommentId, authorName: targetName };
    // Pré-remplir le champ avec le tag @nom (comme Facebook / Instagram)
    this.newReply[postId] = `@${targetName} `;
    // Petit scroll/focus : Angular auto-focus via [autofocus] sur l'input existant.
  }

  cancelReply(postId: number): void {
    this.replyingTo[postId] = null;
    this.newReply[postId] = '';
  }

  submitReply(postId: number): void {
    const reply = this.replyingTo[postId];
    if (!reply) return;
    const raw = this.newReply[postId]?.trim();
    if (!raw || raw.length < 2) return;

    const contenu = this.badWords.filter(raw);
    const hadBadWords = contenu !== raw;
    if (hadBadWords) {
      this.replyBadWordWarning[postId] = true;
      this.newReply[postId] = contenu;
      setTimeout(() => { this.replyBadWordWarning[postId] = false; }, 4000);
      return;
    }
    this.replyBadWordWarning[postId] = false;

    this.commentaireService
      .create(postId, contenu, this.currentUserName, this.currentUserPhoto, reply.commentId)
      .subscribe(c => {
        const newReply: Commentaire = { ...c, auteurNom: c.auteurNom || this.currentUserName, replies: [] };
        // Ajouter la réponse sous le bon commentaire parent
        const parent = this.comments[postId]?.find(p => p.id === reply.commentId);
        if (parent) {
          parent.replies = [...(parent.replies || []), newReply];
        }
        this.replyingTo[postId] = null;
        this.newReply[postId] = '';
      });
  }

  submitComment(postId: number): void {
    const raw = this.newComment[postId]?.trim();
    if (!raw || raw.length < 2) return;

    // Filtrer les mots interdits
    const contenu = this.badWords.filter(raw);
    const hadBadWords = contenu !== raw;
    if (hadBadWords) {
      this.badWordWarning[postId] = true;
      this.newComment[postId] = contenu;
      setTimeout(() => { this.badWordWarning[postId] = false; }, 4000);
      return; // Afficher l'avertissement et laisser l'utilisateur corriger
    }
    this.badWordWarning[postId] = false;

    this.commentaireService.create(postId, contenu, this.currentUserName, this.currentUserPhoto).subscribe(c => {
      if (!this.comments[postId]) this.comments[postId] = [];
      this.comments[postId].unshift({ ...c, auteurNom: c.auteurNom || this.currentUserName });
      this.newComment[postId] = '';
    });
  }

  startEditComment(comment: Commentaire): void {
    this.editingCommentId = comment.id;
    this.editCommentContent[comment.id] = comment.contenu;
  }

  cancelEditComment(): void { this.editingCommentId = null; }

  submitEditComment(postId: number, commentId: number): void {
    const contenu = this.editCommentContent[commentId]?.trim();
    if (!contenu || contenu.length < 2) return;
    this.editCommentLoading = true;
    this.commentaireService.update(postId, commentId, contenu).subscribe({
      next: (updated) => {
        const idx = this.comments[postId].findIndex(c => c.id === commentId);
        if (idx !== -1) this.comments[postId][idx] = { ...this.comments[postId][idx], contenu: updated.contenu };
        this.editingCommentId = null;
        this.editCommentLoading = false;
      },
      error: () => this.editCommentLoading = false
    });
  }

  deleteComment(postId: number, commentId: number): void {
    if (!confirm('Supprimer ce commentaire ?')) return;
    this.commentaireService.delete(postId, commentId).subscribe(() => {
      this.comments[postId] = this.comments[postId].filter(c => c.id !== commentId);
    });
  }
}
