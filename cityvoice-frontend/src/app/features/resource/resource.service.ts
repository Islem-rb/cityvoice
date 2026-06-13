import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Resource {
  id: number;
  nom: string;
  type: string;
  etat: string;
  valeur: number;
  dureeVieEstimee: number;
  dateAchat?: string;  // ← À AJOUTER
  imageUrl?: string;  // ← champ pour ton image
  dernierAlerte?: string;
  statut?: string;        // DISPONIBLE, OCCUPE, EN_MAINTENANCE
  occupePar?: string;
  dateDebutOccupation?: string;
  dateFinOccupation?: string;
  matricule?: string;  // ← AJOUTER CETTE LIGNE
  // ← AJOUTE CETTE LIGNE

  // ← À AJOUTER (date de la dernière alerte)

}

@Injectable({
  providedIn: 'root'
})
export class ResourceService {
  getResources() {
    throw new Error('Method not implemented.');
  }
  

  private apiUrl = 'http://localhost:8085/api/ressources';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Resource[]> {
    return this.http.get<Resource[]>(this.apiUrl);
  }

  // Dans resource.service.ts, ajoutez cette méthode :

getById(id: number): Observable<Resource> {
  return this.http.get<Resource>(`${this.apiUrl}/${id}`);
}

 // 🔹 pour JSON (ancien code)
create(resource: Resource): Observable<Resource> {
  return this.http.post<Resource>(this.apiUrl, resource);
}

// 🔹 pour FormData (image + ajout moderne)
createWithImage(formData: FormData): Observable<Resource> {
  return this.http.post<Resource>(this.apiUrl, formData);
}

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
  update(id: number, resource: Resource): Observable<Resource> {
    return this.http.put<Resource>(`${this.apiUrl}/${id}`, resource);
  }


uploadImage(formData: FormData): Observable<string> {
  return this.http.post('http://localhost:8085/api/ressources/upload', formData, { responseType: 'text' });
}





// resource.service.ts
// resource.service.ts
getImageUrl(imageUrl?: string): string {
  if (!imageUrl) {
    return '';
  }
  
  console.log('🖼️ Image URL reçue:', imageUrl);
  
  // Si l'URL est déjà complète
  if (imageUrl.startsWith('http://') || imageUrl.startsWith('https://')) {
    return imageUrl;
  }
  
  // Si l'URL commence par /api/images/ (nouveau format)
  if (imageUrl.startsWith('/api/images/')) {
    return `http://localhost:8085${imageUrl}`;
  }
  
  // Si l'URL commence par /images/ (ancien format)
  if (imageUrl.startsWith('/images/')) {
    // Rediriger vers le nouveau endpoint
    const fileName = imageUrl.replace('/images/', '');
    return `http://localhost:8085/api/images/${fileName}`;
  }
  
  return `http://localhost:8085/api/images/${imageUrl}`;
}




predictPanne(id: number): Observable<any> {
  return this.http.get(`${this.apiUrl}/${id}/predict`);
}



updateWithImage(id: number, formData: FormData): Observable<Resource> {
    return this.http.put<Resource>(`${this.apiUrl}/${id}`, formData);
}


}