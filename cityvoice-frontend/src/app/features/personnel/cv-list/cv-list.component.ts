import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CvUserService, QuizResultRow } from '../../../core/services/cvUser.service';
import { CandidatureEquipeService } from '../../../core/services/candidature.service';
import { AuthService } from '../../../core/services/auth.service';

export interface CvEntry {
  cvId: string;
  fileName: string;
  userId: string;
  userName: string;
  userEmail: string;
  userPhoto?: string;
  score?: number;
  scoreJustification?: string;
  analyzing?: boolean;
  mailSent?: boolean;
  sendingMail?: boolean;
  preselected?: boolean;
  preselecting?: boolean;
}

@Component({
  selector: 'app-cv-list',
  templateUrl: './cv-list.component.html',
  styleUrls: ['./cv-list.component.css']
})
export class CvListComponent implements OnInit {

  candidatureId!: string;
  description   = '';
  equipeNom     = '';
  cvList: CvEntry[] = [];
  isLoading     = true;
  fonction='';

  previewUrl: string | null = null;
  previewName = '';
  popupCv: CvEntry | null = null;

  analyzeAll_running = false;
  quizResults: QuizResultRow[] = [];
  loadingQuizResults = false;
  hiringResultIds = new Set<string>();
  quizResultsModalOpen = false;
  recruitedResultIds = new Set<string>();
  quizSearchTerm = '';
  onlyAbove60 = false;

  constructor(
    private route:       ActivatedRoute,
    private cvService:   CvUserService,
    private candService: CandidatureEquipeService,
    private authService: AuthService,
    private http:        HttpClient
  ) {}

  ngOnInit(): void {
    this.candidatureId = this.route.snapshot.paramMap.get('id')!;
    this.loadCandidature();
    this.loadCvs();
  }

  loadCandidature(): void {
    this.candService.getById(this.candidatureId).subscribe({
      next: c => {
        this.description = c.description ?? '';
        this.equipeNom   = c.equipe?.name ?? c.gouvernorat ?? '';
        this.fonction = c.fonction ?? '';
      }
    });
  }

  loadCvs(): void {
    this.cvService.getCvsWithUsers(this.candidatureId).subscribe({
      next: data => {
        this.cvList    = data as CvEntry[];
        this.isLoading = false;
        this.loadQuizResults();
      },
      error: () => (this.isLoading = false)
    });
  }

  loadQuizResults(): void {
    this.loadingQuizResults = true;
    this.cvService.getQuizResultsByCandidature(this.candidatureId).subscribe({
      next: results => {
        this.quizResults = [...results].sort((a, b) => {
          const pa = this.getResultPercent(a);
          const pb = this.getResultPercent(b);
          if (pb !== pa) return pb - pa;
          // ceux sans date (pas de test) à la fin
          const da = a.passedAt ? new Date(a.passedAt).getTime() : 0;
          const db = b.passedAt ? new Date(b.passedAt).getTime() : 0;
          return db - da;
        });
        this.loadingQuizResults = false;
      },
      error: () => {
        this.quizResults = [];
        this.loadingQuizResults = false;
      }
    });
  }

  // ═══ MÉTHODE AJOUTÉE POUR GÉRER LES PHOTOS ═══
  getPhotoUrl(photo: string | undefined | null): string | null {
    if (!photo) return null;
    
    // Si c'est déjà une URL absolue (http, https)
    if (photo.startsWith('http://') || photo.startsWith('https://')) {
      return photo;
    }
    
    // Si c'est un chemin relatif
    if (photo.startsWith('/')) {
      return photo;
    }
    
    // Si c'est déjà une data URL
    if (photo.startsWith('data:image')) {
      return photo;
    }
    
    // Si c'est une chaîne base64 (sans préfixe)
    // Vérifier si la chaîne ressemble à du base64
    if (photo.match(/^[A-Za-z0-9+/=]+$/)) {
      return `data:image/jpeg;base64,${photo}`;
    }
    
    // Si aucun des cas précédents, retourner la chaîne originale
    return photo;
  }
  // ═══ FIN DE LA MÉTHODE AJOUTÉE ═══

  openQuizResultsModal(): void {
    this.quizResultsModalOpen = true;
    if (this.quizResults.length === 0) {
      this.loadQuizResults();
    }
  }

  closeQuizResultsModal(): void {
    this.quizResultsModalOpen = false;
  }

  previewCv(cv: CvEntry): void {
    this.cvService.downloadCvBlob(cv.cvId).subscribe(blob => {
      this.previewUrl  = URL.createObjectURL(blob);
      this.previewName = cv.fileName;
    });
  }

  closePreview(): void { this.previewUrl = null; }

  downloadCv(cv: CvEntry): void {
    this.cvService.downloadCvBlob(cv.cvId).subscribe(blob => {
      const a    = document.createElement('a');
      a.href     = URL.createObjectURL(blob);
      a.download = cv.fileName;
      a.click();
    });
  }

  openPopup(cv: CvEntry): void {
    if (cv.score !== undefined && cv.score >= 0) this.popupCv = cv;
  }

  closePopup(): void { this.popupCv = null; }

  analyze(cv: CvEntry): void {
    if (!this.description) { alert('Aucune description disponible.'); return; }
    if (cv.analyzing) return;
    cv.analyzing = true;
    cv.score     = undefined;
    this.cvService.scoreCv(cv.cvId, this.description).subscribe({
      next: res => { cv.score = res.score; cv.scoreJustification = res.justification; cv.analyzing = false; },
      error: () => { cv.score = -1; cv.scoreJustification = 'Erreur IA backend'; cv.analyzing = false; }
    });
  }

  analyzeAll(): void {
    if (this.analyzeAll_running) return;
    this.analyzeAll_running = true;
    const pending = this.cvList.filter(cv => cv.score === undefined || cv.score === -1);
    if (pending.length === 0) { this.analyzeAll_running = false; return; }
    let completed = 0;
    pending.forEach(cv => {
      cv.analyzing = true; cv.score = undefined;
      this.cvService.scoreCv(cv.cvId, this.description).subscribe({
        next: res => {
          cv.score = res.score; cv.scoreJustification = res.justification; cv.analyzing = false;
          if (++completed === pending.length) this.analyzeAll_running = false;
        },
        error: () => {
          cv.score = -1; cv.scoreJustification = 'Erreur IA backend'; cv.analyzing = false;
          if (++completed === pending.length) this.analyzeAll_running = false;
        }
      });
    });
  }

  sortByScore(): void {
    this.cvList = [...this.cvList].sort((a, b) => (b.score ?? -1) - (a.score ?? -1));
  }

  preselect(cv: CvEntry): void {
    if (cv.preselecting || cv.preselected) return;
    cv.preselecting = true;
    const chefEquipeId = this.authService.getUserId() ?? '';
    this.cvService.preselectCv(cv.cvId, {
      userId:       cv.userId,
      poste:        this.fonction,
      equipeNom:    this.equipeNom,
      chefEquipeId: chefEquipeId
    }).subscribe({
      next: () => { cv.preselected = true; cv.preselecting = false; },
      error: err => {
        console.error('Préselection error', err);
        cv.preselecting = false;
        alert('Erreur lors de la préselection');
      }
    });
  }

  sendMail(cv: CvEntry): void {
    if (cv.sendingMail || cv.mailSent) return;
    cv.sendingMail = true;
    const payload = {
      to:      cv.userEmail,
      subject: `Votre candidature — ${this.equipeNom}`,
      body:    `Bonjour ${cv.userName},\n\nNous avons bien reçu votre CV pour le poste :\n${this.description}\n\nCordialement,\nL'équipe CityVoice`
    };
    this.cvService.sendEmail(payload).subscribe({
      next: () => { cv.mailSent = true; cv.sendingMail = false; },
      error: err => {
        console.error('Email error', err);
        cv.sendingMail = false;
        alert('Erreur lors de l\'envoi de l\'email');
      }
    });
  }

  get noValidScore(): boolean { return this.cvList.every(c => c.score === undefined || c.score === -1); }
  get allScored(): boolean { return this.cvList.length > 0 && this.cvList.every(c => c.score !== undefined && c.score >= 0); }
  get preselectedCount(): number { return this.cvList.filter(c => c.preselected).length; }

  getInitials(name: string): string { return name.split(' ').slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join(''); }
  getScoreColor(score: number): string { if (score >= 7) return '#10b981'; if (score >= 4) return '#f59e0b'; return '#ef4444'; }
  getScoreLabel(score: number): string { if (score >= 7) return 'Excellent'; if (score >= 4) return 'Moyen'; return 'Faible'; }
  getScoreDash(score: number): string { const pct = Math.max(0, Math.min(10, score)) / 10 * 100; return `${pct} 100`; }
  trackById(_: number, cv: CvEntry): string { return cv.cvId; }

  getResultPercent(result: QuizResultRow): number {
    if (!result.totalQuestions || result.totalQuestions <= 0) return 0;
    if (result.score === null || result.score === undefined) return 0;
    return Math.round((result.score / result.totalQuestions) * 100);
  }

  hasPassedTest(result: QuizResultRow): boolean {
    return result.score !== null && result.totalQuestions !== null && !!result.passedAt;
  }

  getResultBadgeClass(result: QuizResultRow): string {
    const pct = this.getResultPercent(result);
    if (pct >= 80) return 'result-badge--excellent';
    if (pct >= 60) return 'result-badge--good';
    if (pct >= 40) return 'result-badge--medium';
    return 'result-badge--low';
  }

  get filteredQuizResults(): QuizResultRow[] {
    const term = this.quizSearchTerm.trim().toLowerCase();
    return this.quizResults.filter(result => {
      const pct = this.getResultPercent(result);
      const matchScore = !this.onlyAbove60 || pct >= 60;
      const matchText = !term
        || (result.nom || '').toLowerCase().includes(term)
        || (result.email || '').toLowerCase().includes(term);
      return matchScore && matchText;
    });
  }

  hireCandidate(result: QuizResultRow): void {
    // On autorise le recrutement seulement si le test est passé et >= 60%
    if (!this.hasPassedTest(result) || this.getResultPercent(result) < 60) {
      alert('Recrutement impossible: test non passé ou score insuffisant.');
      return;
    }
    if (this.hiringResultIds.has(result.resultId)) return;
    this.hiringResultIds.add(result.resultId);
    this.cvService.hireFromQuizResult(this.candidatureId, result.resultId).subscribe({
      next: () => {
        this.hiringResultIds.delete(result.resultId);
        this.recruitedResultIds.add(result.resultId);
        alert('Candidat ajouté à l’équipe avec succès');
        this.loadQuizResults();
      },
      error: err => {
        console.error('Hire error', err);
        this.hiringResultIds.delete(result.resultId);
        if (err?.status === 409) {
          this.recruitedResultIds.add(result.resultId);
          alert('Ce candidat est déjà membre d’une équipe (recrutement impossible).');
          this.loadQuizResults();
          return;
        }
        alert('Erreur lors de l’ajout du candidat à l’équipe');
      }
    });
  }
}