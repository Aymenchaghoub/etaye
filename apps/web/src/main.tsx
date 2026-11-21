import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AuthApp, usesAuth } from './auth/AuthApp';
import { DemoShowcase } from './demo/DemoShowcase';
import { LOCAL_DEMO, SHOWCASE } from './demo/demo-entry';

const path = window.location.pathname;
const Screen = path === SHOWCASE && (LOCAL_DEMO || import.meta.env.DEV) ? DemoShowcase : usesAuth(path) ? AuthApp : App;

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Screen />
  </React.StrictMode>
);
