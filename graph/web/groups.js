// A display projection of a snapshot. Original nodes and edge slots stay intact.
class GraphGroups {
  static view(snap, folded) {
    const raw = new Map(snap.nodes.map(n => [n.id, n]));
    const groups = new Map((snap.groups || []).map(g => [g.id, {...g, members: new Set(g.nodes)}]));
    const home = new Map(), cover = new Map();
    for (const g of groups.values()) for (const id of g.nodes) home.set(id, g.id);
    for (const g of groups.values()) {
      for (let p = groups.get(g.par); p; p = groups.get(p.par))
        for (const id of g.nodes) p.members.add(id);
    }
    for (const n of snap.nodes) {
      let id = n.id;
      for (let g = groups.get(home.get(id)); g; g = groups.get(g.par))
        if (folded.has(g.id)) id = g.id;
      cover.set(n.id, id);
    }
    const nodes = [], byId = new Map();
    for (const n of snap.nodes) {
      if (cover.get(n.id) !== n.id) continue;
      const fold = groups.has(n.id) && folded.has(n.id);
      const v = {...n, slots: n.edges.length, edges: [], fold: fold ? groups.get(n.id) : null};
      if (fold) {
        v.proj = null;
        v.type = `${v.fold.members.size} nodes folded`;
      }
      nodes.push(v); byId.set(n.id, v);
    }
    const bundles = new Map(), rank = {ASSOC: 0, DATA: 1, MEM: 2, CTRL: 3};
    for (const n of snap.nodes) for (const e of n.edges) {
      const use = byId.get(cover.get(n.id)), def = cover.get(e.def) || 0;
      if (use.fold && (!def || def === use.id)) continue;
      const orig = {use: n.id, def: e.def, idx: e.idx, role: e.role, label: e.label};
      // During inlining the old function may disappear. Keep ordinary edges
      // then; a shortcut needs a real, selectable function in this snapshot.
      const jump = raw.get(e.jump)?.kind === "FUN" ? e.jump : 0;
      const key = jump ? `j${use.id}:${jump}` : def && (use.fold || byId.get(def).fold) ? `${use.id}:${def}` : null;
      const old = bundles.get(key);
      if (old) {
        old.refs.push(orig);
        if (rank[e.role] > rank[old.role]) old.role = e.role;
        old.label = null;
      } else {
        const edge = {...e, idx: use.fold ? use.edges.length : e.idx, def, jump, orig, refs: [orig]};
        use.edges.push(edge);
        if (key) bundles.set(key, edge);
      }
    }
    // Expanded groups only; a folded group is represented by its header box.
    for (const n of nodes) if (n.kind === "SCOPE") for (const e of n.edges)
      e.label = e.refs.map(r => r.label || `[${r.idx}]`).join(", ");
    const open = [...groups.values()].filter(g => !folded.has(g.id) && cover.get(g.id) === g.id);
    const depth = g => {
      let n = 0;
      for (; g.par; g = groups.get(g.par)) n++;
      return n;
    };
    // Paint outer containers before inner ones, even after headers are cloned.
    open.sort((a,b) => depth(a) - depth(b) || a.id - b.id);
    return {snap: {...snap, nodes}, raw, home, cover, open, groups};
  }
}
