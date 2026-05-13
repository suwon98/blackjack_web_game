import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/useAuthStore";
import { loginAPI } from "../api/authApi";
import axios from "axios";
import '../styles/AuthPage.css';

const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  
  const setTokens = useAuthStore((state) => state.setTokens);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const response = await loginAPI({username, password});
      
      // 🚀 백엔드에서 데이터가 정확히 어떻게 오는지 콘솔에 출력!
      console.log("로그인 응답 데이터:", response.data); 

      setTokens(response.data.accessToken, response.data.refreshToken);
      navigate('/');
    } catch (error) {
       // ... 생략
      if(axios.isAxiosError(error) && error.response) {
        alert('아이디 또는 비밀번호가 올바르지 않습니다.');
      } else {
        alert('서버와 통신할 수 없습니다.');
      }
    }
  };

  return (
    <div className="auth-container">
      <h1>로그인</h1>
      <form className="auth-form" onSubmit={handleLogin}>
        <input 
          type="text" 
          placeholder="아이디" 
          value={username} 
          onChange={(e) => setUsername(e.target.value)} 
          className="auth-input" 
          required 
        />
        <input 
          type="password" 
          placeholder="비밀번호" 
          value={password} 
          onChange={(e) => setPassword(e.target.value)} 
          className="auth-input" 
          required 
        />
        <button type="submit" className="auth-button">로그인</button>
      </form>
      <div className="auth-link" onClick={() => navigate('/register')}>
        아직 계정이 없으신가요? 회원가입
      </div>
    </div>
  );
};

export default LoginPage;