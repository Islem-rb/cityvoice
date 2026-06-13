import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';

import { StartLiveComponent } from './components/start-live/start-live.component';
import { WatchLiveComponent } from './components/watch-live/watch-live.component';
import { LiveListComponent } from './components/live-list/live-list.component';
// Composant standalone du chat live (vit dans features/actualite/components)
import { LiveCommentPanelComponent } from '../live-comment-panel/live-comment-panel.component';

const routes: Routes = [
  { path: 'start', component: StartLiveComponent },
  { path: 'watch/:roomName', component: WatchLiveComponent }
];

@NgModule({
  declarations: [
    StartLiveComponent,
    WatchLiveComponent,
    LiveListComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    RouterModule.forChild(routes),
    LiveCommentPanelComponent
  ],
  exports: [
    LiveListComponent // exporté pour être utilisé dans post-list
  ]
})
export class LiveModule {}
