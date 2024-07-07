import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { GameRegisterService } from '../game-register.service';
import { MatTabsModule } from '@angular/material/tabs';
import { GameInfo } from '../../api/game-info';
import { Player } from '../../api/player';

@Component({
  selector: 'app-end-room',
  standalone: true,
  imports: [ CommonModule, 
             MatTabsModule ],
  template: `
    
    <button (click)="createTextList()">Export Card List as Text</button>
    <mat-tab-group>
      <mat-tab label="Your Selections" *ngIf="player!.cardsDrafted!.length > 0">
        <div class="grid-container">
          <div class="grid-item" *ngFor="let card of player!.cardsDrafted">
            <div class="image-container">
              <img [src]="card.details.image_normal" alt="Image" class="grid-image">
            </div>
          </div>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
  styleUrl: './end-room.component.css'
})
export class EndRoomComponent {

  route: ActivatedRoute = inject(ActivatedRoute);
  gameID: string;
  playerName: string;
  gameInfo: GameInfo | undefined;
  player: Player | undefined;
  
  constructor(private gameService: GameRegisterService, private router: Router) {
    this.gameID = this.route.snapshot.params['gameID'];
    this.playerName = this.route.snapshot.params['playerName'];

    gameService.triggerGameEnd(this.gameID).then(item => {
      console.log("Ended Game: " + this.gameID);
    });
    gameService.getGameInfo(this.gameID).then(gameInfo => {
      this.gameInfo = gameInfo;
      this.player = this.gameInfo?.players.find(player => player.playerName === this.playerName);
    });
  }
   
  createTextList() {
    
    // Convert JSON object to a formatted string
    const jsonString = //this.player?.cardsDrafted.map(str => str.name + ',').join('\n')
    
    this.player?.cardsDrafted.map((str, index) => {
      return index === this.player!.cardsDrafted!.length - 1 ? str.name : str.name + ',';
    }).join('\n');


    // Create a new window and write the JSON string
    const newTab = window.open();
    newTab!.document.open();
    newTab!.document.write('<pre>' + jsonString + '</pre>');
    newTab!.document.close();

  }
}