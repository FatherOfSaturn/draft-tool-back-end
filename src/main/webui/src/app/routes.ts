import { Routes } from '@angular/router';
import { PyramidComponent } from './pyramid/pyramid.component';
import { AppComponent } from './app.component';
import { GridComponent } from './grid/grid.component';
import { WaitingRoomComponent } from './waiting-room/waiting-room.component';
import { EndRoomComponent } from './end-room/end-room.component';

const routeConfig: Routes = [
  {
    path: '',
    component: PyramidComponent,
    title: 'Home page'
  },
  {
    path: 'pyramid/:gameID/:playerName',
    component: GridComponent,
    title: 'Pyramid Draft In Progress'
  },
  {
    path: 'waiting/:gameID/:playerName',
    component: WaitingRoomComponent,
    title: 'Waiting'
  },
  {
    path: 'endGame/:gameID/:playerName',
    component: EndRoomComponent,
    title: 'End'
  }
];

export default routeConfig;
