import { NgModule } from '@angular/core';
import { BrowserModule} from '@angular/platform-browser';
import { CoreModule } from './core/core.module';



import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {CommonModule} from '@angular/common';
import {SharedModule} from './shared/shared.module';
import { LandingComponent } from './features/landing/landing.component';
import {FormsModule,ReactiveFormsModule} from '@angular/forms';
import {UserModule} from './features/user/user.module';
import { EquipesListComponent } from './features/equipes/equipes-list/equipes-list.component';

import { HttpClientModule } from '@angular/common/http';
import { ResourceModule } from './features/resource/resource.module';
import { ExpertDashboardComponent } from './features/expert-dashboard/expert-dashboard.component';

@NgModule({
  declarations: [
    AppComponent,
    LandingComponent,
    EquipesListComponent,
    ExpertDashboardComponent,


  ],
imports: [
  BrowserModule,
  BrowserAnimationsModule,
  CommonModule,
  AppRoutingModule,
  CoreModule,
  SharedModule,
  ReactiveFormsModule,
  FormsModule,
  UserModule,
  HttpClientModule,
  ResourceModule
],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
