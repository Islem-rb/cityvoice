import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ResourceListComponent } from './resource-list/resource-list.component';

const routes: Routes = [
    { path: '', component: ResourceListComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ResourceRoutingModule { }
