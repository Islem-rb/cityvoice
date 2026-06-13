import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PostListComponent } from './components/post-list/post-list.component';
import { PostDetailComponent } from './components/post-detail/post-detail.component';
import { FriendsComponent } from './components/friends/friends.component';
import { SavedPostsComponent } from './components/saved-posts/saved-posts.component';

const routes: Routes = [
  { path: '',            component: PostListComponent },
  { path: 'friends',     component: FriendsComponent },
  { path: 'saved-posts', component: SavedPostsComponent },
  // NB: le path ':id' doit rester en dernier pour ne pas masquer les routes ci-dessus
  { path: ':id',         component: PostDetailComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ActualiteRoutingModule {}
