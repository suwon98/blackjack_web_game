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
  naturalBlackjack: boolean;
  canSplit: boolean;
  result: string | null;
  payout: number;
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
  host: string | null;
  readyPlayers: string[];
  waitingDeadline: number;
  waitingTarget: string | null;
  waitingReason: 'HOST_IDLE' | 'NOT_READY' | null;
  roomId: string;
  paused: boolean;
  title: string;
  serverTime: number;
  state: GameState;
  dealer: DealerDto | null;
  revision: number;
  deadline: number;
  roundId: number;
  minBet: number;
  maxPlayers: number;
  bets: Record<string, number>;
  insuranceDecisions: Record<string, boolean>;
  players: PlayerDto[];
  seats: Record<number, string>;
  currentPlayerIndex: number;
  remainingCards?: number; 
}