/* Local design navigation only. Never treat this as server authorization or persistence. */
(() => {
  const siteRoot = new URL('../../', location.href);
  const params = new URLSearchParams(location.search);
  const safeReturn = () => {
    const value = params.get('return');
    if (!value) return null;
    const url = new URL(value, location.href);
    return url.origin === siteRoot.origin && url.href.startsWith(siteRoot.href) ? url : null;
  };
  const go = (target) => {
    const url = new URL(target, location.href);
    if (!url.href.startsWith(siteRoot.href)) return;
    if (/\/I-09\/source-|\/S-29\/extrait-/.test(url.pathname)) {
      url.searchParams.set('return', location.href);
      if (document.activeElement.id) url.searchParams.set('focus', document.activeElement.id);
    }
    location.assign(url.href);
  };
  const journeyData=document.getElementById('design-journeys');
  if (journeyData && params.has('journey')) {
    const journey=JSON.parse(journeyData.textContent).find(j=>j.id===params.get('journey'));
    const step=Number(params.get('step'));
    if (journey && Number.isInteger(step) && step>=0 && step<journey.steps.length) {
      const nav=document.createElement('nav');nav.className='guided-design';nav.setAttribute('aria-label','Parcours de conception');
      const title=document.createElement('strong');title.textContent=journey.title+' · étape '+(step+1)+' / '+journey.steps.length;nav.append(title);
      const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Contexte du scénario';details.append(summary);const description=document.createElement('p');description.textContent=journey.scenario;details.append(description);nav.append(details);
      const links=document.createElement('div');
      for (const [offset,label] of [[-1,'Étape précédente'],[1,'Étape suivante']]) {
        const index=step+offset;if(index<0||index>=journey.steps.length)continue;
        const a=document.createElement('a');a.textContent=label;a.href='../'+journey.steps[index]+'.html?journey='+journey.id+'&step='+index;links.append(a);
      }
      const exit=document.createElement('a');exit.textContent='Tous les parcours';exit.href='../../parcours.html';links.append(exit);nav.append(links);
      document.body.prepend(nav);
      if (!dialogElementExists()) nav.querySelector('a')?.focus();
    }
  }
  function dialogElementExists() { return Boolean(document.querySelector('[role="dialog"],[role="alertdialog"]')); }
  const notify = (text) => {
    document.querySelector('.prototype-notice')?.remove();
    const box = document.createElement('div'); box.className='prototype-notice'; box.setAttribute('role','status');
    const message=document.createElement('span');message.textContent=text;box.append(message);
    const close=document.createElement('button');close.textContent='Fermer';close.type='button';close.addEventListener('click',()=>box.remove());box.append(close);document.body.append(box);
  };
  const openSource = (button) => {
    const source=document.getElementById(button.dataset.template);
    if(!source)return;
    // Keep the underlying editor alive in memory; no browser storage or fake save occurs.
    const old=document.querySelector('[data-source-preview]');old?.remove();
    const overlay=document.createElement('dialog');overlay.dataset.sourcePreview='';overlay.setAttribute('aria-label','Source datée');
    const close=document.createElement('button');close.type='button';close.textContent='Revenir à la phrase';
    const body=document.createElement('article');body.className='source-preview-body';body.append(source.content.cloneNode(true));
    close.addEventListener('click',()=>overlay.close());overlay.append(close,body);document.body.append(overlay);
    overlay.addEventListener('close',()=>{overlay.remove();button.focus();});overlay.showModal();close.focus();
  };
  const dialog=document.querySelector('[role="dialog"],[role="alertdialog"]');
  const closeDialog = () => {
    // Session recovery must never dismiss back into protected clinical content.
    if (/\/S-38\/expiree\.html$/.test(location.pathname)) { go('../S-02/connexion.html'); return; }
    const back=safeReturn();
    if (back) { if(params.get('focus'))back.searchParams.set('focus',params.get('focus'));go(back.href); return; }
    if(document.body.dataset.safeReturn){go(document.body.dataset.safeReturn);return;}
    const key=location.pathname.split('/ecrans/')[1]?.split('/')[0];
    const fallback={'I-01':'S-31/equipe','I-02':'S-32/membre','I-03':'S-25/accueil','I-09':'S-09/claire','I-10':'S-14/jour','I-11':'S-16/claire','I-12':'S-16/claire','I-19':'S-39/options','I-22':'S-02/connexion','I-25':'S-34/admin'};
    go('../'+(fallback[key]||'S-23/validation')+'.html');
  };
  if (dialog) {
    const safe=dialog.querySelector('[data-close], input, textarea, select, button:not([disabled]), a[href]');
    safe?.focus();
    document.addEventListener('keydown',event=>{
      if(document.querySelector('dialog[data-source-preview][open]'))return;
      if (event.key==='Escape') { event.preventDefault();closeDialog(); }
      if (event.key==='Tab') {
        const nodes=[...dialog.querySelectorAll('button:not([disabled]),a[href],input:not([disabled]),textarea:not([disabled]),select:not([disabled])')].filter(x=>x.getClientRects().length);
        const first=nodes[0],last=nodes.at(-1);
        if (event.shiftKey && document.activeElement===first) {event.preventDefault();last?.focus();}
        if (!event.shiftKey && document.activeElement===last) {event.preventDefault();first?.focus();}
      }
    });
  }
  document.querySelectorAll('[data-next]').forEach((button,index)=>{
    button.id ||= 'prototype-action-'+index;
    button.addEventListener('click',()=>{
      if(button.dataset.source){openSource(button);return;}
      if(button.dataset.requiredFields){
        const missing=button.dataset.requiredFields.split(',').map(id=>document.getElementById(id)).find(field=>!field?.value.trim());
        if(missing){missing.setAttribute('aria-invalid','true');missing.focus();notify('Renseignez le motif avant de continuer.');return;}
      }
      if(button.closest('[role="dialog"]') && /I-16\//.test(location.pathname) && /Valider/.test(button.textContent)) {
        const attestation=document.querySelector('input[type="checkbox"]');
        if(attestation && !attestation.checked){notify('Relisez la version et ses sources, puis cochez l’attestation avant de valider.');attestation.focus();return;}
      }
      if(/I-18\/retrait/.test(location.pathname) && /Retirer/.test(button.textContent)) {
        const reason=document.getElementById('i18-motif');if(reason && !reason.value.trim()){reason.setAttribute('aria-invalid','true');reason.focus();notify('Indiquez le motif du retrait.');return;}
      }
      go(button.dataset.next);
    });
  });
  document.querySelectorAll('[data-close]').forEach(button=>button.addEventListener('click',event=>{event.preventDefault();closeDialog();}));
  document.querySelectorAll('a[href]').forEach((anchor,index)=>{
    const target=new URL(anchor.href,location.href);
    if(anchor.hasAttribute('data-close') || !target.href.startsWith(siteRoot.href) || !/\/I-\d+\//.test(target.pathname)) return;
    anchor.id ||= 'prototype-link-'+index;
    anchor.addEventListener('click',event=>{
      event.preventDefault();target.searchParams.set('return',location.href);target.searchParams.set('focus',anchor.id);go(target.href);
    });
  });
  document.querySelectorAll('[data-menu]').forEach(button=>button.addEventListener('click',()=>{
    const menu=document.querySelector('.etaye-nav');if (!menu) return;
    const open=menu.dataset.open!=='true';menu.dataset.open=String(open);button.setAttribute('aria-expanded',String(open));
    if (open) menu.querySelector('a')?.focus();
  }));
  document.querySelectorAll('button:not([data-next]):not([data-close]):not([data-menu]):not([disabled])').forEach(button=>button.addEventListener('click',()=>{
    notify('Cette action secondaire est spécifiée dans la maquette. Elle ne déclenche aucun enregistrement dans ce prototype.');
  }));
  if(!dialog && params.get('focus'))document.getElementById(params.get('focus'))?.focus();
})();
