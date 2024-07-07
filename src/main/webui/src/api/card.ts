import { CardDetail } from './card-detail';

export interface Card {
    cardID: string;
    name: string;
    details: CardDetail;
}