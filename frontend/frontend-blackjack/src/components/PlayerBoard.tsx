import React from "react";
import type { PlayerDto, GameState } from "../types/game.types";
import Card from "./Card";

interface PlayerBoardProps {
  player: PlayerDto;
  isMe: boolean;
  isCurrentTurn: boolean;
  gameState: GameState;
  dealerScore: number;
}

const PlayerBoard: React.FC<PlayerBoardProps> = ({player, isMe, isCurrentTurn, gameState, dealerScore}) => {
  
  const getHandResult = (handScore: number, handStatus: string, cardCount: number) => {
    if (gameState !== 'FINISHED') return null;
    if (handStatus === 'BUSTED') return { text: 'BUST', color: 'gray' };
    if (handStatus === 'SURRENDERED') return { text: 'SURRENDER', color: 'gray' };
    
    const isPlayerBJ = handScore === 21 && cardCount === 2;
    const isDealerBJ = dealerScore === 21; 
    
    if (isPlayerBJ) {
      if (isDealerBJ) return { text: 'PUSH', color: 'push' }; 
      return { text: 'BLACKJACK!', color: 'win' }; 
    }

    if (dealerScore > 21) return { text: 'WIN!', color: 'win' };
    if (handScore > dealerScore) return { text: 'WIN!', color: 'win' };
    if (handScore === dealerScore) return { text: 'PUSH', color: 'push' };
    if (handScore < dealerScore) return { text: 'LOSE', color: 'lose' };
    return null;
  };

  const activeHandIndex = player.hands.findIndex(h => h.status === 'PLAYING');

  return (
    <div className={`player-board-clean ${isCurrentTurn ? 'active-turn' : ''} ${isMe ? 'is-me' : ''}`}>
      <div className="hands-wrapper">
        {player.hands.map((hand, handIdx) => {
          const isBlackjack = hand.score === 21 && hand.cards.length === 2;
          const result = getHandResult(hand.score, hand.status, hand.cards.length);
          
          const isPlayingThisHand = isCurrentTurn && handIdx === activeHandIndex;

          return (
            <div 
              key={handIdx} 
              className={`hand-container ${isPlayingThisHand ? 'playing-hand-highlight' : ''}`} 
              style={{ position: 'relative' }}
            >
              
              <div className={`cards-overlap ${result && result.color === 'lose' ? 'dimmed-cards' : ''}`}>
                {hand.cards.map((card, cardIdx) => (
                  <div key={cardIdx} className="card-slot">
                    <Card card={card} />
                  </div>
                ))}
              </div>

              {result && (
                <div className={`result-stamp stamp-${result.color}`}>
                  {result.text}
                </div>
              )}

              <div className="status-badge top-badge">
                벳: {hand.betAmount.toLocaleString()} | 점수: {hand.score} 
                {isBlackjack ? (
                  <span className="blackjack-highlight"> ✨ BLACKJACK</span>
                ) : (
                  hand.status !== 'PLAYING' && gameState !== 'FINISHED' && ` (${hand.status})`
                )}
              </div>
              
            </div>
          );
        })}
      </div>

      <div className="status-badge bottom-badge">
        <span className="name">{player.name} {isMe && '(나)'}</span>
        <span className="money">💰 {player.money.toLocaleString()}</span>
        {player.hasInsurance && <span>🛡️</span>}
      </div>
    </div>
  );
};

export default PlayerBoard;