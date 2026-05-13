import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useGameStore } from '../store/useGameStore';
import { leaveRoom } from '../api/roomApi';
import DealerBoard from '../components/DealerBoard';
import PlayerBoard from '../components/PlayerBoard';
import ActionButtons from '../components/ActionButtons';
import '../styles/GamePage.css';
import '../styles/Board.css';

const GamePage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();

  const { 
    isConnected, gameData, currentUsername, connect, disconnect, 
    startGame, sendBet, sendInsurance, sendSeat 
  } = useGameStore();
  
  const [betAmount, setBetAmount] = useState<number>(0);
  const [timeLeft, setTimeLeft] = useState(10);
  const [hasBet, setHasBet] = useState(false);
  const [prevGameState, setPrevGameState] = useState<string | undefined>(undefined);

  const myPlayerInfo = gameData?.players?.find(p => p.name === currentUsername);
  const isMyTurn = gameData?.state === 'PLAYER_TURN' && gameData?.players[gameData.currentPlayerIndex]?.name === currentUsername;
  const activeHandIndex = myPlayerInfo?.hands.findIndex(h => h.status === 'PLAYING') ?? -1;
  const activeHand = activeHandIndex >= 0 ? myPlayerInfo?.hands[activeHandIndex] : null;

  useEffect(() => {
    if (roomId && currentUsername) {
      connect(roomId, currentUsername);
    }
  }, [roomId, currentUsername, connect]);

  useEffect(() => {
    if (isConnected && roomId && currentUsername) {
      const stompClient = useGameStore.getState().stompClient;
      if (stompClient) {
        stompClient.publish({
          destination: '/app/game/sync',
          body: JSON.stringify({ roomId }),
        });
      }
    }
  }, [isConnected, roomId, currentUsername]);

  if (gameData?.state !== prevGameState) {
    setPrevGameState(gameData?.state);
    if (gameData?.state === 'BETTING') {
      setTimeLeft(10);
      setHasBet(false);
      setBetAmount(0); 
    }
  }

  useEffect(() => {
    if (gameData?.state === 'BETTING') {
      const timer = window.setInterval(() => setTimeLeft((prev) => (prev > 0 ? prev - 1 : 0)), 1000);
      return () => window.clearInterval(timer);
    }
  }, [gameData?.state]);

  const getVisualPos = (actualSeatIdx: number) => {
    const myActualSeat = Object.keys(gameData?.seats || {}).find(
      key => gameData?.seats[Number(key)] === currentUsername
    );
    
    if (myActualSeat !== undefined) {
      const myIdx = Number(myActualSeat);
      return (actualSeatIdx - myIdx + 6) % 6;
    }
    
    return actualSeatIdx - 1;
  };

  const handleSeatClick = (idx: number) => {
    if (roomId) sendSeat(roomId, idx);
  };

  const handleStartGame = () => {
    if (roomId) startGame(roomId);
  };

  const handleAddChip = (amount: number) => {
    if (!myPlayerInfo) return;
    setBetAmount(prev => {
      const nextAmount = prev + amount;
      return nextAmount > myPlayerInfo.money ? myPlayerInfo.money : nextAmount;
    });
  };

  const handleAllIn = () => {
    if (!myPlayerInfo) return;
    setBetAmount(myPlayerInfo.money);
  };

  const handleResetBet = () => {
    setBetAmount(0);
  };

  const handlePlaceBet = () => {
    if (roomId && betAmount > 0) {
      sendBet(roomId, betAmount);
      setHasBet(true);
    }
  };
  
  const handleLeaveRoom = async () => {
    if (window.confirm("정말로 방에서 나가시겠습니까?")) {
      try { if (roomId) await leaveRoom(roomId); } 
      catch (e) { console.error(e); } 
      finally { disconnect(); navigate('/'); }
    }
  };

  if (!isConnected && !currentUsername) {
    return (
      <div className="game-container">
        <h2>연결되지 않았습니다. 로비에서 다시 입장해주세요.</h2>
        <button onClick={() => navigate('/')} className="lobby-button">로비로 돌아가기</button>
      </div>
    );
  }

  return (
    <div className="game-container">
      <header className="game-header">
        <div className="game-header-top">
          <h1>🃏 게임방: {roomId}</h1>
          <button onClick={handleLeaveRoom} className="btn-leave">방 나가기 🚪</button>
        </div>
        <div>접속자: {currentUsername} | 상태: {isConnected ? '🟢 연결됨' : '🟡 재연결 중...'}</div>
      </header>

      {/* 카지노 테이블 영역 */}
      <div className="casino-table-wrapper">
        <div className="casino-table">
          
          {/* 카드 슈 */}
          <div className="card-shoe">
            <div className="cut-card-line"></div>
            
            <div 
              className="card-shoe-deck" 
              style={{ 
                height: `${Math.max(0, Math.min(100, ((gameData?.remainingCards ?? 312) / 312) * 100))}%` 
              }}
            ></div>
          </div>

          {/* 딜러 영역 */}
          {gameData && gameData.state !== 'WAITING' && gameData.state !== 'BETTING' && (
            <div className="dealer-area">
              <DealerBoard dealer={gameData.dealer} />
            </div>
          )}

          {/* 칩 배팅 */}
          {gameData?.state === 'BETTING' && myPlayerInfo && (
            <div className="table-center-overlay">
              <h2 className="betting-title">⏳ 배팅 시간 ({timeLeft}초)</h2>
              {!hasBet ? (
                <div className="chip-betting-container">
                  <div className="current-bet-display">
                    현재 배팅액: <span>{betAmount.toLocaleString()}</span> 원
                    <div className="my-money-info">(내 돈: {myPlayerInfo.money.toLocaleString()}원)</div>
                  </div>
                  <div className="chip-buttons">
                    <button className="chip-btn chip-1k" onClick={() => handleAddChip(1000)}>1K</button>
                    <button className="chip-btn chip-10k" onClick={() => handleAddChip(10000)}>1만</button>
                    <button className="chip-btn chip-50k" onClick={() => handleAddChip(50000)}>5만</button>
                    <button className="chip-btn chip-100k" onClick={() => handleAddChip(100000)}>10만</button>
                    <button className="chip-btn chip-max" onClick={handleAllIn}>MAX</button>
                  </div>
                  <div className="bet-action-buttons">
                    <button onClick={handleResetBet} className="btn-reset" disabled={betAmount === 0}>초기화</button>
                    <button onClick={handlePlaceBet} className="btn-next btn-confirm-bet" disabled={betAmount < 1000}>배팅 확정</button>
                  </div>
                </div>
              ) : (
                <h3 className="betting-completed-text">✔️ 배팅 완료! 다른 플레이어를 기다리는 중...</h3>
              )}
            </div>
          )}

          {/* 플레이어 자리 배치 */}
          <div className="players-arc-area">
            {[1, 2, 3, 4, 5, 6].map((seatIndex) => {
              const seatOwner = gameData?.seats?.[seatIndex];
              const playerObj = gameData?.players?.find((p) => p.name === seatOwner);
              const isMeSitting = Object.values(gameData?.seats || {}).includes(currentUsername);

              return (
                <div key={seatIndex} className={`seat-slot pos-${getVisualPos(seatIndex)}`}>
                  
                  {seatOwner ? (
                    playerObj ? (
                      <PlayerBoard 
                        player={playerObj} 
                        isMe={seatOwner === currentUsername} 
                        isCurrentTurn={gameData.currentPlayerIndex !== null && gameData.players[gameData.currentPlayerIndex]?.name === seatOwner}
                        gameState={gameData.state}
                        dealerScore={gameData.dealer?.score || 0}
                      />
                    ) : (
                      <div className="status-badge bottom-badge">
                        <span className="name">{seatOwner} {seatOwner === currentUsername && '(나)'}</span>
                        <span>⏳ 다음 라운드 대기중...</span>
                      </div>
                    )
                  ) : (
                    !isMeSitting && (
                      <button 
                        className="btn-bar-primary" 
                        style={{ opacity: 0.8, transform: 'scale(0.8)' }} 
                        onClick={() => handleSeatClick(seatIndex)}
                      >
                        🪑 자리 앉기
                      </button>
                    )
                  )}
                  
                </div>
              );
            })}
          </div>

        </div>
      </div>

      {/* 하단 고정 액션 바 */}
      <div className="bottom-action-bar">
        
        {/* 대기 상태 */}
        {(!gameData || gameData.state === 'WAITING') && (
          <div className="action-bar-content">
            <button onClick={handleStartGame} className="btn-bar-primary">
              ▶️ 게임 시작
            </button>
          </div>
        )}

        {/* 라운드 종료 후 대기 */}
        {gameData?.state === 'FINISHED' && (
          <div className="action-bar-content wait-message">
            정산 중입니다. 곧 다음 라운드가 시작됩니다... ⏳
          </div>
        )}

        {/* 인슈어런스 */}
        {gameData?.state === 'INSURANCE' && (
          <div className="action-bar-content insurance-bar">
            <span className="insurance-text">⚠️ 딜러의 카드가 ACE입니다. 보험(Insurance)에 가입하시겠습니까?</span>
            <button onClick={() => roomId && sendInsurance(roomId, true)} className="btn-bar-accept">수락</button>
            <button onClick={() => roomId && sendInsurance(roomId, false)} className="btn-bar-decline">거절</button>
          </div>
        )}

        {/* 내 턴 */}
        {isMyTurn && activeHand && myPlayerInfo && (
          <div className="action-bar-content">
            <ActionButtons 
              handIndex={activeHandIndex}
              playerMoney={myPlayerInfo.money}
              betAmount={activeHand.betAmount}
              cardCount={activeHand.cards.length}
              handCount={myPlayerInfo.hands.length}
            />
          </div>
        )}

        {/* 다른 사람 턴 */}
        {gameData?.state === 'PLAYER_TURN' && !isMyTurn && (
          <div className="action-bar-content wait-message">
            다른 플레이어의 턴을 기다리는 중입니다... ⏳
          </div>
        )}

      </div>
    </div>
  );
};

export default GamePage;