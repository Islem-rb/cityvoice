import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarComponent } from './components/navbar/navbar.component';
import { LoaderComponent } from './components/loader/loader.component';
import { FooterComponent } from './components/footer/footer.component';
import { TranslatePipe } from './pipes/translate.pipe';
import { SafeUrlPipe } from './pipes/safe-url.pipe';
import { RouterModule } from '@angular/router';
import { SignalementDetailPopupComponent } from './components/signalement-detail-popup/signalement-detail-popup.component';
import { WeatherAlertBannerComponent } from './components/weather-alert-banner/weather-alert-banner.component';
import { FestiveBannerComponent } from './components/festive-banner/festive-banner.component';
import { ChatbotComponent } from './components/chatbot/chatbot.component';
import { ChefResolutionModalComponent } from './components/chef-resolution-modal/chef-resolution-modal.component';
import { ReactionBarComponent } from './components/reaction-bar/reaction-bar.component';
import { LeftSidebarComponent } from './components/left-sidebar/left-sidebar.component';
import { ChatPanelComponent } from './components/chat-panel/chat-panel.component';

@NgModule({
  declarations: [
    NavbarComponent,
    LoaderComponent,
    FooterComponent,
    TranslatePipe,
    SignalementDetailPopupComponent,
    WeatherAlertBannerComponent,
    FestiveBannerComponent,
    ChatbotComponent,
    ChefResolutionModalComponent,
    ReactionBarComponent,
    LeftSidebarComponent,
    ChatPanelComponent,
    SafeUrlPipe
  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
  ],
  exports: [
    NavbarComponent,
    FooterComponent,
    LoaderComponent,
    TranslatePipe,
    RouterModule,
    FormsModule,
    SignalementDetailPopupComponent,
    WeatherAlertBannerComponent,
    FestiveBannerComponent,
    ChatbotComponent,
    ChefResolutionModalComponent,
    ReactionBarComponent,
    LeftSidebarComponent,
    ChatPanelComponent,
    SafeUrlPipe
  ],
})
export class SharedModule {
  constructor() {
    console.log('PIPE REGISTERED');
  }
 }
