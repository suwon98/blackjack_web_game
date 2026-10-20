import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useGameStore } from '../store/useGameStore';
import { leaveRoom } from '../api/roomApi';
import DealerBoard from '../components/DealerBoard';
import PlayerBoard from '../components/PlayerBoard';
import ActionButtons from '../components/ActionButtons';
import ChatPanel from '../components/ChatPanel';
import '../styles/GamePage.css';
import '../styles/Board.css';

const GamePage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();

  const { 
    isConnected, gameData, currentUsername, connect, disconnect, 
    startGame, resumeGame, sendBet, sendInsurance, sendSeat, error, pending, clockOffset 
  } = useGameStore();
  
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [leaveError, setLeaveError] = useState('');
  const [betAmount, setBetAmount] = useState<number>(0);
  const [now, setNow] = useState(() => Date.now());
  const timeLeft = Math.max(0, Math.ceil(((gameData?.deadline ?? 0) - now - clockOffset) / 1000));
  const hasBet = gameData?.bets[currentUsername] !== undefined;
  const [prevRound, setPrevRound] = useState<number | undefined>(undefined);

  const myPlayerInfo = gameData?.players?.find(p => p.name === currentUsername);
  const isMyTurn = !gameData?.paused && gameData?.state === 'PLAYER_TURN' && gameData?.players[gameData.currentPlayerIndex]?.name === currentUsername;
  const activeHandIndex = myPlayerInfo?.hands.findIndex(h => h.status === 'PLAYING') ?? -1;
  const activeHand = activeHandIndex >= 0 ? myPlayerInfo?.hands[activeHandIndex] : null;

  useEffect(() => {
    if (roomId) connect(roomId);
    return () => disconnect();
  }, [roomId, connect, disconnect]);

  if (gameData?.roundId !== prevRound) {
    setPrevRound(gameData?.roundId);
    setBetAmount(gameData?.minBet ?? 1000);
  }
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [gameData?.deadline]);

  const getVisualPos = (actualSeatIdx: number) => {
    const myActualSeat = Object.keys(gameData?.seats || {}).find(
      key => gameData?.seats?.[Number(key)] === currentUsername
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

    }
  };
  
  const handleLeaveRoom = async () => {
    try { if (roomId) await leaveRoom(roomId); disconnect(); navigate('/'); }
    catch { setLeaveError('퇴장 요청에 실패했습니다. 연결을 확인하고 다시 시도해주세요.'); }
  };

  if (error && !isConnected) {
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
          <h1>🃏 {gameData?.title || '게임 연결 중'}</h1>
          <button onClick={() => setConfirmLeave(true)} className="btn-leave">방 나가기 🚪</button>
        </div>
        <div>접속자: {currentUsername || '확인 중'} | 상태: {isConnected ? '🟢 연결됨' : '🟡 재연결 중...'} | {gameData?.paused ? '복구 완료 · 일시정지' : `제한시간: ${timeLeft}초`}</div>
        {error && <p role="alert" style={{ color: '#ffb4b4' }}>{error}</p>}
      </header>

      {gameData?.paused && <section className="recovery-panel" role="status"><h2>진행 중이던 게임을 복구했습니다</h2><p>카드, 베팅과 잔액이 저장된 상태입니다. 이어하기를 누르면 제한시간을 새로 시작합니다.</p><button disabled={pending || !isConnected} onClick={() => roomId && resumeGame(roomId)}>게임 이어하기</button></section>}
      {confirmLeave && <div className="recovery-panel" role="dialog" aria-label="방 나가기 확인"><p>진행 중인 패는 Stand 처리 후 정산됩니다. 나가시겠습니까?</p><button onClick={handleLeaveRoom}>나가기</button> <button autoFocus onClick={() => setConfirmLeave(false)}>계속 플레이</button>{leaveError && <p role="alert">{leaveError}</p>}</div>}
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
          {gameData?.dealer && gameData.state !== 'WAITING' && gameData.state !== 'BETTING' && (
            <div className="dealer-area">
              <DealerBoard dealer={gameData.dealer} />
            </div>
          )}

          {/* 칩 배팅 */}
          {!gameData?.paused && gameData?.state === 'BETTING' && myPlayerInfo && (
            <div className="table-center-overlay">
              <h2 className="betting-title">⏳ 배팅 시간 ({timeLeft}초)</h2>
              {!hasBet ? (
                <div className="chip-betting-container">
                  <div className="current-bet-display">
                    최소 베팅: {gameData?.minBet.toLocaleString()} | 현재 베팅액: <span>{betAmount.toLocaleString()}</span> 칩
                    <div className="my-money-info">(내 칩: {myPlayerInfo.money.toLocaleString()} 칩)</div>
                  </div>
                  <div className="chip-buttons">
                    <button className="chip-btn chip-1k" onClick={() => handleAddChip(100)}>100</button>
                    <button className="chip-btn chip-1k" onClick={() => handleAddChip(1000)}>1K</button>
                    <button className="chip-btn chip-10k" onClick={() => handleAddChip(10000)}>1만</button>
                    <button className="chip-btn chip-50k" onClick={() => handleAddChip(50000)}>5만</button>
                    <button className="chip-btn chip-100k" onClick={() => handleAddChip(100000)}>10만</button>
                    <button className="chip-btn chip-max" onClick={handleAllIn}>MAX</button>
                  </div>
                  <div className="bet-action-buttons">
                    <button onClick={handleResetBet} className="btn-reset" disabled={betAmount === 0}>초기화</button>
                    <button onClick={handlePlaceBet} className="btn-next btn-confirm-bet" disabled={pending || !isConnected || betAmount < (gameData?.minBet ?? 100) || betAmount % 100 !== 0}>배팅 확정</button>
                  </div>
                </div>
              ) : (
                <h3 className="betting-completed-text">✔️ 배팅 완료! 다른 플레이어를 기다리는 중...</h3>
              )}
            </div>
          )}

          {/* 플레이어 자리 배치 */}
          <div className="players-arc-area">
            {Array.from({ length: gameData?.maxPlayers ?? 6 }, (_, i) => i + 1).map((seatIndex) => {
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
                        <span>{gameData?.state === 'WAITING' ? '착석 완료 · 게임을 시작하세요' : '⏳ 다음 라운드 대기중...'}</span>
                      </div>
                    )
                  ) : (
                    !isMeSitting && (
                      <button 
                        className="btn-bar-primary" 
                        style={{ opacity: 0.8, transform: 'scale(0.8)' }} 
                        disabled={pending || !isConnected}
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

      <ChatPanel />
      {/* 하단 고정 액션 바 */}
      <div className="bottom-action-bar" style={{display:gameData?.paused?'none':undefined}}>
        
        {/* 대기 상태 */}
        {(!gameData || gameData.state === 'WAITING') && (
          <div className="action-bar-content">
            <button onClick={handleStartGame} disabled={pending || !isConnected || !Object.values(gameData?.seats ?? {}).includes(currentUsername)} className="btn-bar-primary">
              ▶️ 게임 시작
            </button>
          </div>
        )}

        {/* 라운드 종료 후 대기 */}
        {!gameData?.paused && gameData?.state === 'FINISHED' && (
          <div className="action-bar-content wait-message">
            정산 중입니다. 곧 다음 라운드가 시작됩니다... ⏳
          </div>
        )}

        {/* 인슈어런스 */}
        {!gameData?.paused && gameData?.state === 'INSURANCE' && myPlayerInfo && (
          <div className="action-bar-content insurance-bar">
            <span className="insurance-text">⚠️ 딜러의 카드가 ACE입니다. 보험(Insurance)에 가입하시겠습니까?</span>
            <button disabled={pending || gameData.insuranceDecisions[currentUsername] !== undefined} onClick={() => roomId && sendInsurance(roomId, true)} className="btn-bar-accept">수락</button>
            <button disabled={pending || gameData.insuranceDecisions[currentUsername] !== undefined} onClick={() => roomId && sendInsurance(roomId, false)} className="btn-bar-decline">거절</button>
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
              canSplitHand={activeHand.canSplit}
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
