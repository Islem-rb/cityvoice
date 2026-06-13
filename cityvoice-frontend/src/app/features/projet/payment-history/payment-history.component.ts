import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ProjetService } from '../projet.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-payment-history',
  templateUrl: './payment-history.component.html',
  styleUrls:  ['./payment-history.component.css']
})
export class PaymentHistoryComponent implements OnInit {

  payments: any[] = [];
  loading = true;

  constructor(
    private projetService: ProjetService,
    private authService:   AuthService,
    private router:        Router
  ) {}

  ngOnInit(): void {
  const userId = this.authService.getUserId();
  console.log('Looking for history with userId:', userId); 
  
  this.projetService.getPaymentHistory(userId).subscribe({
    next: (data) => {
      console.log('Payments found:', data); 
      this.payments = data;
      this.loading = false;
    },
    error: (err) => {
      console.error('History error:', err); 
      this.loading = false;
    }
  });
}

  get confirmedPayments(): any[] {
    return this.payments.filter(p => p.statut === 'CONFIRME');
  }

  get totalDonne(): number {
    return this.confirmedPayments.reduce((s, p) => s + p.montant, 0);
  }

  get totalPoints(): number {
    return this.confirmedPayments.reduce(
      (s, p) => s + this.calculatePoints(p.montant), 0
    );
  }

  calculatePoints(montant: number): number {
    if (montant >= 1000) return 100;
    if (montant >= 500)  return 50;
    if (montant >= 100)  return 20;
    return 5;
  }

  statutLabel(s: string): string {
    const m: any = {
      CONFIRME:   'Confirmé',
      EN_ATTENTE: 'En attente',
      ECHOUE:     'Échoué',
      REMBOURSE:  'Remboursé'
    };
    return m[s] || s;
  }
 isStripe(p: any):  boolean { return p.reference?.startsWith('STRIPE-'); }
isKonnect(p: any): boolean { return p.reference?.startsWith('KONNECT-'); }


getMethodLabel(p: any): string {
    if (this.isStripe(p))  return 'Stripe';
    if (this.isKonnect(p)) return 'Konnect';
    return 'Autre';
  }

  getStatutLabel(s: string): string {
    const m: any = {
      CONFIRME:   'Confirmé',
      EN_ATTENTE: 'En attente',
      ECHOUE:     'Échoué',
      REMBOURSE:  'Remboursé',
    };
    return m[s] || s;
  }


    goBack(): void { this.router.navigate(['/projets']); }

      getPoints(montant: number): number {
    if (montant >= 1000) return 100;
    if (montant >= 500)  return 50;
    if (montant >= 100)  return 20;
    return 5;
  }
}