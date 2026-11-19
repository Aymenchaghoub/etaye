import { useState, useEffect } from 'react';

interface ReadinessResponse {
  status: string;
  database: string;
}

export default function App() {
  const [readiness, setReadiness] = useState<ReadinessResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/v1/probes/readiness')
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(`Probe failed with status ${res.status}`);
        }
        return res.json() as Promise<ReadinessResponse>;
      })
      .then((data) => {
        setReadiness(data);
        setLoading(false);
      })
      .catch((err: Error) => {
        setError(err.message);
        setReadiness({ status: 'DOWN', database: 'DOWN' });
        setLoading(false);
      });
  }, []);

  if (loading) {
    return (
      <main>
        <h1>Étaye - Plateforme Médicale</h1>
        <div data-testid="readiness-status">LOADING</div>
      </main>
    );
  }

  const isReady = readiness?.status === 'UP' && readiness?.database === 'UP';

  return (
    <main style={{ padding: '2rem', fontFamily: 'system-ui, sans-serif' }}>
      <h1>Étaye - Plateforme Médicale</h1>
      <p>Consultation, inspection des preuves et documentation clinique.</p>
      
      <div style={{ marginTop: '1.5rem', border: '1px solid #ccc', padding: '1rem', borderRadius: '4px' }}>
        <h2>État de l'application (Sonde BDD)</h2>
        <div>
          Statut Système :{' '}
          <strong data-testid="readiness-status">{isReady ? 'READY' : 'UNAVAILABLE'}</strong>
        </div>
        <div style={{ marginTop: '0.5rem' }}>
          Base de données :{' '}
          <span data-testid="db-status">{readiness?.database ?? 'DOWN'}</span>
        </div>
        {error && (
          <div data-testid="probe-error" style={{ color: 'red', marginTop: '0.5rem' }}>
            Erreur détectée : {error}
          </div>
        )}
      </div>
    </main>
  );
}
