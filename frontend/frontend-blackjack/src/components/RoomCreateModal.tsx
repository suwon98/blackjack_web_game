import React, { useState } from 'react';
import axios from 'axios';
import { createRoom, type RoomCreateRequest } from '../api/roomApi';
import '../styles/Modal.css';

interface RoomCreateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (roomId: string) => void;
}

const RoomCreateModal: React.FC<RoomCreateModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [title, setTitle] = useState('');
  const [maxPlayers, setMaxPlayers] = useState<number | string>(6); 
  const [isPrivate, setIsPrivate] = useState(false);
  const [inviteCode, setInviteCode] = useState('');
  const [minBet, setMinBet] = useState<number | string>(1000);

  if (!isOpen) return null;

  const handleMaxPlayersChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value.replace(/^0+/, '');
    setMaxPlayers(val);
  };

  const handleMinBetChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value.replace(/^0+/, '');
    setMinBet(val);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      alert('방 제목을 입력해주세요.');
      return;
    }

    const parsedMaxPlayers = Number(maxPlayers);
    if (!parsedMaxPlayers || parsedMaxPlayers < 1 || parsedMaxPlayers > 6) {
      alert('인원수는 1명에서 6명 사이여야 합니다.');
      return;
    }

    const parsedMinBet = Number(minBet);
    if (!parsedMinBet || parsedMinBet < 100) {
      alert('최소 배팅금은 100원 이상이어야 합니다.');
      return;
    }

    const requestData: RoomCreateRequest = {
      title,
      maxPlayers: parsedMaxPlayers,
      isPrivate,
      inviteCode: isPrivate ? inviteCode : undefined,
      minBet: parsedMinBet,
    };

    try {
      const response = await createRoom(requestData);
      onSuccess(response.data.roomId);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        alert(error.response?.data?.message || '방 생성에 실패했습니다.');
      } else {
        alert('예기치 않은 오류가 발생했습니다.');
      }
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <h2>방 만들기</h2>
        <form onSubmit={handleSubmit} className="modal-form">
          <div>
            <label>방 제목:</label>
            <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} className="modal-input" />
          </div>
          <div>
            <label>최대 인원 (1~6명):</label>
            <input 
              type="number" 
              min="1" 
              max="6" 
              value={maxPlayers} 
              onChange={handleMaxPlayersChange} 
              className="modal-input" 
            />
          </div>
          <div>
            <label>최소 배팅금:</label>
            <input 
              type="number" 
              min="100" 
              step="100" 
              value={minBet} 
              onChange={handleMinBetChange} 
              className="modal-input" 
            />
          </div>
          <div>
            <label>
              <input type="checkbox" checked={isPrivate} onChange={(e) => setIsPrivate(e.target.checked)} />
              비공개 방
            </label>
          </div>
          {isPrivate && (
            <div>
              <label>초대 코드 (비밀번호):</label>
              <input type="text" value={inviteCode} onChange={(e) => setInviteCode(e.target.value)} className="modal-input" disabled={!isPrivate} />
            </div>
          )}
          <div className="modal-actions">
            <button type="submit" className="btn-submit">생성</button>
            <button type="button" onClick={onClose} className="btn-cancel">취소</button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default RoomCreateModal;