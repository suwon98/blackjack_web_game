import axios from 'axios';
import { create } from 'zustand';
import { Client } from '@stomp/stompjs';
import type { GameStatusResponse, PlayerAction } from '../types/game.types';
import { useAuthStore } from './useAuthStore';
import { getMyInfo } from '../api/memberApi';
import { apiBase } from '../api/axiosInstance';
export interface ChatMessage { id: string; username: string; nickname: string; kind: 'TEXT' | 'EMOJI'; text: string; sentAt: number }
interface GameStore {
  messages: ChatMessage[];
  chatError: string | null;
  sendChat: (text: string, kind?: 'TEXT' | 'EMOJI') => boolean;
  gameData: GameStatusResponse | null;
  isConnected: boolean;
  stompClient: Client | null;
  currentUsername: string;
  roomId: string | null;
  error: string | null;
  pending: boolean;
  kicked: string | null;
  sendReady: (roomId: string, ready: boolean) => void;
  clockOffset: number;
  connect: (roomId: string, username?: string) => void;
  disconnect: () => void;
  sendSeat: (roomId: string, seat: number) => void;
  resumeGame: (roomId: string) => void;
  startGame: (roomId: string) => void;
  sendBet: (roomId: string, amount: number) => void;
  sendInsurance: (roomId: string, accept: boolean) => void;
  sendAction: (roomId: string, action: PlayerAction, hand: number) => void;
}
export const useGameStore = create<GameStore>((set, get) => {
  let requestSequence=0;
  const send = (route: string, body: object) => {
    const client = get().stompClient;
    if (!client?.connected || get().pending) return;
    const sequence=++requestSequence;
    set({ pending: true, error: null });
    client.publish({ destination: `/app/game/${route}`, body: JSON.stringify(body) });
    window.setTimeout(() => { if(sequence===requestSequence && get().pending && get().stompClient===client) { set({pending:false,error:'응답이 지연되어 상태를 다시 확인합니다.'}); if(client.connected) client.publish({destination:'/app/game/sync',body:JSON.stringify({roomId:get().roomId})}); } },8000);
  };
  return {
    messages: [], chatError: null,
    sendChat: (text, kind = 'TEXT') => {
      const client = get().stompClient;
      if (!client?.connected) return false;
      set({ chatError: null });
      client.publish({ destination: '/app/chat/send', body: JSON.stringify({ roomId: get().roomId === 'lobby' ? null : get().roomId, kind, text }) });
      return true;
    },
    kicked: null,
    clockOffset: 0, gameData: null, isConnected: false, stompClient: null, currentUsername: '', roomId: null, error: null, pending: false,
    connect: (roomId) => {
      if (get().stompClient && get().roomId === roomId) return;
      get().disconnect();
      const url = new URL('/ws-blackjack', apiBase || window.location.origin);
      url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
      const client = new Client({ brokerURL: url.toString(), reconnectDelay: 3000, heartbeatIncoming: 10000, heartbeatOutgoing: 10000 });
      const headers = () => ({ ...(roomId === 'lobby' ? { channel: 'lobby' } : { roomId }), Authorization: `Bearer ${useAuthStore.getState().accessToken}` });
      client.beforeConnect = async () => {
        client.connectHeaders = headers();
        try {
          const { data } = await getMyInfo();
          client.connectHeaders = headers();
          if (get().stompClient === client) set({ currentUsername: data.username });
        } catch (error) {
          if (get().stompClient !== client) return;
          if(axios.isAxiosError(error) && (error.response?.status===401 || error.response?.status===400)) { set({error:'로그인 상태를 확인해주세요.'}); void client.deactivate(); }
          else set({error:'서버에 다시 연결하는 중입니다.'});
        }
      };
      client.onConnect = () => {
        if (get().stompClient !== client) return;
        if (roomId !== 'lobby') client.subscribe(`/topic/room/${roomId}`, message => { if (get().stompClient !== client) return; const data=JSON.parse(message.body); set({gameData:data,clockOffset:data.serverTime-Date.now(),pending:false}); });
        client.subscribe(roomId === 'lobby' ? '/topic/chat/lobby' : `/topic/chat/room/${roomId}`, message => {
          if (get().stompClient !== client) return;
          const incoming: ChatMessage = JSON.parse(message.body);
          set(state => ({ messages: [...state.messages.filter(item => item.id !== incoming.id), incoming].slice(-100) }));
        });
        client.subscribe('/user/queue/room-events', message => {
          if (get().stompClient !== client) return;
          const event = JSON.parse(message.body);
          if (event.type === 'KICKED' && event.roomId === roomId) {
            set({ kicked: event.message, pending: false, isConnected: false, gameData: null });
            void client.deactivate();
          }
        });
        client.subscribe('/user/queue/errors', message => {
          if (get().stompClient !== client) return;
          const data = JSON.parse(message.body);
          if (data.source === 'chat') set({ chatError: data.message });
          else set({ error: data.message, pending: false });
        });
        set({ isConnected: true, error: null, pending: false });
        if (roomId !== 'lobby') client.publish({ destination: '/app/game/sync', body: JSON.stringify({ roomId }) });
      };
      client.onWebSocketClose = () => { if (get().stompClient === client) set({ isConnected: false, pending: false }); };
      client.onStompError = () => {
        if (get().stompClient !== client) return;
        set({ error: '게임 연결이 거부되었습니다. 로비에서 다시 입장해주세요.', isConnected: false, pending: false });
      };
      set({ stompClient: client, roomId, error: null, kicked: null }); client.activate();
    },
    disconnect: () => {
      const client = get().stompClient;
      set({ stompClient: null, isConnected: false, gameData: null, roomId: null, pending: false, messages: [], chatError: null });
      if (client) void client.deactivate();
    },
    sendReady: (roomId, ready) => send('ready', { roomId, ready }),
    sendSeat: (roomId, seatIndex) => send('seat', { roomId, seatIndex }),
    resumeGame: roomId => send('resume', { roomId }),
    startGame: roomId => send('start', { roomId }),
    sendBet: (roomId, betAmount) => send('bet', { roomId, betAmount }),
    sendInsurance: (roomId, accept) => send('insurance', { roomId, accept }),
    sendAction: (roomId, action, handIndex) => send('action', { roomId, action, handIndex, revision: get().gameData?.revision }),
  };
});
