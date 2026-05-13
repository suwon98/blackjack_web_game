import React from "react";
import type { CardDto } from "../types/game.types";
import '../styles/Card.css';

interface CardProps {
  card: CardDto;
}

const Card: React.FC<CardProps> = ({card}) => {
  if(card.isHidden) {
    return <div className="playing-card hidden"></div>;
  }

  const getSuitSymbolAndColor = (suit: string) => {
    switch (suit) {
      case 'HEART': return {symbol: '♥', color: 'red'};
      case 'DIAMOND': return { symbol: '♦', color: 'red' };
      case 'SPADE': return { symbol: '♠', color: 'black' };
      case 'CLUB': return { symbol: '♣', color: 'black' };
      default: return { symbol: '?', color: 'black' };
    }
  };

  const {symbol, color} = getSuitSymbolAndColor(card.suit);

  const getDenominationText = (deno: string) => {
    const map: Record<string, string> = {
      'ACE': 'A', 'TWO': '2', 'THREE': '3', 'FOUR': '4',
      'FIVE': '5', 'SIX': '6', 'SEVEN': '7', 'EIGHT': '8',
      'NINE': '9', 'TEN': '10', 'JACK': 'J', 'QUEEN': 'Q', 'KING': 'K'
    };
    return map[deno] || deno;
  };

  const text = getDenominationText(card.denomination);

  return (
    <div className={`playing-card ${color}`}>
      <div className="card-top-left">
        <span>{text}</span>
        <span>{symbol}</span>
      </div>
      <div className="card-center">
        {symbol}
      </div>
      <div className="card-bottom-right">
        <span>{text}</span>
        <span>{symbol}</span>
      </div>
    </div>
  );
};

export default Card;