import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProjetService } from '../projet.service';

@Component({
  selector: 'app-payment-success',
  templateUrl: './payment-success.component.html',
  styleUrls: ['./payment-success.component.css']
})
export class PaymentSuccessComponent implements OnInit {

  verifying = true;
  success   = false;
  montant   = 0;
  points    = 0;
  error     = false;
  method    = '';

  constructor(
    private route:         ActivatedRoute,
    private router:        Router,
    private projetService: ProjetService
  ) {}
/*
  ngOnInit(): void {
    const params = this.route.snapshot.queryParams;

    console.log('URL params:', params);
    console.log('localStorage:', {
      stripe_paiement_id: localStorage.getItem('stripe_paiement_id'),
      stripe_session_id:  localStorage.getItem('stripe_session_id'),
      konnect_paiement_id: localStorage.getItem('konnect_paiement_id'),
      konnect_payment_ref: localStorage.getItem('konnect_payment_ref'),
    });

    // ── Get IDs — URL params take priority over localStorage ──
    const paiementId = params['paiement']
                    || localStorage.getItem('stripe_paiement_id')
                    || localStorage.getItem('konnect_paiement_id')
                    || '';

    const sessionId  = params['session_id']
                    || localStorage.getItem('stripe_session_id')
                    || '';

    const payRef     = params['payment_ref']
                    || localStorage.getItem('konnect_payment_ref')
                    || '';

    console.log('Resolved:', { paiementId, sessionId, payRef });

    if (!paiementId) {
      console.error('No paiementId found');
      this.verifying = false;
      this.error     = true;
      return;
    }

    // ── Decide: Konnect or Stripe ──────────────────────────
    const isKonnect = !!payRef
                   || !!localStorage.getItem('konnect_paiement_id');
    const isStripe  = !!sessionId
                   && !isKonnect;

    console.log('Method detected:', isKonnect ? 'KONNECT' : isStripe ? 'STRIPE' : 'UNKNOWN');

    if (isKonnect) {
      this.method = 'Konnect';
      this.projetService.verifyKonnect({
        paiementId,
        paymentRef: payRef
      }).subscribe({
        next: (res) => {
          console.log('Konnect verify response:', res);
          this.verifying = false;
          this.success   = res.success;
          this.montant   = res.montant || 0;
          this.points    = res.points  || 0;
          if (res.success) {
            localStorage.removeItem('konnect_paiement_id');
            localStorage.removeItem('konnect_payment_ref');
          }
        },
        error: (err) => {
          console.error('Konnect verify error:', err);
          this.verifying = false;
          this.success   = false;
        }
      });

    } else if (isStripe) {
      this.method = 'Stripe';
      console.log('Calling verifyStripe with:', { paiementId, sessionId });
      this.projetService.verifyStripe({
        paiementId,
        sessionId
      }).subscribe({
        next: (res) => {
          console.log('Stripe verify response:', res);
          this.verifying = false;
          this.success   = res.success;
          this.montant   = res.montant || 0;
          this.points    = (res as any).points || 0;
          if (res.success) {
            localStorage.removeItem('stripe_paiement_id');
            localStorage.removeItem('stripe_session_id');
          }
        },
        error: (err) => {
          console.error('Stripe verify error:', err);
          this.verifying = false;
          this.success   = false;
        }
      });

    } else {
      console.error('No sessionId or payRef — cannot verify');
      this.verifying = false;
      this.error     = true;
    }
  }
*/
ngOnInit(): void {
  const params = this.route.snapshot.queryParams;

  console.log('URL params:', params);

  // ── ALWAYS prefer URL params — they are the source of truth ──
  const sessionId  = params['session_id']  || '';
  const payRef     = params['payment_ref'] || '';
  const paiementId = params['paiement']    || '';

  console.log('Resolved:', { paiementId, sessionId, payRef });

  // Clean ALL leftover localStorage immediately
  localStorage.removeItem('stripe_paiement_id');
  localStorage.removeItem('stripe_session_id');
  localStorage.removeItem('konnect_paiement_id');
  localStorage.removeItem('konnect_payment_ref');

  if (!paiementId) {
    console.error('No paiementId in URL');
    this.verifying = false;
    this.error     = true;
    return;
  }

  // ── Method detection ONLY from URL params ─────────────────
  const isStripe  = !!sessionId && !payRef;
  const isKonnect = !!payRef    && !sessionId;

  console.log('Method:', isStripe ? 'STRIPE' : isKonnect ? 'KONNECT' : 'UNKNOWN');
  console.log('sessionId:', sessionId, 'payRef:', payRef);

  if (isStripe) {
    this.method = 'Stripe';
    this.projetService.verifyStripe({ paiementId, sessionId }).subscribe({
      next: (res) => {
        console.log('Stripe verify response:', res);
        this.verifying = false;
        this.success   = res.success;
        this.montant   = res.montant || 0;
        this.points    = (res as any).points || 0;
      },
      error: (err) => {
        console.error('Stripe verify error:', err);
        this.verifying = false;
        this.success   = false;
      }
    });

  } else if (isKonnect) {
    this.method = 'Konnect';
    this.projetService.verifyKonnect({ paiementId, paymentRef: payRef }).subscribe({
      next: (res) => {
        console.log('Konnect verify response:', res);
        this.verifying = false;
        this.success   = res.success;
        this.montant   = res.montant || 0;
        this.points    = res.points  || 0;
      },
      error: (err) => {
        console.error('Konnect verify error:', err);
        this.verifying = false;
        this.success   = false;
      }
    });

  } else {
    console.error('Cannot determine payment method from URL');
    this.verifying = false;
    this.error     = true;
  }
}
  goHome(): void { this.router.navigate(['/projets']); }
}