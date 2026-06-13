import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-payment-success',
  standalone: true,
  template: `
    <div style="text-align:center; padding:50px">
      <div style="font-size:64px">✅</div>
      <h1>Paiement réussi !</h1>
      <p>Redirection vers le tableau de bord...</p>
    </div>
  `
})
export class PaymentSuccessComponent implements OnInit {
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const redirectStatus = params['redirect_status'];
      const factureId = params['factureId'];  // ← Récupère l'ID

      if (redirectStatus === 'succeeded') {
        console.log('✅ Paiement réussi');
        this.marquerFacturePayee(factureId);
        // 🔥 Rediriger vers /resource au lieu de /chef/factures
        setTimeout(() => {
          window.location.href = '/resource';  // ← Changement ici
          // OU avec router : this.router.navigate(['/resource']);
        }, 2000);
      }
    });
  }




  // 🔥 AJOUTE CETTE MÉTHODE
  marquerFacturePayee(factureId: number): void {
    console.log('📞 Mise à jour facture:', factureId);
    
    this.http.put(`http://localhost:8085/api/factures/${factureId}/payer`, {}).subscribe({
      next: () => {
        console.log('✅ Facture marquée payée');
        setTimeout(() => this.router.navigate(['/resource']), 1500);
      },
      error: (err) => {
        console.error('❌ Erreur:', err);
        setTimeout(() => this.router.navigate(['/resource']), 1500);
      }
    });
  }
}