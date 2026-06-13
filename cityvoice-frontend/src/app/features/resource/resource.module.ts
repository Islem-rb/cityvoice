import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ResourceRoutingModule } from './resource-routing.module';
import { ResourceListComponent } from './resource-list/resource-list.component';
import { FormsModule } from '@angular/forms';
import { ResourceEditComponent } from './resource-edit/resource-edit.component';

@NgModule({
  declarations: [
    ResourceListComponent,
    ResourceEditComponent,
  ],
  imports: [
    CommonModule,
    ResourceRoutingModule,
    FormsModule,

  ],
 
})
export class ResourceModule { }
