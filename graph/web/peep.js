// Temporary geometry for one edit. Stable snapshots and their layouts are untouched.
class GraphPeep {
  // A recursive peephole can return a replacement before its caller attaches
  // it. Accept the next snapshot only when every intervening graph change is
  // that substitution or deletion of its now-unused inputs.
  static attached(a, b, evt) {
    const pre = new Map(a.nodes.map(n => [n.id,n]));
    const post = new Map(b.nodes.map(n => [n.id,n]));
    if (!pre.has(evt.node) || !pre.has(evt.repl) || !post.has(evt.repl)) return false;
    if (b.nodes.some(n => !pre.has(n.id))) return false;
    const gone = new Set();
    let rewires = 0;
    for (const n of a.nodes) {
      const m = post.get(n.id);
      if (!m) { gone.add(n.id); continue; }
      if (n.kind !== m.kind || n.label !== m.label || n.type !== m.type || !!n.folding !== !!m.folding ||
          JSON.stringify(n.proj) !== JSON.stringify(m.proj) || n.edges.length !== m.edges.length) return false;
      for (let i=0; i<n.edges.length; i++) {
        const e = n.edges[i], f = m.edges[i];
        if (JSON.stringify(e) === JSON.stringify(f)) continue;
        if (e.def !== evt.node || f.def !== evt.repl ||
            JSON.stringify({...e, def: f.def}) !== JSON.stringify(f)) return false;
        rewires++;
      }
    }
    const todo = [evt.node];
    while (todo.length) {
      const id = todo.pop();
      if (!gone.delete(id)) continue;
      for (const e of pre.get(id).edges) if (gone.has(e.def)) todo.push(e.def);
    }
    return rewires > 0 && !gone.size;
  }

  static diff(a, b, evt) {
    const pre = new Map(a.nodes.map(n => [n.id, n]));
    const post = new Map(b.nodes.map(n => [n.id, n]));
    const changed = new Set();
    let structural = false;
    for (const id of new Set([...pre.keys(), ...post.keys()])) {
      const n = pre.get(id), m = post.get(id);
      const shape = n => n && JSON.stringify([n.kind, n.proj, !!n.folding, n.edges.map(e => [e.idx,e.def])]);
      const edit = shape(n) !== shape(m);
      if (!edit && n.type === m.type && n.label === m.label && JSON.stringify(n.edges) === JSON.stringify(m.edges)) continue;
      changed.add(id);
      if (edit) structural = true;
    }
    const core = new Set([...changed, evt.node]);
    // GVN can return an unchanged node that was already in the graph. Show it
    // alongside the original, even when it isn't a def of any changed node.
    if (evt.repl) core.add(evt.repl);
    const near = new Set(core);
    // One hop from changed nodes and both peep roots, in both snapshots. Do
    // not expand from the added defs or scheduling dependencies.
    for (const ns of [pre,post]) for (const id of core) {
      const n = ns.get(id);
      if (!n) continue;
      const constant = n.kind !== "SCOPE" && !n.proj && n.edges.every(e => e.role === "ASSOC");
      for (const e of n.edges) {
        const def = ns.get(e.def);
        if (!def) continue;
        // Early chapters still describe Start as CTRL rather than START.
        if (constant && (def.kind === "START" || def.label === "Start")) continue;
        near.add(e.def);
      }
    }
    near.delete(0);
    return {structural, changed, near};
  }

  static async make(pre, post, diff, layout, folded, sceneFor, evt = post.evt) {
    const folds = new Set(folded);
    // Reveal the edit even when its function/loop is folded. Release restores
    // the user's folds; gathering never changes the persistent fold set.
    for (const frame of [pre, post]) {
      const view = GraphGroups.view(frame.snap, new Set());
      for (const id of folds)
        if ([...(view.groups.get(id)?.members || [])].some(n => diff.near.has(n))) folds.delete(id);
    }
    const a = await sceneFor(pre, folds), b = await sceneFor(post, folds);
    const ids = new Set(diff.near);
    const nodes = new Map();
    for (const scene of [a, b]) for (const n of scene.nodes) nodes.set(n.n.id, n);
    // Multi/projection cells travel as one box. A gathered Phi brings its
    // Region for the slot-0 marker, but a Region does not bring sibling Phis.
    let more = true;
    while (more) {
      more = false;
      for (const scene of [a, b]) for (const n of scene.nodes) {
        const par = n.par || n.reg;
        if (!par || !(ids.has(n.n.id) || n.par && ids.has(par.n.id))) continue;
        for (const id of [n.n.id, par.n.id]) if (!ids.has(id)) { ids.add(id); more = true; }
      }
    }
    for (const id of ids) if (!nodes.has(id)) ids.delete(id);
    const rim = new Set();
    for (const scene of [a,b]) {
      // Use original slots too: some associations have no routed display edge.
      for (const n of scene.nodes) for (const e of n.n.edges) if (e.def) {
        if (ids.has(n.n.id) && !ids.has(e.def)) rim.add(e.def);
        if (ids.has(e.def) && !ids.has(n.n.id)) rim.add(n.n.id);
      }
      if (scene.edges.some(e => e.role === "PARSER" && ids.has(e.def))) rim.add(0);
    }
    const parent = new Map([...ids].map(id => [id, id]));
    const root = id => {
      while (parent.get(id) !== id) id = parent.get(id);
      return id;
    };
    for (const scene of [a, b]) for (const n of scene.nodes) {
      const par = n.par || n.reg;
      if (ids.has(n.n.id) && par && ids.has(par.n.id)) parent.set(root(n.n.id), root(par.n.id));
    }
    const parts = new Map(), sizes = new Map();
    for (const scene of [a, b]) for (const n of scene.nodes) if (ids.has(n.n.id)) {
      const old = sizes.get(n.n.id);
      sizes.set(n.n.id, {width: Math.max(n.width, old?.width || 0), height: Math.max(n.height, old?.height || 0)});
    }
    for (const id of ids) {
      const par = root(id);
      if (!parts.has(par)) parts.set(par, []);
      parts.get(par).push(nodes.get(id));
    }
    const pos = new Map(), children = [];
    for (const [id, ns] of parts) {
      const n = nodes.get(id), size = sizes.get(id);
      const projs = ns.filter(p => p.n.id !== id && p.par).sort((a,b) => a.n.proj.idx-b.n.proj.idx);
      const row = projs.reduce((sum,p) => sum + sizes.get(p.n.id).width, 0);
      const width = Math.max(size.width, row);
      pos.set(id, {x: 0, y: 0, width, height: size.height});
      let x = 0, height = size.height;
      for (const p of projs) {
        const s = sizes.get(p.n.id), w = s.width + (width-row)/projs.length;
        pos.set(p.n.id, {x, y: size.height, width: w, height: s.height});
        x += w; height = Math.max(height, size.height+s.height);
      }
      x = width;
      for (const p of ns.filter(p => p !== n && !projs.includes(p))) {
        const s = sizes.get(p.n.id);
        pos.set(p.n.id, {x: x+32, y: 0, ...s});
        x += 32+s.width; height = Math.max(height, s.height);
      }
      children.push({id: String(id), width: x, height});
    }
    const edges = new Map();
    for (const scene of [a, b]) for (const e of scene.edges) {
      if (!ids.has(e.def) || !ids.has(e.use) || ["ASSOC", "PARSER"].includes(e.role)) continue;
      const def = root(e.def), use = root(e.use), id = `${def}:${use}`;
      if (def !== use) edges.set(id, {id, sources: [String(def)], targets: [String(use)]});
    }
    const graph = await layout.elk.layout({id: "peep", children, edges: [...edges.values()], layoutOptions: {
      "elk.algorithm": "layered", "elk.direction": "DOWN", "elk.spacing.nodeNode": 32,
      "elk.layered.spacing.nodeNodeBetweenLayers": 48, "elk.padding": "[top=32,left=32,bottom=32,right=32]",
      "elk.randomSeed": 1
    }});
    // Gather near the peep's original location, then pan/zoom to the enclosure.
    const anchor = a.nodes.find(n => n.n.id === evt.node) || a.nodes.find(n => ids.has(n.n.id));
    const x = (anchor?.x || 0) - graph.width/2, y = (anchor?.y || 0) - graph.height/2;
    for (const box of graph.children) for (const n of parts.get(Number(box.id))) {
      const p = pos.get(n.n.id);
      p.x += box.x+x; p.y += box.y+y;
    }
    const area = {x, y, width: graph.width, height: graph.height};
    const oldNodes = new Map(a.nodes.map(n => [n.n.id,n]));
    const focus = scene => {
      const ns = scene.nodes.map(n => {
        const p = {...(pos.get(n.n.id) || oldNodes.get(n.n.id) || n)};
        // Clear the enclosure of unrelated boxes. Their exact placement is
        // deliberately unimportant here, but they must not obscure the edit.
        if (!ids.has(n.n.id) && p.x+p.width > x-24 && p.x < x+area.width+24 &&
            p.y+p.height > y-24 && p.y < y+area.height+24) {
          const dx = (p.x+p.width/2-x-area.width/2)/area.width;
          const dy = (p.y+p.height/2-y-area.height/2)/area.height;
          if (Math.abs(dx) > Math.abs(dy)) p.x = dx < 0 ? x-p.width-48 : x+area.width+48;
          else p.y = dy < 0 ? y-p.height-48 : y+area.height+48;
        }
        return {...n, x: p.x, y: p.y, width: p.width, height: p.height,
          ports: n.ports.map(q => ({...q, x: (q.x+3)*p.width/n.width-3, y: (q.y+3)*p.height/n.height-3}))};
      });
      const byId = new Map(ns.map(n => [n.n.id,n]));
      const parser = scene.parser && {...scene.parser,
        x: a.parser?.x ?? scene.parser.x, y: a.parser?.y ?? scene.parser.y};
      const es = scene.edges.map(e => {
        const def = byId.get(e.def), use = e.role === "PARSER" ? parser : byId.get(e.use);
        const p = def?.ports.find(p => p.id === e.sources[0]), q = use?.ports.find(p => p.id === e.targets[0]);
        if (!p || !q) return e;
        const x = def.x+p.x+3, y = def.y+p.y+3, u = use.x+q.x+3, v = use.y+q.y+3;
        const bend = Math.max(24, Math.abs(v-y)/2);
        return {...e, path: `M${x},${y}C${x},${y+bend} ${u},${v-bend} ${u},${v}`};
      });
      // Container furniture fades away while the local enclosure explains scope.
      return {...scene, nodes: ns, edges: es, parser, groups: a.groups, jumps: [],
        peep: {area, ids, rim, changed: diff.changed}};
    };
    return {scenes: [focus(a), focus(b)], area};
  }
}
