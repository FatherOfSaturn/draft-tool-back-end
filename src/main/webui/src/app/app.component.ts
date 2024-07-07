import { GameRegisterService } from './game-register.service';
import { Component, importProvidersFrom } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { APP_INITIALIZER } from '@angular/core';
import { AppConfigService } from './app-config.service';
import { HttpClientModule, provideHttpClient } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterModule, 
            MatIconModule, 
            HttpClientModule],
  template: `
  <main>
    <a [routerLink]="['/']">
      <header class="brand-name">
        <!-- <mat-icon aria-hidden="false" aria-label="Example home icon" fontIcon="home"></mat-icon> -->
        <button mat-button class="home-button">
          <mat-icon aria-hidden="false" aria-label="Example home icon" fontIcon="home"></mat-icon>
          Home
        </button>
      </header>
    </a>
    <section class="content">
      <router-outlet></router-outlet>
    </section>
  </main>
  `,
  providers: [
    // {
    //   provide: APP_INITIALIZER,
    //   multi: true,
    //   deps: [AppConfigService],
    //   useFactory: (appConfigService: AppConfigService) => {
    //     return () => {
    //       //Make sure to return a promise!
    //       return appConfigService.loadAppConfig();
    //     };
    //   }}
      ],
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'draft-tool';
  textInput: string = '';
}