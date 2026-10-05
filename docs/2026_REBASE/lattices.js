/* Presentation-sized views of the chapter lattices. Lines run down the order.
 * Focused views deliberately omit other domains; see README.md for sources.
 */
(function () {
  const R=window.REBASE, esc=R.escape;
  const text=(x,y,s,cls='')=>'<text class="'+cls+'" x="'+x+'" y="'+y+'" text-anchor="middle">'+esc(s)+'</text>';
  const edge=(x,y,a,b)=>'<path class="lat-edge" d="M'+x+' '+y+' L'+a+' '+b+'"/>';
  function node(x,y,label,kind='integer',width=150) {
    return '<rect class="lat-node '+kind+'" x="'+(x-width/2)+'" y="'+(y-23)+'" width="'+width+'" height="46" rx="12"/>'+text(x,y+8,label);
  }
  function svg(label,body) {
    return '<svg class="lattice-svg" viewBox="0 0 900 500" role="img" aria-label="'+esc(label)+'"><title>'+esc(label)+'</title>'+body+'</svg>';
  }
  function early(control) {
    let s=edge(450,58,130,control?127:227)+edge(130,control?373:273,450,442)+
      edge(450,58,450,127)+edge(450,373,450,442)+edge(450,58,760,127)+edge(760,373,450,442);
    for(const x of [285,385,485,585]) s+=edge(450,173,x,227)+edge(x,273,450,327);
    s+=edge(760,173,760,227)+edge(760,273,760,327);
    if(control) s+=edge(130,173,130,327);
    s+=node(450,35,'⊤','global',65)+node(450,465,'⊥','global',65)+
      node(450,150,'⊤:int')+node(285,250,'−1','integer',68)+node(385,250,'0','integer',68)+node(485,250,'1','integer',68)+node(585,250,'2','integer',68)+text(639,258,'…','hint')+node(450,350,'⊥:int')+
      node(760,150,'⊤:tuple','tuple')+node(760,250,'[T₀, …, Tₙ]','tuple',200)+node(760,350,'⊥:tuple','tuple');
    s+=control?node(130,150,'⊤:ctrl','control')+node(130,350,'⊥:ctrl','control')+text(130,207,'unreachable','hint')+text(130,405,'reachable','hint'):node(130,250,'ctrl','control');
    return svg(control?'Chapter 8: control, flat integers, and tuples':'Chapter 4: control, flat integers, and tuples',s);
  }
  function domains() {
    const ds=[['Control','ctrl','control'],['Integers','int','integer'],['Pointers','ptr','pointer'],['Structs','struct','struct'],['Memory','mem','memory'],['Tuples','tuple','tuple']];
    let s='';
    ds.forEach(([name,suffix,kind],i)=>{
      const x=75+i*150;
      s+=edge(450,58,x,137)+(suffix==='mem'?'':edge(x,183,x,327))+edge(x,373,450,442)+
        node(x,160,'⊤:'+suffix,kind,134)+node(x,350,'⊥:'+suffix,kind,134)+'<rect x="'+(x-70)+'" y="92" width="140" height="29" fill="#f5f3ec"/>'+text(x,115,name,'domain-name');
      if(suffix==='mem') {
        s+=edge(x,183,x-38,237)+edge(x,183,x+38,237)+edge(x-38,263,x,327)+edge(x+38,263,x,327);
        s+=node(x-38,250,'#2',kind,58)+node(x+38,250,'#3',kind,58);
      } else if(suffix!=='ctrl') {
        s+='<rect x="'+(x-55)+'" y="221" width="110" height="58" rx="8" class="lat-elided"/>'+text(x,258,'…','ellipsis');
      }
    });
    s+=node(450,35,'⊤','global',65)+node(450,465,'⊥','global',65);
    return svg('Chapter 11 domain overview; interiors elided except two memory aliases',s);
  }
  function contents() {
    let s=edge(450,78,450,147)+edge(450,193,250,257)+edge(450,193,650,257)+edge(250,303,450,367)+edge(650,303,450,367)+edge(450,413,450,442);
    s+=node(450,55,'MEM#2 : ⊤:int','memory',290)+node(450,170,'MEM#2 : [1, 0]','memory',300)+
      node(250,280,'MEM#2 : 0','memory',220)+node(650,280,'MEM#2 : 1','memory',220)+
      node(450,390,'MEM#2 : [0, 1]','memory',300)+node(450,465,'MEM#2 : ⊥:int','memory',290);
    return svg('A slice of typed memory: fixed alias 2, integer contents, selected elements',s);
  }
  function ranges(optimistic=false) {
    let s=edge(450,58,450,127)+edge(450,173,250,227)+edge(450,173,650,227)+edge(250,273,450,327)+edge(650,273,450,327)+edge(450,373,450,442);
    if(optimistic) s+='<path class="lat-solve" d="M485 61 Q765 95 685 221"/>'+text(735,127,'start → 1','solve-label');
    s+=node(450,35,'⊤:int','integer',170)+node(450,150,'[1, 0]','integer',180)+
      node(250,250,'0','integer',100)+node(650,250,'1',optimistic?'chosen':'integer',100)+
      node(450,350,'[0, 1]','integer',180)+node(450,465,'⊥:int','integer',170);
    return svg('Selected integer-range lattice elements: dual ranges above constants, ordinary ranges below',s);
  }
  function functions() {
    let s=edge(450,83,240,207)+edge(450,83,660,207)+edge(240,253,450,367)+edge(660,253,450,367);
    s+=node(450,60,'∅','function',130)+node(240,230,'{add1}','chosen',190)+node(660,230,'{twice}','function',190)+node(450,390,'{add1, twice}','function',280);
    s+=text(450,465,'Target-set projection; meet = union','hint');
    return svg('Function target-set lattice restricted to two targets, with signature, return type, and nullability fixed',s);
  }
  const point=(title,body)=>'<div class="lattice-point"><h3>'+title+'</h3><p>'+body+'</p></div>';
  function page(title,subtitle,picture,points,caption) {
    return '<div class="lattice-page"><h2>'+title+'</h2><p class="lattice-subtitle">'+subtitle+'</p><div class="lattice-layout"><figure>'+picture+'<figcaption>'+caption+'</figcaption></figure><aside>'+points.join('')+'</aside></div><p class="lattice-legend">⊤ above ⊥ · lines show lattice order · meet moves downward to the greatest lower bound</p></div>';
  }
  const views={
    early:{id:'lattice',label:'Lattice',steps:0,
      body:()=>page('Types describe possible values.','Chapter 4 · one lattice, several domains',early(false),[
        point('Constants are types.','<code>arg : ⊥:int</code><br><code>3 : 3</code>'),
        point('Meet combines alternatives.','<code>1 ∧ 1 = 1</code><br><code>1 ∧ 2 = ⊥:int</code>'),
        point('Every node computes a type.','A constant result can replace the node. Tuples meet element by element.')
      ],'Standard flat integer lattice (monotone analysis frameworks). Tuples recursively contain collections of types.'),
      notes:'<h3>Lattice · about 30 seconds of this demo slot</h3><p>This is the textbook flat integer constant-propagation lattice taught in monotone analysis frameworks. The ellipsis stands for the other integer constants, each at the same level. These are abstract values, not the Java class hierarchy. Show global top/bottom versus domain top/bottom. Chapter 4 has one control element, flat integer constants, and tuples. AddNode.compute evaluates constant operands; meet is the operation for combining alternatives, not integer addition. Return to Example for the local rewrites.</p>'},
    control:{id:'lattice',label:'Lattice',steps:0,
      body:()=>page('Reachability is a type, too.','Chapter 8 · the Chapter 6 control lattice carries through loops',early(true),[
        point('Control gains a dual.','<code>⊤:ctrl = unreachable</code><br><code>⊥:ctrl = reachable</code>'),
        point('A Phi meets live inputs.','<code>Phi(1, 2) : ⊥:int</code><br>A dead predecessor contributes no value.'),
        point('Loops close the cycle.','The backedge is another input. Lazy Phis allow construction before that input exists.')
      ],'The integer lattice is still flat here; ranges arrive in Chapter 14.'),
      notes:'<h3>Lattice · about 30 seconds of this demo slot</h3><p>Only the control branch changed from the preceding diagram. Region computes reachability; Region removes dead predecessors and their Phi inputs; Phi meets the remaining value types. A loop introduces a cycle in the graph, not another type domain. Optimistic solving comes later, in Chapter 24.</p>'},
    domains:{id:'domains',label:'Domains',steps:0,
      body:()=>page('Memory joins the type lattice.','Chapter 11 · six domains under the same global bounds',domains(),[
        point('Memory has alias slices.','<code>MEM#2 ∧ MEM#3 = ⊥:mem</code><br>In this chapter the alias elements are flat.'),
        point('Pointers carry structure.','Nullability and struct field types participate in the lattice; tuples contain types recursively.'),
        point('A MemMerge is an IR node.','It carries several dependency slices. Its inputs are not a meet of distinct aliases.')
      ],'Domain interiors are elided; #2 and #3 stand for two of many aliases.'),
      notes:'<h3>Domain overview · about 20 seconds</h3><p>This is specifically Chapter 11: control, integers, pointers, structs, memory, and tuples. The boxes with ellipses abbreviate each domain, not extra lattice elements. Memory slices in 11 are flat aliases; typed contents arrive in Chapter 15. Distinguish the TypeMem meet from the MemMerge dependency node we just inspected.</p>'},
    contents:{id:'contents',label:'Memory types',steps:0,
      body:()=>page('Memory carries facts about contents.','Chapters 15–17b · one alias, with integer ranges from Chapter 14',contents(),[
        point('Fix the alias; vary its contents.','<code>MEM#2 : T</code><br>The contents have their own lattice.'),
        point('Meet the stored-value types.','<code>MEM#2:0 ∧ MEM#2:1</code><br><code>= MEM#2:[0,1]</code>'),
        point('Types and dependencies cooperate.','The type bounds values in a slice. Memory edges and pointer identity determine which Store a Load can see.')
      ],'Selected elements for alias #2 only; other aliases and memory bounds omitted.'),
      notes:'<h3>Typed contents · about 20 seconds</h3><p>Chapter 15 adds TypeMem contents, using the integer ranges introduced in Chapter 14. This is a fixed-alias, integer-contents slice of the larger lattice. The top and bottom pictured belong to those integer contents, not global memory top and bottom. The contents type summarizes allocated objects in that alias; it does not name the object selected by p.x. Return to Examples and connect this to the independent p.y Store.</p>'},
    ranges:{id:'ranges',label:'Ranges',steps:0,
      body:()=>page('Refine the integer domain.','Chapter 14 · constants become singleton ranges',ranges(),[
        point('Meet takes the bounding interval.','<code>0 ∧ 1 = [0,1]</code><br><code>[a,b] ∧ [c,d]</code><br><code>= [min(a,c), max(b,d)]</code>'),
        point('Dual swaps the endpoints.','<code>dual([0,1]) = [1,0]</code><br>High ranges live above constants.'),
        point('The interface stays the same.','Meet, dual, and transfer functions extend to richer types. Floats add another domain.')
      ],'A selected sublattice, not all intervals. Lines may span omitted elements.'),
      notes:'<h3>Ranges · optional 15–20 seconds</h3><p>Show the 0/1 diamond, with [0,1] below and its dual [1,0] above. The high side is an algebraic dual, not a second collection of runtime intervals. These selected elements omit other ranges and the float domain. Keep this quick: it supplies the picture needed again at SCCP.</p>'},
    functions:{id:'lattice',label:'Lattice',steps:0,
      body:()=>page('A function value has a target set.','Chapter 18 · hold signature, result type, and nullability fixed',functions(),[
        point('Merge alternatives by union.','<code>{add1} ∧ {twice}</code><br><code>= {add1, twice}</code>'),
        point('A singleton identifies the callee.','One known target makes direct linking possible. Trivial inlining also checks usage and recursion.'),
        point('The full type has more components.','Argument tuple, return type, nullability, and target bits all participate. Only the target-set projection is drawn.')
      ],'Restricted to two possible targets. The empty set is top in this projection.'),
      notes:'<h3>Function targets · about 30 seconds of this demo slot</h3><p>TypeFunPtr meets target bits with bitwise OR. For this drawing fix the other components, including an int return type, and restrict the universe to the two named functions. This is not the complete function-pointer lattice or a diagram of function subtyping. The concrete demo calls each singleton directly; it does not form the two-target union. Connect singleton information to the additional single-use and non-recursion checks for inlining.</p>'},
    rich:{id:'rich',label:'Rich lattice',steps:0,
      body:()=>'<div class="rich-lattice-page"><h2>Rich lattice</h2><p class="rich-domains">Control · integer ranges · floats · nullability · pointers · function targets · memory · structs · tuples · return addresses · constant arrays</p><a class="rich-lattice-link" href="assets/rich-lattice.svg" target="_blank" rel="noopener" aria-label="Open the rich lattice at full size"><img class="rich-lattice-image" src="assets/rich-lattice.svg" alt="Chapter 24 type ordering: 143 representative types and their duals across all type families, connected by 256 order edges, from global top to global bottom."></a><div class="rich-takeaways"><p>It’s just a lattice.<small>So the fixed-point theory works.</small></p><p>It’s a rich lattice.<small>So we get rich results.</small></p></div><p class="rich-caption">Chapter 24’s actual type ordering · representative elements across every family · click for full size</p></div>',
      notes:'<h3>Rich lattice</h3><p>Pull back from the little integer diamond to the full range of type families. This diagram is generated from Chapter 24 Type.gather(), including duals, and the actual isa/meet ordering: 143 representative types, 256 transitively reduced order edges. It samples each family; it does not enumerate every interval, function target set, or recursive aggregate. An edge can span elements outside the sample. Repeated compact labels can hide differing type components; opening the SVG gives full-type tooltips.</p><p>The two messages are: it is just a lattice, so the monotone fixed-point framework applies; it is a rich lattice, so the result contains far more than integer constants. Reachability, ranges, nullability, memory contents, and callable targets all participate. The usual monotonicity and convergence conditions still matter. The arrows here are lattice order, not IR dependencies.</p>'},
    sccp:{id:'lattice',label:'Lattice',steps:0,
      body:()=>page('Solve the cycle from the high side.','Chapter 24 · the integer lattice is already available',ranges(true),[
        point('Seed unknown results at top.','<code>Phi(1, ⊤:int) = 1</code>'),
        point('The backedge confirms it.','<code>2 − 1 = 1</code><br><code>Phi(1, 1) = 1</code>'),
        point('Propagate to a fixed point.','Types descend as reachable information arrives. Control types decide which predecessors contribute.')
      ],'Orange shows an analysis update; gray lines show the selected lattice order.'),
      notes:'<h3>SCCP lattice · about 30 seconds of this two-minute slot</h3><p>Reuse the range picture, now showing the descent from integer top to 1. The loop Phi and subtraction both settle at 1. Global top may be used to initialize an untyped result; the picture projects onto the integer domain. Contrast with seeding at integer bottom, which cannot discover this invariant. Lattice order and the direction of solving are distinct from the graph viewer’s use-to-definition arrows.</p>'}
  };
  const attach=(id,items)=>{R.slides.find(s=>s.id===id).views=items.map(key=>views[key]);};
  attach('arithmetic',['early']);
  attach('control',['control']);
  attach('memory',['domains','contents','ranges']);
  attach('inline',['functions']);
  attach('sccp',['rich']);
})();
