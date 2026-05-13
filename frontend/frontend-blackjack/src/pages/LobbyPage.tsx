import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getRooms, joinRoom, type RoomListResponse } from '../api/roomApi';
import { getMyInfo, type MemberInfoResponse } from '../api/memberApi';
import { useAuthStore } from '../store/useAuthStore';
import { useGameStore } from '../store/useGameStore';
import RoomCreateModal from '../components/RoomCreateModal';
import axios from 'axios';
import '../styles/LobbyPage.css';

const LobbyPage: React.FC = () => {
  const [rooms, setRooms] = useState<RoomListResponse[]>([]);
  const [userInfo, setUserInfo] = useState<MemberInfoResponse | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();
  
  const connectToSocket = useGameStore((state) => state.connect);
  const logout = useAuthStore((state) => state.logout);

  useEffect(() => {
    const initLoad = async () => {
      try {
        const [userRes, roomsRes] = await Promise.all([getMyInfo(), getRooms()]);
        setUserInfo(userRes.data);
        setRooms(roomsRes.data);
      } catch (error) {
        console.error('초기 데이터 로드 실패:', error);
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

  const handleJoinRoom = async (room: RoomListResponse) => {
    if (!userInfo) return;

    if (userInfo.money < room.minBet) {
      alert(`소지금이 부족합니다. (최소 배팅금: ${room.minBet.toLocaleString()}원)`);
      return;
    }

    let inviteCode = undefined;
    if (room.isPrivate) {
      const code = prompt('비밀번호(초대코드)를 입력하세요:');
      if (code === null) return;
      inviteCode = code;
    }

    try {
      await joinRoom(room.roomId, { inviteCode });
      connectToSocket(room.roomId, userInfo.username);
      navigate(`/game/${room.roomId}`);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        alert(error.response?.data?.message || '방 입장에 실패했습니다.');
      }
    }
  };

  const handleLogout = () => {
    if (window.confirm('로그아웃 하시겠습니까?')) {
      logout();
      navigate('/login');
    }
  };

  return (
    <div className="lobby-container">
      <header className="lobby-header lobby-header-top">
        <h1>🎰 블랙잭 로비</h1>
        
        {userInfo ? (
          <div className="user-info-badge">
            <span className="user-nickname">{userInfo.nickname} 님</span>
            <span className="user-money">💰 {userInfo.money.toLocaleString()}원</span>
            <button onClick={handleLogout} className="btn-logout">
              로그아웃
            </button>
          </div>
        ) : (
          <div className="user-info-badge">
            <button onClick={() => navigate('/login')} className="btn-login">
              로그인
            </button>
            <button onClick={() => navigate('/signup')} className="btn-signup">
              회원가입
            </button>
          </div>
        )}
      </header>

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
                <p>배팅: {room.minBet.toLocaleString()}원</p>
                <p>상태: {room.isPlaying ? '게임 진행 중' : '대기 중'}</p>
              </div>
              <button 
                className="btn-join" 
                onClick={() => handleJoinRoom(room)}
                disabled={room.currentPlayers >= room.maxPlayers}
              >
                입장하기
              </button>
            </div>
          ))
        )}
      </div>

      <RoomCreateModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        onSuccess={handleModalSuccess} 
      />
    </div>
  );
};

export default LobbyPage;