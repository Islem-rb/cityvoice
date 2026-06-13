import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Resource, ResourceService } from '../resource.service';

@Component({
  selector: 'app-resource-edit',
  templateUrl: './resource-edit.component.html'
})
export class ResourceEditComponent {

  @Input() resource!: Resource;  // la ressource à éditer
  @Output() updated = new EventEmitter<Resource>();
  @Output() cancel = new EventEmitter<void>();

  errors: any = {};
  selectedFile: any;

  constructor(private resourceService: ResourceService) {}

  validateForm(): boolean {
  this.errors = {};

  if (!this.resource.nom || this.resource.nom.trim().length < 3) {
    this.errors.nom = 'Le nom doit contenir au moins 3 caractères.';
  }

  if (!this.resource.type) {
    this.errors.type = 'Le type est requis.';
  }

  if (!this.resource.etat) {
    this.errors.etat = 'L’état est requis.';
  }

  if (this.resource.valeur <= 0) {
    this.errors.valeur = 'Valeur invalide.';
  }

  if (this.resource.dureeVieEstimee <= 0) {
    this.errors.dureeVieEstimee = 'Durée invalide.';
  }

  // 🔥 Validation image seulement si nouvelle image choisie
  if (this.selectedFile) {
    const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg'];

    if (!allowedTypes.includes(this.selectedFile.type)) {
      this.errors.image = 'Format image invalide (PNG/JPG seulement)';
    }

    if (this.selectedFile.size > 2 * 1024 * 1024) {
      this.errors.image = 'Image trop grande (max 2MB)';
    }
  }

  return Object.keys(this.errors).length === 0;
}

  updateResource() {
  alert("CLICK OK"); // test du click

  if (!this.validateForm()) return;

  // 🔹 Test sans image
  this.selectedFile = null;

  this.resourceService.update(this.resource.id!, this.resource)
    .subscribe({
      next: (res) => {
        alert('Ressource mise à jour !');
        this.updated.emit(res);
      },
      error: err => console.error(err)
    });
}
}