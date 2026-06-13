import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {ProfileComponent} from './profile/profile.component';
import {MesPointsComponent} from './mes-points/mes-points.component';
import {MesBadgesComponent} from './mes-badges/mes-badges.component';
import {LeaderboardComponent} from './leaderboard/leaderboard.component';
import {PublicProfileComponent} from './public-profile/public-profile.component';
// Les routes « saved-posts » et « friends » ont été déplacées vers
// ActualiteRoutingModule (features/actualite/actualite-routing.module.ts).

const routes: Routes = [
  { path: 'profile',     component: ProfileComponent },
  { path: 'mes-points',  component: MesPointsComponent },
  { path: 'mes-badges',  component: MesBadgesComponent },
  { path: 'leaderboard', component: LeaderboardComponent },
  { path: 'profil/:id',  component: PublicProfileComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class UserRoutingModule { }
