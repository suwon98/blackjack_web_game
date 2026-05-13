import axiosInstance from "./axiosInstance";

export interface LoginRequest {
  username: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  nickname: string;
}

export const loginAPI = (data: LoginRequest) => {
  return axiosInstance.post<TokenResponse>('/api/members/login', data);
};

export const registerAPI = (data: RegisterRequest) => {
  return axiosInstance.post<string>('/api/members/join', data);
};