import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActualiteRoutingModule } from './actualite-routing.module';
import { PostListComponent } from './components/post-list/post-list.component';
import { PostDetailComponent } from './components/post-detail/post-detail.component';
// LiveCommentPanelComponent est STANDALONE : on l'importe directement dans les
// templates qui en ont besoin (watch-live, start-live) — pas besoin de le
// déclarer ici.
import { LiveCommentPanelComponent } from './components/live-comment-panel/live-comment-panel.component';
import { SharedModule } from '../../shared/shared.module';
// LiveModule vit maintenant sous components/live (drag & drop depuis features/live)
import { LiveModule } from './components/live/live.module';

// Composants déplacés depuis features/user (appartiennent au travail « actualité »)
import { FriendsComponent } from './components/friends/friends.component';
import { SavedPostsComponent } from './components/saved-posts/saved-posts.component';

@NgModule({
  declarations: [
    PostListComponent,
    PostDetailComponent,
    FriendsComponent,
    SavedPostsComponent
  ],
  imports: [
    CommonModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    ActualiteRoutingModule,
    SharedModule,
    LiveModule,
    LiveCommentPanelComponent
  ],
  exports: [
    LiveCommentPanelComponent
  ]
})
export class ActualiteModule { }
