import { Component, ElementRef, HostListener, OnInit } from '@angular/core';
import { Resource, ResourceService } from '../resource.service';
import { AuthService } from '../../../core/services/auth.service';
import { HttpClient } from '@angular/common/http';
import { DemandeMaintenance, DemandeMaintenanceService } from '../../demande-maintenance/demande-maintenance.service';
import { MaintenanceLog, MaintenanceLogService } from '../../maintenance-log/maintenance-log.service';
import { MessageService } from '../../message/message.service';  // Ajoutez cet import
import { Facture, FactureService } from '../../facture.service';
import { ActivatedRoute, Router } from '@angular/router';



@Component({
  selector: 'app-resource',
  templateUrl: './resource-list.component.html'
})
export class ResourceListComponent implements OnInit {

  resources: Resource[] = [];
  showForm = false;
  selectedResource: Resource | null = null;

  newResource: Resource = {
    id: 0,
    nom: '',
    type: '',
    etat: '',
    valeur: 0,
    dureeVieEstimee: 0,
    imageUrl: ''
  };


  // 🔥 NOUVEAU : Propriétés pour les factures
  facturesEnAttente: Facture[] = [];
  facturesPayees: Facture[] = [];
  totalAPayer: number = 0;
  chefId: number = 0;
  showFacturesModal: boolean = false;
  isLoadingFactures: boolean = false;
selectedFactures: Set<number> = new Set();





// Ajoute cette propriété dans la classe
showSidebar: boolean = false;


// Ajoutez ces propriétés
showPaymentModal: boolean = false;
selectedFactureForPayment: Facture | null = null;

  sideRibbonExpanded: boolean = false;



  selectedFile: File | null = null;
  errors: any = {};
  isChefEquipe: boolean = false;
  accessDenied: boolean = false;
  filePreview: string | ArrayBuffer | null = null;

  // Demandes de maintenance
  showDemandeModal: boolean = false;
  demandeMotif: string = '';
  demandeUrgence: 'BASSE' | 'MOYENNE' | 'HAUTE' | 'CRITIQUE' = 'MOYENNE';
  demandeDateRemise: string = '';
  selectedResourceForDemande: Resource | null = null;

  // Filtres et alertes
  selectedFilter: string = 'all';
  filteredResources: Resource[] = [];

  // ========== PROPRIÉTÉS POUR L'OCCUPATION ==========
  // Les propriétés statut, occupePar, dateDebutOccupation, dateFinOccupation sont dans l'entité Resource

  // ========== PROPRIÉTÉS POUR LE CHATBOT ==========
  showChatbot: boolean = false;
  messages: { text: string; isUser: boolean; voice?: boolean }[] = [];
  inputText: string = '';
  isListening: boolean = false;
  private recognition: any;





// ========== PROPRIÉTÉS POUR LE CHAT ==========
  showChatModal: boolean = false;
  selectedDemandeForChat: any = null;
  chatMessages: any[] = [];
  newChatMessage: string = '';




  constructor(
    public resourceService: ResourceService,
    public authService: AuthService,
    private http: HttpClient,
    private demandeService: DemandeMaintenanceService,  private maintenanceService: MaintenanceLogService,private messageService: MessageService ,    private factureService: FactureService,    private router: Router // ← AJOUTER CETTE LIGNE DANS LE CONSTRUCTEUR

  // ← AJOUTER CETTE LIGNE

  ) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    this.isChefEquipe = user?.role === 'CHEF_EQUIPE';
    
    console.log('=== VÉRIFICATION RÔLE ===');
    console.log('Utilisateur:', user);
    console.log('Rôle:', user?.role);
    console.log('Est Chef Equipe:', this.isChefEquipe);
    
    if (this.isChefEquipe) {
      console.log('✅ Accès autorisé - Chargement des ressources');
      this.loadResources();
      this.initSpeechRecognition();
      this.chargerDemandes();  // 🔥 AJOUTEZ CETTE LIGNE SI MANQUANTE

    } else {
      console.log('❌ Accès refusé - Utilisateur non autorisé');
      this.accessDenied = true;
    }
      this.loadMaintenancesTerminees();
      this.chargerFactures();



       

  }




  // Ajoute cette méthode
openPaymentPage(facture: any): void {
  this.router.navigate(['/payment', facture.id, facture.coutTotal]);
}
 loadResources() {
  this.resourceService.getAll().subscribe(data => {
    this.resources = data;
    console.log('=== RESSOURCES CHARGÉES ===');
    this.resources.forEach(r => {
      console.log(`Ressource ID ${r.id}: ${r.nom}`);
      console.log(`  - dureeVieEstimee BRUTE: "${r.dureeVieEstimee}" (type: ${typeof r.dureeVieEstimee})`);
      
      // 🔥 FORCER LA CONVERSION EN NOMBRE
      if (r.dureeVieEstimee) {
        r.dureeVieEstimee = Number(r.dureeVieEstimee);
        console.log(`  - dureeVieEstimee APRÈS CONVERSION: ${r.dureeVieEstimee} (type: ${typeof r.dureeVieEstimee})`);
      } else {
        r.dureeVieEstimee = 0;
      }
      
      // Date d'achat
      if (r.dateAchat && r.dateAchat.includes('/')) {
        const parts = r.dateAchat.split('/');
        if (parts.length === 3) {
          r.dateAchat = `${parts[2]}-${parts[1]}-${parts[0]}`;
        }
      } else if (r.dateAchat && r.dateAchat.includes('T')) {
        r.dateAchat = r.dateAchat.split('T')[0];
      }
      
      // Recalculer l'état
      r.etat = this.getEtatFromDureeVie(r.dureeVieEstimee);
    });
    
    this.filteredResources = [...this.resources];
    this.verifierAlertes();
    this.loadIAPredictions();
  });
}

iaPredictions: { [key: number]: any } = {};
loadIAPredictions() {
  console.log('🤖 Chargement des prédictions IA...');
  
  this.resources.forEach(resource => {
    const pourcentageVie = this.calculerPourcentageVie(resource);
    const risque = this.calculerRisquePanne(resource);
    
    let niveau = '';
    let couleur = '';
    let recommandation = '';
    
    if (risque >= 100) {
      niveau = 'Critique';
      couleur = '#ef4444';
      recommandation = '⚠️ REMPLACEMENT IMMÉDIAT !';
    } else if (risque >= 80) {
      niveau = 'Très élevé';
      couleur = '#f97316';
      recommandation = '🔧 Planifier remplacement urgent';
    } else if (risque >= 60) {
      niveau = 'Élevé';
      couleur = '#f59e0b';
      recommandation = '📊 Surveillance renforcée';
    } else if (risque >= 40) {
      niveau = 'Moyen';
      couleur = '#eab308';
      recommandation = '🔍 Inspection recommandée';
    } else if (risque >= 20) {
      niveau = 'Faible';
      couleur = '#10b981';
      recommandation = '✅ Surveillance normale';
    } else {
      niveau = 'Très faible';
      couleur = '#22c55e';
      recommandation = '🌟 Bon état';
    }
    
    this.iaPredictions[resource.id!] = {
      risque: risque,
      niveau: niveau,
      couleur: couleur,
      recommandation: recommandation,
      pourcentageVie: pourcentageVie
    };
    
    console.log(`🤖 ${resource.nom}: ${risque.toFixed(0)}% risque (${pourcentageVie.toFixed(0)}% vie utilisée)`);
  });
   if (this.resources.length > 0) {
      const testResource = this.resources[0];
      console.log('🔍 TEST RESSOURCE:', testResource.nom);
      console.log('🔍 dureeVieEstimee valeur:', testResource.dureeVieEstimee);
      console.log('🔍 dureeVieEstimee type:', typeof testResource.dureeVieEstimee);
      console.log('🔍 Objet complet:', JSON.stringify(testResource));
    }
}


ajouterDateAchat(resource: Resource, event: Event): void {
  event.stopPropagation();
  const date = prompt("Entrez la date d'achat (AAAA-MM-JJ) :", "2020-01-01");
  if (date) {
    resource.dateAchat = date;
    this.resourceService.update(resource.id!, resource).subscribe(() => {
      this.loadResources();
      alert('Date d\'achat ajoutée !');
    });
  }
}


calculerRisquePanne(resource: Resource): number {
  const pourcentageVie = this.calculerPourcentageVie(resource);
  
  // Formule exponentielle: risque = (pourcentageVie / 70) ^ 1.5 * 100
  // À 70% de durée de vie → risque = 100%
  let risque = Math.pow(pourcentageVie / 70, 1.5) * 100;
  
  // Limiter entre 0 et 100
  if (risque > 100) risque = 100;
  if (risque < 0) risque = 0;
  
  return risque;
}




  deleteResource(id: number) {
    if (confirm("Voulez-vous vraiment supprimer cette ressource ?")) {
      this.resourceService.delete(id).subscribe({
        next: () => this.loadResources(),
        error: err => console.error(err)
      });
    }
  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
    if (this.selectedFile) {
      const reader = new FileReader();
      reader.onload = e => this.filePreview = reader.result;
      reader.readAsDataURL(this.selectedFile);
    }
  }

 editResource(resource: Resource) {
  console.log('🔍 editResource appelé avec:', resource);
  
  this.selectedResource = { ...resource };
  
  console.log('📝 selectedResource après clonage:', this.selectedResource);
  
  // Formater la date
  if (this.selectedResource.dateAchat) {
    if (this.selectedResource.dateAchat.includes('/')) {
      const parts = this.selectedResource.dateAchat.split('/');
      if (parts.length === 3) {
        this.selectedResource.dateAchat = `${parts[2]}-${parts[1]}-${parts[0]}`;
      }
    } else if (this.selectedResource.dateAchat.includes('T')) {
      this.selectedResource.dateAchat = this.selectedResource.dateAchat.split('T')[0];
    }
  }
  
  // Convertir la durée de vie
  if (this.selectedResource.dureeVieEstimee) {
    this.selectedResource.dureeVieEstimee = Number(this.selectedResource.dureeVieEstimee);
  }
  
  this.selectedFile = null;
  this.filePreview = null;
  this.errors = {};
  
  console.log('✅ Formulaire prêt:', this.selectedResource);
}

  openAddResourceModal() {
    this.selectedResource = {
      id: 0, nom: '', type: '', etat: '', valeur: 0, dureeVieEstimee: 0, dateAchat: '', imageUrl: ''
    };
    this.selectedFile = null;
    this.filePreview = null;
    this.errors = {};
    if (this.selectedResource) {
      this.selectedResource.etat = this.getEtatFromDureeVie(this.selectedResource.dureeVieEstimee);
    }
  }

  submitResource() {
    if (!this.selectedResource) return;
    this.selectedResource.etat = this.getEtatFromDureeVie(this.selectedResource.dureeVieEstimee);
    if (!this.validateFormForModal()) return;

    if (this.selectedResource.id !== 0) {
        // MODIFICATION
        if (this.selectedFile) {
            // Avec nouvelle image - envoyer FormData
            const formData = new FormData();
  formData.append('nom', this.selectedResource.nom);
  formData.append('type', this.selectedResource.type);
  formData.append('etat', this.selectedResource.etat);
  formData.append('valeur', this.selectedResource.valeur.toString());
  formData.append('dureeVieEstimee', this.selectedResource.dureeVieEstimee.toString());
  formData.append('dateAchat', this.selectedResource.dateAchat || '');
  formData.append('file', this.selectedFile, this.selectedFile.name);
            


            console.log('📤 FormData envoyé:');
    for (let pair of (formData as any).entries()) {
        console.log('  -', pair[0], '=', pair[1]);
    }
            this.resourceService.updateWithImage(this.selectedResource.id, formData).subscribe({
                next: () => {
                    alert('Ressource mise à jour !');
                    this.resetModal();
                    this.loadResources();
                },
                error: (err) => console.error('Erreur:', err)
            });
        } else {
            // Sans nouvelle image - envoyer JSON
            const resourceToUpdate = { ...this.selectedResource };
            this.resourceService.update(resourceToUpdate.id!, resourceToUpdate).subscribe({
                next: () => {
                    alert('Ressource mise à jour !');
                    this.resetModal();
                    this.loadResources();
                },
                error: (err) => console.error('Erreur:', err)
            });
        }
    } else {
        // AJOUT
        if (!this.selectedFile) {
            this.errors.imageUrl = 'Veuillez sélectionner une image.';
            return;
        }
        if (!this.selectedResource.dateAchat) {
            this.errors.dateAchat = 'La date d\'achat est requise.';
            return;
        }
        const formData = new FormData();
        formData.append('nom', this.selectedResource!.nom);
        formData.append('type', this.selectedResource!.type);
        formData.append('etat', this.selectedResource!.etat);
        formData.append('valeur', this.selectedResource!.valeur.toString());
        formData.append('dureeVieEstimee', this.selectedResource!.dureeVieEstimee.toString());
        formData.append('dateAchat', this.selectedResource!.dateAchat);
        formData.append('file', this.selectedFile);
        
        this.resourceService.createWithImage(formData).subscribe({
            next: () => {
                alert('Ressource ajoutée !');
                this.resetModal();
                this.loadResources();
            },
            error: (err) => console.error(err)
        });
    }
}

  validateFormForModal(): boolean {
    this.errors = {};
    if (!this.selectedResource!.nom || this.selectedResource!.nom.trim().length < 3) {
      this.errors.nom = 'Le nom doit contenir au moins 3 caractères.';
    }
    if (!this.selectedResource!.type || this.selectedResource!.type.trim().length === 0) {
      this.errors.type = 'Le type est requis.';
    }
    if (!this.selectedResource!.dateAchat) {
      this.errors.dateAchat = 'La date d\'achat est requise.';
    }
    if (this.selectedResource!.valeur <= 0) {
      this.errors.valeur = 'La valeur doit être supérieure à 0.';
    }
    if (this.selectedResource!.dureeVieEstimee <= 0) {
      this.errors.dureeVieEstimee = 'La durée de vie doit être supérieure à 0.';
    }
    if (this.selectedResource!.id === 0 && !this.selectedFile) {
      this.errors.imageUrl = 'Veuillez sélectionner une image.';
    }
    return Object.keys(this.errors).length === 0;
  }

  resetModal() {
    this.selectedResource = null;
    this.selectedFile = null;
    this.filePreview = null;
    this.errors = {};
  }

 getEtatFromDureeVie(dureeVie: number): string {
  // Convertir en nombre si ce n'est pas déjà fait
  const duree = Number(dureeVie);
  
  if (!duree || duree <= 0) {
    return '';
  }
  
  if (duree < 5) {
    return 'Mauvais';
  } else if (duree >= 5 && duree <= 10) {
    return 'Moyen';
  } else {
    return 'Bon';
  }
}

  onDureeVieChange() {
    if (this.selectedResource) {
      this.selectedResource.etat = this.getEtatFromDureeVie(this.selectedResource.dureeVieEstimee);
    }
  }

  // ========== DEMANDES DE MAINTENANCE ==========
  openDemandeMaintenanceModal(resource: Resource, event: Event) {
    event.stopPropagation();
    console.log('🟢 Demande maintenance pour:', resource.nom);
    this.selectedResourceForDemande = resource;
    this.demandeMotif = '';
    this.demandeUrgence = 'MOYENNE';
    this.demandeDateRemise = new Date().toISOString().split('T')[0];
    this.showDemandeModal = true;
  }
ressourceMatricule: string = '';
selectionnerRessourcePourDemande(resource: any) {
  this.selectedResourceForDemande = resource;
  this.ressourceMatricule = resource.matricule;  // ← Stocker le matricule
}
  envoyerDemandeMaintenance() {
  if (!this.selectedResourceForDemande) return;
  if (!this.demandeMotif.trim()) {
    alert('Veuillez saisir un motif');
    return;
  }
  if (!this.demandeDateRemise) {
    alert('Veuillez indiquer la date de remise en service');
    return;
  }

  if (!this.ressourceMatricule) {
    console.error('❌ ressourceMatricule est VIDE ou NULL !');
    alert('Le matricule est obligatoire');
    return;
  }  
  
  // 🔥 Utiliser le même ID fixe
  const chefId = 1;
  
  const demande: Partial<DemandeMaintenance> = {
    ressourceMatricule: this.ressourceMatricule,
    ressourceId: this.selectedResourceForDemande.id,
    chefId: chefId,  // ID fixe
    motif: this.demandeMotif,
    urgence: this.demandeUrgence,
    dateRemiseSouhaitee: this.demandeDateRemise + 'T00:00:00',
    statut: 'EN_ATTENTE',
    ressourceImageUrl: this.selectedResourceForDemande.imageUrl,
    ressourceNom: this.selectedResourceForDemande.nom,
    ressourceType: this.selectedResourceForDemande.type
  };
  
  console.log('📦 Demande envoyée:', demande);
  
  this.demandeService.create(demande).subscribe({
    next: (response) => {
      console.log('✅ Réponse:', response);
      alert('Demande envoyée !');
      this.showDemandeModal = false;
      this.ressourceMatricule = '';
      this.demandeMotif = '';
      this.demandeDateRemise = '';
      this.selectedResourceForDemande = null;
      this.chargerDemandes();
    },
    error: (err) => {
      console.error('❌ Erreur:', err);
      alert('Erreur lors de l\'envoi');
    }
  });
}
  // ========== MÉTHODES POUR L'OCCUPATION ==========
  libererRessource(resource: Resource): void {
    resource.statut = 'DISPONIBLE';
    resource.occupePar = '';
    resource.dateDebutOccupation = '';
    resource.dateFinOccupation = '';
    
    this.resourceService.update(resource.id!, resource).subscribe({
      next: () => {
        this.loadResources();
        alert(`✅ ${resource.nom} libérée`);
      },
      error: (err: any) => console.error(err)
    });
  }

  planifierOccupation(resource: Resource): void {
    const equipe = prompt("Nom de l'équipe :");
    if (!equipe) return;
    
    const dateDebut = prompt("Date de début (YYYY-MM-DD) :", new Date().toISOString().split('T')[0]);
    if (!dateDebut) return;
    
    const dateFin = prompt("Date de fin (YYYY-MM-DD) :");
    if (!dateFin) return;
    
    if (new Date(dateFin) <= new Date(dateDebut)) {
      alert("❌ La date de fin doit être après la date de début !");
      return;
    }
    
    resource.statut = 'OCCUPE';
    resource.occupePar = equipe;
    resource.dateDebutOccupation = dateDebut;
    resource.dateFinOccupation = dateFin;
    
    this.resourceService.update(resource.id!, resource).subscribe({
      next: () => {
        this.loadResources();
        alert(`✅ ${resource.nom} assigné à ${equipe} du ${dateDebut} au ${dateFin}`);
      },
      error: (err: any) => console.error(err)
    });
  }

  remettreEnService(resource: Resource): void {
    resource.statut = 'DISPONIBLE';
    resource.etat = 'Bon';
    resource.occupePar = '';
    resource.dateDebutOccupation = '';
    resource.dateFinOccupation = '';
    
    this.resourceService.update(resource.id!, resource).subscribe({
      next: () => {
        this.loadResources();
        alert(`✅ ${resource.nom} remis en service`);
      },
      error: (err: any) => console.error(err)
    });
  }

  // ========== MÉTHODES POUR LE CHATBOT ==========
  toggleChatbot(): void {
    this.showChatbot = !this.showChatbot;
  }

  initSpeechRecognition(): void {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      this.recognition = new SpeechRecognition();
      this.recognition.lang = 'fr-FR';
      this.recognition.continuous = false;
      this.recognition.interimResults = false;
      
      this.recognition.onresult = (event: any) => {
        this.inputText = event.results[0][0].transcript;
        this.isListening = false;
        this.sendMessage(true);
      };
      
      this.recognition.onerror = () => {
        this.isListening = false;
      };
    }
  }

  startListening(): void {
    if (this.recognition) {
      this.isListening = true;
      this.recognition.start();
    } else {
      alert("Reconnaissance vocale non supportée");
    }
  }

  sendMessage(isVoice: boolean = false): void {
    if (!this.inputText.trim()) return;
    
    this.messages.push({ text: this.inputText, isUser: true, voice: isVoice });
    const question = this.inputText;
    this.inputText = '';
    
    let reponse = this.getReponseIntelligente(question);
    this.messages.push({ text: reponse, isUser: false });
  }

  getReponseIntelligente(question: string): string {
    const q = question.toLowerCase();
    
    if (q.includes('critique') || q.includes('danger')) {
      const critiques = this.resources.filter(r => this.calculerPourcentageVie(r) > 80);
      if (critiques.length === 0) return "Aucune ressource critique pour le moment.";
      return `Ressources critiques : ${critiques.map(r => r.nom).join(', ')}.`;
    }
    
    if (q.includes('camion') && (q.includes('combien') || q.includes('nombre'))) {
      const count = this.resources.filter(r => r.type === 'Camion').length;
      return `Vous avez ${count} camion(s).`;
    }
    
    if (q.includes('état') && q.includes('camion')) {
      const camion = this.resources.find(r => r.type === 'Camion');
      if (camion) {
        const age = this.calculerAge(camion);
        return `Le camion ${camion.nom} a ${age.toFixed(1)} ans, son état est ${camion.etat}.`;
      }
    }
    
    if (q.includes('total ressource')) {
      return `Vous avez ${this.resources.length} ressource(s) au total.`;
    }
    
    return "Je suis votre assistant. Posez-moi des questions sur les ressources.";
  }

  // ========== ALERTES ET FILTRES ==========
  getValeurTotale(): number {
    return this.resources.reduce((total, r) => total + r.valeur, 0);
  }

  getResourcesByEtat(etat: string): Resource[] {
    return this.resources.filter(r => r.etat === etat);
  }

  
calculerPourcentageVie(resource: Resource): number {
  console.log(`📊 Calcul pour ${resource.nom}:`);
  console.log(`  - dateAchat: ${resource.dateAchat}`);
  console.log(`  - dureeVieEstimee: ${resource.dureeVieEstimee}`);
  
  if (!resource.dateAchat || resource.dureeVieEstimee <= 0) {
    console.log(`  - ⚠️ Retour 0 (date manquante ou durée invalide)`);
    return 0;
  }
  
  const age = this.calculerAge(resource);
  console.log(`  - age calculé: ${age.toFixed(2)} ans`);
  
  const pourcentage = (age / resource.dureeVieEstimee) * 100;
  const resultat = Math.min(100, Math.max(0, pourcentage));
  
  console.log(`  - pourcentage final: ${resultat.toFixed(0)}%`);
  
  return resultat;
}

calculerAge(resource: Resource): number {
  console.log(`📅 Calcul age pour ${resource.nom}: dateAchat = "${resource.dateAchat}"`);
  
  if (!resource.dateAchat) return 0;
  
  const dateAchat = new Date(resource.dateAchat);
  const aujourdHui = new Date();
  const ageEnAnnees = (aujourdHui.getTime() - dateAchat.getTime()) / (1000 * 3600 * 24 * 365.25);
  
  console.log(`  - dateAchat parsée: ${dateAchat}`);
  console.log(`  - aujourd'hui: ${aujourdHui}`);
  console.log(`  - age: ${ageEnAnnees.toFixed(2)} ans`);
  
  return ageEnAnnees;
}

  getNiveauAlerte(resource: Resource): { niveau: string; couleur: string; icone: string; message: string } {
    const pourcentage = this.calculerPourcentageVie(resource);
    if (pourcentage >= 100) return { niveau: 'critique', couleur: '#ef4444', icone: '🔴', message: 'Fin de vie atteinte' };
    if (pourcentage >= 90) return { niveau: 'urgence', couleur: '#f97316', icone: '🟠', message: 'Urgent' };
    if (pourcentage >= 80) return { niveau: 'warning', couleur: '#f59e0b', icone: '⚠️', message: 'Attention' };
    if (pourcentage >= 70) return { niveau: 'info', couleur: '#3b82f6', icone: 'ℹ️', message: 'Info' };
    if (pourcentage >= 50) return { niveau: 'normal', couleur: '#10b981', icone: '✅', message: 'Bonne santé' };
    return { niveau: 'excellent', couleur: '#22c55e', icone: '🌟', message: 'Excellente condition' };
  }

  calculerJoursRestants(resource: Resource): number {
    if (!resource.dateAchat || resource.dureeVieEstimee <= 0) return 0;
    const dateAchat = new Date(resource.dateAchat);
    const dateFin = new Date(dateAchat);
    dateFin.setFullYear(dateFin.getFullYear() + resource.dureeVieEstimee);
    const aujourdHui = new Date();
    return Math.max(0, Math.ceil((dateFin.getTime() - aujourdHui.getTime()) / (1000 * 3600 * 24)));
  }

  verifierAlertes(): void {
    this.resources.forEach(resource => {
      const alerte = this.getNiveauAlerte(resource);
      if ((alerte.niveau === 'critique' || alerte.niveau === 'urgence') && 
          resource.dernierAlerte !== new Date().toDateString()) {
        this.afficherNotification(resource, alerte);
        resource.dernierAlerte = new Date().toDateString();
      }
    });
  }

  afficherNotification(resource: Resource, alerte: any): void {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(`🚨 Alerte: ${resource.nom}`, { body: alerte.message });
    }
    this.showToast(alerte.message, alerte.couleur);
  }

  showToast(message: string, couleur: string): void {
    const toast = document.createElement('div');
    toast.style.cssText = `position:fixed; bottom:20px; right:20px; background:${couleur}; color:white; padding:12px 20px; border-radius:8px; z-index:9999;`;
    toast.innerHTML = message;
    document.body.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => toast.remove(), 500);
    }, 3000);
  }

  filterResources(filter: string): void {
    this.selectedFilter = filter;
    if (filter === 'all') {
      this.filteredResources = [...this.resources];
    } else if (filter === 'warning') {
      this.filteredResources = this.resources.filter(r => {
        const niveau = this.getNiveauAlerte(r).niveau;
        return niveau === 'warning' || niveau === 'urgence';
      });
    } else if (filter === 'critique') {
      this.filteredResources = this.resources.filter(r => this.getNiveauAlerte(r).niveau === 'critique');
    } else {
      this.filteredResources = this.resources.filter(r => r.type === filter);
    }
  }

  get alertesCount(): number {
    return this.resources.filter(r => {
      const niveau = this.getNiveauAlerte(r).niveau;
      return niveau === 'warning' || niveau === 'urgence';
    }).length;
  }

  get critiquesCount(): number {
    return this.resources.filter(r => this.getNiveauAlerte(r).niveau === 'critique').length;
  }

  getResourcesByType(type: string): Resource[] {
    return this.resources.filter(r => r.type === type);
  }

  getStockByType(type: string): number {
    return this.resources.filter(r => r.type === type).length;
  }



  maintenancesTerminees: MaintenanceLog[] = [];
showRapportModal: boolean = false;
rapportSelectionne: MaintenanceLog | null = null;


loadMaintenancesTerminees(): void {
  this.maintenanceService.getAll().subscribe({
    next: (data) => {
      // Filtrer les maintenances qui ont une date (terminées)
      this.maintenancesTerminees = data.filter(m => m.date).sort((a, b) => 
        new Date(b.date).getTime() - new Date(a.date).getTime()
      );
      console.log('📋 Maintenances terminées chargées:', this.maintenancesTerminees.length);
    },
    error: (err) => console.error('Erreur chargement maintenances:', err)
  });
}
voirRapport(maintenance: MaintenanceLog): void {
  this.rapportSelectionne = maintenance;
  this.showRapportModal = true;
}

fermerRapport(): void {
  this.showRapportModal = false;
  this.rapportSelectionne = null;
}
getNomRessource(ressourceId: any): string {
  if (!ressourceId) return 'Ressource inconnue';
  const resource = this.resources.find(r => r.id === ressourceId);
  return resource?.nom || 'Ressource inconnue';
}

// Variable pour l'expansion
expandedRessourceId: number | null = null;

// Basculer l'affichage des maintenances d'une ressource
toggleRessourceMaintenances(ressourceId: number): void {
  if (this.expandedRessourceId === ressourceId) {
    this.expandedRessourceId = null;
  } else {
    this.expandedRessourceId = ressourceId;
  }
}

// Récupérer les maintenances d'une ressource spécifique
getMaintenancesByRessource(ressourceId: number): MaintenanceLog[] {
  return this.maintenancesTerminees.filter(m => m.ressourceId === ressourceId);
}
voirRapportsParRessource(ressourceId: number, event: Event): void {
  event.stopPropagation();
  console.log('🔍 voirRapportsParRessource appelé pour ID:', ressourceId);
  console.log('📋 maintenancesTerminees:', this.maintenancesTerminees);
  
  const maintenances = this.getMaintenancesByRessource(ressourceId);
  console.log('📋 Maintenances trouvées:', maintenances);
  
  if (maintenances.length === 0) {
    alert('Aucune maintenance terminée pour cette ressource');
    return;
  }
  
  console.log('📄 Ouverture rapport pour:', maintenances[0]);
  this.voirRapport(maintenances[0]);
}





  showEditModal = false;
  demandeEnEdition: DemandeMaintenance | null = null;
  showDeleteModal = false;
  demandeASupprimer: DemandeMaintenance | null = null;
  loading = false;

  demandes: any[] = [];
  // 📋 Charger les demandes du chef connecté
 chargerDemandes(): void {
  console.log('🟢 CHARGEMENT DES DEMANDES');
  this.loading = true;
  
  // 🔥 SOLUTION TEMPORAIRE : Utiliser un ID numérique fixe
  const chefId = 1;  // ID fixe en attendant la correction backend
  console.log('🟢 chefId (fixe temporaire):', chefId);
  
  this.demandeService.getByChef(chefId).subscribe({
    next: (data) => {
      this.demandes = data;
      this.loading = false;
      console.log('📋 Demandes chargées:', this.demandes.length);
    },
    error: (err) => {
      console.error('❌ Erreur chargement:', err);
      this.loading = false;
      alert('Erreur lors du chargement des demandes');
    }
  });
}
  // ✏️ Modifier
  modifierDemande(demande: DemandeMaintenance): void {
    if (demande.statut !== 'EN_ATTENTE') {
      alert(`Impossible de modifier une demande ${demande.statut}`);
      return;
    }
    this.demandeEnEdition = { ...demande };
    this.showEditModal = true;
  }

  // 💾 Sauvegarder modification
  sauvegarderModification(): void {
    if (!this.demandeEnEdition) return;
    
    this.loading = true;
    const demandeModifiee = {
      motif: this.demandeEnEdition.motif,
      urgence: this.demandeEnEdition.urgence,
      dateRemiseSouhaitee: this.demandeEnEdition.dateRemiseSouhaitee
    };
    
    this.demandeService.update(this.demandeEnEdition.id, demandeModifiee).subscribe({
      next: () => {
        alert('✅ Demande modifiée avec succès !');
        this.showEditModal = false;
        this.demandeEnEdition = null;
        this.chargerDemandes();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur:', err);
        alert('❌ Erreur lors de la modification');
        this.loading = false;
      }
    });
  }

  // 🗑️ Confirmer suppression
  confirmerSuppression(demande: DemandeMaintenance): void {
    if (demande.statut !== 'EN_ATTENTE') {
      alert(`Impossible de supprimer une demande ${demande.statut}`);
      return;
    }
    this.demandeASupprimer = demande;
    this.showDeleteModal = true;
  }

  // 🗑️ Supprimer
  supprimerDemande(): void {
    if (!this.demandeASupprimer) return;
    
    this.loading = true;
    this.demandeService.delete(this.demandeASupprimer.id).subscribe({
      next: () => {
        alert('🗑️ Demande supprimée avec succès !');
        this.showDeleteModal = false;
        this.demandeASupprimer = null;
        this.chargerDemandes();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur:', err);
        alert('❌ Erreur lors de la suppression');
        this.showDeleteModal = false;
        this.demandeASupprimer = null;
        this.loading = false;
      }
    });
  }

  // Annuler
  annulerModification(): void {
    this.showEditModal = false;
    this.demandeEnEdition = null;
  }

  annulerSuppression(): void {
    this.showDeleteModal = false;
    this.demandeASupprimer = null;
  }

  // Couleurs des badges
  getUrgenceColor(urgence: string): string {
    switch(urgence) {
      case 'CRITIQUE': return 'bg-danger';
      case 'HAUTE': return 'bg-warning';
      case 'MOYENNE': return 'bg-info';
      case 'BASSE': return 'bg-success';
      default: return 'bg-secondary';
    }
  }

  getStatutColor(statut: string): string {
    switch(statut) {
      case 'EN_ATTENTE': return 'bg-warning';
      case 'ACCEPTEE': return 'bg-success';
      case 'REFUSEE': return 'bg-danger';
      case 'TERMINEE': return 'bg-secondary';
      default: return 'bg-secondary';
    }
  }
 






    activeTab: 'ressources' | 'demandes' = 'ressources';
// Dans votre ResourceListComponent

// Index actuel du carrousel
currentIndex: number = 0;

// Navigation
nextResource(): void {
  if (this.currentIndex < this.filteredResources.length - 1) {
    this.currentIndex++;
  }
}

prevResource(): void {
  if (this.currentIndex > 0) {
    this.currentIndex--;
  }
}

goToResource(index: number): void {
  this.currentIndex = index;
}

// Gestion de la molette de souris
onMouseWheel(event: WheelEvent): void {
  if (event.deltaY > 0) {
    this.nextResource();
  } else if (event.deltaY < 0) {
    this.prevResource();
  }
}

// Gestion des flèches clavier (optionnel)
@HostListener('window:keydown', ['$event'])
onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'ArrowRight') {
    this.nextResource();
  } else if (event.key === 'ArrowLeft') {
    this.prevResource();
  }
}




// ========== MÉTHODES POUR LE CHAT ==========
  
  /**
   * Ouvrir le chat pour une demande
   */
  ouvrirChat(demande: any): void {
    console.log('💬 Ouverture chat pour demande:', demande.id);
    this.selectedDemandeForChat = demande;
    this.showChatModal = true;
    this.chargerMessagesChat(demande.id);
  }

  /**
   * Charger les messages de la demande
   */
  chargerMessagesChat(demandeId: number): void {
    this.messageService.getMessagesByDemande(demandeId).subscribe({
      next: (data) => {
        this.chatMessages = data;
        this.scrollToBottomChat();
        console.log('📋 Messages chargés:', data.length);
      },
      error: (err) => {
        console.error('❌ Erreur chargement messages:', err);
      }
    });
  }

  /**
   * Envoyer un message
   */
  envoyerMessageChat(): void {
  if (!this.newChatMessage.trim()) return;

  const user = this.authService.getCurrentUser();
  
  // 🔥 Vérification que user n'est pas null
  if (!user) {
    console.error('❌ Utilisateur non connecté');
    alert('Erreur: Vous devez être connecté pour envoyer un message');
    return;
  }
  
  // Vérification que user.userId existe
  if (!user.userId) {
    console.error('❌ ID utilisateur manquant');
    alert('Erreur: Identifiant utilisateur manquant');
    return;
  }

  // Récupérer l'ID de l'expert (à adapter selon votre logique)
  const expertId = this.selectedDemandeForChat?.expertId || 'EXPERT_ID_FIXE';

  const message = {
    expediteurId: user.userId,
    expediteurNom: user.email || user.userId,
    expediteurRole: user.role || 'CHEF_EQUIPE',
    destinataireId: expertId,
    destinataireRole: 'EXPERT',
    contenu: this.newChatMessage,
    demandeId: this.selectedDemandeForChat.id,
    dateEnvoi: new Date().toISOString()
  };

  this.messageService.envoyerMessage(message).subscribe({
    next: () => {
      console.log('✅ Message envoyé');
      this.newChatMessage = '';
      this.chargerMessagesChat(this.selectedDemandeForChat.id);
    },
    error: (err) => {
      console.error('❌ Erreur envoi message:', err);
      alert('Erreur lors de l\'envoi du message');
    }
  });
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
    this.showChatModal = false;
    this.selectedDemandeForChat = null;
    this.chatMessages = [];
    this.newChatMessage = '';
  }


































chargerFactures(): void {
  this.loading = true;
  
  // 🔥 Récupérer TOUTES les factures (temporairement)
  this.factureService.getAll().subscribe({
    next: (data) => {
      console.log('📋 Factures reçues:', data);
      this.facturesEnAttente = data.filter(f => f.statut === 'EN_ATTENTE');
      this.facturesPayees = data.filter(f => f.statut === 'PAYEE').slice(0, 5);
      this.totalAPayer = this.facturesEnAttente.reduce((sum, f) => sum + f.coutTotal, 0);
      this.loading = false;
    },
    error: (err) => {
      console.error('❌ Erreur:', err);
      this.loading = false;
    }
  });
}

  payerFacture(facture: Facture): void {
    if (confirm(`Payer ${facture.coutTotal}€ pour "${facture.description}" ?`)) {
      this.factureService.payerFacture(facture.id!).subscribe({
        next: () => {
          this.afficherNotificationFacture(`✅ Paiement effectué : ${facture.coutTotal}€`);
          this.chargerFactures();
        },
        error: (err) => {
          console.error('Erreur:', err);
          alert('Erreur lors du paiement');
        }
      });
    }
  }

  toggleSelection(factureId: number): void {
    if (this.selectedFactures.has(factureId)) {
      this.selectedFactures.delete(factureId);
    } else {
      this.selectedFactures.add(factureId);
    }
  }

  selectAll(): void {
    if (this.selectedFactures.size === this.facturesEnAttente.length) {
      this.selectedFactures.clear();
    } else {
      this.facturesEnAttente.forEach(f => this.selectedFactures.add(f.id!));
    }
  }

  payerGroupe(): void {
    const montant = this.getMontantSelectionne();
    if (montant === 0) return;
    
    if (confirm(`Payer ${montant}€ pour ${this.selectedFactures.size} facture(s) ?`)) {
      const ids = Array.from(this.selectedFactures);
      // Appeler API pour payer plusieurs factures
      this.factureService.payerFacturesGroupe(ids).subscribe({
        next: () => {
          this.afficherNotificationFacture(`✅ ${ids.length} facture(s) payée(s) : ${montant}€`);
          this.selectedFactures.clear();
          this.chargerFactures();
        },
        error: (err) => console.error('Erreur:', err)
      });
    }
  }

  getMontantSelectionne(): number {
    return this.facturesEnAttente
      .filter(f => this.selectedFactures.has(f.id!))
      .reduce((sum, f) => sum + f.coutTotal, 0);
  }

  afficherNotificationFacture(message: string): void {
    const toast = document.createElement('div');
    toast.className = 'payment-toast';
    toast.innerHTML = `
      <div class="toast-content">
        <span class="toast-icon">✅</span>
        <span class="toast-message">${message}</span>
      </div>
    `;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
  }

  getUrgenceClass(dateEmission: string): string {
    const dateFacture = new Date(dateEmission);
    const aujourdhui = new Date();
    const joursDiff = Math.floor((aujourdhui.getTime() - dateFacture.getTime()) / (1000 * 3600 * 24));
    
    if (joursDiff > 7) return 'urgence-critique';
    if (joursDiff > 3) return 'urgence-moyenne';
    return 'urgence-normale';
  }






  // Ajoutez ces méthodes dans la classe ResourceListComponent

openFacturesModal(): void {
  console.log('🔵 ouverture modal factures');
  this.chefId = 1;
  this.chargerFactures();
  this.showFacturesModal = true;
  console.log('🟢 showFacturesModal =', this.showFacturesModal);
}

// Fermer le modal
closeFacturesModal(): void {
  this.showFacturesModal = false;
}

// Calculer le taux de paiement
getTauxPaiement(): number {
  const total = this.facturesEnAttente.length + this.facturesPayees.length;
  if (total === 0) return 100;
  return Math.round((this.facturesPayees.length / total) * 100);
}

// Calculer le pourcentage du délai
getPourcentageDelai(dateEmission: string): number {
  const dateFacture = new Date(dateEmission);
  const aujourdhui = new Date();
  const joursDiff = Math.min(30, Math.floor((aujourdhui.getTime() - dateFacture.getTime()) / (1000 * 3600 * 24)));
  return Math.min(100, (joursDiff / 30) * 100);
}

// Rafraîchir les factures périodiquement
startFacturesPolling(): void {
  setInterval(() => {
    if (this.showFacturesModal) {
      this.chargerFactures();
    }
  }, 30000);
}

// Appeler cette méthode dans ngOnInit si nécessaire
// this.startFacturesPolling();



// Ajoutez cette méthode
openPaymentModal(facture: Facture): void {
  this.selectedFactureForPayment = facture;
  this.showPaymentModal = true;
}

closePaymentModal(): void {
  this.showPaymentModal = false;
  this.selectedFactureForPayment = null;
}









traiterPaiementReussi(paymentIntent: string): void {
  this.http.get(`http://localhost:8085/api/factures/payment-intent/${paymentIntent}`).subscribe({
    next: (facture: any) => {
      console.log('Facture trouvée:', facture);
      this.marquerFacturePayee(facture.id);
    },
    error: (err) => {
      console.error('Facture non trouvée:', err);
      this.chargerFactures();
    }
  });
}

marquerFacturePayee(factureId: number): void {
  this.http.put(`http://localhost:8085/api/factures/${factureId}/payer`, {}).subscribe({
    next: () => {
      console.log('✅ Facture marquée payée');
      this.chargerFactures();
      // 🔥 Supprime ou commente cette ligne
      // this.afficherMessageSuccess('Paiement réussi !');
      window.history.replaceState({}, document.title, window.location.pathname);
    },
    error: (err) => console.error('Erreur:', err)
  });
}
}

