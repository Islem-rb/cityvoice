import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ProjetService } from '../../projet/projet.service';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';

@Component({
  selector: 'app-stats',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.css']
})
export class StatsComponent implements OnInit {

  govStats:    any[] = [];
  catStats:    any[] = [];
  topDonators: any[] = [];

  loadingGov  = true;
  loadingCat  = true;
  loadingTop  = true;
  exporting   = false;

  selectedGov    = '';   // filter for top donators
  selectedCatGov = '';   // filter for categories

  gouvernorats = [
    'Ariana','Béja','Ben Arous','Bizerte','Gabès',
    'Gafsa','Jendouba','Kairouan','Kasserine','Kébili',
    'Le Kef','Mahdia','La Manouba','Médenine','Monastir',
    'Nabeul','Sfax','Sidi Bouzid','Siliana','Sousse',
    'Tataouine','Tozeur','Tunis','Zaghouan'
  ];

  catColors: Record<string, string> = {
    'Infrastructure': '#185FA5',
    'Espaces verts':  '#3B6D11',
    'Culture':        '#3C3489',
    'Mobilité':       '#854F0B',
    'Autre':          '#5F5E5A',
  };

  constructor(
    private projetService: ProjetService,
    private router:        Router
  ) {}

  ngOnInit(): void {
    this.loadGov();
    this.loadCat();
    this.loadTop();
  }

  // ── Loaders ──────────────────────────────────────────
  loadGov(): void {
    this.loadingGov = true;
    this.projetService.getStatsByGouvernorat().subscribe({
      next: (d) => { this.govStats = d; this.loadingGov = false; },
      error: (e) => { console.error('Gov stats error:', e); this.loadingGov = false; }
    });
  }

  loadCat(gov?: string): void {
    this.loadingCat = true;
    this.projetService.getStatsByCategory(gov).subscribe({
      next: (d) => { this.catStats = d; this.loadingCat = false; },
      error: (e) => { console.error('Cat stats error:', e); this.loadingCat = false; }
    });
  }

  loadTop(gov?: string): void {
    this.loadingTop = true;
    this.projetService.getTopDonators(gov).subscribe({
      next: (d) => { this.topDonators = d; this.loadingTop = false; },
      error: (e) => { console.error('Top donators error:', e); this.loadingTop = false; }
    });
  }

  // ── Filter change handlers ────────────────────────────
  onCatGovChange(): void {
    this.loadCat(this.selectedCatGov || undefined);
  }

  onGovChange(): void {
    this.loadTop(this.selectedGov || undefined);
  }

  // ── Computed maxes for bar widths ─────────────────────
  get maxGov(): number {
    return Math.max(...this.govStats.map(g => g.count), 1);
  }

  get maxCat(): number {
    return Math.max(...this.catStats.map(c => c.count), 1);
  }

  get maxDon(): number {
    return Math.max(...this.topDonators.map(d => d.total), 1);
  }

  getCatColor(cat: string): string {
    return this.catColors[cat] || '#8888A8';
  }

  // ── Navigation ────────────────────────────────────────
  goBack(): void {
    this.router.navigate(['/admin/projets']);
  }

  // ── Export PDF ────────────────────────────────────────
  exportAsPdf(): void {
    this.exporting = true;
    const el = document.querySelector('.stats-container') as HTMLElement;
    if (!el) { this.exporting = false; return; }

    html2canvas(el, { scale: 2, useCORS: true }).then(canvas => {
      const imgData = canvas.toDataURL('image/png');
      const pdf     = new jsPDF('p', 'mm', 'a4');
      const pageW   = pdf.internal.pageSize.getWidth();
      const pageH   = pdf.internal.pageSize.getHeight();
      const imgW    = pageW;
      const imgH    = (canvas.height * imgW) / canvas.width;

      let remaining = imgH;
      let pageIndex = 0;

      while (remaining > 0) {
        if (pageIndex > 0) pdf.addPage();
        pdf.addImage(
          imgData, 'PNG',
          0, -(pageIndex * pageH),
          imgW, imgH
        );
        remaining -= pageH;
        pageIndex++;
      }

      pdf.save('statistiques-projets.pdf');
      this.exporting = false;
    }).catch(() => {
      this.exporting = false;
    });
  }
}