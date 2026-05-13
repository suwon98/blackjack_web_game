import React from "react";
import { useParams } from "react-router-dom";
import { useGameStore } from "../store/useGameStore";
import type { PlayerAction } from "../types/game.types";
import '../styles/ActionButtons.css';

interface ActionButtonsProps {
  handIndex: number;
  playerMoney: number;
  betAmount: number;
  cardCount: number;
  handCount: number;
}

const ActionButtons: React.FC<ActionButtonsProps> = ({
  handIndex, playerMoney, betAmount, cardCount, handCount
}) => {
  const {roomId} = useParams<{roomId: string}>();
  const {sendAction} = useGameStore();

  if(!roomId) return null;

  const handleAction = (action: PlayerAction) => {
    sendAction(roomId, action, handIndex);
  };

  const canDoubleDown = playerMoney >= betAmount && cardCount === 2;
  const canSplit = playerMoney >= betAmount && cardCount === 2;
  const canSurrender = cardCount === 2 && handCount === 1;

  return (
    <div className="action-buttons-container">
      <button className="btn-action btn-hit" onClick={() => handleAction('HIT')}>
        Hit
      </button>
      <button className="btn-action btn-stand" onClick={() => handleAction('STAND')}>
        Stand
      </button>
      <button
        className="btn-action btn-special"
        onClick={() => handleAction('DOUBLE_DOWN')}
        disabled={!canDoubleDown}
      >
        Double Down
      </button>
      <button 
        className="btn-action btn-special"
        onClick={() => handleAction('SPLIT')}
        disabled={!canSplit}
      >
        Split
      </button>
      <button
        className="btn-action btn-special"
        onClick={() => handleAction('SURRENDER')}
        disabled={!canSurrender}
      >
        Surrender
      </button>
    </div>
  );
};

export default ActionButtons;