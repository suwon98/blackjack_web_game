import axios from 'axios';
import { useAuthStore } from '../store/useAuthStore';
export const apiBase = import.meta.env.VITE_API_URL || '';
const api = axios.create({ baseURL: apiBase, timeout: 10000 });
api.interceptors.request.use(config => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
let refresh: Promise<string> | null = null;
api.interceptors.response.use(response => response, async error => {
  const request = error.config;
  if (error.response?.status !== 401 || !request || request._retry || /members\/(login|join|reissue)/.test(request.url)) throw error;
  request._retry = true;
  const token = useAuthStore.getState().refreshToken;
  if (!token) { useAuthStore.getState().logout(); throw error; }
  if (!refresh) {
    refresh = axios.post(`${apiBase}/api/members/reissue`, { refreshToken: token }, { timeout: 10000 })
      .then(({ data }) => { useAuthStore.getState().setTokens(data.accessToken, data.refreshToken); return data.accessToken as string; })
      .catch(e => { useAuthStore.getState().logout(); window.location.assign('/login'); throw e; })
      .finally(() => { refresh = null; });
  }
  request.headers.Authorization = `Bearer ${await refresh}`;
  return api(request);
});
export default api;
