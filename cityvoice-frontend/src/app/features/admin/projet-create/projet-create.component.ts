import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ProjetService } from '../../projet/projet.service';
import { AuthService }   from '../../../core/services/auth.service';
import { Projet }        from '../../projet/projet.model';

@Component({
  selector: 'app-projet-create',
  templateUrl: './projet-create.component.html',
  styleUrls: ['./projet-create.component.css']
})
export class ProjetCreateComponent {

  form: FormGroup;
  loading      = false;
  imagePreview: string | null = null;
  toastMsg     = '';
  showToast    = false;
  toastSuccess = true;

  // AI 1 — Image validation
  validatingImage  = false;
  imageValid: boolean | null = null;
  imageValidReason = '';
  imageValidConfidence = '';

  // AI 2 — Project suggestion
  suggesting       = false;
  suggestion: any  = null;

  // AI price prediction
  predicting = false;
  aiResult: {
    montant: number; fourchetteBasse: number;
    fourchetteHaute: number; justification: string;
  } | null = null;

  categories = [
    'Infrastructure', 'Espaces verts', 'Culture', 'Mobilité', 'Autre'
  ];

  gouvernorats = [
    'Ariana','Béja','Ben Arous','Bizerte','Gabès',
    'Gafsa','Jendouba','Kairouan','Kasserine','Kébili',
    'Le Kef','Mahdia','La Manouba','Médenine','Monastir',
    'Nabeul','Sfax','Sidi Bouzid','Siliana','Sousse',
    'Tataouine','Tozeur','Tunis','Zaghouan'
  ];

  constructor(
    private fb:           FormBuilder,
    private projetService: ProjetService,
    private authService:  AuthService,
    private router:       Router
  ) {
    this.form = this.fb.group({
      titre:        ['', [Validators.required, Validators.minLength(5)]],
      description:  ['', [Validators.required, Validators.minLength(20)]],
      categorie:    ['', Validators.required],
      location:     ['', Validators.required],
      tags:         [''],
      dureeDays:    [30, [Validators.required, Validators.min(1)]],
      dateDebut:    ['', Validators.required],
      montantCible: [null],
    });
  }

  // ── AI 1: Auto-validate image when uploaded ───────────
  onImageChange(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      this.imagePreview        = reader.result as string;
      this.imageValid          = null;
      this.imageValidReason    = '';

      // Trigger validation automatically
      this.validateImageWithAI();
    };
    reader.readAsDataURL(file);
  }

validateImageWithAI(): void {
  const desc  = this.form.get('description')?.value || '';
  const titre = this.form.get('titre')?.value || '';

  if (!this.imagePreview) return;
  if (desc.length < 10) {
    this.imageValid       = null;
    this.imageValidReason = 'Ajoutez une description pour valider l\'image';
    return;
  }

  this.validatingImage  = true;
  this.imageValid       = null;
  this.imageValidReason = '';

  this.projetService.validateImage({
    image:       this.imagePreview,
    description: desc,
    titre:       titre
  }).subscribe({
    next: (res) => {
      this.validatingImage      = false;
      this.imageValid           = res.matches;
      this.imageValidReason     = res.reason;
      this.imageValidConfidence = res.confidence;
    },
    error: () => {
      this.validatingImage  = false;
      this.imageValid       = false;  // ← block on error, don't allow
      this.imageValidReason = 'Validation impossible — vérifiez votre connexion';
    }
  });
}

  // Re-validate when description changes (debounced)
  private validateTimer: any;
  onDescriptionChange(): void {
    if (this.imagePreview) {
      clearTimeout(this.validateTimer);
      this.validateTimer = setTimeout(() => this.validateImageWithAI(), 1200);
    }
  }

  removeImage(): void {
    this.imagePreview        = null;
    this.imageValid          = null;
    this.imageValidReason    = '';
  }

  // ── AI 2: Suggest project ─────────────────────────────
  suggestProject(): void {
    const gov = this.form.get('location')?.value;
    if (!gov) {
      this.toast('Veuillez d\'abord choisir un gouvernorat', false);
      return;
    }

    this.suggesting = true;
    this.suggestion = null;

    this.projetService.suggestProject(gov).subscribe({
      next: (res) => {
        this.suggesting = false;
        this.suggestion = res.result;
      },
      error: () => {
        this.suggesting = false;
        this.toast('Erreur IA suggestion', false);
      }
    });
  }

  acceptSuggestion(): void {
    if (!this.suggestion) return;
    this.form.patchValue({
      titre:        this.suggestion.titre       || '',
      description:  this.suggestion.description || '',
      categorie:    this.suggestion.categorie   || '',
      montantCible: this.suggestion.montantEstime || null,
    });
    this.suggestion = null;
    this.toast('Suggestion appliquée ✓', true);
  }

  dismissSuggestion(): void { this.suggestion = null; }

  // ── AI price prediction ───────────────────────────────
  predictPrice(): void {
    const desc = this.form.get('description')?.value;
    const cat  = this.form.get('categorie')?.value;
    const loc  = this.form.get('location')?.value;
    const tit  = this.form.get('titre')?.value;

    if (!desc || desc.length < 20) {
      this.toast('Écrivez une description (min. 20 car.)', false);
      return;
    }

    this.predicting = true;
    this.aiResult   = null;

    this.projetService.predictPrice({
      description: desc, categorie: cat || '',
      location: loc || '', titre: tit || '',
    }).subscribe({
      next: (res: any) => {
        this.predicting = false;
        const data = res.result;
        if (!data || !data.montant) {
          this.toast('Réponse IA invalide', false); return;
        }
        this.aiResult = {
          montant:         Number(data.montant),
          fourchetteBasse: Number(data.fourchetteBasse),
          fourchetteHaute: Number(data.fourchetteHaute),
          justification:   data.justification || '',
        };
        this.form.patchValue({ montantCible: this.aiResult.montant });
        this.toast('Prix prédit ✓', true);
      },
      error: () => { this.predicting = false; this.toast('Erreur IA', false); }
    });
  }

  // ── Submit ────────────────────────────────────────────
  submit(): void {
  if (this.form.invalid) { this.form.markAllAsTouched(); return; }
  if (this.imagePreview && this.validatingImage) {
    this.toast('Attendez la validation de l\'image', false);
    return;
  }
    // Block if image is invalid
    if (this.imageValid === false) {
      this.toast('L\'image ne correspond pas à la description', false);
      return;
    }

    this.loading = true;
    const v = this.form.value;

    const projet: Projet = {
      titre:       v.titre,
      description: v.description,
      categorie:   v.categorie,
      location:    v.location || '',
      tags:        v.tags     || '',
      dureeDays:   Number(v.dureeDays),
      dateDebut:   v.dateDebut,
      image:       this.imagePreview || undefined,
      adminId:     this.authService.getUserId(),
      adminNom:    this.authService.getNom(),
      collecte:    v.montantCible
        ? { montantCible: Number(v.montantCible), montantCollecte: 0 }
        : undefined,
    };

    this.projetService.create(projet).subscribe({
      next: () => {
        this.loading = false;
        this.toast('Projet créé ✓', true);
        setTimeout(() => this.router.navigate(['/admin/projets']), 1400);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 201 || err.status === 200) {
          this.toast('Projet créé ✓', true);
          setTimeout(() => this.router.navigate(['/admin/projets']), 1400);
        } else {
          this.toast('Erreur lors de la création', false);
        }
      }
    });
  }

  cancel(): void { this.router.navigate(['/admin/projets']); }

  blockInvalidChar(e: KeyboardEvent): void {
    if (['e','E','+','-'].includes(e.key)) e.preventDefault();
  }

  private toast(msg: string, success: boolean): void {
    this.toastMsg = msg; this.toastSuccess = success;
    this.showToast = true;
    setTimeout(() => this.showToast = false, 2800);
  }

  isInvalid(f: string): boolean {
    const c = this.form.get(f);
    return !!(c?.invalid && c?.touched);
  }
}