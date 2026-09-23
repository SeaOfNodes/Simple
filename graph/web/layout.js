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

  async run(snap, evt, folded = new Set()) {
    const view = GraphGroups.view(snap, folded);
    snap = view.snap;
    const raw = new Map(snap.nodes.map(n => [n.id, n]));
    const outs = new Map();
    for (const n of snap.nodes) for (const e of n.edges) if (raw.get(e.def)?.fold) {
      if (!outs.has(e.def)) outs.set(e.def, new Set());
      outs.get(e.def).add(e.orig.def);
    }
    // The whole-program SCCP cycle is not source control flow.
    const hidden = (n, e) => ["START", "UNIT"].includes(n.kind) && raw.get(e.def)?.kind === "STOP";
    const nodes = snap.nodes.map(n => {
      const label = this.clip(n.label), type = this.clip(n.type);
      const compact = ["START", "UNIT", "STOP"].includes(n.kind);
      let w = Math.ceil(Math.max(120, this.ctx.measureText(label).width + 24,
        this.ctx.measureText(type).width + 24, (n.edges.length + 1) * 18));
      const scope = n.kind === "SCOPE";
      const cols = scope ? n.edges.map(e => Math.ceil(Math.max(48,
        this.ctx.measureText(e.label || "[" + e.idx + "]").width + 24))) : [];
      if (scope) w = Math.ceil(Math.max(120, this.ctx.measureText(label).width + 24,
        cols.reduce((sum, w) => sum + w, 0)));
      if (compact) w = Math.ceil(Math.max(72, this.ctx.measureText(`#${n.id} ${label}`).width + 24,
        (n.edges.filter(e => !hidden(n, e)).length + 1) * 18));
      if (n.fold) {
        const slot = Math.max(18, ...n.edges.map(e => this.ctx.measureText(`${e.orig.use}:${e.orig.idx}`).width + 12));
        w = Math.ceil(Math.max(200, w + 32, (n.edges.length + 1) * slot, ((outs.get(n.id)?.size || 0) + 1) * 18));
      }
      return {id: "n" + n.id, width: w, height: compact ? 26 : 62,
        ports: [], n, label, type, scope, cols, compact, stop: n.kind === "STOP", ox: 0, oy: 0};
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
      if (!par || par.n.proj || par.n.fold || n.n.fold) continue;
      if (!projs.has(par)) projs.set(par, []);
      projs.get(par).push(n);
      n.par = par;
    }
    const phis = new Map();
    for (const n of nodes) {
      if (n.n.kind !== "PHI" || n.n.fold) continue;
      const reg = byId.get(n.n.edges.find(e => e.idx === 0)?.def);
      if (!reg || reg.n.fold || !["REGION", "LOOP", "FUN"].includes(reg.n.kind)) continue;
      if (!phis.has(reg)) phis.set(reg, []);
      phis.get(reg).push(n);
      n.reg = reg;
    }
    const groups = nodes.filter(n => !n.par && !n.reg).map(n => {
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
      // ELK sees one row; the renderer keeps each Region/Phi as a separate box.
      let width = n.width;
      for (const p of phis.get(n) || []) {
        p.ox = width + 32;
        width = p.ox + p.width;
        parts.push(p);
      }
      const ports = [];
      for (const p of parts) {
        p.cell = slots.length > 0 && !p.reg;
        const extra = p.scope && p.cols.length ?
          (p.width - p.cols.reduce((sum, w) => sum + w, 0)) / p.cols.length : 0;
        let x = 0;
        for (const [idx, e] of p.n.edges.entries()) {
          if (hidden(p.n, e)) continue;
          // The parent/projection link is represented by the shared box.
          if (p.par && e.idx === 0 && e.def === p.par.n.id) continue;
          // Phi's Region input is a local left-facing marker, not a routed edge.
          if (p.reg && e.idx === 0) continue;
          if (p.scope) {
            const span = p.cols[idx] + extra;
            ports.push({id: p.id + "i" + e.idx, x: x + span / 2 - 3, y: -3, span,
              width: 6, height: 6, layoutOptions: {"elk.port.side": "NORTH"}});
            x += span;
            continue;
          }
          const off = p.n.kind === "PHI" ? 0 : 1;
          ports.push({id: p.id + "i" + e.idx, x: p.ox + p.width * (e.idx + off) / (p.n.edges.length + off) - 3,
            y: p.oy - 3, width: 6, height: 6, layoutOptions: {"elk.port.side": "NORTH"}});
        }
        // Direct uses of the MultiNode attach to its header, projections below.
        const side = p === n && slots.length ? "EAST" : "SOUTH";
        ports.push({id: p.id + "o", x: p.ox + (side === "EAST" ? p.width : p.width / 2) - 3,
          y: p.oy + (side === "EAST" ? p.height / 2 : p.height) - 3,
          width: 6, height: 6, layoutOptions: {"elk.port.side": side}});
        const defs = [...(outs.get(p.n.id) || [])];
        for (const [i, id] of defs.entries()) ports.push({id: p.id + "o" + id,
          x: p.width * (i + 1) / (defs.length + 1) - 3, y: p.height - 3,
          width: 6, height: 6, layoutOptions: {"elk.port.side": "SOUTH"}});
      }
      return {id: n.id, width, height: n.height + (slots.length ? Math.max(...slots.map(p => p.height)) : 0), ports, parts};
    });
    const edges = [];
    for (const use of snap.nodes) {
      for (const e of use.edges) {
        if (!e.def || hidden(use, e)) continue;
        if (byId.get(use.id).reg && e.idx === 0) continue;
        if (byId.get(use.id).par && e.idx === 0 && e.def === use.proj.par) continue;
        const id = "e" + e.orig.use + "i" + e.orig.idx;
        const reg = byId.get(use.edges[0]?.def)?.n;
        const back = !use.fold && e.idx === 2 && (use.kind === "LOOP" ||
          (use.kind === "PHI" && reg?.kind === "LOOP"));
        edges.push({id, use: use.id, def: e.def, idx: e.orig.idx, orig: e.orig, role: e.role, label: e.label,
          bind: use.kind === "SCOPE",
          // Layout follows value/control flow downward. SVG arrows point back
          // from use to def, matching Simple's actual edge direction.
          sources: ["n" + e.def + "o" + (byId.get(e.def).n.fold ? e.orig.def : "")], targets: ["n" + use.id + "i" + e.idx],
          layoutOptions: {"elk.layered.priority.direction": back ? 0 : e.role === "CTRL" ? 10 : 1}});
      }
    }
    const opts = {
      "elk.algorithm": "layered", "elk.direction": "DOWN", "elk.edgeRouting": "ORTHOGONAL",
      "elk.hierarchyHandling": "INCLUDE_CHILDREN",
      "elk.spacing.nodeNode": 32, "elk.layered.spacing.nodeNodeBetweenLayers": 48,
      "elk.padding": "[top=24,left=24,bottom=24,right=24]",
      "elk.separateConnectedComponents": held.length === 0,
      // ELK 0.12's model-order presort fails on nested hierarchy edge dummies.
      "elk.layered.considerModelOrder.strategy": view.open.length ? "NONE" : "NODES_AND_EDGES",
      "elk.randomSeed": 1
    };
    const containers = new Map(view.open.map(g => [g.id, {id: "g" + g.id, children: [],
      layoutOptions: {...opts, "elk.padding": "[top=58,left=28,bottom=28,right=28]"}}]));
    const children = [];
    const add = (par, box) => (containers.get(par)?.children || children).push(box);
    for (const g of view.open) add(g.par, containers.get(g.id));
    for (const {parts, ...box} of groups) {
      const n = parts[0];
      if (n.scope || n.stop) continue;
      // The folded header replaces its own container inside the parent group.
      const par = n.n.fold ? n.n.fold.par : view.home.get(n.n.id);
      add(par, {...box, layoutOptions: {"elk.portConstraints": "FIXED_POS",
        // LAST_SEPARATE conflicts with ELK's hierarchical boundary ports.
        "elk.layered.layering.layerConstraint": parts.length === 1 && n.held ?
          (view.open.length ? "LAST" : "LAST_SEPARATE") : "NONE"}});
    }
    const input = {
      id: "root", children,
      // Scope bindings and lifetime associations must not impose CFG ranks.
      edges: edges.filter(e => e.role !== "ASSOC" && !e.bind && !byId.get(e.def).scope &&
        !byId.get(e.use).stop && !byId.get(e.def).stop).map(e => ({
        id: e.id, sources: e.sources, targets: e.targets, layoutOptions: e.layoutOptions
      })),
      layoutOptions: opts
    };
    // ELK expects an edge on the lowest container containing both endpoints.
    const portBox = new Map(), parents = new Map();
    const index = box => {
      for (const p of box.ports || []) portBox.set(p.id, box);
      for (const child of box.children || []) { parents.set(child, box); index(child); }
    };
    index(input);
    const flow = input.edges;
    input.edges = [];
    for (const e of flow) {
      const ancestors = new Set();
      for (let p = parents.get(portBox.get(e.sources[0])); p; p = parents.get(p)) ancestors.add(p);
      let p = parents.get(portBox.get(e.targets[0]));
      while (!ancestors.has(p)) p = parents.get(p);
      (p.edges ||= []).push(e);
    }
    const graph = await this.elk.layout(input);
    // Place scopes and Stops after the flow graph; their ports stay local.
    for (const g of groups.filter(g => g.parts[0].scope || g.parts[0].stop))
      graph.children.push({...g, x: 0, y: 0});
    const boxes = new Map(), routes = new Map();
    const collect = (box, x, y) => {
      box.x = (box.x || 0) + x; box.y = (box.y || 0) + y;
      boxes.set(box.id, box);
      for (const e of box.edges || []) routes.set(e.id, {e, box});
      for (const child of box.children || []) collect(child, box.x, box.y);
    };
    collect(graph, 0, 0);
    for (const group of groups) {
      const box = boxes.get(group.id);
      const ports = new Map(box.ports.map(p => [p.id, p]));
      for (const n of group.parts) {
        n.x = box.x + n.ox; n.y = box.y + n.oy;
        // Keep semantic nodes/slots intact for selection and neighborhood marks.
        n.ports = n.n.edges.map(e => {
          if (n.reg && e.idx === 0)
            return {id: n.id + "i0", x: -6, y: n.height / 2 - 3, width: 6, height: 6, reg: true, edge: e};
          const p = ports.get(n.id + "i" + e.idx);
          return p && {...p, x: p.x - n.ox, y: p.y - n.oy, edge: e};
        }).filter(Boolean);
        const out = ports.get(n.id + "o");
        n.ports.push({...out, x: out.x - n.ox, y: out.y - n.oy});
        for (const id of outs.get(n.n.id) || []) n.ports.push(ports.get(n.id + "o" + id));
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
    // Stack Stop inputs before their collector: StopCUs above the outer Stop.
    // Peers share a row, so adding CUs costs width rather than another level.
    const pending = new Set(nodes.filter(n => n.stop)), rows = [];
    while (pending.size) {
      const row = [...pending].filter(n => !n.n.edges.some(e => pending.has(byId.get(e.def))));
      // A malformed cycle should still be drawable.
      if (!row.length) row.push(...pending);
      for (const n of row) pending.delete(n);
      rows.push({nodes: row, width: row.reduce((sum, n) => sum + n.width, 0) + (row.length - 1) * 32,
        height: Math.max(...row.map(n => n.height))});
    }
    const stops = rows.length ? {width: Math.max(...rows.map(r => r.width)),
      height: rows.reduce((sum, r) => sum + r.height, 0) + (rows.length - 1) * 32} : null;
    // The whole Stop stack stays below the flow graph, beside the Parser.
    const foot = [...(stops ? [stops] : []), ...(parser ? [parser] : [])];
    if (foot.length) {
      const width = foot.reduce((sum, n) => sum + n.width, 0) + (foot.length - 1) * 32;
      const height = Math.max(...foot.map(n => n.height));
      graph.width = Math.max(graph.width, width + 48);
      let x = (graph.width - width) / 2;
      for (const n of foot) {
        n.x = x;
        n.y = graph.height + 24 + height - n.height;
        x += n.width + 32;
      }
      graph.height += height + 48;
    }
    if (stops) {
      let y = stops.y;
      for (const row of rows) {
        let x = stops.x + (stops.width - row.width) / 2;
        for (const n of row.nodes) {
          n.x = x;
          n.y = y;
          x += n.width + 32;
        }
        y += row.height + 32;
      }
    }
    if (parser) {
      owned.sort((a, b) => a.x - b.x || a.n.id - b.n.id);
      parser.ports = owned.map((n, i) => ({id: "parser-i" + n.n.id,
        x: parser.width * (i + 1) / (owned.length + 1) - 3, y: -3}));
      for (const n of owned) edges.push({id: "parser-hold-" + n.n.id, scope: n.scope, active: n === active,
        use: 0, def: n.n.id, role: "PARSER", sources: [n.id + "o"], targets: ["parser-i" + n.n.id]});
    }
    for (const e of edges) {
      if (e.role === "ASSOC" || e.role === "PARSER" || e.bind || byId.get(e.def).scope ||
          byId.get(e.use)?.stop || byId.get(e.def).stop) {
        const def = byId.get(e.def), use = e.role === "PARSER" ? parser : byId.get(e.use);
        const a = def.ports.find(p => p.id === e.sources[0]);
        const b = use.ports.find(p => p.id === e.targets[0]);
        const x = def.x + a.x + 3, y = def.y + a.y + 3;
        const u = use.x + b.x + 3, v = use.y + b.y + 3;
        e.path = `M${x},${y} C${x},${y + 24} ${u},${v - 24} ${u},${v}`;
      } else {
        const route = routes.get(e.id), origin = boxes.get(route.e.container) || route.box;
        e.path = route.e.sections.map(s =>
          [s.startPoint, ...(s.bendPoints || []), s.endPoint]
            .map((p, i) => `${i ? "L" : "M"}${p.x + origin.x},${p.y + origin.y}`).join(" ")).join(" ");
      }
    }
    return {width: graph.width, height: graph.height, nodes, edges, raw: view.raw, cover: view.cover,
      groups: view.open.map(g => ({...g, ...boxes.get("g" + g.id), gid: g.id, n: view.raw.get(g.id)})),
      parser, scope: active?.n.id || 0, scopes: new Set(owned.filter(n => n.scope).map(n => n.n.id)),
      held: new Set(held.map(n => n.n.id))};
  }
}
