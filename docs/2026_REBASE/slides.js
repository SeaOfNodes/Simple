/* Content and speaker notes. Durations exclude the optional Chapter 25 slide. */
(function () {
  const R = window.REBASE;
  const esc = s => String(s).replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;");
  const external = (url, text, cls="") => '<a class="'+cls+'" href="'+esc(url)+'" target="_blank" rel="noopener noreferrer">'+text+'</a>';
  const demo = id => R.demos.find(d => d.id === id);
  function source(id, long=false) {
    const d=demo(id);
    return '<div class="demo-source"><div class="demo-code'+(long?' long':'')+'"><div class="code-head"><span>'+d.chapter+' / '+id+'.smp</span><button data-copy="'+id+'">Copy code</button></div><pre><code>'+esc(d.code)+'</code></pre></div><div class="demo-actions"><button class="primary" data-command="'+id+'">Copy viewer command</button>'+
      (id==='sccp'?'':'<button data-graph="'+id+'">Final graph</button>')+
      '<a href="demos/'+id+'.smp" download>Source ↗</a></div><code class="launch">make -C '+(d.viewerChapter||d.chapter)+' view</code></div>';
  }
  function result(id, label, formula) {
    const d=demo(id);
    return '<div class="result" data-reveal="2">'+(formula||'<strong>'+esc(d.result)+'</strong>')+'<small>'+esc(label||d.inputs)+'</small></div>';
  }
  function states(items) {
    return '<div class="state-card">'+items.map((item,i)=>'<div class="state" data-state="'+i+'">'+item+'</div>').join('')+'</div>';
  }
  function backendCard(state, index, title, discussion, flow, kind, caption) {
    const flipped=!!(state.backend & (1<<index));
    const text=kind==='selected'?R.backend[kind].trimEnd():R.backend[kind].trim().split('\n')
      .map(line=>line.trim().replace(/ {2,}/g,' ').replace(/-{4,}/g,'---')).join('\n');
    const dump=text.split('\n').map(line=>{
      const hot=/NegX86|MemAddX86|\bneg\b|\badd8\b/.test(line);
      return hot?'<strong>'+esc(line)+'</strong>':esc(line);
    }).join('\n');
    return '<div class="pipeline-card backend-card'+(flipped?' flipped':'')+'" data-reveal="'+index+'">'+
      '<button class="backend-flip" data-backend="'+index+'" aria-pressed="'+flipped+'" aria-label="Chapter '+(19+index)+': '+title+'; '+(flipped?'show discussion':'show compiler dump')+'"'+(state.step<index?' disabled':'')+'>'+
      '<span class="backend-face">'+(flipped?
        '<span class="dump-heading">'+(19+index)+' · '+caption+'</span><code class="backend-dump '+kind+'">'+dump+'</code>':
        '<span class="number">'+(19+index)+'</span><span class="backend-title">'+title+'</span><span class="backend-discussion">'+discussion+'</span><code class="backend-flow">'+flow+'</code>')+'</span>'+
      '<span class="flip-hint">'+(flipped?'Click to return to discussion':'Click to see '+caption.toLowerCase())+' ↻</span></button></div>';
  }
  function memoryPartition(state) {
    const array=state.demo==='memory', x=array?'Totals.sum':'Pair.x', y=array?'Totals.visits':'Pair.y';
    return '<figure class="memory-partition"><svg viewBox="0 0 600 160" role="img" aria-label="Whole memory partitioned into the disjoint equivalence classes '+x+', '+y+', and all the rest"><title>Equivalence classes partition whole memory</title>'+
      '<text class="partition-title" x="300" y="25" text-anchor="middle">Whole memory</text>'+
      '<rect class="alias-x" x="1" y="43" width="198" height="94"/>'+
      '<rect class="alias-y'+(state.step===1?' touched':'')+'" x="200" y="43" width="198" height="94"/>'+
      '<rect class="alias-rest" x="399" y="43" width="200" height="94"/>'+
      '<rect class="partition-outline" x="1" y="43" width="598" height="94"/>'+
      '<text x="100" y="85" text-anchor="middle">'+x+'</text><text class="partition-detail" x="100" y="113" text-anchor="middle">all instances</text>'+
      '<text x="300" y="85" text-anchor="middle">'+y+'</text><text class="partition-detail" x="300" y="113" text-anchor="middle">all instances</text>'+
      '<text x="500" y="85" text-anchor="middle">all the rest</text><text class="partition-detail" x="500" y="113" text-anchor="middle">'+(array?'including data[]':'other locations')+'</text></svg>'+
      '<figcaption>Disjoint classes; together, all memory.<br>Same class <em>may</em> alias. Different classes cannot.</figcaption></figure>';
  }
  function graph(kind) {
    const start='<svg class="svg-graph" viewBox="0 0 530 235" role="img" aria-label="'+(kind==='phi'?'Values feed a Phi associated with a Region':kind==='loop'?'Loop counter: Phi_i starts at zero and receives Add(Phi_i, 1) on the backedge':'Data dependencies in an arithmetic expression')+'"><defs><marker id="arrow" markerWidth="7" markerHeight="7" refX="6" refY="3" orient="auto"><path d="M0 0 L6 3 L0 6" fill="none" stroke="#587075"/></marker></defs>';
    if(kind==='loop') return start+
      '<path class="edge" marker-end="url(#arrow)" d="M210 105 H155"/><path class="edge" marker-end="url(#arrow)" d="M295 82 V48"/>'+
      '<path class="edge" marker-end="url(#arrow)" d="M335 128 V174"/><path class="edge" marker-end="url(#arrow)" d="M265 174 V128"/><path class="edge" marker-end="url(#arrow)" d="M370 198 H435"/>'+
      '<rect class="node ctrl" x="25" y="82" width="130" height="46" rx="7"/><text x="90" y="111" text-anchor="middle">Loop</text>'+
      '<rect class="node phi" x="210" y="82" width="170" height="46" rx="7"/><text x="295" y="111" text-anchor="middle">Phi_i</text>'+
      '<rect class="node" x="260" y="2" width="70" height="46" rx="7"/><text x="295" y="31" text-anchor="middle">0</text>'+
      '<rect class="node" x="230" y="174" width="140" height="48" rx="7"/><text x="300" y="205" text-anchor="middle">Add</text>'+
      '<rect class="node" x="435" y="174" width="70" height="48" rx="7"/><text x="470" y="205" text-anchor="middle">1</text></svg>';
    if(kind==='phi') return start+
      '<path class="edge" marker-end="url(#arrow)" d="M295 163 V91 H175 V69"/><path class="edge" marker-end="url(#arrow)" d="M305 163 V110 H425 V69"/><path class="edge" marker-end="url(#arrow)" d="M220 190 H181"/>'+
      '<rect class="node" x="110" y="22" width="130" height="46" rx="7"/><text x="175" y="51" text-anchor="middle">n</text>'+
      '<rect class="node" x="360" y="22" width="130" height="46" rx="7"/><text x="425" y="51" text-anchor="middle">-n</text>'+
      '<rect class="node ctrl" x="30" y="164" width="150" height="52" rx="7"/><text x="105" y="197" text-anchor="middle">Region</text>'+
      '<rect class="node phi" x="220" y="164" width="160" height="52" rx="7"/><text x="300" y="197" text-anchor="middle">Phi_n</text></svg>';
    return start+
      '<path class="edge" marker-end="url(#arrow)" d="M145 105 V68"/><path class="edge" marker-end="url(#arrow)" d="M205 105 V86 H270 V68"/><path class="edge" marker-end="url(#arrow)" d="M265 188 V167 H175 V150"/><path class="edge" marker-end="url(#arrow)" d="M325 188 V130 H410 V68"/>'+
      '<rect class="node" x="90" y="23" width="110" height="44" rx="7"/><text x="145" y="52" text-anchor="middle">arg</text>'+
      '<rect class="node" x="230" y="23" width="80" height="44" rx="7"/><text x="270" y="52" text-anchor="middle">2</text>'+
      '<rect class="node" x="370" y="23" width="80" height="44" rx="7"/><text x="410" y="52" text-anchor="middle">6</text>'+
      '<rect class="node" x="115" y="105" width="120" height="44" rx="7"/><text x="175" y="134" text-anchor="middle">Mul</text>'+
      '<rect class="node" x="235" y="188" width="120" height="44" rx="7"/><text x="295" y="217" text-anchor="middle">Add</text></svg><p class="graph-caption">Arrows point from uses to definitions.</p>';
  }
  const links=()=>'<div class="link-row">'+external(R.youtube,'YouTube · @compilers')+
    '<span class="pending-link">send an email for a discord invite</span>'+
    external(R.repo,'Simple on GitHub')+'</div>';
  const notes = (lead, beats, extra="") => '<h3>'+lead+'</h3><ol>'+beats.map(b=>'<li>'+b+'</li>').join('')+'</ol>'+extra;
  R.slides=[
    {
      id:'title',label:'A Simple Tutorial',section:'REBASE 2026',minutes:1,steps:0,still:true,
      body:()=>'<div class="title-layout"><div class="title-copy"><div class="title-line"></div><h1>A <span class="accent">Simple</span><br>Tutorial.</h1><p class="subtitle">An open-source, all-Java compiler<br>modeled after Java’s C2.</p><div class="speaker"><strong>Cliff Click</strong><span>Sea of Nodes · from source to machine code</span></div></div><div class="portrait-block"><img class="portrait" src="assets/cliff-click-2020.jpeg" alt="Cliff Click seated on outdoor stone steps"><p class="photo-caption">REBASE / SPLASH · 2026</p></div></div>'+external(R.repo+'/blob/main/docs/2026_REBASE/index.html','<span>Follow along</span>github.com/SeaOfNodes/Simple/<br>docs/2026_REBASE/index.html','title-repo'),
      notes:notes('An executable compiler tutorial',[
        'Assume SSA and compiler background. Simple is a small language used to explain a real optimizing compiler architecture.',
        'The compiler is written in Java, modeled on C2, and open source. The teaching unit is a complete compiler snapshot, with runnable tests and an explanation.',
        'Today: four live graphs, then the path to machine code. Keep the title still; advance when ready.'
      ])
    },
    {
      id:'bio',label:'Who am I?',section:'Cliff Click',minutes:1,steps:0,still:true,
      body:()=>'<div class="bio-grid"><div><h2>Who am I?</h2><p class="accent bio-name">Cliff Click</p><ul class="bio-list"><li>HotSpot’s original C2 compiler<small>Sea of Nodes, optimization, and the JVM</small></li><li>Azul, H2O, and half a dozen other startups<small>JVMs, low-pause GC, and distributed computing</small></li><li>Coffee Compiler Club<small>Founder and host · language implementation discussions</small></li></ul>'+links()+'</div><div class="portrait-block bio-media"><img class="portrait" src="assets/cliff-click-2020.jpeg" alt="Cliff Click seated on outdoor stone steps"><img class="club-image" src="assets/coffee-compiler-club-still.gif" alt="Coffee Compiler Club logo"><p class="club-caption">People who enjoy talking about compilers.</p></div></div>',
      notes:notes('Keep the biography to a minute',[
        'Select two relevant facts: original C2 and Sea of Nodes; subsequent JVM and runtime work at Azul.',
        'Coffee Compiler Club is the community connection. Point to the YouTube channel and invite people to email for a Discord invite.',
        'Transition: rather than describe the entire compiler, watch four graph transformations.'
      ],'<p class="muted">Short bio adapted from the supplied 2024 biography.</p>')
    },
    {
      id:'arithmetic',label:'1–4 · Parsing and peepholes',section:'Chapters 1–4',minutes:5,steps:2,demo:'arithmetic',
      body:()=>'<h2>Parse straight into<br>an optimized graph.</h2><div class="demo-layout"><div>'+source('arithmetic')+'</div><div class="demo-right"><p class="thesis">SSA construction and local rewriting happen together.</p>'+graph('arithmetic')+'<div class="fact" data-reveal="1"><b>Watch</b><span>Identity removal → reassociation → constant folding.</span></div>'+result('arithmetic','Expected optimized expression; inspect the actual graph in the viewer.')+'</div></div>',
      notes:notes('Live demo 1 · leave at 07:00',[
        '<b>0:00–0:45:</b> Show the Lattice tab briefly, then return to Example and predict the source result. No separate AST is needed for this construction. The chapter boundary is intentionally tiny.',
        '<b>0:45–1:15:</b> Copy code. In a terminal at the repository root run <code>make -C chapter04 view</code>. Paste into the viewer and Compile.',
        '<b>1:15–4:15:</b> Use Next/Right to observe a few enclosing peepholes: arg+0 disappears, constants reassociate, and repeated arg becomes multiplication. Point to types on nodes and uses pointing to definitions.',
        '<b>4:15–5:00:</b> Jump Last; the result is 2*arg+6. Return to slides. Before launching the next chapter, stop this viewer with Ctrl+C in its terminal.'
      ],'<p>Fallback: “Final graph” is a captured compiler graph. Advance builds to reveal the conclusion. No need to step through all 51 captured frames.</p>')
    },
    {
      id:'control',label:'5–8 · Control and loops',section:'Chapters 5–8',minutes:5,steps:2,demo:'control',
      body:state=>'<h2>Control flow is<br>part of the graph.</h2><div class="demo-layout"><div>'+source('control',true)+'</div><div class="demo-right"><p class="thesis">'+(state.step===0?'Regions merge control.<br>Phis select the corresponding values.':'Loop is a Region.<br>Phi_i merges entry and backedge.')+'</p>'+graph(state.step===0?'phi':'loop')+'<div class="fact" data-reveal="1"><b>Loops</b><span>A backedge supplies another Phi input. Construction can be incomplete.</span></div>'+result('control',null,'<math display="block" aria-label="sum equals the sum of i from 1 through the absolute value of arg"><mrow><mi>sum</mi><mo>=</mo><munderover><mo>∑</mo><mrow><mi>i</mi><mo>=</mo><mn>1</mn></mrow><mrow><mo>|</mo><mi>arg</mi><mo>|</mo></mrow></munderover><mi>i</mi></mrow></math>')+'</div></div>',
      notes:notes('Live demo 2 · leave at 12:00',[
        '<b>0:00–0:45:</b> Briefly show the Lattice tab: control now has live/dead types. Return to Example. A branch chooses abs(arg); the loop accumulates integers from 1 through abs(arg). Build 1 shows the absolute-value Phi; highlight the corresponding branch in the source. Build 2 switches to Loop, Phi_i, and Add(i,1). Build 3 keeps that graph and reveals the sigma sum. The counter sketch omits the Loop control predecessors.',
        '<b>0:45–1:15:</b> Stop the prior viewer. Run <code>make -C chapter08 view</code>; paste this example and Compile.',
        '<b>1:15–4:15:</b> Find the initial Region/Phi, then the Loop and its backedge. Show the loop-carried i and sum values. Explain lazy Phi creation and how the backedge supplies the next iteration. Fold/unfold the loop for context.',
        '<b>4:15–5:00:</b> Jump Last and trace the exit value. For arg=5 the result is 1+2+3+4+5=15. The viewer shows compilation, not the iterations of runtime execution.'
      ],'<p>Chapters 5/6 introduce branches and dead control; 7/8 introduce loops and lazy Phis. Avoid walking every frame.</p>')
    },
    {
      id:'memory',label:'9–17b · Values, memory, scheduling',section:'Chapters 9–17b',minutes:5,steps:2,demo:'aliases',
      body:state=>'<h2>Memory is dataflow, too.</h2><div class="demo-layout"><div>'+source(state.demo||'aliases',true)+'<div class="demo-tabs"><button data-demo="aliases">Short alias example</button><button data-demo="memory">Array + object loops</button></div></div><div class="demo-right"><p class="thesis">Partition memory into<br>equivalence classes.</p>'+memoryPartition(state)+states(state.demo==='memory'?[
        '<div class="formula">data[i]<small>Array element memory is another slice.</small></div>',
        '<div class="formula">t.sum / t.visits<small>Separate field classes carry separate dependencies.</small></div>',
        '<div class="formula">4*arg + 10<small>Runtime result; the graph still contains loops.</small></div>'
      ]:[
        '<div class="formula">p.x → Pair.x<small>All Pair.x fields share one equivalence class.</small></div>',
        '<div class="formula">p.y = 99 → Pair.y<small>Only this slice changes; Pair.x is untouched.</small></div>',
        '<div class="formula">p.x − before → 0<small>The short example isolates this fold.</small></div>'
      ])+'<p class="watch">'+(state.demo==='memory'?'<strong>Inspect:</strong> two loops, array Loads/Stores, and blue memory edges.<br><strong>Runtime result:</strong> 4*arg+10.':'<strong>Inspect:</strong> writing p.y leaves p.x unchanged.<br><strong>Result:</strong> 0.')+'</p><p class="research-note">Simple type-based aliasing approached the RLE limit.<br>'+external('https://people.cs.umass.edu/~moss/papers/pldi-1998-tbaa.pdf#page=8','Diwan et al., PLDI \u201998 · 8 Modula-3 programs')+'</p></div></div>',
      notes:notes('Live demo 3 · leave at 17:00',[
        '<b>0:00–0:45:</b> Briefly show Domains and Memory types, then return to Examples. Use the partition picture: Pair.x, Pair.y, and the rest are disjoint equivalence classes whose union is whole memory. The classes group locations across all instances, not one class per object.',
        '<b>0:45–1:15:</b> Run <code>make -C chapter17b view</code>. Paste Short alias example. Show that p.y changes while p.x does not, and the result becomes zero.',
        '<b>1:15–3:15:</b> Trace the Loads and Store through their field equivalence classes. Same-class accesses may alias; different classes cannot. MemMerge recombines the slices without emitting an instruction. Explain why writing p.y does not clobber p.x. Read-only access would not make a Load independent of memory.',
        '<b>3:15–4:30:</b> Switch back to this slide, select Array + object loops, Copy code, then compile it in the same viewer. Jump Last, fold/unfold the loops, and inspect loop-carried memory and the sum and visits aliases.',
        '<b>4:30–5:00:</b> GCM uses these dependencies to choose legal placements; it does not guess ordering from source order. Return to slides.'
      ],'<p>The graph viewer captures parsing/optimization/type checking, not GCM execution. The large graph has 54 nodes at its final checkpoint; do not step through all 319 frames.</p><p><b>Research context:</b> Diwan, McKinley, and Moss, <i>Type-Based Alias Analysis</i>, PLDI 1998, sections 3.5–3.6 (p. 113). A runtime upper bound left at most an average 2.5% more heap loads eliminable beyond their analysis on eight Modula-3 benchmarks. This is a load-elimination bound, not a speedup percentage. More static opportunities did not translate into corresponding runtime gains. This supports choosing useful, inexpensive alias distinctions; it does not establish that this exact equivalence-class scheme is optimal for Java. The study is a close match to the recalled oracle experiment, not an identified IBM/Java paper.</p>')
    },
    {
      id:'inline',label:'18 · Functions and Calls',section:'Chapter 18',minutes:5,steps:2,demo:'inline',
      body:state=>'<h2>Functions and Calls</h2><div class="demo-layout"><div>'+source('inline')+'</div><div class="demo-right"><p class="thesis">'+[
        'Declare and define functions.<br>Parse their bodies and calls.<br>Build the graph as we go.',
        'Trivial inlining:<br>erase the call boundary<br>with simple graph rewrites.',
        'Fun is a Region.<br>Parm is a Phi.<br>The familiar rules still apply.'
      ][state.step]+ '</p>'+states([
        '<div class="formula">twice(1 + add1(2)) + 3<small>Two functions. Two call sites.</small></div>',
        '<div class="formula">(1 + (2 + 1)) * 2 + 3<small>Splice control, arguments, memory, and results.</small></div>',
        '<div class="formula">return 11;<small>Ordinary constant folding finishes the job.</small></div>'
      ])+(state.step===0?'<div class="fact"><b>Functions</b><span>Fun, Parm, Return</span></div><div class="fact"><b>Calls</b><span>Call, CallEnd</span></div>':'<div class="fact"><b>Proof</b><span>One target, one use of its function pointer, no self-recursive inline.</span></div>')+result('inline')+'</div></div>',
      notes:notes('Live demo 4 · leave at 22:00',[
        '<b>0:00–0:45:</b> Introduce function definitions and calls: parsing builds Fun, Parm, Return, Call, and CallEnd nodes. The whole top-level body is an implicit main. Each explicit function has a Fun, Parms, and one merged Return. Briefly show the Lattice tab and its singleton function targets, then return to Example. The second build shows trivial inlining by simple graph rewrites.',
        '<b>0:45–1:15:</b> Stop the prior viewer. Run <code>make -C chapter18 view</code>; paste and Compile.',
        '<b>1:15–4:15:</b> Find a Call/CallEnd and the function entry. Look for the FOLDING label while a single-use function is being inlined. Follow one parameter becoming the caller argument and the Return value taking the place of the call result. Gather → rewrite → release makes the local edit visible.',
        '<b>4:15–5:00:</b> Jump Last. Both call boundaries are gone and the return value is 11. This example moves single-caller bodies; it does not demonstrate cloning for multi-callsite inlining.'
      ],'<p>If tracing takes too long, show the captured final graph and use the three schematic builds on the slide.</p>')
    },
    {
      id:'backend',label:'19–21 · From IR to instructions',section:'Chapters 19–21',minutes:2,steps:2,
      body:state=>'<h2>Give the graph a machine.</h2><pre class="backend-source">'+esc(R.backend.source.trim().split('\n').map(line=>line.trim()).reduce((text,line,i)=>text+(i===0?'':i===2?'\n    ':' ')+line,''))+'</pre><div class="pipeline backend-pipeline">'+
        backendCard(state,0,'Select instructions','Match ideal nodes to machine operations. Carry operand and calling-convention constraints.','Ideal graph<br>↓<br>Machine graph','selected','Selected graph')+
        backendCard(state,1,'Allocate registers','Build live ranges and interference. Coalesce, color, split, and spill.','Virtual values<br>↓<br>Registers + stack','assembly','Allocated assembly')+
        backendCard(state,2,'Encode and execute','Lay out code, emit instruction bytes, resolve relocations, and build object files.','Scheduled instructions<br>↓<br>Executable code','encoded','Instruction bytes')+
        '</div><div class="lower-line backend-provenance"><p class="muted">One program · x86-64 System V · Chapter 21 compiler</p><div class="chips"><span>x86-64</span><span>ARM64</span><span>RISC-V</span></div></div>',
      notes:notes('Two minutes for the backend',[
          '19: instruction selection happens before GCM. Target nodes still carry dependencies. Matching can absorb a Load into an arithmetic instruction.',
          'Click each revealed column to flip between discussion and its captured dump; click again to return. Flips persist across builds and synchronize with the presenter window. The returned function receives an existing writable Box, so there is no allocation or calloc lead-in. It adds abs(arg) into box.x and returns that same value.',
          'All three captures use the Chapter 21 compiler at the corresponding phase: Chapters 19 and 20 do not yet select unary Minus. The selected table includes every node and input slot, with concrete Java class names instead of shortened op labels; types and redundant use lists are omitted. Follow NegX86 #20, Phi #19, and MemAddX86 #16. Load + Add + Store become one read-modify-write instruction.',
          '20: global graph coloring, with register masks and calling conventions. Legality first; spill quality is a separate question. ARM joins the existing x86/RISC-V ports here.',
          'The middle capture is CodeGen.asm() immediately after register allocation: arg is rsi, box is rdi, and the result is rax. Labels show ordered blocks; the Phi coalesces into rax. Offsets and the ?? jump are provisional here, and the return still displays callee-save bookkeeping.',
          '21: actual instruction encodings and object output. Distinguish native execution from ARM/RISC-V test evaluators; the encoders are shared, but selection and allocation remain in chapter snapshots.',
          'The final CodeGen.asm() capture follows encode(): 48f7d8 is neg rax; 480107 is add qword [rdi], rax. Layout reverses the branch to j>= and removes the extra jump. Hex is shown in memory byte order. The printer labels the compare-with-zero as test; 4883f800 actually encodes cmp rax, 0. Only whitespace and long divider rules are compacted in these assembly displays; raw dumps and a regeneration tool are included in the deck.'
      ],'<p>Target time: 24:00. This is a progression, not a claim that the three phases exhaust the backend pipeline.</p>')
    },
    {
      id:'language',label:'22–23 · I/O, methods, types',section:'Chapters 22–23',minutes:2,steps:1,
      body:state=>R.languageBody(state),
      notes:notes('Language features stress the representation',[
          '22: printing is a real chain of function and field accesses, ending at a C ABI boundary. Show how much existing infrastructure participates in one line of code.',
          'Click the Chapter 22 column to browse the bundled Chapter 25 standard library. Start in sys; Up reaches the root and sys.smp. Open adt for bitset.smp, or io.smp, aryu8.smp, and libc.smp to connect the library to language features. Breadcrumbs and Up navigate folders or leave a file; Discussion restores the original column. Full width gives long files more room. File contents scroll and can be copied. This is a snapshot of the latest chapter, including features beyond Chapter 22.',
          '23: methods add a self parameter. A struct contains a function whose signature mentions the struct; types themselves become cyclic.',
          'Click the Chapter 23 column for a complete Counter class and calls, validated with the Chapter 23 compiler and evaluator. n is an instance field; new initializes it to arg. add mutates n and returns self, so calls can chain; get reads n. The result is arg + 7. Discussion restores the conceptual explanation.',
        'The interesting point for this audience is preserving canonical type identity and lattice operations while the representation becomes recursive.'
      ],'<p>Target time: 26:00. No extra live demo is budgeted.</p>')
    },
    {
      id:'sccp',label:'24 · Sparse Conditional Constant Propagation',section:'Chapter 24',minutes:2,steps:2,demo:'sccp',
      body:()=>'<h2 class="sccp-title">Sparse Conditional Constant Propagation</h2><div class="demo-layout"><div>'+source('sccp')+'<p class="watch">This one stays on the slide. The loop’s return value is always 1.</p></div><div><p class="thesis">The lattice and transfer functions are already there.</p>'+states([
        '<div class="formula">Phi(1, ⊥) = ⊥<small>Pessimistic propagation gets stuck.</small></div>',
        '<div class="formula">Phi(1, ⊤) = 1<small>Start at the top; propagate reachable information.</small></div>',
        '<div class="formula">2 − 1 = 1<small>The backedge confirms the invariant.</small></div>'
      ])+'<table class="sccp-table"><thead><tr><th>Value</th><th>Initial</th><th>Fixed point</th></tr></thead><tbody><tr><td>Loop Phi</td><td>⊤</td><td data-reveal="1">1</td></tr><tr><td>2 − Phi</td><td>⊤</td><td data-reveal="2">1</td></tr></tbody></table><p class="sccp-warning" data-reveal="2">After SCCP, the program is fully typed.<br><strong>SCCP is the type analysis pass.</strong></p></div></div>',
      notes:notes('Optimism earns its keep',[
          'Use the source as a concrete loop, but the equations are SSA pseudocode. The pessimistic fixed point cannot discover x=1 through the cycle.',
          'For this talk, Copy viewer command launches make -C chapter25 view. Chapter 25 demonstrates the same SCCP example using its separately compiled standard library, avoiding Chapter 24’s thousands of library capture frames. The slide topic and lattice remain Chapter 24.',
        'Use Rich lattice for the big picture, then return to Example. The entry value sharpens the Phi to 1; the backedge evaluates to 1 and confirms it. Explain the direction of monotonicity, not just “assume it works.”',
        'Control propagation makes the solve conditional. Chapter 24 also discovers interprocedural call-graph edges; Chapter 18’s local linking was only the beginning.'
      ],'<p>Target time: 28:00. Continue to the closing slide, or use Overview → Chapter 25 if two minutes remain and replace the closing discussion.</p>')
    },
    {
      id:'modules',label:'25 · Optional: separate compilation',section:'Chapter 25 · optional',minutes:0,steps:1,optional:true,
      body:()=>'<h2>What survives<br>separate compilation?</h2><div class="module-diagram"><div>Library source<br><strong>Types + ideal IR</strong></div><b>→</b><div>Object file<br><strong>Native code + .simple</strong></div><b>→</b><div>Client compilation<br><strong>Import + optimize</strong></div></div><p class="statement">Imported functions can participate in optimization.</p><div class="two-up" data-reveal="1"><div class="panel" style="min-height:180px"><h3>Identity crosses a boundary.</h3><p style="margin-top:15px">Types, aliases, and function indices must be reconciled between units.</p></div><div class="panel" style="min-height:180px"><h3>Information arrives late.</h3><p style="margin-top:15px">SSA construction, forward types, and constructor memory need stronger contracts.</p></div></div>',
      notes:notes('Optional · spend at most two minutes',[
        'This is outside the default 30-minute path. Use it instead of the final two-minute discussion, not in addition.',
        'The object contains both native code and a .simple section with types and ideal IR. Imported IR can be optimized and inlined in the client.',
        'The compiler must reconcile global identities and incomplete information. This is why Chapter 25 is more than an object writer. Do not attempt its whole architecture here.'
      ])
    },
    {
      id:'close',label:'Takeaways and discussion',section:'A Simple Tutorial',minutes:2,steps:0,
      body:()=>'<h2>One graph.<br>Many compiler lessons.</h2><div class="takeaway-grid"><div><h3>Build</h3><p>Construct SSA directly.<br>Make dependencies explicit.</p></div><div><h3>Rewrite</h3><p>Use types and local rules.<br>Iterate to a fixed point.</p></div><div><h3>Lower</h3><p>Keep the graph useful<br>all the way to code.</p></div></div><p class="repo-link">'+external(R.repo,'github.com/SeaOfNodes/Simple')+'</p><p class="statement">Read a chapter. Run it. Watch what changes.</p>'+links()+'<button class="optional-link" data-goto="modules">If there’s time: Chapter 25 →</button>',
      notes:notes('Leave room for the room',[
        'Each chapter is a runnable compiler snapshot, not a diff that needs the final compiler to make sense.',
        'Invite questions about where the unified graph helps and where it imposes costs. There is no need to sell a universal IR winner.',
        'Point people to the repository, chapter READMEs, and Coffee Compiler Club. Stop at 30:00.'
      ])
    }
  ];
  R.escape=esc;
})();
