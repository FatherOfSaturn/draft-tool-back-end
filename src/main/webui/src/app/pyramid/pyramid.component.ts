import { PlayerStart } from './../../api/player-start';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Card } from '../../api/card'
import { FormsModule } from '@angular/forms';
import { GameRegisterService } from '../game-register.service';
import { v4 as uuidv4 } from 'uuid';
import { RouterModule } from '@angular/router';
import { GameCreationInfo } from '../../api/game-creation-info';

@Component({
    selector: 'app-pyramid',
    standalone: true,
    template: `
    <div>
      <p class="large-text">Enter the following info to start a new draft.</p>
      <input type="text" [(ngModel)]="cubeIdInput" placeholder="Enter cube id...">
      <input type="text" [(ngModel)]="player1Name" placeholder="Enter your name...">
      <input type="text" [(ngModel)]="player2Name" placeholder="Enter Player 2 name...">
      <input type="text" [(ngModel)]="draftTokens" placeholder="Enter Draft Token #...">
      <button (click)="createGame()">Create Game</button>
    </div>
    <div>
      
      <p class="large-text">Enter the following info to join a draft.</p>
      <input type="text" [(ngModel)]="gameID" placeholder="Enter game id...">
      <input type="text" [(ngModel)]="playerName" placeholder="Enter Player name...">
      <a [routerLink]="['/pyramid', this.gameID, this.playerName]">
        <button (click)="fetchGame()">Find Game</button>
      </a>
    </div>
    `,
    styleUrl: './pyramid.component.css',
    imports: [
        CommonModule,
        FormsModule,
        RouterModule,
    ]
})
export class PyramidComponent {

  route: ActivatedRoute = inject(ActivatedRoute);
  cardPackList: Card[] = [];
  selectedCard: Card | undefined;
  cubeIdInput: string = '';
  player1Name: string = '';
  player1Id: string = '';
  player2Name: string = '';
  player2Id: string = '';
  playerName: string = '';
  draftTokens: number = 0;
  gameID: string = '';
  retrievedPlayers: PlayerStart[] = [];
  testName: string = '';
  gameCreationInfo: GameCreationInfo | undefined;

  constructor(private gameService: GameRegisterService, private router: Router) {}
  
  submit() {
    console.log('Text input:', this.cubeIdInput);
    console.log('Text input:', this.player1Name);
    console.log('Text input:', this.player2Name);
    console.log('Text input:', this.draftTokens);
  }

  createGame() {
    this.player1Id = uuidv4();
    this.player2Id = uuidv4();
    const player1: PlayerStart = {playerID: this.player1Id, name: this.player1Name};
    const player2: PlayerStart = {playerID: this.player2Id, name: this.player2Name};
    
    const players: PlayerStart[] = [ player1, player2 ];
    this.gameID = uuidv4();
    
    this.gameCreationInfo = {gameID: this.gameID,
                             cubeID: this.cubeIdInput,
                             numberOfDoubleDraftPicksPerPlayer: this.draftTokens, 
                             players: players};

    this.gameService.createGame(this.gameCreationInfo).then(item => {
      console.log("Created Game: ", item.gameID);
      this.router.navigate(['/pyramid', this.gameID, this.player1Name]);
    });
  }

  fetchGame() {
    this.gameService.getGameInfo(this.gameID).then(lh => {
      console.log("pyramid Found Game: ", lh.gameID);
      console.log("pyramid PlayerName: " + this.playerName);
    })
  }
}