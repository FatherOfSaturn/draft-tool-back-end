import { PlayerStart } from './player-start';

export interface GameCreationInfo {
    gameID: string;
    cubeID: string;
    numberOfDoubleDraftPicksPerPlayer: number;
    players: PlayerStart[];
}