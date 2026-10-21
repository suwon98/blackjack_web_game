import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useGameStore } from '../store/useGameStore';
import { leaveRoom } from '../api/roomApi';
import DealerBoard from '../components/DealerBoard';
import PlayerBoard from '../components/PlayerBoard';
import ActionButtons from '../components/ActionButtons';
import ChatPanel from '../components/ChatPanel';
import { addBet, maximumBet, normalizeBet } from '../utils/betting';
import '../styles/GamePage.css';
import '../styles/Board.css';

const GamePage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();

  const {
    isConnected, gameData, currentUsername, connect, disconnect,
    startGame, sendReady, kicked, resumeGame, sendBet, sendInsurance, sendSeat, error, pending, clockOffset
  } = useGameStore();

  const [confirmLeave, setConfirmLeave] = useState(false);
  const [leaveError, setLeaveError] = useState('');
  const [betAmount, setBetAmount] = useState<number>(0);
  const [now, setNow] = useState(() => Date.now());
  const timeLeft = Math.max(0, Math.ceil(((gameData?.deadline ?? 0) - now - clockOffset) / 1000));
  const waitingSeconds = Math.max(0, Math.ceil(((gameData?.waitingDeadline ?? 0) - now - clockOffset) / 1000));
  const isHost = gameData?.host === currentUsername;
  const isSeated = Object.values(gameData?.seats ?? {}).includes(currentUsername);
  const isReady = gameData?.readyPlayers?.includes(currentUsername) ?? false;
  const guestsReady = Object.values(gameData?.seats ?? {}).filter(name => name !== gameData?.host).every(name => gameData?.readyPlayers?.includes(name));
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
    setBetAmount(prev => addBet(prev, amount, myPlayerInfo.money));
  };

  const handleAllIn = () => {
    if (!myPlayerInfo) return;
    setBetAmount(maximumBet(myPlayerInfo.money));
  };

  const handleResetBet = () => {
    setBetAmount(gameData?.minBet ?? 1000);
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

  if (kicked) return <div className="game-container"><h2>방에서 퇴장되었습니다</h2><p role="alert">{kicked}</p><p>같은 방에는 60초 후 다시 입장할 수 있습니다.</p><button onClick={() => navigate('/')}>로비로 돌아가기</button></div>;

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
      {gameData?.state === 'WAITING' && <section className="ready-panel" aria-label="게임 준비 상태">
        <strong>방장: {gameData.host} · 착석한 참가자만 준비 대상입니다.</strong>
        <ul>{Object.values(gameData.seats).map(name => <li key={name}>{name} — {name === gameData.host ? '방장' : gameData.readyPlayers.includes(name) ? '준비 완료' : '준비 대기'}</li>)}</ul>
        <p>일반 참가자가 모두 준비하면 방장은 10초 안에 시작해야 합니다. 일반 참가자가 2명 이상이고 한 명만 미준비이면 그 참가자에게 10초가 주어집니다.</p>
        {gameData.waitingTarget && <p className="ready-warning" role="status">{gameData.waitingTarget}: {gameData.waitingReason === 'HOST_IDLE' ? '게임 시작' : '준비 완료'}까지 {waitingSeconds}초 · 제한시간이 지나면 자동 퇴장됩니다.</p>}
      </section>}
          {/* 칩 배팅 */}
          {!gameData?.paused && gameData?.state === 'BETTING' && myPlayerInfo && (
            <div className="table-center-overlay">
              <h2 className="betting-title">⏳ 배팅 시간 ({timeLeft}초)</h2>
              {!hasBet ? (
                <div className="chip-betting-container">
                  <div className="current-bet-display">
                    최소 베팅: {gameData?.minBet.toLocaleString()} | 현재 베팅액: <span>{betAmount.toLocaleString()}</span> 칩
                    <div className="my-money-info">(내 칩: {myPlayerInfo.money.toLocaleString()} 칩)</div>
                    <div className="my-money-info">베팅은 100칩 단위입니다. MAX는 베팅 가능한 최대 금액을 선택합니다.</div>
                  </div>
                  <fieldset className="bet-controls" disabled={pending || !isConnected}>
                    <label htmlFor="bet-amount">베팅 금액 직접 입력 (100칩 단위)</label>
                    <input id="bet-amount" type="number" inputMode="numeric" min={0} max={maximumBet(myPlayerInfo.money)} step={100} value={betAmount}
                      onChange={event => setBetAmount(Math.max(0, Math.min(Number(event.target.value) || 0, maximumBet(myPlayerInfo.money))))}
                      onBlur={() => setBetAmount(value => normalizeBet(value, myPlayerInfo.money))} />
                    <div className="chip-adjustments">
                      {[100, 1000, 10000, 50000, 100000].map(amount => <div className="chip-adjustment" key={amount}>
                        <button aria-label={`${amount}칩 빼기`} disabled={betAmount === 0} onClick={() => handleAddChip(-amount)}>−</button>
                        <span>{amount.toLocaleString()}</span>
                        <button aria-label={`${amount}칩 더하기`} disabled={betAmount >= maximumBet(myPlayerInfo.money)} onClick={() => handleAddChip(amount)}>+</button>
                      </div>)}
                    </div>
                    <button className="btn-reset" onClick={handleAllIn}>MAX</button>
                  </fieldset>
                  {betAmount < gameData.minBet && <p role="status">최소 {gameData.minBet.toLocaleString()}칩 이상을 선택해주세요.</p>}
                  <div className="bet-action-buttons">
                    <button onClick={handleResetBet} className="btn-reset" disabled={pending || !isConnected || betAmount === gameData.minBet}>최소 베팅</button>
                    <button onClick={handlePlaceBet} className="btn-next btn-confirm-bet" disabled={pending || !isConnected || betAmount < (gameData?.minBet ?? 100) || betAmount % 100 !== 0 || betAmount > maximumBet(myPlayerInfo.money)}>배팅 확정</button>
                  </div>
                </div>
              ) : (
                <h3 className="betting-completed-text">✔️ 배팅 완료! 다른 플레이어를 기다리는 중...</h3>
              )}
            </div>
          )}

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
                        <span>{gameData?.state === 'WAITING' ? (seatOwner === gameData.host ? '방장' : gameData.readyPlayers.includes(seatOwner) ? '준비 완료' : '준비 대기') : '⏳ 다음 라운드 대기중...'}</span>
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
            {isHost ? <button onClick={handleStartGame} disabled={pending || !isConnected || !isSeated || !guestsReady} className="btn-bar-primary">▶️ 게임 시작</button>
              : <button onClick={() => roomId && sendReady(roomId, !isReady)} disabled={pending || !isConnected || !isSeated} aria-pressed={isReady} className="btn-bar-primary">{isReady ? '준비 취소' : '준비 완료'}</button>}
            {!isSeated && <span>먼저 자리에 앉아주세요.</span>}
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
