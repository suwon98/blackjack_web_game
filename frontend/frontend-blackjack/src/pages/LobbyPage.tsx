import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getRooms, joinRoom, type RoomListResponse } from '../api/roomApi';
import { getMyInfo, type MemberInfoResponse } from '../api/memberApi';
import { useAuthStore } from '../store/useAuthStore';
import { useGameStore } from '../store/useGameStore';
import RoomCreateModal from '../components/RoomCreateModal';
import ChatPanel from '../components/ChatPanel';
import axios from 'axios';
import api from '../api/axiosInstance';
import '../styles/LobbyPage.css';

const LobbyPage: React.FC = () => {
  const [rooms, setRooms] = useState<RoomListResponse[]>([]);
  const [userInfo, setUserInfo] = useState<MemberInfoResponse | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [charging, setCharging] = useState(false);
  const [chargeNotice, setChargeNotice] = useState('');
  const [inviteRoom, setInviteRoom] = useState<RoomListResponse | null>(null);
  const [inviteCode, setInviteCode] = useState('');
  const [history, setHistory] = useState<{id:string;createdAt:number;stake:number;payout:number;balance:number}[]>([]);

  
  const connectToSocket = useGameStore((state) => state.connect);
  const logout = useAuthStore((state) => state.logout);
  useEffect(() => {
    connectToSocket('lobby');
    return () => { if (useGameStore.getState().roomId === 'lobby') useGameStore.getState().disconnect(); };
  }, [connectToSocket]);

  const recharge = async () => {
    if (charging) return;
    setCharging(true); setChargeNotice(''); setError('');
    try {
      await api.post('/api/members/recharge');
      setUserInfo((await getMyInfo()).data);
      setChargeNotice('100,000 칩을 충전했습니다.');
    } catch (failure) {
      setError(axios.isAxiosError(failure) ? failure.response?.data?.message || '충전에 실패했습니다. 잔액을 확인하고 다시 시도해주세요.' : '충전에 실패했습니다.');
    } finally { setCharging(false); }
  };

  useEffect(() => {
    const initLoad = async () => {
      try {
        const [userRes, roomsRes] = await Promise.all([getMyInfo(), getRooms()]);
        setUserInfo(userRes.data);
        setRooms(roomsRes.data);
        setHistory((await api.get("/api/members/history")).data);
      } catch (error) {
        console.error(error); setError('데이터를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.');
      }
    };
    initLoad();
  }, []);

  const handleRefreshRooms = async () => {
    try {
      const response = await getRooms();
      setRooms(response.data);
    } catch (error) {
      console.error('방 새로고침 실패', error);
    }
  };

  const handleModalSuccess = (createdRoomId: string) => {
    setIsModalOpen(false);
    if (userInfo) {
      connectToSocket(createdRoomId, userInfo.username);
      navigate(`/game/${createdRoomId}`);
    }
  };

  const handleJoinRoom = async (room: RoomListResponse, code?: string) => {
    setError('');
    if (!userInfo) return;

    if (room.joined) { connectToSocket(room.roomId, userInfo.username); navigate(`/game/${room.roomId}`); return; }
    if (userInfo.money < room.minBet) {
      setError(`칩이 부족합니다. (최소 배팅금: ${room.minBet.toLocaleString()} 칩)`);
      return;
    }

    if (room.isPrivate && code === undefined) { setInviteRoom(room);setInviteCode('');return; }

    try {
      await joinRoom(room.roomId, { inviteCode: code });
      connectToSocket(room.roomId, userInfo.username);
      navigate(`/game/${room.roomId}`);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        setError(error.response?.data?.message || '방 입장에 실패했습니다.');
      }
    }
  };

  const handleLogout = async () => {
    if (window.confirm('로그아웃 하시겠습니까?')) {
      try { await api.post('/api/members/logout'); } finally { useGameStore.getState().disconnect(); logout(); navigate('/login'); }
    }
  };

  return (
    <div className="lobby-container">
      <header className="lobby-header lobby-header-top">
        <h1>🎰 블랙잭 로비</h1>
        
        {userInfo ? (
          <div className="user-info-badge">
            <span className="user-nickname">{userInfo.nickname} 님</span>
            <span className="user-money">💰 {userInfo.money.toLocaleString()} 칩</span>
            <button disabled={charging || userInfo.money >= 1000} onClick={recharge} title="잔액이 1,000 칩 미만이면 100,000 칩 충전">{charging ? '충전 중…' : '10만 칩 충전'}</button>
            <button onClick={handleLogout} className="btn-logout">
              로그아웃
            </button>
          </div>
        ) : (
          <div className="user-info-badge">
            <button onClick={() => navigate('/login')} className="btn-login">
              로그인
            </button>
            <button onClick={() => navigate('/register')} className="btn-signup">
              회원가입
            </button>
          </div>
        )}
      </header>

      {error && <p role="alert">{error}</p>}
      {chargeNotice && <p role="status">{chargeNotice}</p>}
      <p>잔액이 1,000 칩 미만이면 10만 칩을 충전할 수 있습니다.</p>
      {inviteRoom && <form className="recovery-panel" onSubmit={e=>{e.preventDefault();void handleJoinRoom(inviteRoom,inviteCode);}}><label htmlFor="invite-code">{inviteRoom.title} 초대 코드</label> <input id="invite-code" autoFocus type="password" maxLength={72} value={inviteCode} onChange={e=>setInviteCode(e.target.value)} required/> <button type="submit">입장</button> <button type="button" onClick={()=>setInviteRoom(null)}>취소</button></form>}
      <details className="recovery-panel"><summary>게임 안내 · 연습용 칩</summary><p>착석 → 일반 참가자 준비 완료 → 방장 게임 시작 → 베팅 → Hit 또는 Stand를 선택하세요. 딜러는 소프트 17에서 멈춥니다. 블랙잭 3:2, 보험 2:1, 최대 4패 스플릿을 지원합니다.</p><p>시간이 지나면 최소 금액 자동 베팅 또는 Stand 처리됩니다. 서버 재시작 후에는 내 게임 이어하기를 선택하세요. 실제 돈을 사용하지 않습니다.</p></details>
      <div className="lobby-controls">
        <button className="btn-create" onClick={() => setIsModalOpen(true)}>방 만들기</button>
        <button className="btn-refresh" onClick={handleRefreshRooms}>새로고침 🔄</button>
      </div>

      <div className="room-list">
        {rooms.length === 0 ? (
          <p className="no-rooms">현재 생성된 방이 없습니다. 첫 번째 방을 만들어보세요!</p>
        ) : (
          rooms.map((room) => (
            <div key={room.roomId} className="room-card">
              <div className="room-info">
                <h3>{room.isPrivate && '🔒'} {room.title}</h3>
                <p>인원: {room.currentPlayers} / {room.maxPlayers}</p>
                <p>배팅: {room.minBet.toLocaleString()} 칩</p>
                <p>상태: {room.paused ? '복구 완료 · 이어하기 가능' : room.isPlaying ? '게임 진행 중' : '대기 중'}</p>
              </div>
              <button 
                className="btn-join" 
                onClick={() => handleJoinRoom(room)}
                disabled={!room.joined && room.currentPlayers >= room.maxPlayers}
              >
                {room.joined ? '내 게임 이어하기' : '입장하기'}
              </button>
            </div>
          ))
        )}
      </div>

      <details className="recovery-panel"><summary>최근 정산 내역 ({history.length})</summary>{history.length===0?<p>아직 완료한 라운드가 없습니다.</p>:<div style={{overflowX:'auto'}}><table><thead><tr><th>시간</th><th>베팅</th><th>순증감</th><th>정산 후 잔액</th></tr></thead><tbody>{history.map(h=><tr key={h.id}><td>{new Date(h.createdAt).toLocaleString()}</td><td>{h.stake.toLocaleString()}</td><td>{h.payout-h.stake>0?'+':''}{(h.payout-h.stake).toLocaleString()}</td><td>{h.balance.toLocaleString()}</td></tr>)}</tbody></table></div>}</details>
      <ChatPanel lobby />
      <RoomCreateModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        onSuccess={handleModalSuccess} 
      />
    </div>
  );
};

export default LobbyPage;
