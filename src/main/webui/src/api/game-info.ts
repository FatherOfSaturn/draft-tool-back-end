import { Player } from './player';

export interface GameInfo {
    gameID: string;
    players: Player[];
    gameState: string;
}