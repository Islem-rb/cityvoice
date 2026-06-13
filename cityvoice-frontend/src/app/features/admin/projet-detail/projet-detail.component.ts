import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProjetService } from '../../projet/projet.service';
import { Projet } from '../../projet/projet.model';
import html2canvas from 'html2canvas';

@Component({
  selector: 'app-projet-detail',
  templateUrl: './projet-detail.component.html',
  styleUrls: ['./projet-detail.component.css']
})
export class ProjetDetailComponent implements OnInit {

  projet: Projet | null = null;
  loading = true;

  constructor(
    private projetService: ProjetService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.projetService.getById(id).subscribe({
      next: (p) => { this.projet = p; this.loading = false; },
      error: ()  => { this.loading = false; }
    });
  }

  getProgress(): number {
    if (!this.projet?.collecte?.montantCible
        || !this.projet.collecte.montantCollecte) return 0;
    return Math.min(
      Math.round((this.projet.collecte.montantCollecte
        / this.projet.collecte.montantCible) * 100), 100
    );
  }

  getPourPct(): number {
    if (!this.projet?.totalVotes) return 50;
    return Math.round(
      ((this.projet.votePour || 0) / this.projet.totalVotes) * 100
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

  goBack(): void { this.router.navigate(['/admin/projets']); }
  goEdit(): void {
    this.router.navigate(['/admin/projets', this.projet?.id, 'edit']);
  }
  exportAsPng(): void {
  const el = document.querySelector('.detail-container') as HTMLElement;
  if (!el) return;
  html2canvas(el, { scale: 2, useCORS: true }).then(canvas => {
    const link = document.createElement('a');
    link.download = `projet-${this.projet?.titre || 'export'}.png`;
    link.href = canvas.toDataURL('image/png');
    link.click();
  });
}
}