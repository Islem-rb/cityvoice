import {
  Component, HostListener, AfterViewInit,
  ElementRef, OnInit, OnDestroy
} from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { LangService, Lang } from '../../../core/services/lang.service';
import { SoundService } from '../../../core/services/sound.service';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';
import { NotificationService, AppNotification } from '../../../core/services/notification.service';
import { ThemeService } from '../../../core/services/theme.service';
import { AutoTranslateService } from '../../../core/services/auto-translate.service';
import { EvenementNotificationService } from '../../../core/services/evenement-notification.service';
import { EvenementNotification } from '../../../features/evenement/models/evenement-notification.model';
import { WebSocketService, RealtimeNotification } from '../../../core/services/websocket.service';
import { DemandeMaintenance } from '../../../features/demande-maintenance/demande-maintenance.service';
import { environment } from '../../../../environments/environment';
declare const gsap: any;

export interface QuizQuestion {
  question:     string;
  options:      string[];
  correctIndex: number;
}

type QuizState = 'idle' | 'loading' | 'in-progress' | 'submitting' | 'result';

/** Type discriminant pour toutes les sources de notifications */
export type NotifSource = 'system' | 'event' | 'ws' | 'maintenance';

/** Notification unifiée affichée dans le dropdown */
export interface UnifiedNotification {
  uid:           string;
  source:        NotifSource;
  type:          'resolved' | 'progress' | 'badge' | 'info' | 'preselection' | 'maintenance';
  message:       string;
  date:          Date;
  timeAgo:       string;
  read:          boolean;
  lien?:         string;
  // WS / quiz specific
  cvId?:         string;
  userId?:       string;
  fonction?:     string;
  quizTaken?:    boolean;
  checkingQuiz?: boolean;
  _raw?: AppNotification | EvenementNotification | WsNotification;
}

interface WsNotification {
  id:            string;
  type:          'resolved' | 'progress' | 'badge' | 'info' | 'preselection';
  message:       string;
  time:          string;
  read:          boolean;
  cvId?:         string;
  userId?:       string;
  fonction?:     string;
  quizTaken?:    boolean;
  checkingQuiz?: boolean;
}

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css'],
})
export class NavbarComponent implements OnInit, AfterViewInit, OnDestroy {
  scrolled      = false;
  isHomePage    = true;
  notifsOpen    = false;
  userMenuOpen  = false;

  // Auth
  isAuthenticated = false;
  authLoading     = true;
  currentUser: any = null;

  // Toast
  toastMsg  = '';
  toastType: 'success' | 'error' = 'success';
  toast     = false;
  private toastTimeout: any;

  private authSub!:          Subscription;
  private notifSub!:         Subscription;
  private incomingNotifSub!: Subscription;
  private wsSub!:            Subscription;
  private seenNotifIds       = new Set<number>();
  private notificationsHydrated = false;

  // ─── Raw sources ────────────────────────────────────────────────────────
  private _sysNotifs: AppNotification[]       = [];
  private _evNotifs:  EvenementNotification[] = [];
  private _wsNotifs:  WsNotification[]        = [];

  /** Number of pending maintenance requests (EXPERT / MODERATEUR) */
  demandesEnAttente = 0;

  // ─── Unified view ───────────────────────────────────────────────────────
  unifiedNotifications: UnifiedNotification[] = [];

  get totalUnread(): number {
    return this.unifiedNotifications.filter(n => !n.read).length;
  }

  // ─── Quiz popup ─────────────────────────────────────────────────────────
  quizOpen         = false;
  quizState: QuizState = 'idle';
  quizFonction     = '';
  quizCvId         = '';
  quizUserId       = '';
  quizQuestions:  QuizQuestion[] = [];
  quizCurrentIdx   = 0;
  quizAnswers:    (number | null)[] = [];
  quizScore        = 0;
  quizTimeLeft     = 600;
  quizTimeExpired  = false;
  private quizTimer: any;

  get quizProgress():    number  { return ((this.quizCurrentIdx + 1) / this.quizQuestions.length) * 100; }
  get quizMinutes():     string  { return String(Math.floor(this.quizTimeLeft / 60)).padStart(2, '0'); }
  get quizSeconds():     string  { return String(this.quizTimeLeft % 60).padStart(2, '0'); }
  get quizTimerDanger(): boolean { return this.quizTimeLeft <= 60; }
  get quizPercentage():  number  { return Math.round((this.quizScore / this.quizQuestions.length) * 100); }
  get quizResultLabel(): string {
    if (this.quizPercentage >= 80) return 'Excellent ! 🎉';
    if (this.quizPercentage >= 60) return 'Bien joué ! 👍';
    if (this.quizPercentage >= 40) return 'Passable 😐';
    return 'À améliorer 💪';
  }
  get quizResultColor(): string {
    if (this.quizPercentage >= 80) return '#10b981';
    if (this.quizPercentage >= 60) return '#3b82f6';
    if (this.quizPercentage >= 40) return '#f59e0b';
    return '#ef4444';
  }
  get canSubmit():     boolean { return this.quizAnswers.every(a => a !== null); }
  get answeredCount(): number  { return this.quizAnswers.filter(a => a !== null).length; }

  constructor(
    public  lang:          LangService,
    public  sound:         SoundService,
    public  theme:         ThemeService,
    private authService:   AuthService,
    private userService:   UserService,
    public  notifSvc:      NotificationService,
    public  autoTranslate: AutoTranslateService,
    private el:            ElementRef,
    private router:        Router,
    public  notifService:  EvenementNotificationService,
    private wsService:     WebSocketService,
    private http:          HttpClient,
  ) {}

  // ─────────────────────────────────────────────────────────────────────────
  //  LIFECYCLE
  // ─────────────────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.authSub = this.authService.authState$.subscribe(() => this.updateAuthState());
    this.updateAuthState();

    // System notifications
    this.notifSub = this.notifSvc.notifs$.subscribe(notifs => {
      this._sysNotifs = notifs;
      this._rebuildUnified();
      this._handleNewSysNotif(notifs);
    });

    this.incomingNotifSub = this.notifSvc.incomingNotifs$.subscribe(notif => {
      if (notif.lu) return;
      this.showToast(notif.message, 'success');
      this.sound.notification();
    });

    // WebSocket
    this.wsSub = this.wsService.notification$.subscribe(notif => {
      this._onRealtimeNotification(notif as any);
    });

    // Route tracking
    this.isHomePage = this.router.url === '/';
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe((e: any) => {
      this.isHomePage = (e.urlAfterRedirects ?? e.url) === '/';
    });
  }

  ngAfterViewInit(): void {
    if (typeof gsap === 'undefined') return;
    gsap.fromTo(
      this.el.nativeElement.querySelector('nav'),
      { y: -80, opacity: 0 },
      { y: 0, opacity: 1, duration: .8, ease: 'power3.out', delay: .3 }
    );
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.notifSub?.unsubscribe();
    this.incomingNotifSub?.unsubscribe();
    this.wsSub?.unsubscribe();
    this.notifSvc.destroy();
    this.wsService.disconnect();
    clearInterval(this.quizTimer);
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  AUTH
  // ─────────────────────────────────────────────────────────────────────────

  private updateAuthState(): void {
    this.isAuthenticated = this.authService.isLoggedIn();
    const user = this.authService.getCurrentUser();

    if (this.isAuthenticated && user) {
      this.authLoading = true;
      this.userService.getById(user.userId).subscribe({
        next: (fullUser) => {
          this.currentUser = {
            id: fullUser.id, email: fullUser.email, role: fullUser.role,
            nom: fullUser.nom, points: fullUser.points || 0,
            photo: fullUser.photo || null, telephone: fullUser.telephone || null,
          };
          this.authLoading = false;
          this._animateUserIn();
          this._initServices(user);
        },
        error: () => {
          this.currentUser = {
            id: user.userId, email: user.email, role: user.role,
            nom: user.email?.split('@')[0] || 'Utilisateur',
            points: 0, photo: null, telephone: null,
          };
          this.authLoading = false;
          this._animateUserIn();
          this._initServices(user);
        }
      });
    } else {
      this.currentUser  = null;
      this.authLoading  = false;
      this._wsNotifs    = [];
      this._sysNotifs   = [];
      this._evNotifs    = [];
      this.demandesEnAttente = 0;
      this.notificationsHydrated = false;
      this.seenNotifIds.clear();
      this.unifiedNotifications = [];
      this.notifSvc.destroy();
      this.wsService.disconnect();
    }
  }

  private _initServices(user: any): void {
    this.notifSvc.init();

    // Event notifications
    this.notifService.init(user.userId);
    this.notifService.getNotifications().subscribe(notifs => {
      this._evNotifs = notifs;
      this._rebuildUnified();
    });

    // WebSocket
    this.wsService.connect(user.userId);
    this._loadWsNotificationsFromDb(user.userId);

    // Maintenance requests (EXPERT / MODERATEUR only)
    this._loadMaintenanceRequests();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  MAINTENANCE REQUESTS (mate's feature, integrated into unified notifs)
  // ─────────────────────────────────────────────────────────────────────────

  private _loadMaintenanceRequests(): void {
    const role = this.currentUser?.role;
    if (role !== 'EXPERT' && role !== 'MODERATEUR') return;

    this.http.get<DemandeMaintenance[]>(`${environment.apiUrl}/demandes-maintenance/en-attente`).subscribe({
      next: (demandes) => {
        this.demandesEnAttente = demandes.length;
        this._rebuildUnified();
      },
      error: (err) => console.warn('[Navbar] Impossible de charger les demandes de maintenance', err)
    });
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  UNIFIED NOTIFICATIONS — build & sort
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rebuilds the unified list from all three sources plus maintenance requests,
   * sorted by date descending (most recent first).
   */
  private _rebuildUnified(): void {
    const all: UnifiedNotification[] = [];

    // ── System ──────────────────────────────────────────────────────────
    for (const n of this._sysNotifs) {
      all.push({
        uid:     `system:${n.id}`,
        source:  'system',
        type:    NotificationService.iconType(n.type) as any,
        message: n.message,
        date:    new Date(n.dateCreation),
        timeAgo: NotificationService.timeAgo(n.dateCreation),
        read:    n.lu,
        lien:    n.lien ?? undefined,
        _raw:    n,
      });
    }

    // ── Events ───────────────────────────────────────────────────────────
    for (const n of this._evNotifs) {
      const dateStr = (n as any).dateCreation ?? (n as any).date ?? new Date().toISOString();
      all.push({
        uid:     `event:${n.id}`,
        source:  'event',
        type:    'info',
        message: n.message ?? (n as any).titre ?? '',
        date:    new Date(dateStr),
        timeAgo: this._timeAgo(dateStr),
        read:    n.lu,
        _raw:    n,
      });
    }

    // ── WebSocket / recruitment ──────────────────────────────────────────
    for (const n of this._wsNotifs) {
      const d = (n as any)._date ? new Date((n as any)._date) : new Date();
      all.push({
        uid:          `ws:${n.id}`,
        source:       'ws',
        type:         n.type,
        message:      n.message,
        date:         d,
        timeAgo:      n.time,
        read:         n.read,
        cvId:         n.cvId,
        userId:       n.userId,
        fonction:     n.fonction,
        quizTaken:    n.quizTaken,
        checkingQuiz: n.checkingQuiz,
        _raw:         n,
      });
    }

    // ── Maintenance requests (EXPERT / MODERATEUR) ───────────────────────
    // Injected as a single synthetic unread notification at "now" so it
    // always sorts to the top while there are pending requests.
    if (this.demandesEnAttente > 0) {
      all.push({
        uid:     'maintenance:pending',
        source:  'maintenance',
        type:    'maintenance',
        message: `${this.demandesEnAttente} demande${this.demandesEnAttente > 1 ? 's' : ''} de maintenance en attente`,
        date:    new Date(), // always "now" so it floats near the top
        timeAgo: 'À traiter',
        read:    false,
        lien:    '/expert-dashboard',
      });
    }

    // Sort descending by date — most recent first
    all.sort((a, b) => b.date.getTime() - a.date.getTime());

    this.unifiedNotifications = all;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  WS NOTIFICATIONS
  // ─────────────────────────────────────────────────────────────────────────

  private _onRealtimeNotification(notif: RealtimeNotification): void {
    const alreadyExists = this._wsNotifs.some(n => String(n.id) === String(notif.id));
    if (alreadyExists) return;

    const newNotif: WsNotification & { _date: string } = {
      id:           notif.id,
      type:         notif.type === 'PRESELECTION' ? 'preselection' : 'info',
      message:      notif.message,
      time:         "À l'instant",
      read:         false,
      cvId:         (notif as any).cvId,
      userId:       (notif as any).receiverId ?? (notif as any).userId,
      fonction:     (notif as any).fonction,
      quizTaken:    false,
      checkingQuiz: false,
      _date:        new Date().toISOString(),
    };

    this._wsNotifs = [newNotif, ...this._wsNotifs];
    this._rebuildUnified();
    this._refreshQuizTakenForWsNotif(newNotif);

    this.notifsOpen = true;
    this.showToast(notif.title + ' — ' + notif.message, 'success');
    if (this.sound.isEnabled) this.sound.success?.();

    setTimeout(() => {
      if (typeof gsap !== 'undefined') {
        gsap.fromTo('.notif-badge', { scale: 0 }, { scale: 1, duration: .4, ease: 'back.out(2)' });
      }
    }, 50);
  }

  private _loadWsNotificationsFromDb(userId: string): void {
    this.wsService.getAll(userId).subscribe({
      next: (dbNotifs) => {
        const existingIds = new Set(this._wsNotifs.map(n => n.id));
        const newNotifs = dbNotifs
          .filter(n => !existingIds.has(n.id))
          .map(n => ({
            id:           n.id,
            type:         (n.type === 'PRESELECTION' ? 'preselection' : 'info') as any,
            message:      n.message,
            time:         this._timeAgo((n as any).createdAt),
            read:         n.read,
            cvId:         (n as any).cvId     ?? undefined,
            userId:       (n as any).receiverId ?? userId,
            fonction:     (n as any).fonction  ?? undefined,
            quizTaken:    false,
            checkingQuiz: false,
            _date:        (n as any).createdAt ?? new Date().toISOString(),
          }));

        this._wsNotifs = [...newNotifs, ...this._wsNotifs];
        this._rebuildUnified();
        this._refreshAllQuizTakenFlags();
      },
      error: (err) => console.warn('[Notifs] Impossible de charger depuis la BDD', err)
    });
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  NOTIFICATION ACTIONS
  // ─────────────────────────────────────────────────────────────────────────

  /** Single entry point: mark a unified notification as read */
  readNotification(n: UnifiedNotification): void {
    if (n.read) return;

    switch (n.source) {
      case 'system': {
        const raw = n._raw as AppNotification;
        this.notifSvc.marquerLue(raw);
        if (raw.lien) this.router.navigate([raw.lien]);
        break;
      }
      case 'event': {
        const raw = n._raw as EvenementNotification;
        this.notifService.marquerLue(raw.id);
        break;
      }
      case 'ws': {
        const raw = n._raw as WsNotification;
        raw.read = true;
        this.wsService.markRead(raw.id).subscribe();
        break;
      }
      case 'maintenance': {
        // Navigate to expert dashboard
        if (n.lien) this.router.navigate([n.lien]);
        this.notifsOpen = false;
        break;
      }
    }

    n.read = true; // optimistic UI update
  }

  markAllRead(e: Event): void {
    e.stopPropagation();

    this.notifSvc.marquerToutesLues();

    const userId = this.authService.getUserId() ?? this.authService.getCurrentUser()?.userId;
    if (userId) {
      this.wsService.markAllRead(userId).subscribe();
      this.notifService.marquerToutesLues(userId);
    }

    // Optimistic update
    this._wsNotifs.forEach(n => n.read = true);
    this._sysNotifs.forEach(n => (n.lu = true));
    this._evNotifs.forEach(n => (n.lu = true));
    this.unifiedNotifications.forEach(n => (n.read = true));

    this.sound.toggle2?.(true);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  QUIZ
  // ─────────────────────────────────────────────────────────────────────────

  openQuizFromNotif(n: UnifiedNotification, event: Event): void {
    event.stopPropagation();
    if (n.quizTaken) {
      this.showToast('Test déjà passé pour cette candidature.', 'error');
      return;
    }
    this.readNotification(n);
    this.notifsOpen = false;

    this.quizCvId     = n.cvId    || '';
    this.quizUserId   = n.userId  || this.currentUser?.id || '';
    this.quizFonction = n.fonction || this._extractFonction(n.message);

    this.quizOpen        = true;
    this.quizState       = 'loading';
    this.quizCurrentIdx  = 0;
    this.quizAnswers     = [];
    this.quizTimeExpired = false;
    clearInterval(this.quizTimer);

    this.http.get<QuizQuestion[]>(
      `${environment.apiUrl}/personnel/quiz/generate?fonction=${encodeURIComponent(this.quizFonction)}`
    ).subscribe({
      next: (questions) => {
        this.quizQuestions = questions;
        this.quizAnswers   = new Array(questions.length).fill(null);
        this.quizState     = 'in-progress';
        this.quizTimeLeft  = 600;
        this._startQuizTimer();
      },
      error: (err) => {
        console.error('[Quiz] Erreur génération:', err);
        this.quizState = 'idle';
        this.showToast('Impossible de charger le quiz. Réessayez.', 'error');
      }
    });
  }

  private _extractFonction(message: string): string {
    const match = message.match(/poste\s+[«""]([^»""]+)[»""]/i)
      || message.match(/poste\s+:\s*([^\n.]+)/i);
    return match ? match[1].trim() : 'Poste';
  }

  closeQuiz(): void {
    if (this.quizState === 'in-progress') {
      if (!confirm('Êtes-vous sûr de vouloir quitter ? Votre progression sera perdue.')) return;
    }
    this.quizOpen  = false;
    this.quizState = 'idle';
    clearInterval(this.quizTimer);
  }

  private _startQuizTimer(): void {
    clearInterval(this.quizTimer);
    this.quizTimer = setInterval(() => {
      if (this.quizTimeLeft <= 0) {
        clearInterval(this.quizTimer);
        this.quizTimeExpired = true;
        this.submitQuiz();
      } else {
        this.quizTimeLeft--;
      }
    }, 1000);
  }

  selectAnswer(index: number): void {
    if (this.quizState !== 'in-progress') return;
    this.quizAnswers[this.quizCurrentIdx] = index;
  }

  nextQuestion(): void { if (this.quizCurrentIdx < this.quizQuestions.length - 1) this.quizCurrentIdx++; }
  prevQuestion(): void { if (this.quizCurrentIdx > 0) this.quizCurrentIdx--; }
  goToQuestion(idx: number): void { this.quizCurrentIdx = idx; }

  submitQuiz(): void {
    if (this.quizState === 'submitting' || this.quizState === 'result') return;
    clearInterval(this.quizTimer);
    this.quizState = 'submitting';

    this.quizScore = this.quizAnswers.reduce((acc: number, ans, i) =>
      acc + (ans === this.quizQuestions[i]?.correctIndex ? 1 : 0), 0);

    const payload = {
      userId:         this.quizUserId || this.currentUser?.id,
      cvId:           this.quizCvId  || '00000000-0000-0000-0000-000000000000',
      fonction:       this.quizFonction,
      score:          this.quizScore,
      totalQuestions: this.quizQuestions.length,
      timeExpired:    this.quizTimeExpired,
    };

    this.http.post(`${environment.apiUrl}/personnel/quiz/submit`, payload).subscribe({
      next:  () => { this.quizState = 'result'; this._markQuizAsTaken(this.quizCvId, this.quizUserId || this.currentUser?.id); },
      error: () => { this.quizState = 'result'; }
    });
  }

  restartQuiz(): void { this.quizState = 'idle'; this.quizOpen = false; }

  private _refreshAllQuizTakenFlags(): void {
    this._wsNotifs
      .filter(n => n.type === 'preselection')
      .forEach(n => this._refreshQuizTakenForWsNotif(n));
  }

  private _refreshQuizTakenForWsNotif(notif: WsNotification): void {
    if (notif.type !== 'preselection' || !notif.cvId || !notif.userId) return;
    notif.checkingQuiz = true;
    this.http.get<{ taken: boolean }>(
      `${environment.apiUrl}/personnel/quiz/check?cvId=${encodeURIComponent(notif.cvId)}&userId=${encodeURIComponent(notif.userId)}`
    ).subscribe({
      next:  (res) => { notif.quizTaken = !!res?.taken; notif.checkingQuiz = false; this._rebuildUnified(); },
      error: ()    => { notif.checkingQuiz = false; }
    });
  }

  private _markQuizAsTaken(cvId: string, userId?: string): void {
    this._wsNotifs = this._wsNotifs.map(n => {
      if (n.type !== 'preselection' || !n.cvId || String(n.cvId) !== String(cvId)) return n;
      if (userId && n.userId && String(n.userId) !== String(userId)) return n;
      return { ...n, quizTaken: true, checkingQuiz: false };
    });
    this._rebuildUnified();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  ROUTING (mate's feature)
  // ─────────────────────────────────────────────────────────────────────────

  getDashboardLink(): string {
    const role = this.currentUser?.role;
    if (role === 'CHEF_EQUIPE')   return '/ressources';
    if (role === 'EXPERT')        return '/expert-dashboard';
    if (role === 'MEMBRE_EQUIPE') return '/technicien';
    if (role === 'MODERATEUR')    return '/expert-dashboard';
    return '/';
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  UI HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  private _animateUserIn(): void {
    if (typeof gsap === 'undefined') return;
    setTimeout(() => {
      const el = document.querySelector('.user-menu-wrap');
      if (el) gsap.fromTo(el,
        { opacity: 0, x: 20, scale: 0.92 },
        { opacity: 1, x: 0, scale: 1, duration: .5, ease: 'back.out(1.6)' }
      );
    }, 50);
  }

  @HostListener('window:scroll')
  onScroll(): void { this.scrolled = window.scrollY > 50; }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.notifsOpen   = false;
    this.userMenuOpen = false;
    if (this.quizOpen && this.quizState !== 'in-progress') this.quizOpen = false;
  }

  toggleNotifs(): void {
    this.sound.nav();
    this.notifsOpen = !this.notifsOpen;
    if (this.notifsOpen && typeof gsap !== 'undefined') {
      setTimeout(() => {
        gsap.fromTo('.nd-item', { opacity: 0, y: 8 }, { opacity: 1, y: 0, duration: .3, stagger: .05, ease: 'power2.out' });
      }, 10);
    }
  }

  toggleUserMenu(): void { this.userMenuOpen = !this.userMenuOpen; this.sound.nav(); }
  closeUserMenu():  void { this.userMenuOpen = false; }

  setLang(l: Lang): void {
    this.sound.nav();
    this.lang.switch(l);
    this.autoTranslate.switch(l).catch(err => console.warn('[navbar] auto-translate failed', err));
  }

  toggleSound(): void { this.sound.toggle(); if (this.sound.isEnabled) this.sound.click(); }

  toggleTheme(event: MouseEvent): void {
    this.sound.nav();
    this.theme.toggle(event.currentTarget as HTMLElement);
  }

  onBtnClick(): void { this.sound.click(); }

  // ─────────────────────────────────────────────────────────────────────────
  //  USER INFO
  // ─────────────────────────────────────────────────────────────────────────

  getFullName(): string {
    if (!this.currentUser) return 'Utilisateur';
    const nom = this.currentUser.nom || this.currentUser.email?.split('@')[0] || 'Utilisateur';
    return nom.split(' ').map((w: string) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
  }

  getFirstName(): string { return this.getFullName().split(' ')[0]; }

  getUserInitials(): string {
    const parts = this.getFullName().split(' ');
    return parts.length === 1
      ? parts[0].charAt(0).toUpperCase()
      : (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  }

  getUserPoints(): number { return this.currentUser?.points || 0; }

  getRoleLabel(): string {
    const map: Record<string, string> = {
      CITOYEN: 'Citoyen', CHEF_EQUIPE: "Chef d'équipe",
      MEMBRE_EQUIPE: 'Agent terrain', MODERATEUR: 'Modérateur', ADMIN_VILLE: 'Admin',
    };
    return map[this.currentUser?.role ?? ''] ?? 'Utilisateur';
  }

  getRoleColor(): string {
    const map: Record<string, string> = {
      CITOYEN: '#0D9B76', CHEF_EQUIPE: '#3B82F6',
      MEMBRE_EQUIPE: '#C9973E', MODERATEUR: '#E8532A', ADMIN_VILLE: '#7C3AED',
    };
    return map[this.currentUser?.role ?? ''] ?? '#8888A8';
  }

  getRoleBg(): string {
    const map: Record<string, string> = {
      CITOYEN: 'rgba(13,155,118,.1)', CHEF_EQUIPE: 'rgba(59,130,246,.1)',
      MEMBRE_EQUIPE: 'rgba(201,151,62,.1)', MODERATEUR: 'rgba(232,83,42,.1)',
      ADMIN_VILLE: 'rgba(124,58,237,.1)',
    };
    return map[this.currentUser?.role ?? ''] ?? 'rgba(136,136,168,.1)';
  }

  isCitoyen():    boolean { return this.currentUser?.role === 'CITOYEN'; }
  isChefEquipe(): boolean { return this.currentUser?.role === 'CHEF_EQUIPE'; }
  isExpert():     boolean { return this.currentUser?.role === 'EXPERT'; }
  isModerator():  boolean { return this.currentUser?.role === 'MODERATEUR'; }

  // ─────────────────────────────────────────────────────────────────────────
  //  LOGOUT
  // ─────────────────────────────────────────────────────────────────────────

  logout(): void {
    const el = document.querySelector('.user-menu-wrap');
    if (el && typeof gsap !== 'undefined') {
      gsap.to(el, {
        opacity: 0, x: 20, scale: 0.92, duration: .3, ease: 'power2.in',
        onComplete: () => this._doLogout()
      });
    } else { this._doLogout(); }
  }

  private _doLogout(): void {
    this.notifService.deconnecter();
    this.authService.logout();
    this.userMenuOpen = false;
    this.sound.success?.();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  TOAST
  // ─────────────────────────────────────────────────────────────────────────

  showToast(msg: string, type: 'success' | 'error'): void {
    this.toastMsg  = msg;
    this.toastType = type;
    this.toast     = true;

    setTimeout(() => {
      const el = document.querySelector('.nav-toast');
      if (!el || typeof gsap === 'undefined') {
        setTimeout(() => { this.toast = false; }, 3000);
        return;
      }
      gsap.killTweensOf(el);
      gsap.fromTo(el, { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: .4, ease: 'back.out(1.6)' });
      if (this.toastTimeout) clearTimeout(this.toastTimeout);
      this.toastTimeout = setTimeout(() => {
        gsap.to(el, { opacity: 0, y: 30, duration: .35, ease: 'power2.in', onComplete: () => { this.toast = false; } });
      }, 4000);
    }, 50);
  }

  private _handleNewSysNotif(notifs: AppNotification[]): void {
    if (!this.notificationsHydrated) {
      this.seenNotifIds = new Set(notifs.map(n => n.id));
      this.notificationsHydrated = true;
      return;
    }
    const newUnread = notifs.filter(n => !this.seenNotifIds.has(n.id) && !n.lu);
    notifs.forEach(n => this.seenNotifIds.add(n.id));
    if (!newUnread.length) return;
    const latest = newUnread.sort((a, b) =>
      new Date(b.dateCreation).getTime() - new Date(a.dateCreation).getTime()
    )[0];
    this.showToast(latest.message, 'success');
    this.sound.notification();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  UTILITIES
  // ─────────────────────────────────────────────────────────────────────────

  private _timeAgo(isoString: string): string {
    const diff  = Date.now() - new Date(isoString).getTime();
    const mins  = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days  = Math.floor(diff / 86400000);
    if (mins  < 1)  return "À l'instant";
    if (mins  < 60) return `il y a ${mins}min`;
    if (hours < 24) return `il y a ${hours}h`;
    if (days  < 2)  return 'hier';
    return `il y a ${days}j`;
  }

  trackByUid(_: number, n: UnifiedNotification): string { return n.uid; }

  sourceLabel(source: NotifSource): string {
    const map: Record<NotifSource, string> = {
      system:      'Activité',
      event:       'Événement',
      ws:          'Recrutement',
      maintenance: 'Maintenance',
    };
    return map[source];
  }

  sourceBadgeClass(source: NotifSource): string {
    const map: Record<NotifSource, string> = {
      system:      'nd-badge--system',
      event:       'nd-badge--event',
      ws:          'nd-badge--ws',
      maintenance: 'nd-badge--maintenance',
    };
    return map[source];
  }
}
