import { Card } from "./card";
import { CardPack } from "./card-pack";

export interface Player {
    playerName: string;
    playerID: string;
    cardPacks: CardPack[];
    cardsDrafted: Card[];
    doubleDraftPicksRemaining: number;
    currentDraftPack: number;
    readyForMerge: boolean;
}