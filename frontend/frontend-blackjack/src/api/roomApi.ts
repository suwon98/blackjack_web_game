import axiosInstance from './axiosInstance';

export interface RoomCreateRequest {
  title: string;
  maxPlayers: number;
  isPrivate: boolean;
  inviteCode?: string;
  minBet: number;
}

export interface RoomJoinRequest {
  inviteCode?: string;
}

export interface RoomListResponse {
  roomId: string;
  title: string;
  maxPlayers: number;
  currentPlayers: number;
  isPrivate: boolean;
  minBet: number;
  isPlaying: boolean;
  joined: boolean;
  paused: boolean;
}

export const getRooms = () => {
  return axiosInstance.get<RoomListResponse[]>('/api/rooms');
};

export const createRoom = (data: RoomCreateRequest) => {
  return axiosInstance.post<RoomListResponse>('/api/rooms', data);
};

export const joinRoom = (roomId: string, data: RoomJoinRequest) => {
  return axiosInstance.post<RoomListResponse>(`/api/rooms/${roomId}/enter`, data);
};

export const leaveRoom = (roomId: string) => {
  return axiosInstance.post(`/api/rooms/${roomId}/leave`, {});
};