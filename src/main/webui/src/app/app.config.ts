import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import routes from './routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideAnimationsAsync()]
};
