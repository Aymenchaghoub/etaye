import { isApiReady } from '../api/browser';
import '../auth/auth.css';
import './demo.css';
import { applyDemoTheme, SHOWCASE } from './demo-entry';
import { demoSign } from './demo-sign';

applyDemoTheme();
const mark = document.querySelector<HTMLElement>('#signe')!;
const status = document.querySelector<HTMLElement>('#etat')!;
const retry = document.querySelector<HTMLButtonElement>('#reessayer')!;
mark.innerHTML = demoSign;

async function prepare() {
  const hadFocus = document.activeElement === retry;
  retry.hidden = true;
  if (hadFocus) {
    const heading = document.querySelector('h1')!;
    heading.tabIndex = -1;
    heading.focus();
  }
  status.textContent = 'Les données fictives se chargent…';
  mark.removeAttribute('data-state');
  const trace = window.setTimeout(() => { mark.dataset.state = 'loading'; }, 400);
  const deadline = Date.now() + 30_000;
  try {
    while (Date.now() < deadline) {
      try {
        if (await isApiReady(AbortSignal.timeout(3_000), () => {})) {
          const theme = new URLSearchParams(window.location.search).get('theme');
          window.location.replace(theme === 'clair' || theme === 'sombre' ? `${SHOWCASE}?theme=${theme}` : SHOWCASE);
          return;
        }
      } catch {
        // The edge can serve this page while the application is still starting.
      }
      await new Promise((resolve) => window.setTimeout(resolve, 1_500));
    }
    status.textContent = 'La démonstration est temporairement indisponible.';
    mark.dataset.state = 'unavailable';
    retry.hidden = false;
  } finally {
    window.clearTimeout(trace);
  }
}

retry.addEventListener('click', () => void prepare());
void prepare();
