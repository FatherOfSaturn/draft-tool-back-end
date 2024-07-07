import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GameRegisterService } from '../game-register.service';

@Component({
  selector: 'app-waiting-room',
  standalone: true,
  imports: [],
  template: `
    <p>
      waiting-room works!
    </p>
  `,
  styles: ``
})
export class WaitingRoomComponent {

  route: ActivatedRoute = inject(ActivatedRoute);
  gameID: string;
  playerName: string;
  private intervalId: any;
  
  constructor(private gameService: GameRegisterService, private router: Router) {
    this.gameID = this.route.snapshot.params['gameID'];
    this.playerName = this.route.snapshot.params['playerName'];
    this.startInterval();
  }

  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  startInterval(): void {
    this.intervalId = setInterval(() => {
      this.myMethod();
    }, 10000);
  }

  myMethod(): void {
    console.log('Checking Game State.');

    this.gameService.triggerPackMergeAndSwap(this.gameID).then(item => {

      console.log('Recieved Item: ' + item.toString);
      console.log("GameID: " + item.gameID);
      console.log("Was Game Merged: " + item.gameState);

      if (item.gameState === "GAME_MERGED") {
        clearInterval(this.intervalId);
        this.router.navigate(['/pyramid', this.gameID, this.playerName]);
      }
    });
  }
}