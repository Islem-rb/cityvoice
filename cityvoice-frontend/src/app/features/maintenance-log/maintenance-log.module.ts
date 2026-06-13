import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { MaintenanceLogRoutingModule } from './maintenance-log-routing.module';
import { MaintenanceLogListComponent } from './maintenance-log-list/maintenance-log-list.component';


@NgModule({
  declarations: [
    MaintenanceLogListComponent
  ],
  imports: [
    CommonModule,
    MaintenanceLogRoutingModule
  ]
})
export class MaintenanceLogModule { }
