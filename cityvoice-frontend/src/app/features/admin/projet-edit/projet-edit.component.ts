import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProjetService } from '../../projet/projet.service';
import { Projet } from '../../projet/projet.model';

@Component({
  selector: 'app-projet-edit',
  templateUrl: './projet-edit.component.html',
  styleUrls: ['./projet-edit.component.css']
})
export class ProjetEditComponent implements OnInit {

  form!: FormGroup;
  loading      = false;
  saving       = false;
  imagePreview: string | null = null;
  toastMsg     = '';
  showToast    = false;
  toastSuccess = true;
  projetId!:   number;

  categories = [
    'Infrastructure', 'Espaces verts', 'Culture', 'Mobilité', 'Autre'
  ];

  constructor(
    private fb: FormBuilder,
    private projetService: ProjetService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.projetId = Number(this.route.snapshot.paramMap.get('id'));
    this.loading  = true;

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

    this.projetService.getById(this.projetId).subscribe({
      next: (p: Projet) => {
        this.loading      = false;
        this.imagePreview = p.image || null;
        this.form.patchValue({
          titre:        p.titre,
          description:  p.description,
          categorie:    p.categorie,
          location:     p.location,
          tags:         p.tags,
          dureeDays:    p.dureeDays,
          dateDebut:    p.dateDebut,
          montantCible: p.collecte?.montantCible || null,
        });
      },
      error: () => {
        this.loading = false;
        this.toast('Projet introuvable', false);
      }
    });
  }

  onImageChange(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { this.imagePreview = reader.result as string; };
    reader.readAsDataURL(file);
  }

  removeImage(): void { this.imagePreview = null; }

 submit(): void {
  if (this.form.invalid) { this.form.markAllAsTouched(); return; }
  this.saving = true;
  const v = this.form.value;
  const updated: Projet = {
    titre:       v.titre,
    description: v.description,
    categorie:   v.categorie,
    location:    v.location || '',
    tags:        v.tags     || '',
    dureeDays:   Number(v.dureeDays),
    dateDebut:   v.dateDebut,
    image:       this.imagePreview || undefined,
    collecte: v.montantCible
      ? { montantCible: Number(v.montantCible), montantCollecte: 0 }
      : undefined,
  };

  this.projetService.update(this.projetId, updated).subscribe({
    next: () => {
      this.saving = false;
      this.toast('Projet mis à jour ✓', true);
      setTimeout(() => this.router.navigate(['/admin/projets']), 1400);
    },
    error: () => {
      this.saving = false;
      this.toast('Erreur lors de la mise à jour', false);
    }
  });
}

  cancel(): void { this.router.navigate(['/admin/projets']); }

  private toast(msg: string, success: boolean): void {
    this.toastMsg = msg; this.toastSuccess = success;
    this.showToast = true;
    setTimeout(() => this.showToast = false, 2800);
  }

  isInvalid(f: string): boolean {
    const c = this.form.get(f);
    return !!(c?.invalid && c?.touched);
  }
  blockInvalidChar(e: KeyboardEvent): void {
  if (['e','E','+','-'].includes(e.key)) e.preventDefault();
}
gouvernorats = [
  'Ariana', 'Béja', 'Ben Arous', 'Bizerte', 'Gabès',
  'Gafsa', 'Jendouba', 'Kairouan', 'Kasserine', 'Kébili',
  'Le Kef', 'Mahdia', 'La Manouba', 'Médenine', 'Monastir',
  'Nabeul', 'Sfax', 'Sidi Bouzid', 'Siliana', 'Sousse',
  'Tataouine', 'Tozeur', 'Tunis', 'Zaghouan'
];
}