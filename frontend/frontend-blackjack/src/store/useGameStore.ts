import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { Client } from "@stomp/stompjs";
import type { GameStatusResponse, PlayerAction } from "../types/game.types";
import { useAuthStore } from "./useAuthStore";

interface GameStateStore {
  gameData: GameStatusResponse | null;
  isConnected: boolean;
  stompClient: Client | null;
  currentUsername: string;

  connect: (roomId: string, username: string) => void;
  disconnect: () => void;

  sendSeat: (roomId: string, seatIndex: number) => void;

  startGame: (roomId: string) => void;
  sendBet: (roomId: string, betAmount: number) => void;
  
  sendInsurance: (roomId: string, accept: boolean) => void;
  sendAction: (roomId: string, action: PlayerAction, handIndex: number) => void;
}

export const useGameStore = create<GameStateStore>()(
  persist(
    (set, get) => ({
      gameData: null,
      isConnected: false,
      stompClient: null,
      currentUsername: '',

      connect: (roomId: string, username: string) => {
        if(get().stompClient) return;

        const token = useAuthStore.getState().token;

        const client = new Client({
          brokerURL: 'ws://localhost:8080/ws-blackjack/websocket',
          connectHeaders: {
            username: username,
            roomId: roomId,
            Authorization: `Bearer ${token}`
          },
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
        });

        client.onConnect = () => {
          console.log('STOMP 웹소켓 연결 성공');
          set({isConnected: true, currentUsername: username});

          client.subscribe(`/topic/room/${roomId}`, (message) => {
            const receivedData: GameStatusResponse = JSON.parse(message.body);
            set({gameData: receivedData});
          });

          client.subscribe(`/topic/room/${roomId}/errors`, (message) => {
            const errorData = JSON.parse(message.body);
            if (errorData.username === username) {
              alert(`[알림] ${errorData.message}`);
            }
          });
        };

        client.onStompError = (frame) => {
          console.error('Broker reported error:' + frame.headers['message']);
        };

        client.activate();
        set({stompClient: client});
      },

      disconnect: () => {
        const {stompClient} = get();
        if(stompClient) {
          stompClient.deactivate();
          set({isConnected: false, stompClient: null, gameData: null});
        }
      },

      sendSeat: (roomId, seatIndex) => {
        const { stompClient, currentUsername } = get();
        if (stompClient?.connected) {
          stompClient.publish({
            destination: '/app/game/seat',
            body: JSON.stringify({ roomId, username: currentUsername, seatIndex }),
          });
        }
      },

      startGame: (roomId) => {
        const {stompClient, currentUsername} = get();
        if(stompClient?.connected) {
          stompClient.publish({
            destination: '/app/game/start',
            body: JSON.stringify({roomId, username: currentUsername}),
          });
        }
      },

      sendBet: (roomId, betAmount) => {
        const {stompClient, currentUsername} = get();
        if(stompClient?.connected) {
          stompClient.publish({
            destination: '/app/game/bet',
            body: JSON.stringify({roomId, username: currentUsername, betAmount}),
          });
        }
      },

      sendInsurance: (roomId, accept) => {
        const {stompClient, currentUsername} = get();
        if(stompClient?.connected) {
          stompClient.publish({
            destination: '/app/game/insurance',
            body: JSON.stringify({roomId, username: currentUsername, accept}),
          });
        }
      },

      sendAction: (roomId, action, handIndex) => {
        const {stompClient, currentUsername} = get();
        if(stompClient?.connected) {
          stompClient.publish({
            destination: '/app/game/action',
            body: JSON.stringify({roomId, username: currentUsername, action, handIndex}),
          });
        }
      },
    }),
    {
      name: 'blackjack-session-storage',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ currentUsername: state.currentUsername }), 
    }
  )
);