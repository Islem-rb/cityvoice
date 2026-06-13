import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ProjetService } from '../../projet/projet.service';
import { Projet, StatutProjet } from '../../projet/projet.model';

@Component({
  selector: 'app-projet-dashboard',
  templateUrl: './projet-dashboard.component.html',
  styleUrls: ['./projet-dashboard.component.css']
})
export class ProjetDashboardComponent implements OnInit {

  projets:  Projet[] = [];
  filtered: Projet[] = [];
  loading      = true;
  activeFilter = 'TOUS';
  toastMsg     = '';
  showToast    = false;

  filters = ['TOUS','EN_VOTE','APPROUVE','EN_COURS','TERMINE','REJETE'];

  statutOptions: StatutProjet[] = [
    'EN_VOTE','APPROUVE','EN_FINANCEMENT',
    'EN_COURS','TERMINE','REJETE'
  ];

  constructor(
    private projetService: ProjetService,
    private router: Router
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.projetService.getAll().subscribe({
      next: (data) => {
        this.projets  = data;
        this.applyFilter();
        this.loading  = false;
      },
      error: () => {
        this.loading = false;
        this.toast('Erreur chargement');
      }
    });
  }

  applyFilter(): void {
    this.filtered = this.activeFilter === 'TOUS'
      ? [...this.projets]
      : this.projets.filter(p => p.statut === this.activeFilter);
  }

  setFilter(f: string): void {
    this.activeFilter = f;
    this.applyFilter();
  }

  changeStatut(projet: Projet, statut: StatutProjet): void {
    if (!projet.id) return;
    const prev = projet.statut;
    projet.statut = statut;
    this.projetService.updateStatut(projet.id, statut).subscribe({
      next: (updated) => {
        projet.statut = updated.statut;
        this.applyFilter();
        this.toast('Statut mis à jour ✓');
      },
      error: () => {
        projet.statut = prev;
        this.toast('Erreur mise à jour statut');
      }
    });
  }

  delete(projet: Projet): void {
    if (!projet.id) return;
    if (!confirm(`Supprimer "${projet.titre}" ?`)) return;
    this.projetService.delete(projet.id).subscribe({
      next: () => {
        this.projets  = this.projets.filter(p => p.id !== projet.id);
        this.filtered = this.filtered.filter(p => p.id !== projet.id);
        this.toast('Projet supprimé');
      },
      error: () => this.toast('Erreur suppression')
    });
  }

  viewProjet(p: Projet): void { this.router.navigate(['/admin/projets', p.id]); }
  editProjet(p: Projet): void { this.router.navigate(['/admin/projets', p.id, 'edit']); }
  goCreate():  void { this.router.navigate(['/admin/projets/create']); }
  goFeed():    void { this.router.navigate(['/projets']); }

  get totalProjets():    number { return this.projets.length; }
  get totalVotes():      number {
    return this.projets.reduce((a, p) => a + (p.totalVotes || 0), 0);
  }
  get totalDons():       number {
    return this.projets.reduce(
      (a, p) => a + (p.collecte?.montantCollecte || 0), 0
    );
  }
  get tauxApprobation(): number {
    const ok = this.projets.filter(p =>
      ['APPROUVE','EN_COURS','EN_FINANCEMENT','TERMINE']
        .includes(p.statut || '')
    ).length;
    return this.projets.length
      ? Math.round((ok / this.projets.length) * 100) : 0;
  }

  getProgress(projet: Projet): number {
    if (!projet.collecte?.montantCible || !projet.collecte.montantCollecte)
      return 0;
    return Math.min(
      Math.round((projet.collecte.montantCollecte
        / projet.collecte.montantCible) * 100), 100
    );
  }

  statutLabel(s: string): string {
    const m: any = {
      EN_VOTE: 'En vote', APPROUVE: 'Approuvé',
      EN_FINANCEMENT: 'Financement', EN_COURS: 'En cours',
      TERMINE: 'Terminé', REJETE: 'Rejeté'
    };
    return m[s] || s;
  }

  private toast(msg: string): void {
    this.toastMsg  = msg;
    this.showToast = true;
    setTimeout(() => this.showToast = false, 2500);
  }
  searchQuery = '';

onSearch(): void {
  if (!this.searchQuery.trim()) {
    this.applyFilter();
    return;
  }
  const q = this.searchQuery.toLowerCase();
  const base = this.activeFilter === 'TOUS'
    ? this.projets
    : this.projets.filter(p => p.statut === this.activeFilter);
  this.filtered = base.filter(p =>
    p.titre?.toLowerCase().includes(q) ||
    p.categorie?.toLowerCase().includes(q) ||
    p.location?.toLowerCase().includes(q) ||
    p.adminNom?.toLowerCase().includes(q)
  );
}

clearSearch(): void {
  this.searchQuery = '';
  this.applyFilter();
}
goStats(): void { this.router.navigate(['/admin/projets/stats']); }
}