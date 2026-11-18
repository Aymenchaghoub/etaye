const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/runtime.js','utf8');
function fixture(){
 const page={id:'15:2',type:'PAGE'},section={id:'section',parent:page};
 const root={id:'root',type:'FRAME',name:'S-22/test',parent:section,width:1440,height:100,children:[],stamp:'initial'};
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
 flows[0].chains[0].segments[1].sourceX=500;
 await assert.rejects(f.context.populateInlineFlowEdits(f.scope,plan,flows),/Invalid measured inline gap/);
});
