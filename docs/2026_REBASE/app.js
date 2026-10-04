(function () {
  "use strict";
  const R=window.REBASE, slides=R.slides, $=id=>document.getElementById(id);
  const presenter=new URLSearchParams(location.search).has("presenter");
  const state={index:0,step:0,demo:null,view:"example",backend:0,library:null,libraryWide:false,libraryScroll:0,methods:false};
  let held=0,started=null,toastTimeout,channel;
  if(presenter) document.body.classList.add("presenter");
  try { channel=new BroadcastChannel("simple-rebase-"+location.pathname); } catch (_) {}
  const current=()=>slides[state.index];
  const view=()=>current().views?.find(v=>v.id===state.view);
  const steps=()=>view()?.steps??current().steps;
  const elapsed=()=>held+(started===null?0:Date.now()-started);
  const clock=ms=>Math.floor(ms/60000).toString().padStart(2,"0")+":"+Math.floor(ms/1000%60).toString().padStart(2,"0");
  const starts=i=>slides.slice(0,i).reduce((n,s)=>n+s.minutes,0);
  function fit() {
    const box=$("viewport").getBoundingClientRect();
    document.documentElement.style.setProperty("--scale",Math.min(box.width/1440,box.height/900));
  }
  function readHash() {
    const [id,step,tab]=location.hash.slice(1).split("/");
    const index=slides.findIndex(s=>s.id===id);
    if(index>=0) {
      state.index=index;
      state.view=slides[index].views?.some(v=>v.id===tab)?tab:"example";
      state.step=Math.max(0,Math.min(steps(),Number(step)||0));
      state.demo=null;
    }
  }
  function broadcast() {
    if(!presenter && channel) channel.postMessage({type:"state",state:{...state},elapsed:elapsed(),running:started!==null});
  }
  function render(remote=false) {
    const s=current(), count=slides.filter(x=>!x.optional).length;
    const v=view(), builds=steps();
    const tabs=s.views?'<nav class="view-tabs" aria-label="Slide views">'+
      [{id:"example",label:s.id==="memory"?"Examples":"Example"},...s.views].map(t=>
        '<button data-view="'+t.id+'" aria-pressed="'+String(state.view===t.id)+'">'+R.escape(t.label)+'</button>').join("")+'</nav>':"";
    const number=s.optional?"BONUS":slides.slice(0,state.index+1).filter(x=>!x.optional).length;
    $("slide").className="slide"+(s.still?" still":"");
    $("slide").dataset.id=s.id;
    $("slide").dataset.step=state.step;
    $("slide").dataset.view=state.view;
    $("slide").innerHTML='<header class="kicker"><span>'+R.escape(s.section)+'</span>'+tabs+'<span class="tag">'+(s.demo&&s.id!=="sccp"?"LIVE GRAPH":s.optional?"OPTIONAL":"REBASE 2026")+'</span></header>'+
      '<div class="slide-content">'+(v?v.body(state):s.body(state))+'</div><footer class="footer"><span><strong>Simple</strong> / Cliff Click</span>'+
      '<span class="build-dots" aria-label="Build '+state.step+' of '+builds+'">'+Array.from({length:builds+1},(_,i)=>'<i class="'+(i<=state.step?"on":"")+'"></i>').join("")+'</span>'+
      '<span>'+R.escape(s.section)+' <strong> / '+number+'</strong></span></footer>';
    $("slide").querySelectorAll("[data-reveal]").forEach(el=>{
      const hidden=Number(el.dataset.reveal)>state.step;
      el.classList.toggle("unrevealed",hidden);
      el.setAttribute("aria-hidden",hidden?"true":"false");
      el.inert=hidden;
    });
    $("slide").querySelectorAll(".state-card").forEach(card=>{
      const entries=card.querySelectorAll(".state");
      entries.forEach((el,i)=>{
        const active=i===Math.min(state.step,entries.length-1);
        el.classList.toggle("active",active);
        el.setAttribute("aria-hidden",String(!active));
      });
    });
    $("slide").querySelectorAll("[data-demo]").forEach(button=>{
      const active=button.dataset.demo===(state.demo||s.demo);
      button.classList.toggle("active",active);
      button.setAttribute("aria-pressed",String(active));
    });
    $("position").textContent=s.optional?"BONUS":number+" / "+count;
    $("previous").disabled=state.index===0&&state.step===0;
    $("next").disabled=state.index===slides.length-1&&state.step===builds;
    $("notes-content").innerHTML=v?v.notes:s.notes;
    const source=$("slide").querySelector('[data-library-scroll]');
    if(source) source.scrollTop=state.libraryScroll;
    document.title=s.label+" · A Simple Tutorial";
    if(!remote) {
      history.replaceState(null,"","#"+s.id+(v?"/"+state.step+"/"+v.id:state.step?"/"+state.step:""));
      broadcast();
    }
    tick();
    fit();
  }
  function go(id) {
    const index=slides.findIndex(s=>s.id===id);
    if(index<0) return;
    state.index=index;state.step=0;state.demo=null;state.view="example";render();
  }
  function move(direction,jump=false) {
    const s=current();
    if(!jump && direction>0 && state.step<steps()) {state.step++;render();return;}
    if(!jump && direction<0 && state.step>0) {state.step--;render();return;}
    let index=state.index+direction;
    while(index>=0&&index<slides.length&&slides[index].optional) index+=direction;
    if(index<0||index>=slides.length) return;
    state.index=index;state.step=direction<0&&!jump?slides[index].steps:0;state.demo=null;state.view="example";render();
  }
  function action(name,value,remote=false) {
    if(presenter&&!remote&&channel) {channel.postMessage({type:"command",name,value});return;}
    if(name==="next") move(1);
    else if(name==="previous") move(-1);
    else if(name==="next-slide") move(1,true);
    else if(name==="previous-slide") move(-1,true);
    else if(name==="goto") go(value);
    else if(name==="demo") {state.demo=value;render();}
    else if(name==="language") {
      if(current().id!=="language") return;
      if(value==="library-open") {state.library="sys";state.libraryScroll=0;}
      else if(value==="library-close") {state.library=null;state.libraryWide=false;state.libraryScroll=0;}
      else if(value==="library-width") state.libraryWide=!state.libraryWide;
      else if(value==="methods-open" && state.step>=1) state.methods=true;
      else if(value==="methods-close") state.methods=false;
      else return;
      render();
      const focus=value==="library-close"?'[data-language="library-open"]':value==="methods-close"?'[data-language="methods-open"]':value.startsWith("methods")?'[data-language="methods-close"]':'[data-language="library-close"]';
      $("slide").querySelector(focus)?.focus({preventScroll:true});
    }
    else if(name==="library-path") {
      if(current().id!=="language"||!R.libraryHasPath(value)) return;
      state.library=value;state.libraryScroll=0;render();
      $("slide").querySelector('[data-library-scroll],.library-list')?.focus({preventScroll:true});
    }
    else if(name==="library-scroll") {
      if(current().id!=="language"||!Number.isFinite(value)||value<0) return;
      state.libraryScroll=value;
      const source=$("slide").querySelector('[data-library-scroll]');
      if(source && Math.abs(source.scrollTop-value)>1) source.scrollTop=value;
      broadcast();
    }
    else if(name==="backend") {
      const index=Number(value);
      if(current().id!=="backend"||!Number.isInteger(index)||index<0||index>state.step) return;
      state.backend^=1<<index;render();
      $("slide").querySelector('[data-backend="'+index+'"]').focus({preventScroll:true});
    }
    else if(name==="view") {
      if(value!=="example"&&!current().views?.some(v=>v.id===value)) return;
      state.view=value;state.step=0;render();
      $("slide").querySelector('[data-view="'+value+'"]').focus({preventScroll:true});
    }
    else if(name==="cycle-view"&&current().views) {
      const ids=["example",...current().views.map(v=>v.id)];
      action("view",ids[(ids.indexOf(state.view)+1)%ids.length],remote);
    }
    else if(name==="timer") {
      if(started===null) started=Date.now();
      else {held=elapsed();started=null;}
      tick();broadcast();
    } else if(name==="reset") {held=0;started=null;tick();broadcast();}
  }
  function tick() {
    const s=current(), time=elapsed(), start=starts(state.index), end=start+s.minutes;
    $("timer").textContent=(started===null?(held?"Resume ":"Start timer "):"Pause ")+clock(time);
    $("pace").textContent=(presenter&&!channel?"Independent notes · ":"")+
      (s.optional?"Optional 2 min · replaces closing discussion":"Planned "+clock(start*60000)+"–"+clock(end*60000))+
      " · elapsed "+clock(time);
    $("pace").classList.toggle("over",!s.optional&&time>end*60000);
  }
  function toast(message) {
    clearTimeout(toastTimeout);
    $("toast").textContent=message;$("toast").classList.add("show");
    toastTimeout=setTimeout(()=>$("toast").classList.remove("show"),2600);
  }
  function openDialog(content) {
    $("dialog-content").innerHTML=content;
    if(!$("dialog").open) $("dialog").showModal();
  }
  async function copy(text,label) {
    try {
      if(!navigator.clipboard) throw new Error("No clipboard API");
      await navigator.clipboard.writeText(text);
      toast(label+" copied");
    } catch (_) {
      const field=document.createElement("textarea");
      field.value=text;field.style.cssText="position:fixed;left:-9999px";
      document.body.append(field);field.select();
      let ok=false;
      try {ok=document.execCommand("copy");} catch (_) {}
      field.remove();
      if(ok) toast(label+" copied");
      else {
        openDialog('<h2>Copy '+R.escape(label.toLowerCase())+'</h2><p>Select and copy the text below.</p><textarea id="manual-copy" spellcheck="false"></textarea>');
        $("manual-copy").value=text;$("manual-copy").focus();$("manual-copy").select();
      }
    }
  }
  function overview() {
    openDialog('<h2>The 30-minute route</h2><div class="overview-grid">'+slides.map(s=>
      '<button data-goto="'+s.id+'">'+R.escape(s.label)+'<small>'+
      (s.optional?"Optional · 2 min, outside the default route":s.minutes+" min · "+(s.demo&&s.id!=="sccp"?"slide + viewer":"slides"))+
      '</small></button>').join("")+'</div>');
  }
  function help() {
    openDialog('<h2>Presentation controls</h2><div class="keys">'+[
      ["← / →","Previous / next build"],["Space","Next build"],["PgUp / PgDn","Previous / next slide"],["Home / End","Title / closing slide"],
      ["L","Cycle example / lattice tabs"],["O","Slide overview (includes optional Chapter 25)"],["N","Toggle notes on this screen"],["P","Open synchronized presenter window"],["T","Start / pause the talk timer"],["F","Fullscreen"],["B","Blank the projection"],["Esc","Close a dialog or unblank"],["?","This help"]
    ].map(([key,meaning])=>'<span><kbd>'+key+'</kbd></span><span>'+meaning+'</span>').join("")+
    '</div><p>Builds advance only when you ask. The talk timer keeps running while you switch to the graph viewer. For demos: Copy code → switch to viewer → paste → Compile.</p>');
  }
  function toggleNotes() {
    if(presenter) return;
    $("notes").hidden=!$("notes").hidden;
    $("notes-toggle").setAttribute("aria-pressed",String(!$("notes").hidden));
  }
  function openPresenter() {
    const url=new URL(location.href);url.searchParams.set("presenter","1");
    const child=window.open(url,"simple-rebase-presenter","width=1400,height=850");
    if(!child) toast("Allow popups for presenter mode, or press N for notes.");
  }
  async function fullscreen() {
    try {if(document.fullscreenElement) await document.exitFullscreen();else await document.documentElement.requestFullscreen();}
    catch (_) {toast("Use your browser’s fullscreen command.");}
  }
  function blank() {$("blackout").hidden=!$("blackout").hidden;}
  $("previous").addEventListener("click",()=>action("previous"));
  $("next").addEventListener("click",()=>action("next"));
  $("overview").addEventListener("click",overview);
  $("help").addEventListener("click",help);
  $("notes-toggle").addEventListener("click",toggleNotes);
  $("presenter").addEventListener("click",openPresenter);
  $("timer").addEventListener("click",()=>action("timer"));
  $("timer-reset").addEventListener("click",()=>action("reset"));
  $("fullscreen").addEventListener("click",fullscreen);
  $("dialog-close").addEventListener("click",()=>$("dialog").close());
  $("dialog").addEventListener("click",e=>{if(e.target===$("dialog")) $("dialog").close();});
  document.addEventListener("click",e=>{
    const b=e.target.closest("[data-copy],[data-command],[data-graph],[data-goto],[data-demo],button[data-view],button[data-backend],[data-language],[data-library-path],[data-language-copy]");
    if(!b) return;
    if(b.dataset.goto) {if($("dialog").open) $("dialog").close();action("goto",b.dataset.goto);}
    if(b.dataset.demo) action("demo",b.dataset.demo);
    if(b.dataset.backend!==undefined) action("backend",b.dataset.backend);
    if(b.dataset.language) action("language",b.dataset.language);
    if(b.dataset.libraryPath!==undefined) action("library-path",b.dataset.libraryPath);
    if(b.dataset.languageCopy) copy(b.dataset.languageCopy==='methods'?R.library.methods:R.library.files[state.library],b.dataset.languageCopy==='methods'?'Methods example':'Source file');
    if(b.dataset.view) action("view",b.dataset.view);
    if(b.dataset.copy) copy(R.demos.find(d=>d.id===b.dataset.copy).code,"Demo code");
    if(b.dataset.command) {
      const d=R.demos.find(d=>d.id===b.dataset.command);
      copy("make -C "+(d.viewerChapter||d.chapter)+" view","Viewer command");
    }
    if(b.dataset.graph) {
      const d=R.demos.find(d=>d.id===b.dataset.graph);
      openDialog('<h2>'+R.escape(d.title)+'</h2><img src="assets/'+d.id+'-final.svg" alt="Captured final compiler graph for '+R.escape(d.title)+'"><p>Captured from '+d.chapter+' after its viewer compilation phases. '+R.escape(d.inputs)+'.</p><p><a href="assets/'+d.id+'-final.svg" target="_blank" rel="noopener">Open full-size graph ↗</a></p>');
    }
  });
  document.addEventListener("scroll",e=>{
    if(!e.target.matches?.('[data-library-scroll]')) return;
    const top=e.target.scrollTop;
    if(Math.abs(top-state.libraryScroll)>1) action("library-scroll",top);
  },true);
  document.addEventListener("keydown",e=>{
    if(e.ctrlKey||e.altKey||e.metaKey||e.repeat) return;
    if(e.target.closest("textarea,input,select,[contenteditable=true]")) return;
    if(!$("blackout").hidden) {
      if(e.key==="b"||e.key==="B"||e.key==="Escape") {$("blackout").hidden=true;e.preventDefault();}
      return;
    }
    if($("dialog").open) return;
    if(e.target.closest('.library-text,.library-list') && ['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','PageUp','PageDown','Home','End',' '].includes(e.key)) return;
    let handled=true;
    switch(e.key) {
      case "ArrowRight": action("next");break;
      case " ": if(e.target.closest("button,a")) {handled=false;break;} action("next");break;
      case "ArrowLeft": action("previous");break;
      case "PageDown": action("next-slide");break;
      case "PageUp": action("previous-slide");break;
      case "Home": action("goto","title");break;
      case "End": action("goto","close");break;
      case "o": case "O": overview();break;
      case "l": case "L": action("cycle-view");break;
      case "n": case "N": toggleNotes();break;
      case "p": case "P": openPresenter();break;
      case "t": case "T": action("timer");break;
      case "f": case "F": fullscreen();break;
      case "b": case "B": blank();break;
      case "?": help();break;
      case "Escape": if(!presenter) {$("notes").hidden=true;$("notes-toggle").setAttribute("aria-pressed","false");} break;
      default: handled=false;
    }
    if(handled) e.preventDefault();
  });
  if(channel) channel.onmessage=e=>{
    const m=e.data;
    if(m.type==="request"&&!presenter) broadcast();
    if(m.type==="command"&&!presenter) action(m.name,m.value,true);
    if(m.type==="state"&&presenter) {
      const changed=JSON.stringify(state)!==JSON.stringify(m.state);
      const scrollOnly=changed && JSON.stringify({...state,libraryScroll:m.state.libraryScroll})===JSON.stringify(m.state);
      Object.assign(state,m.state);
      held=m.elapsed;started=m.running?Date.now():null;
      if(changed && !scrollOnly) render(true);
      else {
        const source=$("slide").querySelector('[data-library-scroll]');
        if(scrollOnly && source) source.scrollTop=state.libraryScroll;
        tick();
      }
    }
  };
  window.addEventListener("resize",fit);
  window.addEventListener("hashchange",()=>{if(!presenter){readHash();render();}});
  readHash();render();
  if(presenter) {$("notes").hidden=false;if(channel) channel.postMessage({type:"request"});}
  setInterval(()=>{tick();if(!presenter&&started!==null) broadcast();},1000);
  // Small public surface for rehearsal and offline export tools.
  R.go=id=>action("goto",id);
  R.state=()=>({...state,elapsed:elapsed(),running:started!==null});
})();
