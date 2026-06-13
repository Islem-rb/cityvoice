import { Component, OnInit } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { SoundService } from '../../../core/services/sound.service';
import { Equipe, EquipeService, Etat } from '../../../core/services/equipe.service';
import { CandidatureEquipe, CandidatureEquipeService } from '../../../core/services/candidature.service';
import { Fonction, MembreEquipe, MembreEquipeService } from '../../../core/services/membre.service';

type Tab = 'equipes' | 'membres' | 'candidatures' | 'quiz';

type QuizStatRow = {
  candidatureId: string;
  equipeNom: string;
  fonction: string;
  cvCount: number;
  testsPassed: number;
};

@Component({
  selector: 'app-adminpersonnel',
  templateUrl: './adminpersonnel.component.html',
  styleUrls: ['./adminpersonnel.component.css'],
})
export class AdminpersonnelComponent implements OnInit {

  // Tabs
  tab: Tab = 'equipes';

  // Data
  loading = true;
  equipes: Equipe[] = [];
  candidatures: CandidatureEquipe[] = [];
  membres: MembreEquipe[] = [];
  quizRows: QuizStatRow[] = [];

  // Filters
  search = '';
  filterEtat: '' | Etat = '';
  filterGouv = '';

  // Stats
  totalEquipes = 0;
  totalMembres = 0;
  totalCandidatures = 0;
  totalCv = 0;
  cvCoveragePct = 0;

  // Detail modals
  selectedEquipe: Equipe | null = null;
  equipeEdit: Equipe | null = null;
  equipeSaving = false;
  equipeDeleteConfirm = false;

  selectedCandidature: CandidatureEquipe | null = null;
  candidatureEdit: CandidatureEquipe | null = null;
  candidatureSaving = false;
  candidatureDeleteConfirm = false;

  selectedMembre: MembreEquipe | null = null;
  membreEdit: MembreEquipe | null = null;
  membreSaving = false;
  membreDeleteConfirm = false;

  // CORRECTION ICI : Utiliser 'N_ATTENTE' au lieu de 'EN_ATTENTE'
  readonly etats: Etat[] = ['LIBRE', 'EN_EXECUTION', 'EN_ATTENTE'];
  
  readonly fonctions: Fonction[] = [
    'CHEF_EQUIPE',
    'OUVRIER_SPECIALISTE',
    'OUVRIER_GENERALISTE',
    'TECHNICIEN',
    'RESPONSABLE_SECURITE',
  ];

  constructor(
    public sound: SoundService,
    private equipeService: EquipeService,
    private candidatureService: CandidatureEquipeService,
    private membreService: MembreEquipeService,
  ) {}

  ngOnInit(): void {
    this.reloadAll();
  }

  setTab(t: Tab): void {
    this.sound.nav();
    this.tab = t;
  }

  reloadAll(): void {
    this.loading = true;
    forkJoin({
      equipes: this.equipeService.getAll().pipe(catchError(() => of([] as Equipe[]))),
      candidatures: this.candidatureService.getAll().pipe(catchError(() => of([] as CandidatureEquipe[]))),
      membres: this.membreService.getAll().pipe(catchError(() => of([] as MembreEquipe[]))),
    }).subscribe({
      next: ({ equipes, candidatures, membres }) => {
        this.equipes = equipes || [];
        this.candidatures = candidatures || [];
        this.membres = membres || [];
        this.recomputeStats();
        this.buildQuizRows();
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  recomputeStats(): void {
    this.totalEquipes = this.equipes.length;
    this.totalMembres = this.equipes.reduce((a, e) => a + (e.membresEquipe?.length || 0), 0);
    this.totalCandidatures = this.candidatures.length;
    this.totalCv = (this.candidatures || []).reduce((a, c) => a + (c.cvs?.length || c.nbCv || 0), 0);
    const totalSlots = (this.candidatures || []).reduce((a, c) => a + (c.nbcandidatsA || 0), 0);
    this.cvCoveragePct = totalSlots > 0 ? Math.round((this.totalCv / totalSlots) * 100) : 0;
  }

  buildQuizRows(): void {
    this.quizRows = (this.candidatures || [])
      .filter(c => !!c.id)
      .map(c => ({
        candidatureId: c.id!,
        equipeNom: c.equipe?.name || '—',
        fonction: c.fonction || '—',
        cvCount: c.cvs?.length || c.nbCv || 0,
        testsPassed: 0,
      }));
  }

  cvCountForCandidature(c: CandidatureEquipe): number {
    return c.cvs?.length || c.nbCv || 0;
  }

  participationPct(c: CandidatureEquipe): number {
    const slots = c.nbcandidatsA || 0;
    if (slots <= 0) return 0;
    return Math.min(100, Math.round((this.cvCountForCandidature(c) / slots) * 100));
  }

  // ───────────────────────── Equipes ─────────────────────────
  get filteredEquipes(): Equipe[] {
    const term = this.search.trim().toLowerCase();
    return (this.equipes || []).filter(e => {
      const matchText = !term
        || (e.name || '').toLowerCase().includes(term)
        || (e.specialite || '').toLowerCase().includes(term);
      const matchEtat = !this.filterEtat || e.etat === this.filterEtat;
      const matchGouv = !this.filterGouv || (e.gouvernorat || '').toLowerCase().includes(this.filterGouv.toLowerCase());
      return matchText && matchEtat && matchGouv;
    });
  }

  viewEquipe(e: Equipe): void {
    this.sound.nav();
    this.selectedEquipe = e;
    this.equipeEdit = { ...e, membresEquipe: e.membresEquipe ? [...e.membresEquipe] : [] };
    this.equipeDeleteConfirm = false;
  }

  equipeCandidaturesCount(equipeId?: string): number {
    if (!equipeId) return 0;
    return (this.candidatures || []).filter(c => c.equipe?.id === equipeId).length;
  }

  equipeCvCount(equipeId?: string): number {
    if (!equipeId) return 0;
    return (this.candidatures || [])
      .filter(c => c.equipe?.id === equipeId)
      .reduce((a, c) => a + this.cvCountForCandidature(c), 0);
  }

  equipeCoveragePct(equipeId?: string): number {
    if (!equipeId) return 0;
    const slots = (this.candidatures || [])
      .filter(c => c.equipe?.id === equipeId)
      .reduce((a, c) => a + (c.nbcandidatsA || 0), 0);
    const cvs = this.equipeCvCount(equipeId);
    return slots > 0 ? Math.min(100, Math.round((cvs / slots) * 100)) : 0;
  }

  closeEquipe(): void {
    this.selectedEquipe = null;
    this.equipeEdit = null;
    this.equipeDeleteConfirm = false;
  }

  saveEquipe(): void {
    if (!this.equipeEdit?.id) return;
    this.equipeSaving = true;
    
    const payload: Equipe = {
      id: this.equipeEdit.id,
      name: this.equipeEdit.name || '',
      specialite: this.equipeEdit.specialite || '',
      gouvernorat: this.equipeEdit.gouvernorat || '',
      etat: this.equipeEdit.etat || 'LIBRE',
      membresEquipe: this.equipeEdit.membresEquipe || []
    };
    
    this.equipeService.update(this.equipeEdit.id, payload).subscribe({
      next: () => {
        this.equipeSaving = false;
        this.reloadAll();
        this.closeEquipe();
      },
      error: () => { this.equipeSaving = false; alert('Erreur modification équipe'); }
    });
  }

  confirmDeleteEquipe(): void {
    this.sound.nav();
    this.equipeDeleteConfirm = true;
  }

  deleteEquipe(): void {
    if (!this.selectedEquipe?.id) return;
    this.equipeSaving = true;
    this.equipeService.delete(this.selectedEquipe.id).subscribe({
      next: () => {
        this.equipeSaving = false;
        this.reloadAll();
        this.closeEquipe();
      },
      error: () => { this.equipeSaving = false; alert('Erreur suppression équipe'); }
    });
  }

  // ───────────────────────── Membres ─────────────────────────
  get filteredMembres(): MembreEquipe[] {
    const term = this.search.trim().toLowerCase();
    return (this.membres || []).filter(m => {
      const matchText = !term
        || (m.nom || '').toLowerCase().includes(term)
        || (m.prenom || '').toLowerCase().includes(term)
        || (m.email || '').toLowerCase().includes(term);
      return matchText;
    });
  }

  viewMembre(m: MembreEquipe): void {
    this.sound.nav();
    this.selectedMembre = m;
    this.membreEdit = { ...m };
    this.membreDeleteConfirm = false;
  }

  closeMembre(): void {
    this.selectedMembre = null;
    this.membreEdit = null;
    this.membreDeleteConfirm = false;
  }

  saveMembre(): void {
    if (!this.membreEdit?.id) return;
    this.membreSaving = true;
    
    const payload: MembreEquipe = {
      id: this.membreEdit.id,
      userId: this.membreEdit.userId || '',
      nom: this.membreEdit.nom || '',
      prenom: this.membreEdit.prenom || '',
      fonction: this.membreEdit.fonction || 'OUVRIER_GENERALISTE',
      telephone: this.membreEdit.telephone || '',
      email: this.membreEdit.email || '',
      photo: this.membreEdit.photo || '',
      dateAdhesion: this.membreEdit.dateAdhesion || new Date().toISOString()
    };
    
    this.membreService.update(this.membreEdit.id, payload).subscribe({
      next: () => {
        this.membreSaving = false;
        this.reloadAll();
        this.closeMembre();
      },
      error: () => { this.membreSaving = false; alert('Erreur modification membre'); }
    });
  }

  onMembrePhotoSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.membreEdit) return;

    const reader = new FileReader();
    reader.onload = () => {
      this.membreEdit!.photo = String(reader.result || '');
    };
    reader.readAsDataURL(file);
  }

  confirmDeleteMembre(): void {
    this.sound.nav();
    this.membreDeleteConfirm = true;
  }

  deleteMembre(): void {
    if (!this.selectedMembre?.id) return;
    this.membreSaving = true;
    this.membreService.delete(this.selectedMembre.id).subscribe({
      next: () => {
        this.membreSaving = false;
        this.reloadAll();
        this.closeMembre();
      },
      error: () => { this.membreSaving = false; alert('Erreur suppression membre'); }
    });
  }

  // ─────────────────────── Candidatures ──────────────────────
  get filteredCandidatures(): CandidatureEquipe[] {
    const term = this.search.trim().toLowerCase();
    return (this.candidatures || []).filter(c => {
      const matchText = !term
        || (c.equipe?.name || '').toLowerCase().includes(term)
        || (c.statut || '').toLowerCase().includes(term)
        || (c.gouvernorat || '').toLowerCase().includes(term)
        || (c.fonction || '').toLowerCase().includes(term);
      return matchText;
    });
  }

  viewCandidature(c: CandidatureEquipe): void {
    this.sound.nav();
    this.selectedCandidature = c;
    this.candidatureEdit = { ...c, equipe: c.equipe };
    this.candidatureDeleteConfirm = false;
  }

  closeCandidature(): void {
    this.selectedCandidature = null;
    this.candidatureEdit = null;
    this.candidatureDeleteConfirm = false;
  }

  saveCandidature(): void {
    if (!this.candidatureEdit?.id) return;
    this.candidatureSaving = true;
    
    const payload: CandidatureEquipe = {
      id: this.candidatureEdit.id,
      equipe: this.candidatureEdit.equipe,
      fonction: this.candidatureEdit.fonction || '',
      gouvernorat: this.candidatureEdit.gouvernorat || '',
      nbcandidatsA: this.candidatureEdit.nbcandidatsA || 0,
      dateExpiration: this.candidatureEdit.dateExpiration || '',
      statut: this.candidatureEdit.statut || '',
      description: this.candidatureEdit.description || '',
      cvs: this.candidatureEdit.cvs || [],
      nbCv: this.candidatureEdit.nbCv || 0
    };
    
    this.candidatureService.update(this.candidatureEdit.id, payload).subscribe({
      next: () => {
        this.candidatureSaving = false;
        this.reloadAll();
        this.closeCandidature();
      },
      error: () => { this.candidatureSaving = false; alert('Erreur modification candidature'); }
    });
  }

  confirmDeleteCandidature(): void {
    this.sound.nav();
    this.candidatureDeleteConfirm = true;
  }

  deleteCandidature(): void {
    if (!this.selectedCandidature?.id) return;
    this.candidatureSaving = true;
    this.candidatureService.delete(this.selectedCandidature.id).subscribe({
      next: () => {
        this.candidatureSaving = false;
        this.reloadAll();
        this.closeCandidature();
      },
      error: () => { this.candidatureSaving = false; alert('Erreur suppression candidature'); }
    });
  }

  // helpers
  formatDate(d?: string): string {
    if (!d) return '—';
    const dt = new Date(d);
    if (isNaN(dt.getTime())) return '—';
    return dt.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  trackById(_: number, x: any): string { return x?.id ?? _; }
}