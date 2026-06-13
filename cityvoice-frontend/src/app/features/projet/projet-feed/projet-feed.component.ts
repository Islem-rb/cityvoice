import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ProjetService } from '../projet.service';
import { AuthService } from '../../../core/services/auth.service';
import { Projet, Paiement, MethodePaiement } from '../projet.model';
import { SoundService } from '../../../core/services/sound.service';
import { LangService } from '../../../core/services/lang.service';

@Component({
  selector: 'app-projet-feed',
  templateUrl: './projet-feed.component.html',
  styleUrls: ['./projet-feed.component.css']
})
export class ProjetFeedComponent implements OnInit {

  projets:  Projet[] = [];
  filtered: Projet[] = [];
  paginated: Projet[] = [];
donatePhone:  { [id: number]: string }  = {};
customAmount: { [id: number]: number | null } = {};

stripeLoading: { [id: number]: boolean }       = {};
konnectLoading: { [id: number]: boolean } = {};
  loading        = true;
  activeFilter   = 'TOUS';
  activeLocation = '';
  searchQuery    = '';
  layout: 'grid' | 'list' = 'grid';
  pageSize       = 6;
  currentPage    = 1;
  pageSizeOptions = [3, 6, 9, 12];

  openDonateId: number | null = null;
  selectedAmounts: { [id: number]: number }  = {};
  donateEmail:     { [id: number]: string }  = {};
  anonymous:       { [id: number]: boolean } = {};
  votedIds:        Set<number>               = new Set();
  toastMsg   = '';
  showToast  = false;
  amountsList = [5, 10, 25, 50, 100];

  filters = ['TOUS','EN_VOTE','EN_COURS','EN_FINANCEMENT','TERMINE'];

  gouvernorats = [
    'Ariana','Béja','Ben Arous','Bizerte','Gabès',
    'Gafsa','Jendouba','Kairouan','Kasserine','Kébili',
    'Le Kef','Mahdia','La Manouba','Médenine','Monastir',
    'Nabeul','Sfax','Sidi Bouzid','Siliana','Sousse',
    'Tataouine','Tozeur','Tunis','Zaghouan'
  ];

  categories = [
    { key: 'Infrastructure', color: '#185FA5', bg: '#E6F1FB' },
    { key: 'Espaces verts',  color: '#3B6D11', bg: '#EAF3DE' },
    { key: 'Culture',        color: '#3C3489', bg: '#EEEDFE' },
    { key: 'Mobilité',       color: '#854F0B', bg: '#FAEEDA' },
    { key: 'Autre',          color: '#5F5E5A', bg: '#F1EFE8' },
  ];

  constructor(
    private projetService: ProjetService,
    public  authService: AuthService,
    public  sound:         SoundService,
     public  lang:          LangService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const stored = localStorage.getItem('voted_projets');
    if (stored) this.votedIds = new Set(JSON.parse(stored));
    const savedLayout = localStorage.getItem('feed_layout') as 'grid'|'list';
    if (savedLayout) this.layout = savedLayout;
    const savedSize = localStorage.getItem('feed_pagesize');
    if (savedSize) this.pageSize = Number(savedSize);
    this.load();
  }

  load(): void {
    this.loading = true;
    this.projetService.getAll().subscribe({
      next: (data) => {
        this.projets = data;
        this.applyFilters();
        this.loading = false;
      },
      error: () => { this.loading = false; this.toast('Erreur chargement'); }
    });
  }

  applyFilters(): void {
    let result = [...this.projets];

    if (this.activeFilter !== 'TOUS')
      result = result.filter(p => p.statut === this.activeFilter);

    if (this.activeLocation)
      result = result.filter(p =>
        p.location?.toLowerCase() === this.activeLocation.toLowerCase()
      );

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(p =>
        p.titre?.toLowerCase().includes(q) ||
        p.description?.toLowerCase().includes(q) ||
        p.categorie?.toLowerCase().includes(q) ||
        p.tags?.toLowerCase().includes(q) ||
        p.location?.toLowerCase().includes(q)
      );
    }

    this.filtered    = result;
    this.currentPage = 1;
    this.paginate();
  }

  paginate(): void {
    const start = (this.currentPage - 1) * this.pageSize;
    this.paginated = this.filtered.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.filtered.length / this.pageSize);
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  goPage(p: number): void {
    if (p < 1 || p > this.totalPages) return;
    this.currentPage = p;
    this.paginate();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  setPageSize(n: number): void {
    this.pageSize    = n;
    this.currentPage = 1;
    localStorage.setItem('feed_pagesize', String(n));
    this.paginate();
  }

  setLayout(l: 'grid' | 'list'): void {
    this.layout = l;
    localStorage.setItem('feed_layout', l);
  }

  setFilter(f: string): void   { this.activeFilter = f; this.applyFilters(); }
  setLocation(l: string): void { this.activeLocation = l; this.applyFilters(); }
  onSearch(): void              { this.applyFilters(); }

  clearFilters(): void {
    this.activeFilter = 'TOUS'; this.activeLocation = '';
    this.searchQuery  = ''; this.applyFilters();
  }

  get hasActiveFilters(): boolean {
    return this.activeFilter !== 'TOUS' || !!this.activeLocation || !!this.searchQuery.trim();
  }

  hasVoted(id: number): boolean { return this.votedIds.has(id); }

  vote(projet: Projet, valeur: boolean): void {
    if (!projet.id) return;
    if (this.authService.isAdmin()) { this.toast('Les admins ne votent pas'); return; }
    if (this.hasVoted(projet.id))   { this.toast('Déjà voté !'); return; }
    const userId = this.authService.getUserId();
    this.projetService.vote(projet.id, { userId, valeur }).subscribe({
      next: (updated) => {
        this.votedIds.add(projet.id!);
        localStorage.setItem('voted_projets', JSON.stringify([...this.votedIds]));
        const idx = this.projets.findIndex(p => p.id === projet.id);
        if (idx > -1) {
          this.projets[idx].votePour   = updated.votePour;
          this.projets[idx].voteContre = updated.voteContre;
          this.projets[idx].totalVotes = updated.totalVotes;
        }
        this.applyFilters();
        this.toast(valeur ? 'Vote Pour ✓' : 'Vote Contre ✗');
      },
      error: () => this.toast('Erreur lors du vote')
    });
  }

  toggleDonate(id: number): void {
    this.openDonateId = this.openDonateId === id ? null : id;
  }

  selectAmount(id: number, amt: number): void { this.selectedAmounts[id] = amt; }

  confirmDonate(projet: Projet): void {
    if (!projet.id || !projet.collecte?.id) return;
    const montant = this.selectedAmounts[projet.id];
    if (!montant) { this.toast('Choisissez un montant'); return; }
    const p: Paiement = {
      userId: this.authService.getUserId(), montant,
      anonymous: this.anonymous[projet.id] || false,
      email: this.donateEmail[projet.id] || '',
      methode: 'ESPECES' as MethodePaiement,
    };
    this.projetService.pay(projet.collecte.id!, p).subscribe({
      next: () => { this.openDonateId = null; this.toast(`Don de ${montant} DT !`); this.load(); },
      error: () => this.toast('Erreur paiement')
    });
  }

  getCategoryStyle(cat: string) {
    const f = this.categories.find(c => c.key === cat);
    return f ? { color: f.color, background: f.bg } : { color:'#5F5E5A', background:'#F1EFE8' };
  }

  getProgress(p: Projet): number {
    if (!p.collecte?.montantCible || !p.collecte.montantCollecte) return 0;
    return Math.min(Math.round((p.collecte.montantCollecte / p.collecte.montantCible) * 100), 100);
  }

  getPourPct(p: Projet): number {
    if (!p.totalVotes) return 50;
    return Math.round(((p.votePour || 0) / p.totalVotes) * 100);
  }

  isAdmin():     boolean { return this.authService.isAdmin(); }
  goCreate():    void    { this.router.navigate(['/admin/projets/create']); }
  goDashboard(): void    { this.router.navigate(['/admin/projets']); }

  private toast(msg: string): void {
    this.toastMsg = msg; this.showToast = true;
    setTimeout(() => this.showToast = false, 2800);
  }
payStripe(projet: Projet): void {
  if (!projet.id || !projet.collecte?.id) return;

  const montant = this.customAmount[projet.id]
               || this.selectedAmounts[projet.id];

  if (!montant || montant <= 0) {
    this.toast('Veuillez choisir ou saisir un montant');
    return;
  }

  this.stripeLoading[projet.id] = true;

  const payload = {
    collecteId:  projet.collecte.id,
    montant,
    userId:      this.authService.getUserId(),
    email:       this.donateEmail[projet.id]  || '',
    phone:       this.donatePhone[projet.id]  || '',
    anonymous:   this.anonymous[projet.id]    || false,
    description: projet.titre || 'Projet urbain',
  };

  this.projetService.initiateStripe(payload).subscribe({
    next: (res) => {
            console.log('Stripe initiate response:', res);

      localStorage.setItem('stripe_paiement_id', res.paiementId);
      localStorage.setItem('stripe_session_id',  res.sessionId);
      console.log('Saved to localStorage:', {
        stripe_paiement_id: res.paiementId,
        stripe_session_id:  res.sessionId
      });
      setTimeout(() => {
        window.location.href = res.checkoutUrl;
      }, 100);
    
    },
    error: () => {
      this.stripeLoading[projet.id!] = false;
      this.toast('Erreur lors du paiement');
    }
  });
}

blockInvalidChar(e: KeyboardEvent): void {
  if (['e','E','+','-'].includes(e.key)) e.preventDefault();
}

getPointsPreview(montant: number): number {
  if (montant >= 1000) return 100;
  if (montant >= 500)  return 50;
  if (montant >= 100)  return 20;
  return 5;
}
isDonatable(p: Projet): boolean {
  return ['APPROUVE', 'EN_FINANCEMENT', 'EN_COURS'].includes(p.statut || '')
      && p.collecte?.statut !== 'OBJECTIF_ATTEINT';
}

getFilterLabel(f: string): string {
  const isFr = this.lang.current === 'fr';
  const map: any = {
    TOUS:           isFr ? 'Tous'        : 'All',
    EN_VOTE:        isFr ? 'En vote'     : 'In vote',
    EN_COURS:       isFr ? 'En cours'    : 'Ongoing',
    EN_FINANCEMENT: isFr ? 'Financement' : 'Funding',
    TERMINE:        isFr ? 'Terminés'    : 'Completed',
  };
  return map[f] || f;
}

getStatutLabel(s: string): string {
  const isFr = this.lang.current === 'fr';
  const map: any = {
    EN_VOTE:        isFr ? 'En vote'     : 'In vote',
    APPROUVE:       isFr ? 'Approuvé'    : 'Approved',
    EN_FINANCEMENT: isFr ? 'Financement' : 'Funding',
    EN_COURS:       isFr ? 'En cours'    : 'Ongoing',
    TERMINE:        isFr ? 'Terminé'     : 'Completed',
    REJETE:         isFr ? 'Rejeté'      : 'Rejected',
  };
  return map[s] || s;
}

payKonnect(projet: Projet): void {
  if (!projet.id || !projet.collecte?.id) return;
  const montant = this.customAmount[projet.id] || this.selectedAmounts[projet.id];
  if (!montant || montant <= 0) { this.toast('Veuillez choisir un montant'); return; }

  this.konnectLoading[projet.id] = true;  

  const payload = {
    collecteId:  projet.collecte.id,
    montant,
    userId:      this.authService.getUserId(),
    phone:       this.donatePhone[projet.id]  || '',
    anonymous:   this.anonymous[projet.id]    || false,
    description: projet.titre || 'Projet urbain',
  };

  this.projetService.initiateKonnect(payload).subscribe({
    next: (res) => {
      localStorage.setItem('konnect_paiement_id', res.paiementId);
      localStorage.setItem('konnect_payment_ref',  res.paymentRef);
      window.location.href = res.checkoutUrl;
    },
    error: () => {
      this.konnectLoading[projet.id!] = false;
      this.toast('Erreur Konnect');
    }
  });
}
get approvedCount(): number {
  return this.projets.filter(p =>
    ['APPROUVE','EN_FINANCEMENT','EN_COURS'].includes(p.statut || '')
  ).length;
}
}