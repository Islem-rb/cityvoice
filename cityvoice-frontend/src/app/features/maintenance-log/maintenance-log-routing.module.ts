import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MaintenanceLogListComponent } from './maintenance-log-list/maintenance-log-list.component';

const routes: Routes = [  { path: '', component: MaintenanceLogListComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class MaintenanceLogRoutingModule { }
