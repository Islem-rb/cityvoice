import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';
import {LandingComponent} from './features/landing/landing.component';
import {AdminScanComponent} from './features/evenement/components/admin-scan/admin-scan.component';
import { ExpertDashboardComponent } from './features/expert-dashboard/expert-dashboard.component';
import { TechnicienDashboardComponent } from './features/technicien/technicien-dashboard/technicien-dashboard.component';
import { PaymentFormComponent } from './features/payment-form/payment-form.component';
import { PaymentSuccessComponent } from './features/payment-success/payment-success.component';

const routes: Routes = [
  { path: '',       component: LandingComponent },
  { path: 'landing', component: LandingComponent },

  // Landing page publique
  {
    path : '',
    component: LandingComponent
  },
  {
    path : 'landing',
    component: LandingComponent,
  },
  // Auth (public)
  {
    path: 'auth',
    loadChildren: () =>
      import('./features/auth/auth.module').then(m => m.AuthModule),
  },

  // Protégées par AuthGuard
  {
    path: 'dashboard',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./features/dashboard/dashboard.module').then(m => m.DashboardModule),
  },
  {
    path: 'signaler',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./features/signalement/signalement.module').then(m => m.SignalementModule),
  },
  {
    path: 'evenements',
    loadChildren: () =>
      import('./features/evenement/evenement.module').then(m => m.EvenementModule),
    //canActivate: [AuthGuard],
  },
  {
    path: 'projets',
    loadChildren: () =>
      import('./features/projet/projet.module').then(m => m.ProjetModule),
    canActivate: [AuthGuard],
  },

  {
    path: 'actualites',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./features/actualite/actualite.module').then(m => m.ActualiteModule),
  },

  // LiveModule a été déplacé sous features/actualite/components/live
  {
    path: 'live',
    loadChildren: () =>
      import('./features/actualite/components/live/live.module').then(m => m.LiveModule)
  },

  // ===== USER MODULE — profile, saved-posts, friends, leaderboard… =====
  {
    path: 'personnel',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./features/personnel/personnel.module').then(m => m.PersonnelModule),
  },

  // ===== ADMIN MODULE =====
  {
    path: 'admin',
    canActivate: [AuthGuard],
    data: { role: 'ADMIN_VILLE' },
    loadChildren: () => import('./features/admin/admin.module')
      .then(m => m.AdminModule)
  },
  {
    path: 'user',
    canActivate: [AuthGuard],
    loadChildren: () => import('./features/user/user.module').then(m => m.UserModule)
  },
  {
    path: 'chef',
    canActivate: [AuthGuard],
    data: { role: 'CHEF_EQUIPE' },
    loadChildren: () =>
      import('./features/chef-equipe/chef-equipe.module').then(m => m.ChefEquipeModule),
  },
  {
    path: 'mes-signalements',
    redirectTo: 'signaler/mes-signalements',
    pathMatch: 'full'
  },
  {
    path: 'signalement/voice',
    redirectTo: 'signaler/voice',
    pathMatch: 'full'
  },




  { path: 'technicien', component: TechnicienDashboardComponent },



  { path: 'payment/:factureId/:montant', component: PaymentFormComponent },

  { path: 'payment-success', component: PaymentSuccessComponent },


{ path: 'maintenance-log', loadChildren: () => import('./features/maintenance-log/maintenance-log.module').then(m => m.MaintenanceLogModule) },
{ path: 'resource', loadChildren: () => import('./features/resource/resource.module').then(m => m.ResourceModule) },
  { path: 'expert-dashboard', component: ExpertDashboardComponent },

  { path: '**', redirectTo: '' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {
    anchorScrolling: 'enabled',
    scrollPositionRestoration: 'enabled'
  })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
