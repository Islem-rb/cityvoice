import { NgModule } from '@angular/core';
import { CommonModule, DatePipe, UpperCasePipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminDashboardComponent } from './admin-dashboard/admin-dashboard.component';
import { AdminSidebarComponent } from './admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from './admin-topbar/admin-topbar.component';
import { UsersComponent } from './users/users.component';
import { AdminLayoutComponent } from './admin-layout/admin-layout.component';
import { InvitationCodesComponent } from './invitation-codes/invitation-codes.component';
import { AdminSignalementsComponent } from './signalements/admin-signalements.component';
import { ContratSigningComponent } from './contrats/contrat-signing.component';

import { AdminEvenementListComponent } from '../evenement/components/admin-evenement-list/admin-evenement-list.component';
import { AdminEvenementStatsComponent } from '../evenement/components/admin-evenement-stats/admin-evenement-stats.component';
import { EvenementFormComponent } from '../evenement/components/evenement-form/evenement-form.component';
import { SharedEvenementModule } from '../evenement/shared-evenement.module';
import { AdminScanComponent } from '../evenement/components/admin-scan/admin-scan.component';
import { AdminSuggestionListComponent } from '../evenement/components/admin-suggestion-list/admin-suggestion-list.component';
import { AdminSponsorListComponent } from '../evenement/components/admin-sponsor-list/admin-sponsor-list.component';
import { SharedModule } from '../../shared/shared.module';
import { RapportSponsorComponent } from '../evenement/components/rapport-sponsor/rapport-sponsor.component';

import { ProjetEditComponent } from './projet-edit/projet-edit.component';
import { ProjetDetailComponent } from './projet-detail/projet-detail.component';
import { ProjetCreateComponent } from './projet-create/projet-create.component';
import { ProjetDashboardComponent } from './projet-dashboard/projet-dashboard.component';
import { StatsComponent } from './projet-stats/stats.component'
import { AdminActualitesComponent } from './actualites/actualites.component';
import { AdminpersonnelComponent } from './adminpersonnel/adminpersonnel.component';


@NgModule({
  declarations: [
    AdminLayoutComponent,
    AdminDashboardComponent,
    AdminSidebarComponent,
    AdminTopbarComponent,
    UsersComponent,
    InvitationCodesComponent,
    AdminActualitesComponent,
    AdminSignalementsComponent,
    ContratSigningComponent,
    AdminEvenementListComponent,
    AdminEvenementStatsComponent,
    AdminpersonnelComponent,
    //EvenementFormComponent,
    AdminScanComponent,
    AdminSuggestionListComponent,
    AdminSponsorListComponent,
    RapportSponsorComponent,
    //Projet
    ProjetDetailComponent,
    ProjetEditComponent,
    ProjetCreateComponent,
    ProjetDashboardComponent,
    StatsComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    AdminRoutingModule,
    ReactiveFormsModule,
    RouterModule,
    SharedEvenementModule,
    SharedModule,
    HttpClientModule,
  ],
  providers: [
    DatePipe,
    UpperCasePipe
  ]
})
export class AdminModule { }
