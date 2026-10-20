import { createBrowserRouter, Navigate } from 'react-router-dom'; // Navigate 추가
import LobbyPage from '../pages/LobbyPage';
import GamePage from '../pages/GamePage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import OAuth2Callback from '../pages/OAuth2Callback';
import ProtectedRoute from '../components/ProtectedRoute';

export const router = createBrowserRouter([
  { path: "/oauth2/callback", element: <OAuth2Callback /> },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        path: '/',
        element: <LobbyPage />,
      },
      {
        path: '/game/:roomId',
        element: <GamePage />,
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  }
]);