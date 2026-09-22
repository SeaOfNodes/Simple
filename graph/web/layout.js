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

  async run(snap) {
    const nodes = snap.nodes.map(n => {
      const label = this.clip(n.label), type = this.clip(n.type);
      const w = Math.ceil(Math.max(120, this.ctx.measureText(label).width + 24,
        this.ctx.measureText(type).width + 24, (n.edges.length + 1) * 18));
      const h = 62;
      const ports = n.edges.map(e => ({
        id: "n" + n.id + "i" + e.idx, x: w * (e.idx + 1) / (n.edges.length + 1) - 3,
        y: -3, width: 6, height: 6, layoutOptions: {"elk.port.side": "NORTH"}
      }));
      ports.push({id: "n" + n.id + "o", x: w / 2 - 3, y: h - 3,
        width: 6, height: 6, layoutOptions: {"elk.port.side": "SOUTH"}});
      return {id: "n" + n.id, width: w, height: h, ports,
        layoutOptions: {"elk.portConstraints": "FIXED_POS"}, n, label, type};
    });
    const byId = new Map(nodes.map(n => [n.n.id, n]));
    const edges = [];
    for (const use of snap.nodes) {
      for (const e of use.edges) {
        if (!e.def) continue;
        const id = "e" + use.id + "i" + e.idx;
        const reg = byId.get(use.edges[0]?.def)?.n;
        const back = e.idx === 2 && (use.kind === "LOOP" ||
          (use.kind === "PHI" && reg?.kind === "LOOP"));
        edges.push({id, use: use.id, def: e.def, idx: e.idx, role: e.role, label: e.label,
          // Layout follows value/control flow downward. SVG arrows point back
          // from use to def, matching Simple's actual edge direction.
          sources: ["n" + e.def + "o"], targets: ["n" + use.id + "i" + e.idx],
          layoutOptions: {"elk.layered.priority.direction": back ? 0 : e.role === "CTRL" ? 10 : 1}});
      }
    }
    const graph = await this.elk.layout({
      id: "root", children: nodes.map(({n, label, type, ...box}) => box),
      // Scope bindings and lifetime associations must not impose CFG ranks.
      edges: edges.filter(e => e.role !== "ASSOC").map(e => ({
        id: e.id, sources: e.sources, targets: e.targets, layoutOptions: e.layoutOptions
      })),
      layoutOptions: {
        "elk.algorithm": "layered", "elk.direction": "DOWN", "elk.edgeRouting": "ORTHOGONAL",
        "elk.spacing.nodeNode": 32, "elk.layered.spacing.nodeNodeBetweenLayers": 48,
        "elk.padding": "[top=24,left=24,bottom=24,right=24]",
        "elk.layered.considerModelOrder.strategy": "NODES_AND_EDGES",
        "elk.randomSeed": 1
      }
    });
    const boxes = new Map(graph.children.map(n => [n.id, n]));
    const routes = new Map(graph.edges.map(e => [e.id, e]));
    for (const n of nodes) {
      const box = boxes.get(n.id);
      n.x = box.x; n.y = box.y;
      // Keep ports in input-slot order even if ELK reorders its result.
      const ports = new Map(box.ports.map(p => [p.id, p]));
      n.ports = n.ports.map(p => ports.get(p.id));
    }
    for (const e of edges) {
      if (e.role === "ASSOC") {
        const def = byId.get(e.def), use = byId.get(e.use);
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
    return {width: graph.width, height: graph.height, nodes, edges};
  }
}
