import { useEffect, useRef, useState } from 'react';
import { useGameStore } from '../store/useGameStore';
import '../styles/ChatPanel.css';

const reactions = [['👋', '인사'], ['👍', '좋아요'], ['👏', '박수'], ['🎉', '축하'], ['😮', '놀람'], ['🍀', '행운']];

export default function ChatPanel({ lobby = false }: { lobby?: boolean }) {
  const { messages, isConnected, chatError, sendChat, currentUsername } = useGameStore();
  const [draft, setDraft] = useState('');
  const [collapsed, setCollapsed] = useState(false);
  const list = useRef<HTMLDivElement>(null);
  const follow = useRef(true);
  useEffect(() => {
    if (follow.current && list.current) list.current.scrollTop = list.current.scrollHeight;
  }, [messages, collapsed]);
  const send = () => {
    if (draft.trim() && sendChat(draft.trim())) { setDraft(''); follow.current = true; }
  };
  return <section className={`chat-panel ${lobby ? 'chat-lobby' : 'chat-room'}`} aria-label={lobby ? '대기실 채팅' : '게임방 채팅'}>
    <div className="chat-heading">
      <h2>{lobby ? '💬 대기실 채팅' : '💬 게임방 채팅'}</h2>
      <span className={isConnected ? 'chat-online' : 'chat-offline'} role="status">{isConnected ? '실시간 연결' : '재연결 중'}</span>
      <button type="button" aria-expanded={!collapsed} onClick={() => setCollapsed(!collapsed)}>{collapsed ? '펼치기' : '접기'}</button>
    </div>
    {!collapsed && <>
      <p className="chat-hint">서로 존중하는 대화를 나눠주세요. 접속 중 받은 최근 100개 메시지만 표시됩니다.</p>
      <div className="chat-messages" ref={list} role="log" aria-live="polite" aria-relevant="additions"
        onScroll={() => { const el = list.current; if (el) follow.current = el.scrollHeight - el.scrollTop - el.clientHeight < 50; }}>
        {messages.length === 0 && <p className="chat-empty">첫 인사를 건네보세요 👋</p>}
        {messages.map(message => <div className={`chat-message ${message.username === currentUsername ? 'chat-mine' : ''}`} key={message.id}>
          <div className="chat-meta"><strong>{message.nickname}{message.username === currentUsername ? ' (나)' : ''}</strong><time dateTime={new Date(message.sentAt).toISOString()}>{new Date(message.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time></div>
          <p className={message.kind === 'EMOJI' ? 'chat-emoji' : ''}>{message.text}</p>
        </div>)}
      </div>
      <div className="chat-reactions" aria-label="빠른 반응">{reactions.map(([emoji, label]) =>
        <button type="button" key={emoji} aria-label={`${label} 보내기`} title={label} disabled={!isConnected} onClick={() => sendChat(emoji, 'EMOJI')}>{emoji}</button>)}</div>
      <form className="chat-form" onSubmit={event => { event.preventDefault(); send(); }}>
        <label className="chat-label" htmlFor={lobby ? 'lobby-chat-input' : 'room-chat-input'}>메시지</label>
        <input id={lobby ? 'lobby-chat-input' : 'room-chat-input'} value={draft} maxLength={300}
          onKeyDown={event => { if (event.key === 'Enter' && event.nativeEvent.isComposing) event.preventDefault(); }}
          onChange={event => setDraft(event.target.value)} placeholder={isConnected ? '메시지 입력 (최대 300자)' : '연결을 기다리는 중…'}
          autoComplete="off" disabled={!isConnected} />
        <button type="submit" disabled={!isConnected || !draft.trim()}>보내기</button>
      </form>
      {chatError && <p className="chat-error" role="alert">{chatError}</p>}
    </>}
  </section>;
}
