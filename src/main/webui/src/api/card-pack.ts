import { Card } from "./card";

export interface CardPack {
    packNumber: number;
    cardsInPack: Card[];
    originalCardsInPack: number;
    doubleDraftedFlag: boolean;
}