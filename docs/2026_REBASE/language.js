/* Chapter 22 library explorer and Chapter 23 methods example. No fetch needed. */
(function () {
  const R=window.REBASE, esc=R.escape, files=R.library.files;
  const parent=path=>path.split('/').slice(0,-1).join('/');
  const button=(path,label,cls='')=>'<button class="'+cls+'" data-library-path="'+esc(path)+'">'+label+'</button>';
  R.libraryHasPath=path=>typeof path==='string' && (path==='' || Object.hasOwn(files,path) || Object.keys(files).some(file=>file.startsWith(path+'/')));
  function explorer(state) {
    const path=state.library, isFile=Object.hasOwn(files,path);
    let crumbs=button('','Standard library');
    path.split('/').filter(Boolean).forEach((part,i,parts)=>{
      crumbs+='<span>/</span>'+button(parts.slice(0,i+1).join('/'),esc(part));
    });
    let contents;
    if(isFile) {
      contents='<pre class="library-text" data-library-scroll tabindex="0" aria-label="Source of '+esc(path)+'"><code>'+esc(files[path])+'</code></pre>';
    } else {
      const entries=new Map(), prefix=path?path+'/':'';
      for(const file of Object.keys(files)) if(file.startsWith(prefix)) {
        const rest=file.slice(prefix.length), name=rest.split('/')[0];
        entries.set(name,rest.includes('/'));
      }
      contents='<div class="library-list" tabindex="0" aria-label="Files in '+esc(path||'standard library')+'">'+
        [...entries].sort(([a,ad],[b,bd])=>Number(bd)-Number(ad)||a.localeCompare(b)).map(([name,dir])=>
          button(prefix+name,'<span class="file-kind">'+(dir?'DIR':'SMP')+'</span><span>'+esc(name)+(dir?'/':'')+'</span><span class="file-arrow">'+(dir?'›':'↗')+'</span>','library-entry')).join('')+'</div>';
    }
    return '<section class="panel language-panel library-panel" aria-label="Standard library explorer"><div class="library-toolbar"><button data-language="library-close">← Discussion</button><button data-library-path="'+esc(parent(path))+'"'+(path===''?' disabled':'')+'>↑ Up</button><button data-language="library-width" aria-pressed="'+!!state.libraryWide+'">'+(state.libraryWide?'Two columns':'Full width')+'</button>'+(isFile?'<button data-language-copy="file">Copy file</button>':'')+'</div><nav class="library-crumbs" aria-label="Library path">'+crumbs+'</nav><p class="library-origin">Chapter 25 · '+esc(R.library.origin)+'</p>'+contents+'</section>';
  }
  function intro(which,state) {
    const methods=which==='methods';
    return '<section class="panel language-panel"'+(methods?' data-reveal="1"':'')+'><button class="language-flip" data-language="'+(methods?'methods-open':'library-open')+'"'+(methods && state.step<1?' disabled':'')+'><span class="language-face"><span class="eyebrow">'+(methods?'23 · Methods and types':'22 · Hello, world')+'</span><span class="language-title">'+(methods?'A method is a function<br>with a receiver.':'Cross the runtime boundary.')+'</span>'+
      (methods?'<span class="cycle"><span>Struct</span><b>⇄</b><span>Function(self)</span></span><span class="language-paragraph">Recursive type graphs require cyclic equality and interning.</span><span class="language-paragraph muted">The same meet/dual interface remains.</span>':'<code class="language-hello">sys.io.p("Hello, World!");</code><span class="language-paragraph">Strings, constant data, C calls,<br>and a small system library.</span><span class="language-paragraph muted">The ABI becomes observable behavior.</span>')+
      '</span><span class="flip-hint">'+(methods?'Click to see a class and its methods':'Click to explore the standard library')+' ↻</span></button></section>';
  }
  function methods(state) {
    return '<section class="panel language-panel methods-panel" data-reveal="1"><div class="library-toolbar"><button data-language="methods-close">← Discussion</button><strong>23 · Counter</strong><button data-language-copy="methods">Copy code</button></div><pre class="methods-source"><code>'+esc(R.library.methods)+'</code></pre><p class="methods-caption"><code>c.add(3).add(4)</code> passes <code>c</code> as <code>self</code>.<br>The program returns <strong>arg + 7</strong>.</p></section>';
  }
  R.languageBody=state=>'<h2>The language starts<br>using its compiler.</h2><div class="two-up language-layout'+(state.library!==null && state.libraryWide?' library-wide':'')+'">'+
    (state.library===null?intro('library',state):explorer(state))+
    (state.library!==null && state.libraryWide?'':state.methods?methods(state):intro('methods',state))+'</div>';
})();
