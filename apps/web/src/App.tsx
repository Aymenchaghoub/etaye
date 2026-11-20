import { useEffect, useState } from 'react';
import { isApiReady } from './api/browser';

type Readiness = 'LOADING' | 'READY' | 'UNAVAILABLE';

export default function App() {
  const [readiness, setReadiness] = useState<Readiness>('LOADING');

  useEffect(() => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      controller.abort();
      setReadiness('UNAVAILABLE');
    }, 6_000);

    async function checkReadiness() {
      let nextState: Readiness = 'UNAVAILABLE';
      try {
        const ready = await isApiReady(controller.signal, () => {
          if (!controller.signal.aborted) {
            setReadiness('UNAVAILABLE');
          }
        });
        if (ready) nextState = 'READY';
      } catch {
        // Fetch and JSON parsing failures share the same public state.
      } finally {
        window.clearTimeout(timeout);
      }
      if (!controller.signal.aborted) {
        setReadiness(nextState);
      }
    }

    void checkReadiness();

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, []);

  return (
    <main style={{ padding: '2rem', fontFamily: 'system-ui, sans-serif' }}>
      <h1>Étaye - Plateforme Médicale</h1>
      <p>Consultation, inspection des preuves et documentation clinique.</p>

      <div style={{ marginTop: '1.5rem', border: '1px solid #ccc', padding: '1rem', borderRadius: '4px' }}>
        <h2>État de l'application (Sonde BDD)</h2>
        <div>
          Statut Système :{' '}
          <strong role="status" data-testid="readiness-status">{readiness}</strong>
        </div>
        {readiness === 'UNAVAILABLE' && (
          <div data-testid="probe-error" style={{ color: '#b00020', marginTop: '0.5rem' }}>
            Le service est temporairement indisponible.
          </div>
        )}
      </div>
    </main>
  );
}
