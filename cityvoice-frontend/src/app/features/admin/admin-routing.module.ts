import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminLayoutComponent }         from './admin-layout/admin-layout.component';
import { AdminDashboardComponent }      from './admin-dashboard/admin-dashboard.component';
import { UsersComponent }               from './users/users.component';
import { InvitationCodesComponent }     from './invitation-codes/invitation-codes.component';
import { AdminSignalementsComponent }   from './signalements/admin-signalements.component';
import { ContratSigningComponent }      from './contrats/contrat-signing.component';
import { AdminEvenementListComponent } from '../evenement/components/admin-evenement-list/admin-evenement-list.component';
import { AdminEvenementStatsComponent } from '../evenement/components/admin-evenement-stats/admin-evenement-stats.component';
import { EvenementFormComponent }       from '../evenement/components/evenement-form/evenement-form.component';
import { EvenementDetailComponent } from '../evenement/components/evenement-detail/evenement-detail.component';
import { AdminScanComponent } from '../evenement/components/admin-scan/admin-scan.component';
import { AdminSuggestionListComponent } from '../evenement/components/admin-suggestion-list/admin-suggestion-list.component';
import { AdminSponsorListComponent } from '../evenement/components/admin-sponsor-list/admin-sponsor-list.component';
import { RapportSponsorComponent } from '../evenement/components/rapport-sponsor/rapport-sponsor.component';
import { ProjetDashboardComponent } from './projet-dashboard/projet-dashboard.component';
import { ProjetDetailComponent }    from './projet-detail/projet-detail.component';
import { ProjetEditComponent }      from './projet-edit/projet-edit.component';
import { ProjetCreateComponent }    from './projet-create/projet-create.component';
import { StatsComponent } from './projet-stats/stats.component'
import { AdminActualitesComponent }  from './actualites/actualites.component';
import { AdminpersonnelComponent } from './adminpersonnel/adminpersonnel.component';

const routes: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    children: [
      { path: '',                redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',       component: AdminDashboardComponent },
      { path: 'users',           component: UsersComponent },
      { path: 'invitation-codes', component: InvitationCodesComponent },
      //personnel
      {path: 'personnel',component:AdminpersonnelComponent},
      //actualite
      { path: 'actualites',    component: AdminActualitesComponent },
      // ── Signalements admin — vue dédiée avec delete ────────────────
      { path: 'signalements',    component: AdminSignalementsComponent },
      // ── Contrat de travail — signature numérique ───────────────────
      { path: 'contrats/:id',    component: ContratSigningComponent },
      // Routes à implémenter — pointent sur dashboard pour l'instant
      { path: 'carte',           redirectTo: 'signalements', pathMatch: 'full' },
      { path: 'equipes',         redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'ia',              redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'rapports',        redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'settings',        redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'projets',         redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'actualites',      redirectTo: 'dashboard', pathMatch: 'full' },
      //evenements
      { path: 'evenements',        component: AdminEvenementListComponent },
      { path: 'evenements/stats',  component: AdminEvenementStatsComponent },
      { path: 'evenements/nouveau',  component: EvenementFormComponent },
      { path: 'evenements/:id/edit', component: EvenementFormComponent },
      { path: 'evenements/:id',      component: EvenementDetailComponent },
      { path: 'scan', component: AdminScanComponent },
      { path: 'suggestions', component: AdminSuggestionListComponent },
      { path: 'sponsors', component: AdminSponsorListComponent },
      { path: 'sponsors/rapport', component: RapportSponsorComponent },
      //Projet
      { path: 'projets', component: ProjetDashboardComponent },
      { path: 'projets/create', component: ProjetCreateComponent },
      { path: 'projets/stats', component: StatsComponent } ,
      { path: 'projets/:id', component: ProjetDetailComponent },
      { path: 'projets/:id/edit', component: ProjetEditComponent } ,
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AdminRoutingModule {}
