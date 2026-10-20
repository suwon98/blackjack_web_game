import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/useAuthStore';
export default function OAuth2Callback() {
  const navigate = useNavigate();
  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1));
    const access = params.get('accessToken'); const refresh = params.get('refreshToken');
    window.history.replaceState({}, '', '/oauth2/callback');
    if (access && refresh) { useAuthStore.getState().setTokens(access, refresh); navigate('/', { replace: true }); }
    else navigate('/login', { replace: true });
  }, [navigate]);
  return <p>로그인 처리 중...</p>;
}
