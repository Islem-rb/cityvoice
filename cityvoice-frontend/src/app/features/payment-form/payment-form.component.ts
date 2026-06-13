import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { loadStripe, Stripe, StripeElements } from '@stripe/stripe-js';

@Component({
  selector: 'app-payment-form',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding:20px">
      <button (click)="goBack()" style="background:none; border:none; cursor:pointer">← Retour</button>

      <div style="background:white; border-radius:16px; padding:20px; margin-top:20px">
        <h2>Paiement {{ montant }}€</h2>
        
        <div id="payment-element-container" style="min-height:300px; border:1px solid #ccc; margin:20px 0; padding:10px"></div>
        
        <button (click)="handleSubmit()" [disabled]="isLoading" style="width:100%; background:#0D9B76; color:white; padding:14px; border:none; border-radius:8px">
          {{ isLoading ? 'Traitement...' : 'Payer ' + montant + '€' }}
        </button>
      </div>

      <div *ngIf="error" style="color:red; margin-top:20px">{{ error }}</div>
    </div>
  `
})
export class PaymentFormComponent implements OnInit {
  factureId!: number;
  montant!: number;
  stripe: Stripe | null = null;
  elements: StripeElements | null = null;
  isLoading = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.factureId = +params['factureId'];
      this.montant = +params['montant'];
      this.initializePayment();
    });
  }

  async initializePayment() {
    try {
      console.log('1. Appel API...');
      const response = await this.http.post<{clientSecret: string}>(
        `http://localhost:8085/api/factures/${this.factureId}/create-payment-intent`,
        {}
      ).toPromise();
      
      console.log('2. clientSecret reçu:', response?.clientSecret);
      
      if (!response?.clientSecret) {
        throw new Error('Pas de clientSecret');
      }
      
      console.log('3. Chargement Stripe...');
      const stripe = await loadStripe('pk_test_51TPT33CEuWTXIfk1Fp3aJtl63WFFJoZkkeTbCb8LSxkIffsF6YuKle9nwSMSLVuRUHYEjYjUF2D1XPSMEwWlLK6z00VXSPaNxU');
      
      if (!stripe) {
        throw new Error('Stripe non chargé');
      }
      
      console.log('4. Stripe chargé');
      this.stripe = stripe;
      
      // Attendre que le DOM soit prêt
      setTimeout(() => {
        const container = document.getElementById('payment-element-container');
        console.log('5. Conteneur:', container);
        
        if (container) {
          const elements = stripe.elements({ clientSecret: response.clientSecret });
          const paymentElement = elements.create('payment');
          paymentElement.mount(container);
          this.elements = elements;
          console.log('6. SUCCÈS !');
        } else {
          this.error = 'Conteneur introuvable';
        }
      }, 500);
      
    } catch (error) {
      console.error('ERREUR:', error);
      this.error = error instanceof Error ? error.message : 'Erreur inconnue';
    }
  }

  async handleSubmit() {
    if (!this.stripe || !this.elements) {
      this.error = 'Paiement non initialisé';
      return;
    }
    
    this.isLoading = true;
    
    const { error } = await this.stripe.confirmPayment({
      elements: this.elements,
      confirmParams: {
      return_url: window.location.origin + '/payment-success?factureId=' + this.factureId
      }
    });
    
    if (error) {
this.error = error.message || 'Erreur lors du paiement';
      this.isLoading = false;
    }
  }

  goBack() {
    window.history.back();
  }
}