/* Export only visible native geometry/text; never flatten an entire UI into an image. */
() => {
  const number = value => Number.parseFloat(value) || 0;
  const color = value => {
    const m = value.match(/^rgba?\(([^)]+)\)/);
    if (!m) return null;
    const c = m[1].split(/[, /]+/).map(Number);
    return {r:c[0]/255,g:c[1]/255,b:c[2]/255,a:c.length>3?c[3]:1};
  };
  const textRuns = (node, style, parent) => {
    const runs=[]; let run=null;
    const range=document.createRange();
    for(let i=0;i<node.textContent.length;i++){
      range.setStart(node,i);range.setEnd(node,i+1);
      const rect=range.getBoundingClientRect();
      if(!rect.width||!rect.height)continue;
      let char=node.textContent[i];
      if(style.whiteSpace!=='pre'&&style.whiteSpace!=='pre-wrap')char=char.replace(/\s/,' ');
      if(style.textTransform==='uppercase')char=char.toUpperCase();
      if(style.textTransform==='lowercase')char=char.toLowerCase();
      if(!run||Math.abs(run.y-(rect.y+scrollY-parent.y))>1){
        run={type:'text',name:'Text',text:'',x:rect.x+scrollX-parent.x,y:rect.y+scrollY-parent.y,w:0,h:rect.height,
          family:style.fontFamily.split(',')[0].trim().replace(/["']/g,''),weight:number(style.fontWeight),italic:style.fontStyle==='italic',size:number(style.fontSize),lineHeight:number(style.lineHeight)||number(style.fontSize)*1.2,color:color(style.color)};
        runs.push(run);
      }
      run.text+=char;run.w=rect.right+scrollX-parent.x-run.x;
    }
    return runs;
  };
  const visit=(element,parent)=>{
    if(['SCRIPT','STYLE','LINK','TEMPLATE','NOSCRIPT'].includes(element.tagName)||element.matches('.design-notes,.guided-design,.prototype-notice'))return null;
    const style=getComputedStyle(element),rect=element.getBoundingClientRect();
    if(style.display==='none'||style.visibility==='hidden'||!rect.width||!rect.height||number(style.opacity)===0)return null;
    const absolute={x:rect.x+scrollX,y:rect.y+scrollY};
    const item={type:'frame',name:element.tagName.toLowerCase()+(element.id?'#'+element.id:'.'+(element.getAttribute('class')||'').split(/\s+/).filter(Boolean).slice(0,2).join('.')),
      x:absolute.x-parent.x,y:absolute.y-parent.y,w:rect.width,h:rect.height,opacity:number(style.opacity),fill:color(style.backgroundColor),radius:number(style.borderTopLeftRadius),clip:['hidden','auto','scroll','clip'].includes(style.overflowX)||['hidden','auto','scroll','clip'].includes(style.overflowY),children:[]};
    if(element.tagName.toUpperCase()==='SVG'){
      const clone=element.cloneNode(true);
      const sourceNodes=[element,...element.querySelectorAll('*')],copyNodes=[clone,...clone.querySelectorAll('*')];
      for(let index=0;index<sourceNodes.length;index++){
        const computed=getComputedStyle(sourceNodes[index]);
        for(const property of ['fill','stroke','stroke-width','stroke-linecap','stroke-linejoin','opacity'])copyNodes[index].setAttribute(property,computed.getPropertyValue(property));
      }
      for(const text of clone.querySelectorAll('text'))text.remove();
      item.type='svg';item.svg=clone.outerHTML.replace(/currentColor/g,style.color).replace(/var\((--[^)]+)\)/g,(_,name)=>style.getPropertyValue(name).trim());
      for(const text of element.querySelectorAll('text'))for(const node of text.childNodes)if(node.nodeType===Node.TEXT_NODE)item.children.push(...textRuns(node,getComputedStyle(text),absolute));
      return item;
    }
    if(element.tagName==='IMG'){item.type='image';item.src=element.currentSrc;return item;}
    const mask=style.maskImage||style.webkitMaskImage;
    if(mask&&mask.includes('data:image/svg+xml')){
      const url=mask.match(/url\(["']?(.*?)["']?\)$/)?.[1];
      if(url){item.type='svg';item.svg=decodeURIComponent(url.split(',').slice(1).join(',')).replace(/(?:black|currentColor)/g,style.color);return item;}
    }
    item.borders=['Top','Right','Bottom','Left'].map(edge=>({width:number(style['border'+edge+'Width']),color:color(style['border'+edge+'Color'])}));
    if(element.tagName==='INPUT'&&['checkbox','radio'].includes(element.type)){
      item.radius=element.type==='radio'?rect.width/2:2;
      item.fill=element.checked?color(style.accentColor)||color('rgb(43,76,126)'):color('rgb(255,255,255)');
      if(element.checked)item.children.push({type:'text',name:'Selected',text:element.type==='radio'?'•':'✓',x:2,y:0,w:rect.width-2,h:rect.height,family:'Atkinson Hyperlegible Next',weight:400,size:rect.height-2,lineHeight:rect.height,color:color('rgb(255,255,255)')});
      return item;
    }
    if(['INPUT','TEXTAREA','SELECT'].includes(element.tagName)){
      const value=element.tagName==='SELECT'?element.selectedOptions[0]?.text:element.value||element.placeholder;
      if(value)item.children.push({type:'text',name:'Field value',text:value,x:number(style.paddingLeft)+2,y:element.tagName==='TEXTAREA'?number(style.paddingTop)+2:Math.max(number(style.paddingTop)+2,(rect.height-(number(style.lineHeight)||number(style.fontSize)*1.2))/2),w:Math.max(1,rect.width-number(style.paddingLeft)-number(style.paddingRight)-4),h:rect.height,family:style.fontFamily.split(',')[0].trim().replace(/["']/g,''),weight:number(style.fontWeight),italic:false,size:number(style.fontSize),lineHeight:number(style.lineHeight)||number(style.fontSize)*1.2,color:color(style.color)});
      return item;
    }
    for(const node of element.childNodes){
      if(node.nodeType===Node.TEXT_NODE)item.children.push(...textRuns(node,style,absolute));
      else if(node.nodeType===Node.ELEMENT_NODE){const child=visit(node,absolute);if(child)item.children.push(child);}
    }
    return item;
  };
  return {width:innerWidth,height:Math.max(innerHeight,document.documentElement.scrollHeight),background:color(getComputedStyle(document.body).backgroundColor),node:visit(document.body,{x:0,y:0})};
}
