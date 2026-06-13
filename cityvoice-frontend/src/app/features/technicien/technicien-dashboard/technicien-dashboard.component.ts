import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { ResourceService } from '../../resource/resource.service';
import { HttpClient } from '@angular/common/http';
import { DemandeMaintenanceService } from '../../demande-maintenance/demande-maintenance.service';
import { MaintenanceLogService } from '../../maintenance-log/maintenance-log.service'; // À ajouter


@Component({
  selector: 'app-technicien-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './technicien-dashboard.component.html',
  styleUrls: ['./technicien-dashboard.component.css']
})
export class TechnicienDashboardComponent implements OnInit, OnDestroy {

  interventions: any[] = [];
  interventionSelectionnee: any = null;
  suiviId: number | null = null;
  
  // Temps
  tempsAujourdhui: number = 0;
  tempsTotal: number = 0;
  tempsTotalCumule: number = 0;
  montantEstime: number = 0;
  primeEstimee: number = 0;      // 🔥 NOUVEAU : prime selon type d'intervention
  montantTotalAvecPrime: number = 0;  // 🔥 NOUVEAU : montant total + prime
  statutActuel: string = '';
  modeTest: boolean = true;

  // 🔥 Primes par type d'intervention
  primesParType: { [key: string]: number } = {
    'Préventive': 0,
    'Corrective': 50,
    'Curative': 75,
    'Évolutive': 30
  };

  // Timer
  private timerInterval: any;
  private debutTemps: number = 0;
  private tempsAccumule: number = 0;
  private tauxHoraire: number = 25;

  statuts = [
    { code: 'EN_SERVICE', label: '🟢 EN SERVICE', paye: true },
    { code: 'DEJEUNER', label: '🍽️ DÉJEUNER', paye: false },
    { code: 'PAUSE_WC', label: '🚽 PAUSE', paye: true },
    { code: 'REUNION', label: '📋 RÉUNION', paye: true }
  ];
// Dans technicien-dashboard.component.ts
private apiUrl = 'http://localhost:8085/api';
  constructor(
    private authService: AuthService,
    public resourceService: ResourceService,  private http: HttpClient ,  private demandeService: DemandeMaintenanceService,  private maintenanceService: MaintenanceLogService  // ← AJOUTER CETTE LIGNE
  // ← AJOUTER
 // ← AJOUTER

  ) {}

  ngOnInit(): void {
    this.chargerInterventions();
  }

  ngOnDestroy(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
  }

 // technicien-dashboard.component.ts
// Vérifiez que cette méthode existe et est appelée

chargerInterventions(): void {
  const user = this.authService.getCurrentUser();
  const technicienId = user?.userId;
  
  console.log('👨‍🔧 Chargement interventions pour technicien:', technicienId);
  
  if (!technicienId) {
    console.log('❌ Aucun technicien connecté');
    this.interventions = [];
    return;
  }
  
  // 🔥 Lire depuis localStorage (c'est là que l'expert a stocké)
  const interventionsKey = `interventions_${technicienId}`;
  const stored = localStorage.getItem(interventionsKey);
  
  if (stored) {
    this.interventions = JSON.parse(stored);
    console.log(`📋 ${this.interventions.length} intervention(s) chargées depuis localStorage`);
    
    // Afficher les détails
    this.interventions.forEach((i, idx) => {
      console.log(`  ${idx + 1}. ${i.ressourceNom} - ${i.typeIntervention} - Statut: ${i.statut}`);
    });
  } else {
    console.log('📭 Aucune intervention trouvée dans localStorage');
    this.interventions = [];
  }
  
  // Optionnel: aussi vérifier l'API
  this.http.get(`http://localhost:8085/api/demandes-maintenance/technicien/${technicienId}`).subscribe({
    next: (data: any) => {
      if (data && data.length > 0) {
        console.log('📋 Demandes assignées reçues de l\'API:', data.length);
        // Fusionner ou remplacer selon votre besoin
      }
    },
    error: (err: any) => {
      console.log('ℹ️ API non disponible, utilisation localStorage uniquement');
    }
  });
}
sauvegarderInterventions(): void {
  const user = this.authService.getCurrentUser();
  const technicienId = user?.userId;
  const storageKey = `interventions_${technicienId}`;
  localStorage.setItem(storageKey, JSON.stringify(this.interventions));
}

  selectionnerIntervention(intervention: any): void {
    this.interventionSelectionnee = intervention;
    // 🔥 Calculer la prime en fonction du type d'intervention
    const typeIntervention = intervention.typeIntervention || 'Corrective';
    this.primeEstimee = this.primesParType[typeIntervention] || 0;
  }

  demarrerIntervention(): void {
    if (!this.interventionSelectionnee) return;
    
    console.log('🚀 Démarrer intervention');
    
    this.suiviId = Date.now();
    this.statutActuel = 'EN_SERVICE';
    this.tempsAccumule = 0;
    this.debutTemps = Date.now();
    this.tempsAujourdhui = 0;
    this.tempsTotal = 0;
    this.montantEstime = 0;
    
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
    this.timerInterval = setInterval(() => {
      this.mettreAJourCompteur();
    }, 1000);
    
    alert('✅ Intervention démarrée !');
  }

  mettreAJourCompteur(): void {
    if (this.statutActuel === 'EN_SERVICE' && this.debutTemps > 0) {
      const maintenant = Date.now();
      const tempsEcoule = Math.floor((maintenant - this.debutTemps) / 1000);
      this.tempsAujourdhui = this.tempsAccumule + tempsEcoule;
      this.tempsTotal = this.tempsAujourdhui;
      
      // Calcul du montant horaire
      const totalFinal = this.tempsTotalCumule + this.tempsAujourdhui;
      this.montantEstime = Math.round((totalFinal / 3600) * this.tauxHoraire);
      
      // 🔥 Calcul du montant total AVEC prime
      this.montantTotalAvecPrime = this.montantEstime + this.primeEstimee;
    }
  }

  changerStatut(statutCode: string): void {
    if (!this.suiviId) return;
    
    console.log(`📋 Changement de statut: ${statutCode}`);
    
    if (this.statutActuel === 'EN_SERVICE' && this.debutTemps > 0) {
      const maintenant = Date.now();
      const tempsEcoule = Math.floor((maintenant - this.debutTemps) / 1000);
      this.tempsAccumule += tempsEcoule;
      this.tempsAujourdhui = this.tempsAccumule;
      this.tempsTotal = this.tempsAujourdhui;
    }
    
    this.statutActuel = statutCode;
    
    if (statutCode === 'EN_SERVICE') {
      this.debutTemps = Date.now();
    }
    
    alert(`✅ Statut changé : ${statutCode}`);
  }

  mettreHorsLigne(): void {
    if (!this.suiviId) return;
    
    if (confirm('Mettre fin à la journée ? Vous pourrez continuer demain.')) {
      if (this.statutActuel === 'EN_SERVICE' && this.debutTemps > 0) {
        const maintenant = Date.now();
        const tempsEcoule = Math.floor((maintenant - this.debutTemps) / 1000);
        this.tempsAccumule += tempsEcoule;
        this.tempsAujourdhui = this.tempsAccumule;
      }
      
      const tempsTotalFinal = this.tempsTotalCumule + this.tempsAujourdhui;
      const montantHoraire = Math.round((tempsTotalFinal / 3600) * this.tauxHoraire);
      const montantTotal = montantHoraire + this.primeEstimee;
      
      if (this.timerInterval) {
        clearInterval(this.timerInterval);
        this.timerInterval = null;
      }
      
      alert(`✅ Fin de journée enregistrée !
📅 Temps aujourd'hui: ${this.formaterTemps(this.tempsAujourdhui)}
📊 Temps total cumulé: ${this.formaterTemps(tempsTotalFinal)}
💰 Montant horaire: ${montantHoraire}€
🏆 Prime (${this.getTypeInterventionLabel()}): +${this.primeEstimee}€
💵 TOTAL: ${montantTotal}€`);
      
      this.tempsTotalCumule += this.tempsAujourdhui;
      
      this.statutActuel = '';
      this.suiviId = null;
      this.interventionSelectionnee = null;
      this.debutTemps = 0;
      this.tempsAccumule = 0;
      this.tempsAujourdhui = 0;
      this.tempsTotal = 0;
    }
  }

  // technicien-dashboard.component.ts
// Modifiez votre méthode terminerIntervention() :

// technicien-dashboard.component.ts
// Version utilisant l'endpoint existant

// technicien-dashboard.component.ts
// Version complète qui fonctionne sans l'endpoint /terminer

// Version ultra-simplifiée - juste localStorage
terminerIntervention(): void {
  if (!this.suiviId) return;
  
  if (confirm('Terminer cette intervention ?')) {
    
    // Calcul du temps
    if (this.statutActuel === 'EN_SERVICE' && this.debutTemps > 0) {
      const maintenant = Date.now();
      const tempsEcoule = Math.floor((maintenant - this.debutTemps) / 1000);
      this.tempsAccumule += tempsEcoule;
      this.tempsAujourdhui = this.tempsAccumule;
    }
    
    const tempsTotalFinal = this.tempsTotalCumule + this.tempsAujourdhui;
    const user = this.authService.getCurrentUser();
    
    // 🔥 Supprimer du localStorage du technicien
    const interventionsKey = `interventions_${user?.userId}`;
    const stored = JSON.parse(localStorage.getItem(interventionsKey) || '[]');
    const updated = stored.filter((i: any) => i.id !== this.interventionSelectionnee.id);
    localStorage.setItem(interventionsKey, JSON.stringify(updated));
    
    // 🔥 Ajouter à l'historique de l'expert
    const historiqueKey = `interventions_terminees_expert`;
    const historique = JSON.parse(localStorage.getItem(historiqueKey) || '[]');
    historique.push({
      id: Date.now(),
      demandeId: this.interventionSelectionnee.id,
      maintenanceId: this.interventionSelectionnee.maintenanceId || this.interventionSelectionnee.id,
      ressourceNom: this.interventionSelectionnee.ressourceNom,
      typeIntervention: this.interventionSelectionnee.typeIntervention,
      motif: this.interventionSelectionnee.motif,
      dateFin: new Date().toISOString(),
      tempsTotal: tempsTotalFinal,
      technicienId: user?.userId,
      statut: 'TERMINEE'
    });
    localStorage.setItem(historiqueKey, JSON.stringify(historique));
    
    
    
    this.resetApresTerminer();
  }
}

// 🔥 NOUVELLE MÉTHODE: Ajouter à l'historique de l'expert
ajouterAHistoriqueExpert(intervention: any, maintenanceId: number, tempsTotal: number, technicienId: string | undefined): void {
  const historiqueKey = `interventions_terminees_expert`;
  let historique = JSON.parse(localStorage.getItem(historiqueKey) || '[]');
  
  // Vérifier si déjà présent
  const existe = historique.some((h: any) => h.demandeId === intervention.id);
  if (existe) {
    console.log('⚠️ Intervention déjà dans l\'historique');
    return;
  }
  
  const nouvelleIntervention = {
    id: Date.now(),
    demandeId: intervention.id,
    maintenanceId: maintenanceId,
    ressourceId: intervention.ressourceId,
    ressourceNom: intervention.ressourceNom,
    ressourceMatricule: intervention.ressourceMatricule,
    typeIntervention: intervention.typeIntervention,
    motif: intervention.motif,
    dateDebut: intervention.dateDebut || intervention.dateDemande,
    dateFin: new Date().toISOString(),
    tempsTotal: tempsTotal,
    dureeHeures: (tempsTotal / 3600).toFixed(2),
    technicienId: technicienId,
    statut: 'TERMINEE'
  };
  
  historique.push(nouvelleIntervention);
  localStorage.setItem(historiqueKey, JSON.stringify(historique));
  
  console.log('✅ Intervention ajoutée à l\'historique expert:', nouvelleIntervention);
  
  // 🔥 Notifier l'expert (optionnel)
}



// Assurez-vous que resetApresTerminer existe
resetApresTerminer(): void {
  if (this.timerInterval) {
    clearInterval(this.timerInterval);
    this.timerInterval = null;
  }
  
  this.statutActuel = '';
  this.suiviId = null;
  this.interventionSelectionnee = null;
  this.debutTemps = 0;
  this.tempsAccumule = 0;
  this.tempsAujourdhui = 0;
  this.tempsTotal = 0;
  this.tempsTotalCumule = 0;
  this.montantEstime = 0;
  this.primeEstimee = 0;
  this.montantTotalAvecPrime = 0;
  
  // Recharger la liste
  this.chargerInterventions();
}

  getTypeInterventionLabel(): string {
    return this.interventionSelectionnee?.typeIntervention || 'Corrective';
  }

  formaterTemps(secondes: number): string {
    if (!secondes && secondes !== 0) return '0h00m00s';
    const heures = Math.floor(secondes / 3600);
    const minutes = Math.floor((secondes % 3600) / 60);
    const secs = secondes % 60;
    return `${heures}h${minutes.toString().padStart(2, '0')}m${secs.toString().padStart(2, '0')}s`;
  }
}