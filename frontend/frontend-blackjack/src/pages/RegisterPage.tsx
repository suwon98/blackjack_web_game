import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { registerAPI } from "../api/authApi";
import axios from "axios";
import '../styles/AuthPage.css';

const RegisterPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const navigate = useNavigate();

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await registerAPI({username, password, nickname});
      alert('회원가입이 완료되었습니다. 로그인해주세요.');
      navigate('/login');
    } catch (error) {
      if(axios.isAxiosError(error) && error.response) {
        alert(error.response.data?.message || '회원가입에 실패했습니다.');
      } else {
        alert('서버와 통신할 수 없습니다.');
      }
    }
  };

  return (
    <div className="auth-container">
      <h1>회원가입</h1>
      <form className="auth-form" onSubmit={handleRegister}>
        <input 
          type="text" 
          placeholder="아이디 (영문 소문자, 숫자 4 ~ 20자)"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="auth-input"
          required
        />
        <input 
          type="password" 
          placeholder="비밀번호 (영문, 숫자, 특수문자 포함 8 ~ 16자)"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="auth-input"
          required
        />
        <input 
          type="text" 
          placeholder="게임 닉네임 (2 ~ 10자)"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          className="auth-input"
          required
        />
        <button type="submit" className="auth-button">가입하기</button>
      </form>
      <div className="auth-link" onClick={() => navigate('/login')}>
        이미 계정이 있으신가요? 로그인
      </div>
    </div>
  );
};

export default RegisterPage;