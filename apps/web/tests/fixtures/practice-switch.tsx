import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { PracticeSwitchDialog } from '../../src/auth/PracticeSwitchDialog';
import { AuthShell, FocusedHeading } from '../../src/auth/AuthComponents';

document.documentElement.dataset.theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'sombre' : 'clair';
const requested = new URLSearchParams(location.search).get('outcome');
const saveOutcome = requested === 'failed' || requested === 'invalid' || requested === 'unconfirmed'
  || requested === 'source-unavailable' || requested === 'target-unavailable' ? requested : 'entered';

function Fixture() {
  const [open, setOpen] = useState(false);
  const [memo, setMemo] = useState('Saisie de démonstration en cours.');
  const [screen, setScreen] = useState<'source' | 'destination' | 'removed' | 'choice'>('source');
  const [attempts, setAttempts] = useState(0);
  const leaveSource = (next: 'destination' | 'removed' | 'choice') => {
    setMemo('');
    setOpen(false);
    setScreen(next);
  };

  return <AuthShell wide>
    <FocusedHeading>{{
      source: 'Votre saisie', destination: 'Cabinet du Canal', removed: 'Accès modifié', choice: 'Choisir un contexte',
    }[screen]}</FocusedHeading>
    {screen === 'source' && <>
      <label htmlFor="saisie">Saisie</label>
      <textarea id="saisie" value={memo} onChange={(event) => setMemo(event.target.value)} />
      <button type="button" onClick={() => setOpen(true)}>Changer de contexte</button>
    </>}
    <output data-testid="attempts">{attempts}</output>
    {open && <PracticeSwitchDialog destination="Cabinet du Canal" onStay={() => setOpen(false)}
      onDiscard={() => leaveSource('destination')} onEntered={() => leaveSource('destination')}
      onChooseAnother={() => leaveSource('choice')} onSourceUnavailable={() => leaveSource('removed')}
      saveAndVerify={async () => {
        setAttempts((current) => current + 1);
        await new Promise((resolve) => window.setTimeout(resolve, 500));
        return saveOutcome;
      }} />}
  </AuthShell>;
}

createRoot(document.getElementById('root')!).render(<Fixture />);
