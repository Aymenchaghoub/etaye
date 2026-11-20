import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AuthApp, usesAuth } from './auth/AuthApp';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    {usesAuth(window.location.pathname) ? <AuthApp /> : <App />}
  </React.StrictMode>
);
