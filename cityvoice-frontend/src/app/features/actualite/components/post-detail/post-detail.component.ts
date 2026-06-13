import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Post } from '../../models/post.model';
import { Commentaire } from '../../models/commentaire.model';
import { PostService } from '../../services/post.service';
import { CommentaireService } from '../../services/commentaire.service';
import { UserService } from '../../../../core/services/user.service';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
  selector: 'app-post-detail',
  templateUrl: './post-detail.component.html',
  styleUrls: ['./post-detail.component.css']
})
export class PostDetailComponent implements OnInit {

  post!: Post;
  commentaires: Commentaire[] = [];

  loading = true;
  loadingComments = false;

  showComments = false;

  newComment: string = '';

  constructor(
    private route: ActivatedRoute,
    private postService: PostService,
    private commentaireService: CommentaireService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    const postId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadPost(postId);
  }

  // ✅ Charger le post
  loadPost(id: number): void {
    this.postService.getById(id).subscribe({
      next: (data) => {
        this.post = data;
        this.loading = false;
        // Enrichir avec le nom de l'auteur si manquant
        if (data.auteurId && !data.auteurNom) {
          this.userService.getPublicProfile(String(data.auteurId))
            .pipe(catchError(() => of(null)))
            .subscribe(u => {
              if (u) this.post = { ...this.post, auteurNom: u.nom, auteurPhoto: u.photo };
            });
        }
      },
      error: (err) => {
        console.error('Erreur chargement post', err);
        this.loading = false;
      }
    });
  }

  // ✅ Bouton retour
  goBack(): void {
    window.history.back();
  }

  // ✅ Toggle commentaires
  toggleComments(): void {
    this.showComments = !this.showComments;

    if (this.showComments && this.commentaires.length === 0) {
      this.loadComments();
    }
  }

  // ✅ Charger commentaires
  loadComments(): void {
    if (!this.post?.id) return;

    this.loadingComments = true;

    this.commentaireService.getByPostId(this.post.id).subscribe({
      next: (data) => {
        this.commentaires = data;
        this.loadingComments = false;
      },
      error: (err) => {
        console.error('Erreur chargement commentaires', err);
        this.loadingComments = false;
      }
    });
  }

  // ✅ Ajouter commentaire (DTO propre)
  

}