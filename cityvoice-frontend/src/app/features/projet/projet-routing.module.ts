import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ProjetFeedComponent } from './projet-feed/projet-feed.component';
import { PaymentSuccessComponent } from './projet-payment/payment-success.component';
import { PaymentFailedComponent } from './projet-payment/payment-failed.component';
import { PaymentHistoryComponent } from './payment-history/payment-history.component';
import { AuthGuard } from '../../core/guards/auth.guard';

const routes: Routes = [
  { path: '', component: ProjetFeedComponent },
  {
    path: 'payment/success',
    component: PaymentSuccessComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'payment/fail',
    component: PaymentFailedComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'payment/history',
    component: PaymentHistoryComponent,
    canActivate: [AuthGuard]
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ProjetRoutingModule {}