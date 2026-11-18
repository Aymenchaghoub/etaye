const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/runtime.js','utf8');
function fixture(){
 const page={id:'15:2',type:'PAGE'},section={id:'section',parent:page};
 const root={id:'root',type:'FRAME',name:'S-22/test',parent:section,width:1440,height:100,children:[],stamp:'initial'};
 root.appendChild=n=>{if(n.parent?.children)n.parent.children=n.parent.children.filter(c=>c!==n);n.parent=root;root.children.push(n);};
 const text={id:'text',type:'TEXT',name:'Before',characters:'Before',parent:root,x:0,y:0,fontName:{family:'Atkinson',style:'Regular'}};root.children=[text];
 const protectedPage={id:'protected',type:'PAGE',stamp:'protected',loadAsync:async()=>{}};
 const nodes=new Map([root,text,protectedPage].map(n=>[n.id,n]));let creations=0;
 const figma={fileKey:'file',currentPage:page,root:{children:[page]},showUI(){},ui:{postMessage(){}},getNodeByIdAsync:async id=>nodes.get(id),loadFontAsync:async()=>{},commitUndo(){},createEllipse(){creations++;throw Error('unexpected creation');}};
 const scope={fileKey:'file',pageId:'15:2',roots:[{nodeId:'root',state:'S-22/test',name:root.name,sectionId:'section'}],protectedPages:[{id:'protected',fingerprint:'protected'}]};
 const plan={state:'S-22/test',rootId:'root',beforeFingerprint:'initial',edits:[{id:'text',name:'Before',type:'TEXT',props:{characters:'After',x:12}}],reorder:[],markers:[]};
 const context={figma,__html__:'',SCOPE:scope,PLAN:{plans:[plan]},nativeFingerprint:n=>n.stamp,descendants:n=>[n,...n.children],fontKey:f=>f.family+' '+f.style};vm.createContext(context);vm.runInContext(source,context);
 return {context,figma,root,text,scope,plan,nodes,creations:()=>creations,apply:()=>context.applyCorrections(scope,[plan])};
}
test('wrong file, protected-page drift and target drift fail before changes',async()=>{
 for(const kind of ['file','protected','target']){const f=fixture();if(kind==='file')f.figma.fileKey='wrong';if(kind==='protected')f.nodes.get('protected').stamp='changed';if(kind==='target')f.root.stamp='changed';await assert.rejects(f.apply());assert.equal(f.text.characters,'Before');assert.equal(f.creations(),0);}
});
test('off-root edits and unsupported properties cannot mutate another node',async()=>{
 for(const kind of ['outside','unsupported']){const f=fixture();if(kind==='outside')f.plan.edits[0].id='protected';else f.plan.edits[0].props.remove=true;await assert.rejects(f.apply());assert.equal(f.text.characters,'Before');}
});
test('missing exact font and concurrent change during font load refuse all writes',async()=>{
 for(const kind of ['font','concurrent']){const f=fixture();f.figma.loadFontAsync=async()=>{if(kind==='font')throw Error('font unavailable');f.root.stamp='changed';};await assert.rejects(f.apply());assert.equal(f.text.characters,'Before');}
});
test('valid correction retains root/text identity and records exact edited IDs',async()=>{const f=fixture(),r=await f.apply();assert.equal(f.text.characters,'After');assert.equal(f.text.x,12);assert.equal(f.root.id,'root');assert.equal(f.text.id,'text');assert.equal(r.status,'corrected');assert.deepEqual(Array.from(r.states[0].editedNodeIds),['text']);assert.equal(r.approved,false);});
test('native setter failure rolls back earlier properties',async()=>{const f=fixture();let x=0;Object.defineProperty(f.text,'x',{get:()=>x,set:v=>{if(v===12)throw Error('native failure');x=v;}});await assert.rejects(f.apply(),/rolled back/);assert.equal(f.text.characters,'Before');assert.equal(f.text.x,0);});
test('inline citation gap follows measured native text width without changing typography',async()=>{
 const f=fixture();f.text.width=80;
 const citation={id:'citation',name:'Citation',type:'FRAME',x:102,width:20,parent:f.root};f.root.children.push(citation);
 const plan={plans:[f.plan]},flows=[{rootId:'root',state:'S-22/test',chains:[{parentId:'root',segments:[{id:'text',sourceX:0,sourceWidth:100},{id:'citation',sourceX:102,sourceWidth:20}]}]}];
 await f.context.populateInlineFlowEdits(f.scope,plan,flows);
 assert.equal(plan.plans[0].edits[0].props.x,82);assert.equal(f.text.characters,'Before');assert.equal(citation.x,102);
 flows[0].chains[0].segments[0].trailingSpaceAdvance=4;
 await f.context.populateInlineFlowEdits(f.scope,plan,flows);
 assert.equal(plan.plans[0].edits[0].props.x,86);
 f.text.children=[{x:0,width:83}];
 await f.context.populateInlineFlowEdits(f.scope,plan,flows);
 assert.equal(plan.plans[0].edits[0].props.x,89);
 flows[0].chains[0].segments[1].sourceX=500;
 await assert.rejects(f.context.populateInlineFlowEdits(f.scope,plan,flows),/Invalid measured inline gap/);
});
test('export rejects a corrected state changed after apply',async()=>{
 const f=fixture(),messages=[];f.figma.currentPage.children=[];f.figma.ui.postMessage=m=>messages.push(m);
 await f.figma.ui.onmessage({type:'apply'});
 vm.runInContext('result.renders=Array(52).fill({}); result.exportComplete=true;',f.context);
 f.root.stamp='changed-after-apply';
 await f.figma.ui.onmessage({type:'export'});
 assert.equal(messages.at(-1).failed,true);
 assert.match(messages.at(-1).status,/Corrected state drift since apply/);
 let saves=0;f.figma.saveVersionHistoryAsync=async()=>{saves++;};
 await f.figma.ui.onmessage({type:'save'});assert.equal(saves,0);
 assert.match(messages.at(-1).status,/Export before saving final version/);
});
test('a wrapped inline line clones exact text styling inside its verified parent',async()=>{
 const f=fixture();let clone;
 f.figma.currentPage.children=[];
 f.text.clone=()=>{clone={...f.text,id:'new-line',parent:f.figma.currentPage,remove(){f.root.children=f.root.children.filter(n=>n!==this);}};f.figma.currentPage.children.push(clone);return clone;};
 f.plan.splits=[{sourceId:'text',parentId:'root',characters:'Second measured line',x:0,y:30}];
 const r=await f.apply();assert.equal(f.text.id,'text');assert.equal(clone.parent,f.root);assert.equal(clone.fontName,f.text.fontName);
 assert.equal(clone.characters,'Second measured line');assert.equal(clone.x,0);assert.equal(clone.y,30);
 assert.deepEqual(Array.from(r.createdTextIds),['new-line']);
 const invalid=fixture();invalid.plan.splits=[{sourceId:'protected',parentId:'root',characters:'No',x:0,y:30}];
 await assert.rejects(invalid.apply(),/Invalid text-line split/);assert.equal(invalid.text.characters,'Before');
});
test('only a recorded unchanged newly created text can be adopted from the target page',async()=>{
 for(const mode of ['unauthorized','valid','concurrent']){
  const f=fixture(),page=f.figma.currentPage;
  const orphan={id:'own-line',type:'TEXT',name:'Second line',characters:'Second line',x:0,y:30,parent:page,fontName:f.text.fontName,fontSize:16,stamp:'own-line'};
  f.text.fontSize=16;page.children=[orphan];f.nodes.set(orphan.id,orphan);
  f.scope.adoptableTextIds=mode==='unauthorized'?[]:[orphan.id];
  if(mode==='concurrent')f.figma.loadFontAsync=async()=>{orphan.stamp='changed';};
  f.plan.adoptions=[{id:orphan.id,sourceId:'text',parentId:'root',characters:'Second line',x:0,y:30}];
  if(mode==='valid'){const r=await f.apply();assert.equal(orphan.parent,f.root);assert.equal(page.children.length,0);assert.deepEqual(Array.from(r.adoptedTextIds),['own-line']);}
  else{await assert.rejects(f.apply(),mode==='concurrent'?/Concurrent created text change/:/Unverified created text adoption/);assert.equal(orphan.parent,page);assert.equal(f.text.characters,'Before');}
 }
});
