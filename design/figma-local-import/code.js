/* Native Figma plugin only. No account token, network call, app implementation or existing-node rewrite. */
figma.showUI(__html__, {width:440,height:520});
let busy=false,cancelled=false;
const tell=(message,done=false,evidence=null)=>figma.ui.postMessage({message,done,evidence});
const normalized=value=>value.toLowerCase().replace(/[\s_-]/g,'');
const weightOf=style=>{
  const s=normalized(style).replace(/italic/g,'');
  const names={thin:100,extralight:200,ultralight:200,light:300,regular:400,normal:400,book:400,medium:500,semibold:600,demibold:600,bold:700,extrabold:800,ultrabold:800,black:900,heavy:900};
  return names[s||'regular'];
};
const keyOf=node=>[node.family,node.weight,Boolean(node.italic)].join('|');
function walk(node,fn){fn(node);for(const child of node.children||[])walk(child,fn);}
function validate(data){
  if(data?.format!=='etaye-editable-design-v1'||!Array.isArray(data.frames)||!data.frames.length||data.frames.length>20)throw Error('Lot inconnu ou nombre d’écrans invalide.');
  if(!/^[a-f0-9]{64}$/.test(data.snapshotSha256))throw Error('Empreinte source manquante.');
  for(const frame of data.frames){
    if(typeof frame.id!=='string'||!frame.scene?.node)throw Error('Écran incomplet.');
    walk(frame.scene.node,node=>{
      if(!['frame','text','svg','image'].includes(node.type))throw Error('Type de calque inconnu.');
      for(const v of ['x','y','w','h'])if(!Number.isFinite(node[v])||Math.abs(node[v])>200000)throw Error('Géométrie invalide.');
      if(node.type==='text'&&(typeof node.text!=='string'||typeof node.family!=='string'||!Number.isFinite(node.size)))throw Error('Texte invalide.');
      if(node.type==='svg'&&(!node.svg||/<(?:script|foreignObject)\b|(?:href|src)\s*=\s*["']?https?:/i.test(node.svg)))throw Error('SVG externe ou exécutable refusé.');
    });
  }
}
function paint(c){return {type:'SOLID',color:{r:c.r,g:c.g,b:c.b},opacity:c.a??1};}
function fill(node,c){node.fills=c&&c.a!==0?[paint(c)]:[];}
async function importBatch(data){
  validate(data);
  const available=await figma.listAvailableFontsAsync();
  const needs=new Map();
  for(const frame of data.frames)walk(frame.scene.node,node=>{if(node.type==='text')needs.set(keyOf(node),node);});
  const fonts=new Map(),missing=[];
  for(const [key,node] of needs){
    const match=available.find(f=>normalized(f.fontName.family)===normalized(node.family)&&weightOf(f.fontName.style)===(node.weight||400)&&/italic/i.test(f.fontName.style)===Boolean(node.italic));
    if(match)fonts.set(key,match.fontName);else missing.push(node.family+' · '+node.weight+(node.italic?' Italic':''));
  }
  if(missing.length)throw Error('Polices à installer ou à rendre disponibles avant import :\n'+missing.join('\n')+'\nAucun calque créé. Aucune substitution silencieuse.');
  await Promise.all([...fonts.values()].map(font=>figma.loadFontAsync(font)));
  // Preflight precedes any canvas write. One new section owns all mutations of this run.
  const page=figma.currentPage;
  const duplicate=page.children.find(n=>n.getPluginData('etayeBatch')===data.snapshotSha256+':'+data.frames.map(f=>f.id).join(','));
  if(duplicate)throw Error('Ce lot existe déjà sur cette page : '+duplicate.name+'. Rien remplacé.');
  const created=[],roots=[];
  const section=figma.createSection();created.push(section.id);section.name='Étaye · import à revoir · '+data.frames[0].id;
  section.x=Math.max(0,...page.children.filter(n=>n.id!==section.id).map(n=>n.x+n.width))+160;section.y=80;
  section.setPluginData('etayeBatch',data.snapshotSha256+':'+data.frames.map(f=>f.id).join(','));
  const add=(node,parent)=>{created.push(node.id);parent.appendChild(node);return node;};
  function build(item,parent){
    let node;
    if(item.type==='text'){
      node=add(figma.createText(),parent);node.fontName=fonts.get(keyOf(item));node.fontSize=item.size;node.characters=item.text;
      node.textAutoResize='HEIGHT';node.resize(Math.max(1,item.w+1),Math.max(1,item.h));node.lineHeight={unit:'PIXELS',value:item.lineHeight||item.size*1.2};fill(node,item.color);
    }else if(item.type==='svg'){
      node=add(figma.createNodeFromSvg(item.svg),parent);node.resize(Math.max(1,item.w),Math.max(1,item.h));
      for(const child of node.findAll(()=>true))created.push(child.id);
    }else if(item.type==='image'){
      const image=figma.createImage(figma.base64Decode(item.base64));node=add(figma.createRectangle(),parent);node.resize(Math.max(1,item.w),Math.max(1,item.h));node.fills=[{type:'IMAGE',imageHash:image.hash,scaleMode:'FIT'}];
    }else{
      // Browser snapshots retain measured coordinates. These are editable inspection frames, not a new responsive component library.
      node=add(figma.createFrame(),parent);node.resize(Math.max(1,item.w),Math.max(1,item.h));node.clipsContent=Boolean(item.clip);fill(node,item.fill);node.cornerRadius=Math.min(item.radius||0,Math.min(item.w,item.h)/2);
      const edge=item.borders||[];
      for(let i=0;i<edge.length;i++)if(edge[i].width>0&&edge[i].color?.a){
        const border=add(figma.createRectangle(),node);border.name='Border '+i;fill(border,edge[i].color);
        border.resize(i%2?edge[i].width:item.w,i%2?item.h:edge[i].width);border.x=i===1?item.w-edge[i].width:0;border.y=i===2?item.h-edge[i].width:0;
      }
    }
    node.name=item.type==='text'?item.text.slice(0,80):item.name;node.x=item.x;node.y=item.y;
    if(item.opacity!==undefined)node.opacity=item.opacity;
    for(const child of item.children||[])build(child,node);
    return node;
  }
  try{
    let y=0;
    for(const frame of data.frames){
      if(cancelled)throw Error('Import arrêté ; nouveau lot supprimé.');
      const root=add(figma.createFrame(),section);root.name=frame.id+' · '+frame.viewport+' · à revoir';root.resize(frame.scene.width,frame.scene.height);root.x=0;root.y=y;root.clipsContent=true;fill(root,frame.scene.background);
      root.setPluginData('etayeState',frame.id);root.setPluginData('etayeSnapshot',data.snapshotSha256);
      build(frame.scene.node,root);roots.push({state:frame.id,nodeId:root.id});y+=frame.scene.height+120;
      tell('Import '+roots.length+' / '+data.frames.length+'…');
      await new Promise(resolve=>setTimeout(resolve,0));
    }
    section.resizeWithoutConstraints(Math.max(...data.frames.map(f=>f.scene.width))+80,y);
    figma.currentPage.selection=await Promise.all(roots.map(r=>figma.getNodeByIdAsync(r.nodeId)));figma.viewport.scrollAndZoomIntoView([section]);
    tell('Lot importé en calques modifiables. Vérifier texte, polices, icônes, découpage et navigation. Aucun écran approuvé.',true,{snapshotSha256:data.snapshotSha256,pageId:page.id,sectionId:section.id,roots,createdNodeIds:created,fonts:[...fonts.values()],figmaImported:true,visuallyReviewed:false,approved:false});
  }catch(error){section.remove();throw error;}
}
figma.ui.onmessage=async message=>{
  if(message.type==='cancel'){cancelled=true;if(!busy)figma.closePlugin();return;}
  if(message.type!=='import'||busy)return;
  busy=true;cancelled=false;
  try{await importBatch(message.data);}catch(error){tell(String(error.message||error),true);}finally{busy=false;}
};
