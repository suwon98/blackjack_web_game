import React, { useEffect, useState, useRef } from "react";
import type { DealerDto } from "../types/game.types";
import Card from "./Card";
import '../styles/DealerBoard.css';

interface DealerBoardProps {
  dealer: DealerDto;
}

const DealerBoard: React.FC<DealerBoardProps> = ({dealer}) => {
  const [flipIndex, setFlipIndex] = useState<number | null>(null);
  
  const prevCardsRef = useRef(dealer.cards);

  useEffect(() => {
    const prevCards = prevCardsRef.current;
    
    if (dealer.cards.length >= 2 && prevCards.length >= 2) {
      if (prevCards[1].isHidden === true && dealer.cards[1].isHidden === false) {
        setFlipIndex(1);
        
        const timer = setTimeout(() => {
          setFlipIndex(null);
        }, 600);
        
        return () => clearTimeout(timer);
      }
    }
    
    prevCardsRef.current = dealer.cards;
  }, [dealer.cards]);

  return (
    <div className="dealer-board-clean">
      <div className="cards-overlap">
        {dealer.cards.map((card, idx) => {
          const isFlipping = idx === flipIndex;

          return (
            <div 
              key={idx} 
              className={`card-slot ${isFlipping ? 'flip-animation' : ''}`}
            >
              {isFlipping && (
                <div className="playing-card hidden flip-temp-back"></div>
              )}
              <Card card={card}/>
            </div>
          );
        })}
      </div>

      <div className="status-badge bottom-badge">
        <span className="name">🕴️ 딜러</span>
        <span> | 점수: {dealer.score > 0 ? dealer.score : '?'}</span>
      </div>
    </div>
  );
};

export default DealerBoard;