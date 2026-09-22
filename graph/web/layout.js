// Convert a snapshot to ELK geometry. The compiler's graph stays untouched.
class GraphLayout {
  constructor() {
    this.elk = new ELK({workerUrl: "vendor/elk-worker.min.js"});
    this.ctx = document.createElement("canvas").getContext("2d");
    this.ctx.font = "12px sans-serif";
  }

  clip(text) {
    const chars = Array.from((text || "").replace(/\s+/g, " "));
    return chars.length > 44 ? chars.slice(0, 43).join("") + "…" : chars.join("");
  }

  async run(snap, evt) {
    const nodes = snap.nodes.map(n => {
      const label = this.clip(n.label), type = this.clip(n.type);
      let w = Math.ceil(Math.max(120, this.ctx.measureText(label).width + 24,
        this.ctx.measureText(type).width + 24, (n.edges.length + 1) * 18));
      const scope = n.kind === "SCOPE";
      const cols = scope ? n.edges.map(e => Math.ceil(Math.max(48,
        this.ctx.measureText(e.label || "[" + e.idx + "]").width + 24))) : [];
      if (scope) w = Math.ceil(Math.max(120, this.ctx.measureText(label).width + 24,
        cols.reduce((sum, w) => sum + w, 0)));
      return {id: "n" + n.id, width: w, height: 62,
        ports: [], n, label, type, scope, cols, ox: 0, oy: 0};
    });
    const byId = new Map(nodes.map(n => [n.n.id, n]));
    const parsing = evt?.phase === "Parse" && evt.kind !== "PHASE";
    const roots = new Set(snap.roots);
    const scopes = nodes.filter(n => n.scope);
    const active = parsing ? byId.get(snap.scope) : null;
    // Scope bindings count as users too. Scope ownership comes from parser
    // roots; unfinished expression values are inferred from absent uses.
    const used = new Set();
    for (const n of snap.nodes) for (const e of n.edges) if (e.def) used.add(e.def);
    const held = parsing ? nodes.filter(n =>
      (n.n.proj || ["DATA", "MEM", "PHI"].includes(n.n.kind)) && !used.has(n.n.id)) : [];
    for (const n of held) n.held = true;
    const owned = [...held, ...scopes.filter(n => parsing && roots.has(n.n.id))];
    const projs = new Map();
    for (const n of nodes) {
      const par = byId.get(n.n.proj?.par);
      // A projection without its parent still gets a standalone box.
      if (!par || par.n.proj) continue;
      if (!projs.has(par)) projs.set(par, []);
      projs.get(par).push(n);
      n.par = par;
    }
    const groups = nodes.filter(n => !n.par).map(n => {
      const slots = (projs.get(n) || []).sort((a, b) => a.n.proj.idx - b.n.proj.idx || a.n.id - b.n.id);
      const parts = [n, ...slots];
      if (slots.length) {
        const w = slots.reduce((sum, p) => sum + p.width, 0);
        n.width = Math.max(n.width, w);
        const extra = (n.width - w) / slots.length;
        let x = 0;
        for (const p of slots) {
          p.width += extra;
          p.ox = x;
          p.oy = n.height;
          x += p.width;
        }
      }
      const ports = [];
      for (const p of parts) {
        p.cell = slots.length > 0;
        const extra = p.scope && p.cols.length ?
          (p.width - p.cols.reduce((sum, w) => sum + w, 0)) / p.cols.length : 0;
        let x = 0;
        for (const [idx, e] of p.n.edges.entries()) {
          // The parent/projection link is represented by the shared box.
          if (p.par && e.idx === 0 && e.def === p.par.n.id) continue;
          if (p.scope) {
            const span = p.cols[idx] + extra;
            ports.push({id: p.id + "i" + e.idx, x: x + span / 2 - 3, y: -3, span,
              width: 6, height: 6, layoutOptions: {"elk.port.side": "NORTH"}});
            x += span;
            continue;
          }
          ports.push({id: p.id + "i" + e.idx, x: p.ox + p.width * (e.idx + 1) / (p.n.edges.length + 1) - 3,
            y: p.oy - 3, width: 6, height: 6, layoutOptions: {"elk.port.side": "NORTH"}});
        }
        // Direct uses of the MultiNode attach to its header, projections below.
        const side = p === n && slots.length ? "EAST" : "SOUTH";
        ports.push({id: p.id + "o", x: p.ox + (side === "EAST" ? p.width : p.width / 2) - 3,
          y: p.oy + (side === "EAST" ? p.height / 2 : p.height) - 3,
          width: 6, height: 6, layoutOptions: {"elk.port.side": side}});
      }
      return {id: n.id, width: n.width, height: n.height * (slots.length ? 2 : 1), ports, parts};
    });
    const edges = [];
    for (const use of snap.nodes) {
      for (const e of use.edges) {
        if (!e.def) continue;
        if (byId.get(use.id).par && e.idx === 0 && e.def === use.proj.par) continue;
        const id = "e" + use.id + "i" + e.idx;
        const reg = byId.get(use.edges[0]?.def)?.n;
        const back = e.idx === 2 && (use.kind === "LOOP" ||
          (use.kind === "PHI" && reg?.kind === "LOOP"));
        edges.push({id, use: use.id, def: e.def, idx: e.idx, role: e.role, label: e.label,
          bind: use.kind === "SCOPE",
          // Layout follows value/control flow downward. SVG arrows point back
          // from use to def, matching Simple's actual edge direction.
          sources: ["n" + e.def + "o"], targets: ["n" + use.id + "i" + e.idx],
          layoutOptions: {"elk.layered.priority.direction": back ? 0 : e.role === "CTRL" ? 10 : 1}});
      }
    }
    const graph = await this.elk.layout({
      id: "root", children: groups.filter(g => !g.parts[0].scope).map(({parts, ...box}) => ({...box,
        layoutOptions: {"elk.portConstraints": "FIXED_POS",
          // Keep a projection with its parent even when that parent has real uses.
          "elk.layered.layering.layerConstraint": parts.length === 1 && parts[0].held ? "LAST_SEPARATE" : "NONE"}
      })),
      // Scope bindings and lifetime associations must not impose CFG ranks.
      edges: edges.filter(e => e.role !== "ASSOC" && !e.bind && !byId.get(e.def).scope).map(e => ({
        id: e.id, sources: e.sources, targets: e.targets, layoutOptions: e.layoutOptions
      })),
      layoutOptions: {
        "elk.algorithm": "layered", "elk.direction": "DOWN", "elk.edgeRouting": "ORTHOGONAL",
        "elk.spacing.nodeNode": 32, "elk.layered.spacing.nodeNodeBetweenLayers": 48,
        "elk.padding": "[top=24,left=24,bottom=24,right=24]",
        "elk.separateConnectedComponents": held.length === 0,
        "elk.layered.considerModelOrder.strategy": "NODES_AND_EDGES",
        "elk.randomSeed": 1
      }
    });
    // Scope ports use the same local geometry; place their boxes after CFG nodes.
    for (const g of groups.filter(g => g.parts[0].scope)) graph.children.push({...g, x: 0, y: 0});
    const boxes = new Map(graph.children.map(n => [n.id, n]));
    const routes = new Map((graph.edges || []).map(e => [e.id, e]));
    for (const group of groups) {
      const box = boxes.get(group.id);
      const ports = new Map(box.ports.map(p => [p.id, p]));
      for (const n of group.parts) {
        n.x = box.x + n.ox; n.y = box.y + n.oy;
        // Keep semantic nodes/slots intact for selection and neighborhood marks.
        n.ports = n.n.edges.map(e => {
          const p = ports.get(n.id + "i" + e.idx);
          return p && {...p, x: p.x - n.ox, y: p.y - n.oy, edge: e};
        }).filter(Boolean);
        const out = ports.get(n.id + "o");
        n.ports.push({...out, x: out.x - n.ox, y: out.y - n.oy});
      }
    }
    // Saved scopes describe suspended parser states at their control points.
    // Keep a common column, nudging down only when two name tables overlap.
    const ctrlY = n => {
      const ctrl = byId.get(n.n.edges.find(e => e.label === "$ctrl" || e.idx === 0)?.def);
      return ctrl ? ctrl.y + ctrl.height + 24 : 24;
    };
    const saved = scopes.filter(n => n !== active).sort((a, b) => ctrlY(a) - ctrlY(b) || a.n.id - b.n.id);
    if (saved.length) {
      const x = graph.width + 48;
      let y = 24;
      for (const n of saved) {
        n.x = x;
        n.y = Math.max(y, ctrlY(n));
        y = n.y + n.height + 24;
      }
      graph.width = x + Math.max(...saved.map(n => n.width)) + 24;
      graph.height = Math.max(graph.height, y);
    }
    // The active scope moves with the parser, below the current graph.
    if (active) {
      graph.width = Math.max(graph.width, active.width + 48);
      active.x = (graph.width - active.width) / 2;
      active.y = graph.height + 24;
      graph.height = active.y + active.height + 24;
    }
    // Parser ownership is an overlay, never an IR node or an ELK flow edge.
    const parser = owned.length ? {width: Math.max(180, (owned.length + 1) * 18), height: 36} : null;
    if (parser) {
      graph.width = Math.max(graph.width, parser.width + 48);
      parser.x = (graph.width - parser.width) / 2;
      parser.y = graph.height + 24;
      graph.height = parser.y + parser.height + 24;
      owned.sort((a, b) => a.x - b.x || a.n.id - b.n.id);
      parser.ports = owned.map((n, i) => ({id: "parser-i" + n.n.id,
        x: parser.width * (i + 1) / (owned.length + 1) - 3, y: -3}));
      for (const n of owned) edges.push({id: "parser-hold-" + n.n.id, scope: n.scope, active: n === active,
        use: 0, def: n.n.id, role: "PARSER", sources: [n.id + "o"], targets: ["parser-i" + n.n.id]});
    }
    for (const e of edges) {
      if (e.role === "ASSOC" || e.role === "PARSER" || e.bind || byId.get(e.def).scope) {
        const def = byId.get(e.def), use = e.role === "PARSER" ? parser : byId.get(e.use);
        const a = def.ports.find(p => p.id === e.sources[0]);
        const b = use.ports.find(p => p.id === e.targets[0]);
        const x = def.x + a.x + 3, y = def.y + a.y + 3;
        const u = use.x + b.x + 3, v = use.y + b.y + 3;
        e.path = `M${x},${y} C${x},${y + 24} ${u},${v - 24} ${u},${v}`;
      } else {
        e.path = routes.get(e.id).sections.map(s =>
          [s.startPoint, ...(s.bendPoints || []), s.endPoint]
            .map((p, i) => `${i ? "L" : "M"}${p.x},${p.y}`).join(" ")).join(" ");
      }
    }
    return {width: graph.width, height: graph.height, nodes, edges,
      parser, scope: active?.n.id || 0, scopes: new Set(owned.filter(n => n.scope).map(n => n.n.id)),
      held: new Set(held.map(n => n.n.id))};
  }
}
