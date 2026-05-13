import axiosInstance from "./axiosInstance";

export interface MemberInfoResponse {
  username: string;
  nickname: string;
  role: string;
  money: number;
}

export const getMyInfo = () => {
  return axiosInstance.get<MemberInfoResponse>('/api/members/me');
};