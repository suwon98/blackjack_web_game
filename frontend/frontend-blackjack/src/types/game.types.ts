export type GameState = 
  | 'WAITING'
  | 'BETTING'
  | 'DEALING'
  | 'INSURANCE'
  | 'DEALER_PEEK'
  | 'PLAYER_TURN'
  | 'DEALER_TURN'
  | 'FINISHED';

export type HandStatus = 
  | 'PLAYING' 
  | 'STOOD' 
  | 'BUSTED' 
  | 'SURRENDERED' 
  | 'DOUBLE_DOWN';

export type PlayerAction = 
  | 'HIT' 
  | 'STAND' 
  | 'DOUBLE_DOWN' 
  | 'SPLIT' 
  | 'SURRENDER';

export interface CardDto {
  suit: string;
  denomination: string;
  isHidden: boolean;
}

export interface HandDto {
  cards: CardDto[];
  betAmount: number;
  score: number;
  status: HandStatus;
}

export interface PlayerDto {
  name: string;
  money: number;
  hasInsurance: boolean;
  hands: HandDto[];
}

export interface DealerDto {
  cards: CardDto[];
  score: number;
}

export interface GameStatusResponse {
  roomId: string;
  state: GameState;
  dealer: DealerDto;
  players: PlayerDto[];
  seats?: Record<number, string>;
  currentPlayerIndex: number | null;
  remainingCards?: number; 
}