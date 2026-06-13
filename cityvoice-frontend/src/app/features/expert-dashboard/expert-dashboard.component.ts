import { Component, OnInit } from '@angular/core';
import { DemandeMaintenance, DemandeMaintenanceService } from '../demande-maintenance/demande-maintenance.service';
import { MaintenanceLog, MaintenanceLogService } from '../maintenance-log/maintenance-log.service';
import { Resource, ResourceService } from '../resource/resource.service';
import { AuthService } from '../../core/services/auth.service';
import { HttpClient } from '@angular/common/http';
import { MessageService } from '../message/message.service';
import { IaPredictionService, IAPrediction } from '../ia-prediction.service';
import { FactureService, Facture } from '../facture.service';


@Component({
  selector: 'app-expert-dashboard',
  templateUrl: './expert-dashboard.component.html',
  styleUrls: ['./expert-dashboard.component.css']
})
export class ExpertDashboardComponent implements OnInit {
  resources: Resource[] = [];  // ← AJOUTER CETTE LIGNE

  demandes: DemandeMaintenance[] = [];
  demandesEnAttente: DemandeMaintenance[] = [];
  demandesTerminees: DemandeMaintenance[] = [];
  
  selectedDemande: DemandeMaintenance | null = null;
  showMaintenanceForm: boolean = false;
  showIaDecision: boolean = false;
  
  iaPrediction: any = null;
  accessDenied: boolean = false;



  currentUserId: string = '';
  
  // ========== PROPRIÉTÉS POUR LES MAINTENANCES (DEPUIS resource-list) ==========
  maintenances: MaintenanceLog[] = [];
  maintenanceLogs: { [key: number]: MaintenanceLog[] } = {};
  showMaintenanceListModal: boolean = false;
  selectedResourceForMaintenance: Resource | null = null;
  
  selectedMaintenance: MaintenanceLog = {
    id: 0,
    typeIntervention: '',
    description: '',
    date: new Date().toISOString().slice(0, 16),
    ressourceId: 0
  };
  
  maintenanceErrors: any = {};
  selectedResourceId: number | null = null;
  
  // IA Décision
  showIADecisionModal: boolean = false;
  iaDecisionData: any = null;
  
  // IA Vision
  diagnosticImageFile: File | null = null;
  diagnosticImagePreview: string | ArrayBuffer | null = null;
  diagnosticResultat: any = null;
  maintenanceSelectionnee: any = null;
  showDiagnosticModal: boolean = false;
  
  // Estimation coût
  coutEstimeAuto: number = 0;
  coutFourchette: string = '';
  coutConfiance: string = '';
  
  ressourceDetails: { [key: number]: Resource } = {};




// ========== PROPRIÉTÉS POUR LE CHAT ==========
  showChatModal: boolean = false;
  selectedDemandeForChat: any = null;
  chatMessages: any[] = [];
  newChatMessage: string = '';
  messageInterval: any;


  constructor(
    private demandeService: DemandeMaintenanceService,
    private maintenanceService: MaintenanceLogService,
    public resourceService: ResourceService,
    private authService: AuthService,
    private http: HttpClient,    private messageService: MessageService,private iaPredictionService: IaPredictionService, private factureService: FactureService  // Ajoutez ce service

  ) {}








// Propriété
techniciens: any[] = [];
 ngOnInit(): void {
  console.log('🔧 EXPERT-DASHBOARD INITIALISÉ');
  const user = this.authService.getCurrentUser();
    this.currentUserId = user?.userId || '';  // ← AJOUTEZ CETTE LIGNE

  console.log('👤 Utilisateur:', user);
  
  if (user?.role !== 'EXPERT' && user?.role !== 'MODERATEUR') {
    console.log('❌ Accès refusé');
    this.accessDenied = true;
    return;
  }
  console.log('✅ Chargement des demandes...');
  this.chargerDemandes();
  this.chargerToutesMaintenances();
    this.chargerMaintenancesTerminees();
  this.chargerTechniciens();
  console.log('🔧 Techniciens après chargement:', this.techniciens);

}






chargerTechniciens(): void {
  this.techniciens = [
    { userId: '7521dd66-3ca2-11f1-9674-e8c829f252c9', nom: 'Dupont Jean', email: 'technicien@test.com' }
  ];
  console.log('✅ Techniciens chargés:', this.techniciens);
}






chargerDemandes(): void {
  console.log('📞 Appel API demandes dans expert-dashboard...');
  this.demandeService.getAll().subscribe({
    next: (data) => {
      console.log('📋 Demandes reçues dans expert-dashboard:', data);
      this.demandes = data;
      this.demandesEnAttente = data.filter(d => d.statut === 'EN_ATTENTE');
      this.demandesTerminees = data.filter(d => d.statut === 'TERMINEE');
    },
    error: (err) => console.error('❌ Erreur:', err)
  });
}

  chargerToutesMaintenances(): void {
    this.maintenanceService.getAll().subscribe({
      next: (data: MaintenanceLog[]) => {
        this.maintenances = data;
        data.forEach((m: MaintenanceLog) => {
          if (!this.maintenanceLogs[m.ressourceId]) {
            this.maintenanceLogs[m.ressourceId] = [];
          }
          this.maintenanceLogs[m.ressourceId].push(m);
        });
      },
      error: (err: any) => console.error('Erreur chargement maintenances:', err)
    });
  }

  loadMaintenance(resourceId: number): void {
    this.maintenanceService.getByResource(resourceId).subscribe({
      next: (logs: MaintenanceLog[]) => {
        this.maintenanceLogs[resourceId] = logs;
        if (this.selectedResourceForMaintenance && this.selectedResourceForMaintenance.id === resourceId) {
          this.maintenances = logs;
        }
      },
      error: (err: any) => console.error('Erreur chargement maintenances:', err)
    });
  }

  loadMaintenancesForResource(resourceId: number): void {
    this.maintenanceService.getByResource(resourceId).subscribe({
      next: (logs: MaintenanceLog[]) => {
        this.maintenances = logs;
        this.maintenanceLogs[resourceId] = logs;
      },
      error: (err: any) => {
        console.error('Erreur chargement maintenances:', err);
        this.maintenances = [];
      }
    });
  }

  // ========== LISTE DES MAINTENANCES ==========
  
  // Modifiez cette méthode
// Méthode pour ouvrir la liste des maintenances
openMaintenancesList(resource: Resource, event?: Event): void {
  if (event) event.stopPropagation();
  
  console.log('🔧 Ouverture liste maintenances pour:', resource.nom);
  
  // 🔥 STOCKER LA RESSOURCE
  this.selectedResourceForMaintenance = resource;
  this.selectedResourceId = resource.id;
  
  // Stocker aussi dans ressourceDetails si nécessaire
  if (!this.ressourceDetails[resource.id]) {
    this.ressourceDetails[resource.id] = resource;
  }
  
  // Charger les maintenances
  this.loadMaintenancesForResource(resource.id);
  this.showMaintenanceListModal = true;
}









// Méthode pour ouvrir la liste des maintenances depuis une demande
// Méthode pour ouvrir depuis une demande
voirMaintenancesPourRessource(resourceId: number, event?: Event): void {
  if (event) event.stopPropagation();
  
  console.log('🔧 Voir maintenances pour resourceId:', resourceId);
  
  // Récupérer la ressource depuis le service
  this.resourceService.getById(resourceId).subscribe({
    next: (resource) => {
      console.log('✅ Ressource chargée:', resource.nom);
      
      // 🔥 STOCKER LA RESSOURCE
      this.selectedResourceForMaintenance = resource;
      this.selectedResourceId = resource.id;
      this.ressourceDetails[resourceId] = resource;
      
      // Charger les maintenances
      this.maintenanceService.getByResource(resourceId).subscribe({
        next: (logs) => {
          this.maintenances = logs;
          this.showMaintenanceListModal = true;
        },
        error: (err) => console.error('Erreur:', err)
      });
    },
    error: (err) => console.error('Erreur chargement ressource:', err)
  });
}











  closeMaintenanceListModal(): void {
    this.showMaintenanceListModal = false;
    this.selectedResourceForMaintenance = null;
    this.maintenances = [];
  }

  // ========== CRUD MAINTENANCES ==========
  
  openAddMaintenanceModal(resourceId: number): void {
    this.selectedResourceId = resourceId;
    this.selectedMaintenance = {
      id: 0,
      typeIntervention: '',
      description: '',
      date: new Date().toISOString().slice(0, 16),
      ressourceId: resourceId,
      technicienId: ''
    };
    this.showMaintenanceForm = true;
  }

  openAddMaintenanceFromList(): void {
    if (this.selectedResourceForMaintenance) {
      this.openAddMaintenanceModal(this.selectedResourceForMaintenance.id);
    }
  }

  editMaintenance(maintenance: MaintenanceLog): void {
  console.log('✏️ Édition maintenance:', maintenance);
  this.selectedMaintenance = { ...maintenance };
  if (this.selectedMaintenance.date) {
    this.selectedMaintenance.date = this.selectedMaintenance.date.toString().slice(0, 16);
  }
  this.showMaintenanceForm = true;
  // Pas de selectedDemande car c'est une modification, pas une création
}

  editMaintenanceFromList(maintenance: MaintenanceLog, event: Event): void {
    event.stopPropagation();
    this.editMaintenance(maintenance);
  }

// expert-dashboard.component.ts
// Remplacez vos méthodes par celles-ci :

// 🔥 Nouvelle méthode pour supprimer de l'historique localStorage
// expert-dashboard.component.ts
// Ajoutez cette méthode

// expert-dashboard.component.ts
supprimerIntervention(intervention: any, event: Event): void {
  event.stopPropagation();
  
  if (!intervention || !intervention.id) {
    alert('Erreur: Intervention invalide');
    return;
  }
  
  if (confirm(`Supprimer "${intervention.ressourceNom}" de l'historique ?`)) {
    let historique = JSON.parse(localStorage.getItem('interventions_terminees_expert') || '[]');
    historique = historique.filter((h: any) => h.id !== intervention.id);
    localStorage.setItem('interventions_terminees_expert', JSON.stringify(historique));
    this.chargerMaintenancesTerminees();
    alert('✅ Supprimé !');
  }
}

// expert-dashboard.component.ts
// Ajoutez cette méthode (elle était utilisée pour les maintenances dans la liste des ressources)

deleteMaintenanceFromList(maintenanceId: number, event: Event): void {
  event.stopPropagation();
  console.log('🗑️ deleteMaintenanceFromList - maintenanceId:', maintenanceId);
  
  if (!maintenanceId) {
    console.error('❌ maintenanceId invalide');
    alert('Erreur: ID de maintenance invalide');
    return;
  }
  
  this.deleteMaintenance(maintenanceId);
}



















deleteMaintenance(maintenanceId: number, event?: Event): void {
  if (event) event.stopPropagation();
  
  console.log('🗑️ deleteMaintenance - maintenanceId:', maintenanceId);
  
  if (!maintenanceId) {
    console.error('❌ maintenanceId invalide');
    alert('Erreur: ID de maintenance invalide');
    return;
  }
  
  if (confirm('Voulez-vous vraiment supprimer cette maintenance ?')) {
    this.maintenanceService.delete(maintenanceId).subscribe({
      next: () => {
        console.log('✅ Maintenance supprimée avec succès');
        this.chargerMaintenancesTerminees();
        this.chargerToutesMaintenances();
        alert('✅ Maintenance supprimée avec succès !');
      },
      error: (err: any) => {
        console.error('❌ Erreur suppression:', err);
        alert('Erreur lors de la suppression');
      }
    });
  }
}

// expert-dashboard.component.ts
// Ajoutez cette méthode dans la classe ExpertDashboardComponent

validateMaintenanceForm(): boolean {
  this.maintenanceErrors = {};
  
  if (!this.selectedMaintenance.typeIntervention) {
    this.maintenanceErrors.typeIntervention = 'Le type d\'intervention est requis';
  }
  if (!this.selectedMaintenance.description || this.selectedMaintenance.description.trim().length < 5) {
    this.maintenanceErrors.description = 'La description doit contenir au moins 5 caractères';
  }
  if (!this.selectedMaintenance.date) {
    this.maintenanceErrors.date = 'La date est requise';
  }
  
  return Object.keys(this.maintenanceErrors).length === 0;
}
  saveMaintenance(): void {
    if (!this.validateMaintenanceForm()) return;
    
    if (this.selectedMaintenance.id === 0) {
      this.maintenanceService.create(this.selectedMaintenance).subscribe({
        next: (newMaintenance: MaintenanceLog) => {
          this.showMaintenanceForm = false;
          if (this.selectedResourceForMaintenance) {
            this.loadMaintenancesForResource(this.selectedResourceForMaintenance.id);
          }
          alert('Maintenance ajoutée avec succès !');
        },
        error: (err: any) => console.error('Erreur création:', err)
      });
    } else {
      this.maintenanceService.update(this.selectedMaintenance.id, this.selectedMaintenance).subscribe({
        next: (updated: MaintenanceLog) => {
          this.showMaintenanceForm = false;
          if (this.selectedResourceForMaintenance) {
            this.loadMaintenancesForResource(this.selectedResourceForMaintenance.id);
          }
          alert('Maintenance modifiée avec succès !');
        },
        error: (err: any) => console.error('Erreur modification:', err)
      });
    }
  }

  closeMaintenanceModal(): void {
    this.showMaintenanceForm = false;
    this.maintenanceErrors = {};
  }

  // ========== IA - ANALYSE RÉPARER vs REMPLACER ==========
  
  analyserRemplacer(): void {
   console.log('🔍 === ANALYSE IA REMPLACER VS RÉPARER ===');
  console.log('📌 selectedResourceForMaintenance:', this.selectedResourceForMaintenance);
  console.log('📌 selectedMaintenance:', this.selectedMaintenance);
  console.log('📌 showMaintenanceListModal:', this.showMaintenanceListModal);
  
  if (!this.selectedResourceForMaintenance) {
    console.error('❌ ERREUR: selectedResourceForMaintenance est NULL!');
    console.log('💡 Vérifiez que la ressource est chargée avant d\'ouvrir le modal');
    alert("❌ Erreur: Aucune ressource sélectionnée. Veuillez réessayer.");
    return;
  }
  
  const resource = this.selectedResourceForMaintenance;
  
  // Si aucune maintenance n'est sélectionnée, utiliser un type par défaut
  let typeMaintenance = this.selectedMaintenance?.typeIntervention;
  
  if (!typeMaintenance) {
    console.log('⚠️ Aucun type de maintenance sélectionné, utilisation du type "Corrective" par défaut');
    typeMaintenance = 'Corrective';
    // Optionnel: pré-remplir le type dans selectedMaintenance
    if (this.selectedMaintenance) {
      this.selectedMaintenance.typeIntervention = typeMaintenance;
    }
  }
  
  let coutEstime = 0;
  switch(typeMaintenance) {
    case 'Corrective': coutEstime = 350; break;
    case 'Curative': coutEstime = 450; break;
    case 'Préventive': coutEstime = 150; break;
    case 'Évolutive': coutEstime = 300; break;
    default: coutEstime = 200;
  }
  
  // Ajustement par type de ressource
  if (resource.type === 'Camion') coutEstime = coutEstime * 1.5;
  else if (resource.type === 'Engin') coutEstime = coutEstime * 1.3;
  
  // Ajustement par âge
  const pourcentageVie = this.calculerPourcentageVie(resource);
  if (pourcentageVie > 80) coutEstime = coutEstime * 1.4;
  else if (pourcentageVie > 50) coutEstime = coutEstime * 1.2;
  
  coutEstime = Math.round(coutEstime);
  
  console.log('📊 Coût estimé:', coutEstime, '€');
  console.log('📊 Pourcentage vie:', pourcentageVie, '%');
  
  this.iaDecisionData = this.analyserReparationVsRemplacer(resource, coutEstime);
  this.showIADecisionModal = true;
}

  analyserReparationVsRemplacer(resource: Resource, coutReparation: number): any {
  const valeurRessource = resource.valeur;
  const ratio = (coutReparation / valeurRessource) * 100;
  const age = this.calculerAge(resource);
  const pourcentageVie = this.calculerPourcentageVie(resource);
  const vieRestante = 100 - pourcentageVie;
  
  // 🔥 Utiliser les vraies données des maintenances
  const maintenances = this.maintenanceLogs[resource.id] || [];
  const nbMaintenances = maintenances.length;
  const maintenancesParAn = nbMaintenances / Math.max(1, age);
  
  // Calculer le coût total des maintenances passées
  const coutTotalMaintenances = maintenances.reduce((sum, m) => sum + (m.cout || 0), 0);
  const coutMoyenParMaintenance = nbMaintenances > 0 ? coutTotalMaintenances / nbMaintenances : 0;
  
  console.log('📊 ANALYSE BASÉE SUR LES MAINTENANCES:');
  console.log(`   - Nombre de maintenances: ${nbMaintenances}`);
  console.log(`   - Coût total maintenances: ${coutTotalMaintenances}€`);
  console.log(`   - Coût moyen par maintenance: ${coutMoyenParMaintenance}€`);
  console.log(`   - Fréquence: ${maintenancesParAn.toFixed(1)} maintenances/an`);
  
  let decision = '';
  let justification = '';
  let gain = 0;
  
  // Critères de décision basés sur l'historique
  const hasHighMaintenanceCost = coutTotalMaintenances > valeurRessource * 0.5;
  const hasHighFrequency = maintenancesParAn > 2;
  const isNearEndOfLife = vieRestante < 30;
  const isRepairExpensive = ratio > 50;
  
  if (hasHighMaintenanceCost || (hasHighFrequency && isNearEndOfLife)) {
    decision = 'REMPLACER';
    justification = `📊 Analyse basée sur l'historique :\n`;
    justification += `• ${nbMaintenances} maintenance(s) effectuée(s) sur cette ressource\n`;
    justification += `• Coût total des maintenances : ${coutTotalMaintenances}€\n`;
    justification += `• Fréquence : ${maintenancesParAn.toFixed(1)} intervention(s)/an\n`;
    justification += `• La ressource a utilisé ${pourcentageVie.toFixed(0)}% de sa durée de vie\n`;
    justification += `💰 Conclusion : Le remplacement est plus économique.`;
    gain = (valeurRessource * 0.6) - coutReparation;
  }
  else if (isRepairExpensive && nbMaintenances > 1) {
    decision = 'REMPLACER';
    justification = `📊 Analyse basée sur l'historique :\n`;
    justification += `• Coût de réparation (${coutReparation}€) élevé (${ratio.toFixed(1)}% de la valeur)\n`;
    justification += `• Historique : ${nbMaintenances} maintenance(s) déjà effectuée(s)\n`;
    justification += `💰 Conclusion : Remplacer est plus rentable.`;
    gain = (valeurRessource * 0.5) - coutReparation;
  }
  else {
    decision = 'RÉPARER';
    justification = `📊 Analyse basée sur l'historique :\n`;
    justification += `• Coût de réparation (${coutReparation}€) raisonnable (${ratio.toFixed(1)}% de la valeur)\n`;
    justification += `• Historique : ${nbMaintenances} maintenance(s) effectuée(s)\n`;
    justification += `• La ressource a encore ${vieRestante.toFixed(0)}% de sa durée de vie\n`;
    justification += `💰 Conclusion : La réparation est recommandée.`;
    gain = (valeurRessource * 0.3) - coutReparation;
  }
  
  const details = `
📊 DÉTAILS COMPLETS :
• Ressource : ${resource.nom}
• Valeur : ${valeurRessource} €
• Coût réparation estimé : ${coutReparation} €
• Ratio : ${ratio.toFixed(1)}%

📈 HISTORIQUE MAINTENANCES :
• Nombre de maintenances : ${nbMaintenances}
• Coût total des maintenances : ${coutTotalMaintenances} €
• Coût moyen par maintenance : ${coutMoyenParMaintenance.toFixed(0)} €
• Fréquence : ${maintenancesParAn.toFixed(1)} maintenances/an

⏱️ DURÉE DE VIE :
• Âge : ${age.toFixed(1)} ans
• Durée de vie estimée : ${resource.dureeVieEstimee} ans
• Vie utilisée : ${pourcentageVie.toFixed(0)}%
• Vie restante : ${vieRestante.toFixed(0)}%
  `;
  
  return {
    recommendation: decision,
    justification: justification,
    gainMessage: gain > 0 ? `💰 Économie estimée : ${Math.abs(gain).toFixed(0)} €` : `⚠️ Coût supplémentaire : ${Math.abs(gain).toFixed(0)} €`,
    ratio: ratio.toFixed(1),
    details: details,
    nbMaintenances: nbMaintenances,
    coutTotalMaintenances: coutTotalMaintenances
  };
}

  closeIADecisionModal(): void {
    this.showIADecisionModal = false;
    this.iaDecisionData = null;
  }

  // ========== IA VISION ==========
  
  ouvrirDiagnosticIA(maintenance: MaintenanceLog, event: Event): void {
      console.log('🔍 ouvrirDiagnosticIA appelé', maintenance);

    event.stopPropagation();
    this.maintenanceSelectionnee = maintenance;
    this.diagnosticImageFile = null;
    this.diagnosticImagePreview = null;
    this.diagnosticResultat = null;
    this.showDiagnosticModal = true;
      console.log('✅ Modal ouvert:', this.showDiagnosticModal);

  }

  closeDiagnosticModal(): void {
    this.showDiagnosticModal = false;
    this.maintenanceSelectionnee = null;
    this.diagnosticImageFile = null;
    this.diagnosticImagePreview = null;
    this.diagnosticResultat = null;
  }

  onDiagnosticImageSelected(event: any): void {
    this.diagnosticImageFile = event.target.files[0];
    if (this.diagnosticImageFile) {
      const reader = new FileReader();
      reader.onload = e => this.diagnosticImagePreview = reader.result;
      reader.readAsDataURL(this.diagnosticImageFile);
    }
  }

  analyserImageCloudinary(): void {
    if (!this.diagnosticImageFile) return;
    
    const nomFichier = this.diagnosticImageFile.name.toLowerCase();
    console.log('📷 Analyse du fichier:', nomFichier);
    
    let probleme = "⚠️ Anomalie détectée";
    let solution = "🔧 Inspection recommandée";
    
    if (nomFichier.includes('fuite') || nomFichier.includes('huile') || nomFichier.includes('oil')) {
      probleme = "🔍 Fuite d'huile détectée";
      solution = "🔧 Remplacer les joints d'étanchéité et vérifier le niveau d'huile";
    } 
    else if (nomFichier.includes('batterie') || nomFichier.includes('battery')) {
      probleme = "🔍 Batterie déchargée";
      solution = "🔧 Recharger ou remplacer la batterie";
    }
    else if (nomFichier.includes('courroie') || nomFichier.includes('belt')) {
      probleme = "🔍 Courroie fissurée";
      solution = "🔧 Remplacer la courroie de distribution";
    }
    else if (nomFichier.includes('pneu') || nomFichier.includes('tire')) {
      probleme = "🔍 Pneu endommagé";
      solution = "🔧 Remplacer le pneu";
    }
    else if (nomFichier.includes('moteur') || nomFichier.includes('engine')) {
      probleme = "🔍 Anomalie moteur";
      solution = "🔧 Inspection par un technicien spécialisé";
    }
    
    this.diagnosticResultat = { probleme, solution };
  }

  // ========== ESTIMATION COÛT IA ==========
  
  estimerCoutAuto(): void {
    if (!this.selectedResourceForMaintenance || !this.selectedMaintenance.typeIntervention) {
      this.coutEstimeAuto = 0;
      return;
    }
    
    const resource = this.selectedResourceForMaintenance;
    const type = this.selectedMaintenance.typeIntervention;
    
    const data = {
      type_maintenance: type,
      type_ressource: resource.type,
      age: this.calculerAge(resource),
      duree_vie: resource.dureeVieEstimee,
      nb_maintenances: this.maintenanceLogs[resource.id]?.length || 0,
      valeur: resource.valeur
    };
    
    this.http.post('http://localhost:5001/predict-cost', data).subscribe({
      next: (result: any) => {
        this.coutEstimeAuto = result.cout_estime;
        this.coutFourchette = result.fourchette;
        this.coutConfiance = result.confiance;
      },
      error: (err: any) => {
        console.error('Erreur estimation coût:', err);
        this.coutEstimeAuto = 0;
      }
    });
  }

  // ========== TRAITEMENT DES DEMANDES ==========
  
  selectionnerDemande(demande: DemandeMaintenance): void {
  console.log('📋 Demande sélectionnée:', demande);
  
  this.selectedDemande = demande;
  this.showIaDecision = true;
  this.showMaintenanceForm = false;
  
  // 🔥 Récupérer la ressource associée à la demande
  this.resourceService.getById(demande.ressourceId).subscribe({
    next: (resource) => {
      this.selectedResourceForMaintenance = resource;
      this.ressourceDetails[demande.ressourceId] = resource;
      console.log('✅ Ressource chargée:', resource?.nom);
      
      // Initialiser la maintenance avec les infos de la ressource
      this.selectedMaintenance = {
        id: 0,
        typeIntervention: '',
        description: '',
        date: new Date().toISOString().slice(0, 16),
        ressourceId: demande.ressourceId
      };
      
      // Analyser avec IA
      this.analyserAvecIA(demande.ressourceId);
    },
    error: (err) => {
      console.error('❌ Erreur chargement ressource:', err);
      // Fallback: continuer sans ressource détaillée
      this.selectedResourceForMaintenance = null;
      this.analyserAvecIA(demande.ressourceId);
    }
  });
}

  analyserAvecIA(resourceId: number): void {
    this.resourceService.predictPanne(resourceId).subscribe({
      next: (prediction: any) => {
        this.iaPrediction = prediction;
      },
      error: (err: any) => console.error('Erreur IA:', err)
    });
  }

  choisirReparer(): void {
    this.showIaDecision = false;
    this.showMaintenanceForm = true;
    this.selectedMaintenance.ressourceId = this.selectedDemande!.ressourceId;
    this.selectedMaintenance.technicienId = '';  // ← AJOUTER (sera choisi par l'expert)

    this.estimerCoutAuto();
  }

  choisirRemplacer(): void {
    if (confirm('Confirmez-vous la décision de REMPLACER cette ressource ?')) {
      this.demandeService.updateStatut(this.selectedDemande!.id, 'TERMINEE', undefined).subscribe({
        next: () => {
          alert('✅ Demande de remplacement envoyée au chef');
          this.chargerDemandes();
          this.annulerSelection();
        },
        error: (err: any) => console.error('Erreur:', err)
      });
    }
  }

sauvegarderMaintenance(): void {
  console.log('🔵 1. Début sauvegarderMaintenance');
  
  if (!this.validateMaintenanceForm()) {
    console.log('🔴 2. Formulaire invalide');
    return;
  }
  console.log('🟢 2. Formulaire valide');
  
  // 🔥 VÉRIFIER QU'UN TECHNICIEN EST SÉLECTIONNÉ
  if (!this.selectedMaintenance.technicienId) {
    console.log('🔴 3. Aucun technicien sélectionné');
    alert('❌ Veuillez sélectionner un technicien');
    return;
  }
  console.log('🟢 3. Technicien sélectionné:', this.selectedMaintenance.technicienId);
  
  // CAS 1: MODIFICATION
  if (this.selectedMaintenance.id !== 0) {
    console.log('✏️ 4. Modification maintenance ID:', this.selectedMaintenance.id);
    
    this.maintenanceService.update(this.selectedMaintenance.id, this.selectedMaintenance).subscribe({
      next: (updated) => {
        console.log('✅ Maintenance modifiée:', updated);
        alert('✅ Maintenance modifiée avec succès !');
        this.showMaintenanceForm = false;
        
        this.chargerMaintenancesTerminees();
        this.chargerToutesMaintenances();
        if (this.selectedDemande) {
          this.chargerDemandes();
        }
        
        this.resetMaintenanceForm();
      },
      error: (err: any) => {
        console.error('❌ Erreur modification:', err);
        alert('Erreur lors de la modification');
      }
    });
    return;
  }
  
  // CAS 2: CRÉATION
  if (!this.selectedDemande) {
    console.log('🔴 4. Aucune demande sélectionnée');
    alert('Erreur: aucune demande sélectionnée');
    return;
  }
  console.log('🟢 4. Demande sélectionnée:', this.selectedDemande.id);
  
  this.selectedMaintenance.ressourceId = this.selectedDemande.ressourceId;
  
  console.log('📝 5. Création maintenance:', this.selectedMaintenance);
  console.log('👨‍🔧 Technicien assigné:', this.selectedMaintenance.technicienId);
  
  this.maintenanceService.create(this.selectedMaintenance).subscribe({
    next: (maintenance: MaintenanceLog) => {
      console.log('✅ 6. Maintenance créée:', maintenance);
      
      const maintenanceId = maintenance.id;
      const technicienId = this.selectedMaintenance.technicienId;
      
      if (!technicienId) {
        console.error('🔴 7. technicienId est null');
        alert('Erreur: Technicien non valide');
        return;
      }
      
      console.log('📝 7. Mise à jour statut vers EN_COURS');
      
      // ÉTAPE 1: Mettre à jour le statut
      this.demandeService.updateStatut(this.selectedDemande!.id, 'EN_COURS', maintenanceId).subscribe({
        next: () => {
          console.log('✅ 8. Statut mis à jour avec succès');
          console.log('📝 9. Assignation technicien:', technicienId);
          
          // 🔥 ÉTAPE 2: Stocker dans localStorage pour le technicien
         const interventionsKey = `interventions_${technicienId}`;
const existing = JSON.parse(localStorage.getItem(interventionsKey) || '[]');

const nouvelleIntervention = {
  id: this.selectedDemande!.id,
  ressourceId: this.selectedDemande!.ressourceId,
  ressourceNom: this.selectedDemande!.ressourceNom,
  ressourceMatricule: this.selectedDemande!.ressourceMatricule,
  ressourceType: this.selectedDemande!.ressourceType,
  ressourceImageUrl: this.selectedDemande!.ressourceImageUrl,
  motif: this.selectedDemande!.motif,
  urgence: this.selectedDemande!.urgence,
  dateDemande: this.selectedDemande!.dateDemande,
  typeIntervention: this.selectedMaintenance.typeIntervention,
  statut: 'EN_COURS'
          };
          
          existing.push(nouvelleIntervention);
          localStorage.setItem(interventionsKey, JSON.stringify(existing));
          
          console.log(`✅ Demande stockée dans localStorage pour le technicien ${technicienId}`);
          alert(`✅ Demande #${this.selectedDemande!.id} assignée au technicien !`);
          
          this.chargerDemandes();
          this.chargerMaintenancesTerminees();
          this.chargerToutesMaintenances();
          this.annulerSelection();
          this.resetMaintenanceForm();
        },
        error: (err: any) => {
          console.error('❌ Erreur mise à jour statut:', err);
          alert('Erreur lors de la mise à jour du statut');
          this.chargerDemandes();
          this.annulerSelection();
        }
      });
    },
    error: (err: any) => {
      console.error('❌ Erreur création maintenance:', err);
      alert('Erreur lors de la création de la maintenance');
    }
  });
}

// Ajoute cette méthode pour réinitialiser le formulaire
resetMaintenanceForm(): void {
  this.selectedMaintenance = {
    id: 0,
    typeIntervention: '',
    description: '',
    date: new Date().toISOString().slice(0, 16),
    ressourceId: 0,
    technicienId: ''
  };
  this.maintenanceErrors = {};
  this.showMaintenanceForm = false;
}
  annulerSelection(): void {
    this.selectedDemande = null;
    this.showMaintenanceForm = false;
    this.showIaDecision = false;
    this.iaPrediction = null;
    this.selectedResourceForMaintenance = null;
    this.selectedMaintenance = {
      id: 0,
      typeIntervention: '',
      description: '',
      date: new Date().toISOString().slice(0, 16),
      ressourceId: 0,
      technicienId: ''
    };
    this.maintenanceErrors = {};
    this.coutEstimeAuto = 0;
  }

  // ========== MÉTHODES UTILITAIRES ==========
  
  getNomRessource(ressourceId: number | undefined): string {
  if (!ressourceId) {
    return 'Ressource inconnue';
  }
  const resource = this.ressourceDetails[ressourceId];
  return resource?.nom || 'Ressource inconnue';
}

  getImageRessource(ressourceId: number): string {
    return this.ressourceDetails[ressourceId]?.imageUrl || '';
  }

  calculerAge(resource: Resource): number {
    if (!resource.dateAchat) return 0;
    const dateAchat = new Date(resource.dateAchat);
    const aujourdHui = new Date();
    return (aujourdHui.getTime() - dateAchat.getTime()) / (1000 * 3600 * 24 * 365.25);
  }

  calculerPourcentageVie(resource: Resource): number {
    if (!resource.dateAchat || resource.dureeVieEstimee <= 0) return 0;
    const age = this.calculerAge(resource);
    return Math.min(100, (age / resource.dureeVieEstimee) * 100);
  }



  // Ajoutez dans la classe ExpertDashboardComponent

// Propriété pour le nombre de demandes non traitées
get unreadDemandesCount(): number {
  return this.demandesEnAttente.length;
}

// Méthode pour rafraîchir les demandes (appelée périodiquement)
startPollingDemandes(): void {
  setInterval(() => {
    this.chargerDemandes();
  }, 30000); // Rafraîchit toutes les 30 secondes
}

showMaintenancesTerminees: boolean = false;

// expert-dashboard.component.ts
// Remplacez votre méthode chargerMaintenancesTerminees() par celle-ci :

// expert-dashboard.component.ts
// REMPLACEZ complètement votre méthode chargerMaintenancesTerminees()

// Déclarez la propriété
maintenancesTerminees: any[] = [];

chargerMaintenancesTerminees(): void {
  console.log('🔄 Chargement des maintenances terminées depuis localStorage...');
  
  // 🔥 Lire depuis localStorage (ce que le technicien a stocké)
  const historiqueKey = `interventions_terminees_expert`;
  const historique = JSON.parse(localStorage.getItem(historiqueKey) || '[]');
  
  console.log('📋 Données brutes du localStorage:', historique);
  
  // Trier par date de fin (la plus récente d'abord)
  this.maintenancesTerminees = historique.sort((a: any, b: any) => {
    return new Date(b.dateFin).getTime() - new Date(a.dateFin).getTime();
  });
  
  console.log('✅ Maintenances terminées chargées:', this.maintenancesTerminees.length);
  
  // Afficher chaque intervention pour déboguer
  this.maintenancesTerminees.forEach((m: any, index: number) => {
    console.log(`  ${index + 1}. ${m.ressourceNom} - ${m.typeIntervention} - terminée le ${m.dateFin}`);
  });
}











// expert-dashboard.component.ts
// Ajoutez cette méthode dans la classe

formatTemps(secondes: number): string {
  if (!secondes) return '0h';
  const heures = Math.floor(secondes / 3600);
  const minutes = Math.floor((secondes % 3600) / 60);
  return `${heures}h${minutes.toString().padStart(2, '0')}m`;
}













// Grouper les maintenances par mois
getMaintenancesByMonth(): { month: string; maintenances: MaintenanceLog[] }[] {
  const grouped = new Map<string, MaintenanceLog[]>();
  
  this.maintenancesTerminees.forEach(maint => {
    const date = new Date(maint.date);
    const monthKey = `${date.getFullYear()}-${date.getMonth() + 1}`;
    const monthName = date.toLocaleString('fr-FR', { month: 'long', year: 'numeric' });
    
    if (!grouped.has(monthName)) {
      grouped.set(monthName, []);
    }
    grouped.get(monthName)!.push(maint);
  });
  
  // Convertir en tableau
  return Array.from(grouped.entries())
    .map(([month, maintenances]) => ({
      month: month,
      maintenances: maintenances.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
    }));
}

// Calculer le coût total du mois
getMonthTotal(maintenances: MaintenanceLog[]): number {
  return maintenances.reduce((sum, m) => sum + (m.cout || 0), 0);
}
getTotalCost(): number {
  return this.maintenancesTerminees.reduce((sum, m) => sum + (m.cout || 0), 0);
}


// Variables pour contrôler l'affichage
showDemandes: boolean = true;
showHistorique: boolean = false;  // L'historique est visible par défaut

// Méthode pour afficher les demandes
afficherDemandes(): void {
  this.showDemandes = true;
  this.showHistorique = false;
  setTimeout(() => {
    const element = document.querySelector('.demandes-list');
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, 100);
}

// Méthode pour afficher l'historique
afficherHistorique(): void {
  console.log('📜 Affichage de l\'historique');
  
  // Recharger les données avant d'afficher
  this.maintenanceService.getAll().subscribe({
    next: (data) => {
      this.maintenancesTerminees = data.filter(m => m.date).sort((a, b) => 
        new Date(b.date).getTime() - new Date(a.date).getTime()
      );
      
      console.log('📋 Maintenances terminées:', this.maintenancesTerminees.length);
      
      // Charger les ressources manquantes
      this.maintenancesTerminees.forEach(m => {
        if (!this.ressourceDetails[m.ressourceId]) {
          this.resourceService.getById(m.ressourceId).subscribe(resource => {
            this.ressourceDetails[m.ressourceId] = resource;
          });
        }
      });
      
      this.showHistorique = true;
      this.showDemandes = false;
      
      setTimeout(() => {
        const element = document.querySelector('.historique-section');
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 100);
    },
    error: (err) => console.error('Erreur:', err)
  });
}


// Variables
expandedRessourceId: number | null = null;

// Récupérer toutes les ressources qui ont des maintenances
// Récupérer toutes les ressources qui ont des maintenances
getRessourcesAvecMaintenances(): any[] {
  console.log('🔍 getRessourcesAvecMaintenances - maintenancesTerminees:', this.maintenancesTerminees);
  
  if (!this.maintenancesTerminees || this.maintenancesTerminees.length === 0) {
    console.log('📭 Aucune maintenance terminée');
    return [];
  }
  
  // Afficher les détails des maintenances
  this.maintenancesTerminees.forEach(m => {
    console.log(`  Maintenance: ID=${m.id}, RessourceId=${m.ressourceId}, Type=${m.typeIntervention}`);
  });
  
  const ressourceIds = [...new Set(this.maintenancesTerminees.map(m => m.ressourceId))];
  console.log('🆔 IDs des ressources uniques:', ressourceIds);
  console.log('📦 ressourceDetails:', this.ressourceDetails);
  
  const ressources = ressourceIds.map(id => this.ressourceDetails[id]).filter(r => r);
  console.log('📦 Ressources trouvées:', ressources.length);
  
  if (ressources.length === 0) {
    console.log('⚠️ Aucune ressource trouvée - chargement des ressources...');
    
    // Charger les ressources manquantes
    ressourceIds.forEach(id => {
      if (!this.ressourceDetails[id]) {
        this.resourceService.getById(id).subscribe(resource => {
          this.ressourceDetails[id] = resource;
          console.log(`✅ Ressource chargée: ${resource.nom} (ID: ${id})`);
        });
      }
    });
  }
  
  return ressources;
}

// Récupérer les maintenances d'une ressource spécifique
getMaintenancesByRessourceId(ressourceId: number): MaintenanceLog[] {
  return this.maintenancesTerminees.filter(m => m.ressourceId === ressourceId)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}

// Calculer le coût total par ressource
getTotalCostByRessource(ressourceId: number): number {
  return this.getMaintenancesByRessourceId(ressourceId).reduce((sum, m) => sum + (m.cout || 0), 0);
}

// Basculer l'affichage d'une ressource
toggleRessourceGroupe(ressourceId: number): void {
  if (this.expandedRessourceId === ressourceId) {
    this.expandedRessourceId = null;
  } else {
    this.expandedRessourceId = ressourceId;
  }
}


getTypeRessource(ressourceId: number): string {
  const resource = this.ressourceDetails[ressourceId];
  return resource?.type || 'Type inconnu';
}










analyserRemplacerAvecRessource(resource: Resource): void {
  console.log('🔍 Analyse IA pour:', resource?.nom);
  
  if (!resource) {
    alert("❌ Aucune ressource sélectionnée");
    return;
  }
  
  // Définir la ressource sélectionnée
  this.selectedResourceForMaintenance = resource;
  
  // Créer une maintenance par défaut si nécessaire
  if (!this.selectedMaintenance) {
    this.selectedMaintenance = {
      id: 0,
      typeIntervention: 'Corrective',
      description: '',
      date: new Date().toISOString().slice(0, 16),
      ressourceId: resource.id
    };
  }
  
  // Calculer le coût estimé
  let typeMaintenance = this.selectedMaintenance?.typeIntervention || 'Corrective';
  let coutEstime = 0;
  
  switch(typeMaintenance) {
    case 'Corrective': coutEstime = 350; break;
    case 'Curative': coutEstime = 450; break;
    case 'Préventive': coutEstime = 150; break;
    case 'Évolutive': coutEstime = 300; break;
    default: coutEstime = 200;
  }
  
  if (resource.type === 'Camion') coutEstime = coutEstime * 1.5;
  else if (resource.type === 'Engin') coutEstime = coutEstime * 1.3;
  
  const pourcentageVie = this.calculerPourcentageVie(resource);
  if (pourcentageVie > 80) coutEstime = coutEstime * 1.4;
  else if (pourcentageVie > 50) coutEstime = coutEstime * 1.2;
  
  coutEstime = Math.round(coutEstime);
  
  // Calculer la décision
  this.iaDecisionData = this.analyserReparationVsRemplacer(resource, coutEstime);
  this.showIADecisionModal = true;
}









// Pour la décision Remplacer vs Réparer
// Dans expert-dashboard.component.ts

prendreEnCharge(demande: any): void {
  console.log('🔧 Prendre en charge:', demande);
  
  this.selectedDemande = demande;
  this.showIaDecision = true;
  this.showMaintenanceForm = false;
  
  // Récupérer la ressource
  this.resourceService.getById(demande.ressourceId).subscribe({
    next: (resource) => {
      console.log('✅ Ressource chargée:', resource.nom);
      this.selectedResourceForMaintenance = resource;
      this.ressourceDetails[demande.ressourceId] = resource;
      
      // Initialiser la maintenance
      this.selectedMaintenance = {
        id: 0,
        typeIntervention: '',
        description: demande.motif,
        date: new Date().toISOString().slice(0, 16),
        ressourceId: demande.ressourceId
      };
      
      // 🔥 AJOUT: Appeler l'IA de prédiction (durée + coût)
      this.appelerIAPrediction(demande.motif);
      
      // Calculer la décision IA (réparer vs remplacer)
      this.calculerDecisionIA(resource, demande);
      
      // FORCER l'affichage du panneau
      this.showIaDecision = true;
      
      console.log('📊 iaDecisionData:', this.iaDecisionData);
      console.log('📊 showIaDecision:', this.showIaDecision);
    },
    error: (err) => {
      console.error('❌ Erreur:', err);
      alert('Erreur lors du chargement de la ressource');
    }
  });
}

// Méthode pour calculer la décision IA
calculerDecisionIA(resource: any, demande: any): void {
  console.log('🔍 Calcul de la décision IA...');
  
  let coutEstime = this.estimerCout(demande.motif, resource.type);
  const pourcentageVie = this.calculerPourcentageVie(resource);
  const nbMaintenances = this.maintenanceLogs[resource.id]?.length || 0;
  const valeurRessource = resource.valeur;
  const ratio = (coutEstime / valeurRessource) * 100;
  
  let decision = '';
  let justification = '';
  
  if (ratio < 30 && pourcentageVie < 60) {
    decision = 'RÉPARER';
    justification = `Le coût de réparation (${coutEstime}€) représente ${ratio.toFixed(1)}% de la valeur (${valeurRessource}€). La ressource est encore jeune (${pourcentageVie.toFixed(0)}% de vie utilisée).`;
  } else if (ratio > 60 || pourcentageVie > 80) {
    decision = 'REMPLACER';
    justification = `Le coût de réparation (${coutEstime}€) est élevé (${ratio.toFixed(1)}% de la valeur) et/ou la ressource est en fin de vie (${pourcentageVie.toFixed(0)}%).`;
  } else {
    decision = 'RÉPARER';
    justification = `Le coût de réparation (${coutEstime}€) est raisonnable (${ratio.toFixed(1)}% de la valeur) et la ressource a encore ${(100 - pourcentageVie).toFixed(0)}% de sa durée de vie.`;
  }
  
  this.iaDecisionData = {
    recommendation: decision,
    justification: justification,
    ratio: ratio.toFixed(1),
    gainMessage: decision === 'RÉPARER' ? `${Math.round(coutEstime)}€ économisés` : `${Math.round(valeurRessource * 0.3)}€ économisés`,
    gain: decision === 'RÉPARER' ? coutEstime : -Math.round(valeurRessource * 0.3),
    details: `
📊 DÉTAILS DU CALCUL :
• Coût réparation : ${coutEstime} €
• Valeur ressource : ${valeurRessource} €
• Ratio : ${ratio.toFixed(1)}%
• Âge : ${this.calculerAge(resource).toFixed(1)} ans
• Durée de vie : ${resource.dureeVieEstimee} ans
• Vie utilisée : ${pourcentageVie.toFixed(0)}%
• Nombre maintenances : ${nbMaintenances}
    `
  };
  
  console.log('✅ iaDecisionData calculé:', this.iaDecisionData);
}

// Estimer le coût
estimerCout(motif: string, type: string): number {
  let cout = 200;
  
  const motifLower = motif.toLowerCase();
  if (motifLower.includes('moteur')) cout = 800;
  else if (motifLower.includes('pneu')) cout = 300;
  else if (motifLower.includes('vidange')) cout = 150;
  else if (motifLower.includes('remplacement')) cout = 500;
  
  if (type === 'Camion') cout = cout * 1.5;
  else if (type === 'Engin') cout = cout * 1.3;
  
  return Math.round(cout);
}
// Pour l'analyse d'image de la panne
analyserImagePanne(demande: DemandeMaintenance): void {
  console.log('📸 Analyse image pour:', demande.ressourceNom);
  
  // Ouvrir le modal d'upload d'image
  this.maintenanceSelectionnee = {
    id: 0,
    description: demande.motif,
    ressourceId: demande.ressourceId
  };
  this.diagnosticImageFile = null;
  this.diagnosticImagePreview = null;
  this.diagnosticResultat = null;
  this.showDiagnosticModal = true;
}








  // ========== MÉTHODES POUR LE CHAT ==========
  
  /**
   * Ouvrir le chat pour une demande
   */
  ouvrirChat(demande: any): void {
    console.log('💬 [EXPERT] Ouverture chat pour demande:', demande.id);
    this.selectedDemandeForChat = demande;
    this.showChatModal = true;
    this.chargerMessagesChat(demande.id);
    this.startMessagePolling();
  }

  /**
   * Charger les messages de la demande
   */
  chargerMessagesChat(demandeId: number): void {
  this.messageService.getMessagesByDemande(demandeId).subscribe({
    next: (data) => {
      console.log('📩 Messages reçus:', data);
      data.forEach(msg => {
        console.log(`- "${msg.contenu}" → expediteurRole: ${msg.expediteurRole} → ${msg.expediteurRole === 'EXPERT' ? 'SENT (droite)' : 'RECEIVED (gauche)'}`);
      });
      this.chatMessages = data;
      this.scrollToBottomChat();
    },
    error: (err) => console.error(err)
  });
}

  /**
   * Envoyer un message
   */
  envoyerMessageChat(): void {
    if (!this.newChatMessage.trim()) return;

    const user = this.authService.getCurrentUser();
    
    if (!user || !user.userId) {
      console.error('❌ Utilisateur non connecté');
      alert('Erreur: Veuillez vous reconnecter');
      return;
    }

    // Récupérer l'ID du chef depuis la demande
    const chefId = this.selectedDemandeForChat?.chefId?.toString();

    const message = {
      expediteurId: user.userId,
      expediteurNom: user.email || 'Expert',
      expediteurRole: user.role || 'EXPERT',
      destinataireId: chefId,
      destinataireRole: 'CHEF_EQUIPE',
      contenu: this.newChatMessage,
      demandeId: this.selectedDemandeForChat?.id,
      dateEnvoi: new Date().toISOString()
    };

    console.log('📨 [EXPERT] Envoi du message:', message);

    this.messageService.envoyerMessage(message).subscribe({
      next: (response) => {
        console.log('✅ [EXPERT] Message envoyé:', response);
        this.newChatMessage = '';
        this.chargerMessagesChat(this.selectedDemandeForChat.id);
      },
      error: (err) => {
        console.error('❌ [EXPERT] Erreur envoi message:', err);
        alert('Erreur lors de l\'envoi du message');
      }
    });
  }

  /**
   * Rafraîchir les messages automatiquement
   */
  startMessagePolling(): void {
    if (this.messageInterval) {
      clearInterval(this.messageInterval);
    }
    
    this.messageInterval = setInterval(() => {
      if (this.showChatModal && this.selectedDemandeForChat) {
        this.chargerMessagesChat(this.selectedDemandeForChat.id);
      }
    }, 5000);
  }

  /**
   * Défiler vers le bas des messages
   */
  scrollToBottomChat(): void {
    setTimeout(() => {
      const container = document.querySelector('.chat-messages-list');
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    }, 100);
  }

  /**
   * Fermer le chat
   */
  fermerChat(): void {
    if (this.messageInterval) {
      clearInterval(this.messageInterval);
    }
    this.showChatModal = false;
    this.selectedDemandeForChat = null;
    this.chatMessages = [];
    this.newChatMessage = '';
  }

























  ecouterInterventionsTerminees(): void {
  setInterval(() => {
    const historiqueKey = `interventions_terminees_expert`;
    const historique = JSON.parse(localStorage.getItem(historiqueKey) || '[]');
    
    historique.forEach((intervention: any) => {
      // Vérifier si déjà dans demandesTerminees
      const existe = this.demandesTerminees.find(d => d.id === intervention.demandeId);
      
      if (!existe) {
        console.log(`🎉 Nouvelle intervention terminée: ${intervention.ressourceNom}`);
        
        // Recharger les demandes pour mettre à jour l'affichage
        this.chargerDemandes();
        this.chargerMaintenancesTerminees();
        
        // Afficher notification
        this.afficherNotification(`✅ ${intervention.ressourceNom} - Intervention terminée !`);
      }
    });
  }, 10000); // Vérifier toutes les 10 secondes
}

 





























// expert-dashboard.component.ts
// Ajoutez cette méthode dans la classe ExpertDashboardComponent

afficherNotification(message: string): void {
  // Créer un élément toast de notification
  const toast = document.createElement('div');
  toast.className = 'notification-toast-expert';
  toast.innerHTML = `
    <div style="display: flex; align-items: center; gap: 12px;">
      <span style="font-size: 20px;">✅</span>
      <span style="flex: 1;">${message}</span>
      <button style="background: none; border: none; color: white; cursor: pointer; font-size: 18px;" onclick="this.parentElement.parentElement.remove()">✕</button>
    </div>
  `;
  
  // Styles de la notification
  toast.style.cssText = `
    position: fixed;
    bottom: 20px;
    right: 20px;
    background: linear-gradient(135deg, #4CAF50, #45a049);
    color: white;
    padding: 12px 20px;
    border-radius: 8px;
    z-index: 10000;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    font-weight: 500;
    min-width: 300px;
    max-width: 400px;
    animation: slideInRight 0.3s ease-out;
    cursor: pointer;
  `;
  
  document.body.appendChild(toast);
  
  // Ajouter les animations CSS si pas déjà fait
  this.ajouterStylesNotifications();
  
  // Auto-fermeture après 5 secondes
  setTimeout(() => {
    if (toast && toast.parentElement) {
      toast.style.animation = 'slideOutRight 0.3s ease-out';
      setTimeout(() => {
        if (toast.parentElement) toast.remove();
      }, 300);
    }
  }, 5000);
  
  // Fermer au clic
  toast.onclick = () => {
    toast.style.animation = 'slideOutRight 0.3s ease-out';
    setTimeout(() => toast.remove(), 300);
  };
}

// Ajoutez aussi cette méthode pour les styles CSS
ajouterStylesNotifications(): void {
  // Vérifier si les styles existent déjà
  if (document.querySelector('#notification-styles-expert')) return;
  
  const style = document.createElement('style');
  style.id = 'notification-styles-expert';
  style.textContent = `
    @keyframes slideInRight {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    
    @keyframes slideOutRight {
      from {
        transform: translateX(0);
        opacity: 1;
      }
      to {
        transform: translateX(100%);
        opacity: 0;
      }
    }
    
    .notification-toast-expert {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    
    .notification-toast-expert button {
      transition: opacity 0.2s;
    }
    
    .notification-toast-expert button:hover {
      opacity: 0.8;
    }
  `;
  
  document.head.appendChild(style);
}









iaPredictionActuelle: IAPrediction | null = null;
showIaPrediction: boolean = false;
isLoadingPrediction: boolean = false;





appelerIAPrediction(description: string): void {
  this.isLoadingPrediction = true;
  this.showIaPrediction = true;
  
  console.log('🤖 Appel IA pour:', description);
  
  this.iaPredictionService.predireDepuisDescription(description).subscribe({
    next: (prediction) => {
      console.log('✅ Prédiction reçue:', prediction);
      this.iaPredictionActuelle = prediction;
      this.isLoadingPrediction = false;
      
      if (prediction.success) {
        this.selectedMaintenance.typeIntervention = prediction.type_intervention;
      }
    },
    error: (err) => {
      console.error('❌ Erreur IA:', err);
      this.isLoadingPrediction = false;
      this.iaPredictionActuelle = null;
    }
  });
}

// expert-dashboard.component.ts

accepterPredictionIA(): void {
  if (!this.iaPredictionActuelle || !this.selectedDemande) {
    alert('Erreur: Aucune prédiction disponible');
    return;
  }
  
  console.log('📄 Génération de la facture pour:', this.selectedDemande.ressourceNom);
  
  const facture: Facture = {
    demandeId: this.selectedDemande.id,
    description: this.selectedDemande.motif,
    ressourceNom: this.selectedDemande.ressourceNom,
    typeIntervention: this.iaPredictionActuelle.type_intervention,
    dureeEstimee: this.iaPredictionActuelle.duree_formatee,
    coutTotal: this.iaPredictionActuelle.cout_total,
    statut: 'EN_ATTENTE',
    dateEmission: new Date().toISOString(),
    technicienId: this.currentUserId,
    chefId: this.selectedDemande.chefId
  };
  
  this.factureService.creerFacture(facture).subscribe({
    next: (factureCreee: Facture) => {
      console.log('✅ Facture créée:', factureCreee);
      
      // 🔥 SUPPRIMEZ L'APPEL D'ENVOI POUR L'INSTANT
      alert(`✅ Facture créée avec succès !
      
📄 FACTURE #${factureCreee.id}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📝 Panne: ${factureCreee.description}
🚚 Ressource: ${factureCreee.ressourceNom}
📋 Type: ${factureCreee.typeIntervention}
⏱️ Durée: ${factureCreee.dureeEstimee}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💰 TOTAL: ${factureCreee.coutTotal}€
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

La facture a été enregistrée.
Le chef d'équipe pourra la consulter et payer.`);
      
      this.showIaPrediction = false;
      this.iaPredictionActuelle = null;
      this.chargerDemandes();
    },
    error: (err: any) => {
      console.error('❌ Erreur création facture:', err);
      alert('Erreur lors de la création de la facture');
    }
  });
}

modifierPredictionIA(): void {
  this.showIaPrediction = false;
  this.showMaintenanceForm = true;
}






getUrgenceColor(urgence: string): string {
  switch(urgence) {
    case 'CRITIQUE': return '#ef4444';
    case 'HAUTE': return '#f97316';
    case 'MOYENNE': return '#eab308';
    case 'BASSE': return '#10b981';
    default: return '#0D9B76';
  }
}




// Ajoutez cette méthode vers la fin de votre classe, avant la dernière accolade
utiliserDiagnostic() {
  if (this.diagnosticResultat && this.maintenanceSelectionnee) {
    // Remplir la prédiction IA avec toutes les propriétés requises
    this.iaPredictionActuelle = {
      description: this.diagnosticResultat.probleme,
      type_intervention: "Corrective",
      duree_heures: parseInt(this.diagnosticResultat.dureeEstimee) || 2,
      duree_formatee: this.diagnosticResultat.dureeEstimee || "2 heures",
      taux_horaire: 25,
      cout_main_oeuvre: this.diagnosticResultat.coutEstime || 200,
      prime: 50,
      cout_total: (this.diagnosticResultat.coutEstime || 200) + 50,
      success: true,
      mot_cle_trouve: "diagnostic_photo"
    };
    
    // Afficher la prédiction
    this.showIaPrediction = true;
    this.showIaDecision = true;
    
    // Sélectionner la demande
    this.selectedDemande = this.maintenanceSelectionnee;
    
    // Fermer le modal
    this.closeDiagnosticModal();
  }
}

// Ajoutez cette méthode dans votre classe ExpertDashboardComponent
removeDiagnosticImage() {
  this.diagnosticImageFile = null;
  this.diagnosticImagePreview = null;
  this.diagnosticResultat = null;
}
}