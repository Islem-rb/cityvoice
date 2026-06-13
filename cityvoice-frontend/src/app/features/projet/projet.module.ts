import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

import { ProjetRoutingModule } from './projet-routing.module';
import { ProjetFeedComponent } from './projet-feed/projet-feed.component';
import { PaymentSuccessComponent } from './projet-payment/payment-success.component';
import { PaymentFailedComponent } from './projet-payment/payment-failed.component';
import { PaymentHistoryComponent } from './payment-history/payment-history.component';

@NgModule({
  declarations: [
    ProjetFeedComponent,
    PaymentSuccessComponent,
    PaymentFailedComponent,
    PaymentHistoryComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ProjetRoutingModule,
  ]
})
export class ProjetModule {}