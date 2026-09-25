// SVG rendering and interaction. Layouts are cached by the playback controller.
class GraphView {
  constructor(host) {
    this.host = host;
    this.comp = "";
    this.pick = 0;
    this.auto = true;
    this.scene = null;
    this.colors = {CTRL: "#b63838", DATA: "#536477", MEM: "#2867b2", ASSOC: "#9396a3", PARSER: "#7956a5"};
    this.svg = d3.select(host).append("svg").attr("aria-label", "Compiler graph");
    const defs = this.svg.append("defs");
    for (const [role, color] of Object.entries(this.colors)) {
      defs.append("marker").attr("id", "arrow-" + role)
        .attr("viewBox", "0 -4 8 8").attr("refX", 8).attr("refY", 0)
        .attr("markerWidth", 7).attr("markerHeight", 7).attr("orient", "auto-start-reverse")
        .append("path").attr("d", "M0,-4L8,0L0,4Z").attr("fill", color);
    }
    this.world = this.svg.append("g");
    this.regions = this.world.append("g").attr("class", "regions");
    this.halo = this.world.append("rect").attr("class", "peep-area").attr("rx", 36).style("opacity", 0);
    this.links = this.world.append("g").attr("class", "edges");
    this.hits = this.world.append("g").attr("class", "edge-hits");
    this.parser = this.world.append("g").attr("class", "parser");
    this.nodes = this.world.append("g").attr("class", "nodes");
    // Titles/buttons stay above crossing edges, even with narrow group margins.
    this.heads = this.world.append("g").attr("class", "region-heads");
    this.shortcuts = this.world.append("g").attr("class", "shortcuts");
    this.ghosts = this.world.append("g").attr("class", "peep-ghosts").attr("pointer-events", "none");
    // Keep the same edge clickable under a stationary mouse during the pan,
    // even while other nodes/edges pass underneath it.
    this.hop = null;
    this.spot = this.svg.append("circle").attr("class", "edge-jump").attr("r", 7).style("display", "none")
      .on("click", event => { if (this.hop) this.edgeJump(event, this.hop.e, this.hop.path); });
    this.zoom = d3.zoom().scaleExtent([.02, 4]).on("zoom", event => {
      if (event.sourceEvent) { this.auto = false; this.clearJump(); this.svg.interrupt("peep-view"); }
      this.world.attr("transform", event.transform);
    });
    this.svg.call(this.zoom).on("dblclick.zoom", null)
      .on("click", () => { this.clearJump(); this.select(0); })
      .on("mousemove", event => {
        if (!this.hop) return;
        const [x,y] = d3.pointer(event, this.svg.node());
        if (Math.hypot(x-this.hop.x, y-this.hop.y) > 10) this.clearJump();
      }).on("mouseleave", () => this.clearJump());
    this.resize = new ResizeObserver(() => {
      this.svg.attr("width", host.clientWidth).attr("height", host.clientHeight);
      if (this.auto) this.fit();
    });
    this.resize.observe(host);
  }

  show(snap, scene, evt) {
    this.cancelMove();
    this.clearJump();
    if (this.comp !== snap.comp) {
      this.comp = snap.comp;
      this.pick = 0;
      this.auto = true;
      this.nodes.selectAll("*").remove();
      this.links.selectAll("*").remove();
    }
    this.scene = scene;
    this.evt = evt;
    const regions = this.regions.selectAll("g.region").data(scene.groups || [], g => g.gid).join(enter => {
      const g = enter.append("g").attr("class", "region");
      g.append("rect").attr("class", "region-box").attr("rx", 8);
      return g;
    }).attr("transform", g => `translate(${g.x},${g.y})`);
    regions.select("rect.region-box").attr("width", g => g.width).attr("height", g => g.height)
      .attr("fill", g => g.n.folding ? "#fff0f3" : g.n.kind === "LOOP" ? "#f1f6fc" : g.n.kind === "FUN" ? "#fff8ec" : "#f2f7ef");
    const heads = this.heads.selectAll("g.region-head").data(scene.groups || [], g => g.gid).join(enter => {
      const g = enter.append("g").attr("class", "region-head");
      g.append("text").attr("class", "label").attr("x", 26).attr("y", 17);
      g.append("title");
      return g;
    }).attr("transform", g => `translate(${g.x},${g.y})`);
    heads.select("text.label").each((g, i, items) => this.label(d3.select(items[i]),
      g.n.label.slice(0, Math.max(12, Math.floor((g.width-80-(g.n.folding ? 60 : 0))/7))) + ` #${g.gid}`, g.n.folding));
    heads.select("title").text(g => `${g.n.label} · ${g.members.size} nodes`);
    heads.each((g, i, items) => this.foldButton(d3.select(items[i]), g.gid, false, 4, 4));
    this.links.selectAll("path.edge").data(scene.edges, e => e.id).join("path")
      .attr("class", e => "edge " + e.role).attr("id", e => e.id)
      .classed("bundle", e => e.bundle)
      .attr("d", e => e.path).attr("stroke", e => this.colors[e.bundle && e.role === "ASSOC" ? "DATA" : e.role])
      .attr("marker-start", e => "url(#arrow-" + (e.bundle && e.role === "ASSOC" ? "DATA" : e.role) + ")")
      .each(function(e) {
        d3.select(this).selectAll("title").data([e]).join("title")
          .text(e.role === "PARSER" ? (e.scope ? `Parser holds ${e.active ? "active" : "saved"} scope #${e.def}` :
            `Parser holds #${e.def}; no graph users yet`) :
            (e.bundle ? `${e.refs.length} edges bundled\n` : "") + e.refs.map(r =>
              `#${r.use}[${r.idx}] → #${r.def} · ${r.role}${r.label ? " · " + r.label : ""}`).join("\n"));
      });
    this.hits.selectAll("path").data(scene.edges, e => e.id).join("path")
      .attr("class", e => "edge-hit " + e.role).attr("d", e => e.path)
      .on("click", (event, e) => this.edgeJump(event, e, event.currentTarget))
      .each((e, i, items) => {
        d3.select(items[i]).selectAll("title").data([e]).join("title").text(
          "Click to jump between use and def\n" + document.getElementById(e.id).querySelector("title").textContent);
      });
    this.parser.selectAll("g").data(scene.parser ? [scene.parser] : []).join(enter => {
      const g = enter.append("g");
      g.append("rect").attr("rx", 6);
      g.append("text").attr("class", "label").attr("text-anchor", "middle").attr("y", 22).text("Parser");
      g.append("title").text("Display-only references to parser scopes and unfinished values; these are not compiler edges.");
      return g;
    }).attr("transform", p => `translate(${p.x},${p.y})`)
      .each(function(p) {
        d3.select(this).select("rect").attr("width", p.width).attr("height", p.height);
        d3.select(this).selectAll("text").attr("x", p.width / 2);
      });
    const boxes = this.nodes.selectAll("g.node").data(scene.nodes, n => n.id).join(enter => {
      const g = enter.append("g").attr("class", "node");
      g.append("path").attr("class", "box");
      g.append("text").attr("class", "label").attr("text-anchor", "middle");
      g.append("text").attr("class", "type").attr("text-anchor", "middle").attr("y", 35);
      g.append("title");
      return g;
    });
    boxes.attr("id", n => n.id).attr("data-id", n => n.n.id)
      .classed("held", n => !!n.held)
      .classed("folded", n => !!n.n.fold)
      .classed("folding", n => !!n.n.folding)
      .attr("transform", n => `translate(${n.x},${n.y})`)
      .on("click", (event, n) => { event.stopPropagation(); this.clearJump(); this.select(n.n.id); });
    boxes.select("path.box").attr("d", n => this.shape(n))
      .attr("fill", n => ({CTRL: "#fff1c4", REGION: "#fff1c4", LOOP: "#ffe0ab", FUN: "#ffe0ab",
        UNIT: "#ffe0ab", START: "#fff1c4", STOP: "#fff1c4", MEM: "#dcecff", PHI: "#f5e4fa", SCOPE: "#e5e0ff", DATA: "#edf3f7"})[n.n.kind]);
    boxes.select("text.label").attr("x", n => (n.width - (n.n.fold ? 24 : 0)) / 2)
      .attr("y", n => n.compact ? 17 : n.scope ? 48 : 18)
      .each((n, i, items) => this.label(d3.select(items[i]), n.label, n.n.folding));
    boxes.select("text.type").attr("x", n => n.width / 2).text(n => n.scope || n.compact ? "" : n.type);
    boxes.select("title").text(n => this.info(n.n));
    boxes.each((n, i, groups) => {
      const ports = n.ports.filter(p => p.edge);
      const group = d3.select(groups[i]);
      this.foldButton(group, n.n.fold?.id, true, n.width - 24, 6);
      group.selectAll("line.row").data(n.scope ? ports : [], p => p.id).join("line")
        .attr("class", "row").attr("x1", (p, i) => i ? p.x + 3 - p.span / 2 : 0)
        .attr("x2", (p, i) => i ? p.x + 3 - p.span / 2 : n.width)
        .attr("y1", (p, i) => i ? 0 : 30).attr("y2", 30);
      group.selectAll("text.bind").data(n.scope ? ports : [], p => p.id).join("text")
        .attr("class", "bind").attr("x", p => p.x + 3).attr("y", 20).attr("text-anchor", "middle")
        .text(p => p.edge.label || "[" + p.edge.idx + "]");
      group.selectAll("circle.port").data(ports, p => p.id).join("circle")
        .attr("class", "port").attr("cx", p => p.x + 3).attr("cy", p => p.y + 3)
        .attr("r", 3).attr("fill", p => p.edge.def ? "#536477" : "white");
      group.selectAll("path.reg").data(ports.filter(p => p.reg && p.edge.def), p => p.id).join("path")
        .attr("class", "reg").attr("fill", "none").attr("stroke", this.colors.CTRL)
        .attr("d", p => `M-3,${p.y + 3}H-18m4,-3l-4,3l4,3`)
        .each(function(p) {
          d3.select(this).selectAll("title").data([p]).join("title")
            .text(`[0] → #${p.edge.def}`);
        });
      group.selectAll("text.slot").data(ports, p => p.id).join("text")
        .attr("class", "slot").attr("x", p => p.reg ? -10 : p.x + 3).attr("y", p => p.y - 4)
        .attr("text-anchor", "middle").text(p => n.n.fold ? "" : p.edge.refs.map(r => r.idx).join(","));
    });
    const jumps = this.shortcuts.selectAll("g.shortcut").data(scene.jumps || [], j => j.id).join(enter => {
      const g = enter.append("g").attr("class", "shortcut").attr("role", "button").attr("tabindex", 0);
      g.append("path");
      g.append("rect").attr("rx", 4);
      g.append("text");
      g.append("title");
      return g;
    }).attr("aria-label", j => "Go to function " + j.label)
      .on("click", (event, j) => { event.stopPropagation(); this.center(j.target); })
      .on("keydown", (event, j) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault(); event.stopPropagation(); this.center(j.target);
        }
      });
    jumps.select("path").attr("d", j => j.path).attr("stroke", j => this.colors[j.role])
      .attr("marker-start", j => `url(#arrow-${j.role})`);
    jumps.select("rect").attr("x", j => j.x).attr("y", j => j.y)
      .attr("width", j => j.width).attr("height", j => j.height);
    jumps.select("text").attr("x", j => j.x + 10).attr("y", j => j.y + 15)
      .each((j, i, items) => this.label(d3.select(items[i]), j.label));
    jumps.select("title").text(j => `Center function #${j.target}\n` +
      j.refs.map(r => `#${r.use}[${r.idx}] → #${r.def} · ${r.role}`).join("\n"));
    this.assocs(document.getElementById("assocs").checked);
    const peep = scene.peep;
    if (peep) this.halo.attr("x", peep.area.x).attr("y", peep.area.y)
      .attr("width", peep.area.width).attr("height", peep.area.height);
    this.halo.style("opacity", peep ? 1 : 0);
    this.nodes.selectAll("g.node").style("opacity", n => !peep || peep.ids.has(n.n.id) ? 1 : peep.rim.has(n.n.id) ? .4 : .08)
      .classed("peep-edit", n => !!peep?.changed.has(n.n.id));
    this.links.selectAll("path.edge").style("opacity", e => !peep ? 1 :
      peep.ids.has(e.def) && peep.ids.has(e.use) ? 1 : peep.ids.has(e.def) || peep.ids.has(e.use) ? .4 : .04);
    this.hits.style("pointer-events", peep ? "none" : null).style("display", peep ? "none" : null);
    for (const layer of [this.regions, this.heads, this.parser, this.shortcuts])
      layer.style("opacity", peep ? 0 : 1).style("pointer-events", peep ? "none" : null);
    if (peep?.rim.has(0)) this.parser.style("opacity", .4);
    this.nodes.selectAll(".fold-btn").style("display", peep ? "none" : null);
    this.mark();
    this.select(this.pick);
    if (this.auto) this.fit();
  }

  cancelMove() {
    this.svg.interrupt("peep-view");
    this.world.selectAll("*").interrupt("peep-move");
    this.ghosts.selectAll("*").remove();
  }

  // Sample paths so an orthogonal route can smoothly become a local curve.
  // Rewired edges cross-fade instead of appearing to sweep through other defs.
  pathTween(path, from, to) {
    const points = d => {
      path.setAttribute("d", d);
      const len = path.getTotalLength();
      return Array.from({length: 33}, (_,i) => {
        const p = path.getPointAtLength(len*i/32);
        return [p.x,p.y];
      });
    };
    const a = points(from), b = points(to), mix = d3.interpolateArray(a,b);
    path.setAttribute("d", from);
    return t => t === 1 ? to : mix(t).map((p,i) => `${i ? "L" : "M"}${p[0]},${p[1]}`).join(" ");
  }

  async move(snap, scene, evt, view) {
    this.cancelMove();
    this.clearJump();
    const ms = matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 480;
    const now = d3.zoomTransform(this.svg.node());
    const oldNodes = new Map(), oldEdges = new Map();
    this.nodes.selectAll("g.node").each(function(n) {
      oldNodes.set(n.id, {el: this.cloneNode(true), at: this.getAttribute("transform"), opacity: this.style.opacity});
    });
    this.links.selectAll("path.edge").each(function(e) {
      oldEdges.set(e.id, {e, el: this.cloneNode(true), path: this.getAttribute("d"), opacity: this.style.opacity});
    });
    const opacity = new Map([this.regions, this.heads, this.parser, this.shortcuts, this.halo].map(s => [s, s.style("opacity")]));
    this.auto = false;
    this.show(snap, scene, evt);
    const jobs = [];
    const tween = s => {
      const t = s.transition("peep-move").duration(ms).ease(d3.easeCubicInOut);
      jobs.push(t.end().catch(() => {}));
      return t;
    };
    const ghost = (el, opacity) => {
      el.removeAttribute("id");
      el.querySelectorAll("[id]").forEach(n => n.removeAttribute("id"));
      this.ghosts.node().append(el);
      tween(d3.select(el).style("opacity", opacity || 1)).style("opacity", 0).remove();
    };
    this.nodes.selectAll("g.node").each((n,i,items) => {
      const s = d3.select(items[i]), at = s.attr("transform"), opacity = s.style("opacity");
      const old = oldNodes.get(n.id);
      oldNodes.delete(n.id);
      s.attr("transform", old?.at || at).style("opacity", old?.opacity || 0);
      tween(s).attr("transform", at).style("opacity", opacity);
    });
    for (const n of oldNodes.values()) ghost(n.el, n.opacity);
    this.links.selectAll("path.edge").each((e,i,items) => {
      const s = d3.select(items[i]), opacity = s.style("opacity"), old = oldEdges.get(e.id);
      oldEdges.delete(e.id);
      if (old && old.e.def === e.def && old.e.use === e.use) {
        const path = this.pathTween(items[i], old.path, e.path);
        tween(s.style("opacity", old.opacity)).attrTween("d", () => path).style("opacity", opacity);
      } else {
        if (old) ghost(old.el, old.opacity);
        tween(s.style("opacity", 0)).style("opacity", opacity);
      }
    });
    for (const e of oldEdges.values()) ghost(e.el, e.opacity);
    for (const [s, old] of opacity) {
      const next = s.style("opacity");
      tween(s.style("opacity", old)).style("opacity", next);
    }
    // Hide hit targets until the drawn routes reach their destination.
    this.hits.style("display", "none");
    const mix = d3.interpolateArray([now.x,now.y,now.k], [view.x,view.y,view.k]);
    jobs.push(this.svg.transition("peep-view").duration(ms).ease(d3.easeCubicInOut)
      .tween("view", () => t => {
        const [x,y,k] = mix(t);
        this.svg.call(this.zoom.transform, d3.zoomIdentity.translate(x,y).scale(k));
      }).end().catch(() => {}));
    await Promise.all(jobs);
    if (this.scene === scene) this.hits.style("display", scene.peep ? "none" : null);
  }

  view(area = this.scene) {
    const w = this.host.clientWidth, h = this.host.clientHeight;
    const k = Math.max(.02, Math.min(1.5, (w-32)/area.width, (h-32)/area.height));
    return d3.zoomIdentity.translate((w-area.width*k)/2-(area.x || 0)*k,
      (h-area.height*k)/2-(area.y || 0)*k).scale(k);
  }

  label(host, text, folding = false) {
    const split = text.lastIndexOf(" #");
    const parts = [text.slice(0, split), "\u00a0\u00a0" + text.slice(split + 1)];
    if (folding) parts.push("\u00a0\u00a0FOLDING");
    host.selectAll("tspan").data(parts).join("tspan")
      .attr("class", (_, i) => i === 2 ? "fold-tag" : i ? "node-id" : null).text(s => s);
  }

  foldButton(host, id, folded, x, y) {
    const btn = host.selectAll("g.fold-btn").data(id ? [id] : []).join(enter => {
      const g = enter.append("g").attr("class", "fold-btn").attr("role", "button").attr("tabindex", 0);
      g.append("rect").attr("width", 16).attr("height", 16).attr("rx", 2);
      g.append("text").attr("x", 8).attr("y", 12).attr("text-anchor", "middle");
      g.append("title");
      return g;
    }).attr("transform", `translate(${x},${y})`)
      .attr("aria-label", `${folded ? "Unfold" : "Fold"} region #${id}`)
      .on("click", (event, id) => { event.stopPropagation(); this.clearJump(); this.onFold?.(id); })
      .on("keydown", (event, id) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault(); event.stopPropagation(); this.clearJump(); this.onFold?.(id);
        }
      });
    btn.select("text").text(folded ? "+" : "−");
    btn.select("title").text(folded ? "Unfold region" : "Fold region");
  }

  shape(n) {
    const w = n.width, h = n.height;
    // A MultiNode and its projection cells tile one rectangular box.
    if (n.cell || ["CTRL", "UNIT", "START", "STOP"].includes(n.n.kind)) return `M0,0H${w}V${h}H0Z`;
    if (n.n.kind === "REGION") return `M12,0H${w - 12}L${w},${h}H0Z`;
    if (n.n.kind === "PHI") return `M12,0H${w - 12}L${w},${h / 2}L${w - 12},${h}H12L0,${h / 2}Z`;
    if (n.n.kind === "MEM" || n.n.kind === "FUN")
      return `M10,0H${w - 10}L${w},10V${h - 10}L${w - 10},${h}H10L0,${h - 10}V10Z`;
    const r = n.n.kind === "LOOP" ? 20 : n.n.kind === "SCOPE" ? 4 : 14;
    return `M${r},0H${w - r}Q${w},0 ${w},${r}V${h - r}Q${w},${h} ${w - r},${h}` +
      `H${r}Q0,${h} 0,${h - r}V${r}Q0,0 ${r},0Z`;
  }

  info(n) {
    const fold = this.scene.nodes.find(v => v.n.id === n.id)?.n.fold;
    n = this.scene.raw?.get(n.id) || n;
    return `#${n.id} ${n.label}${n.proj ? "\nProjection " + n.proj.idx + " of #" + n.proj.par : ""}` +
      (n.folding ? "\nFOLDING: being inlined; pending removal" : "") +
      (fold ? `\n${fold.members.size} nodes folded` : "") +
      (this.scene.held.has(n.id) ? "\nHeld by parser; no graph users yet" : "") +
      (this.scene.scopes.has(n.id) ? (this.scene.scope === n.id ? "\nActive parser scope" : "\nSaved parser scope") : "") +
      `\n${n.type || "Type not computed"}\n\n` + n.edges.map(e =>
        `[${e.idx}] → ${e.def ? "#" + e.def : "—"}  ${e.role}${e.label ? "  " + e.label : ""}`).join("\n");
  }

  select(id) {
    this.pick = id;
    const node = this.scene?.raw?.get(id) || this.scene?.nodes.find(n => n.n.id === id)?.n;
    this.nodes.selectAll("g.node").classed("selected", n => n.n.id === (this.scene.cover?.get(id) || id));
    this.links.selectAll("path.edge").classed("selected", e => id !== 0 &&
      (e.refs?.some(r => r.use === id || r.def === id) || e.use === id || e.def === id));
    this.shortcuts.selectAll("g.shortcut").classed("selected", j => id !== 0 &&
      (j.target === id || j.use === id || j.refs.some(r => r.use === id || r.def === id)));
    const detail = document.getElementById("detail");
    detail.hidden = !id;
    const box = this.scene?.cover?.get(id);
    detail.textContent = node ? this.info(node) + (box && box !== id ? `\nInside folded region #${box}` : "") : `#${id} is absent in this frame.`;
    if (this.evt?.kind === "ERROR" && this.evt.node === id) detail.textContent += "\n\n" + this.evt.msg;
  }

  assocs(show) {
    this.clearJump();
    for (const layer of [this.links, this.hits])
      layer.selectAll(".ASSOC").style("display", e => show || e.bind ? null : "none");
  }

  clearJump() {
    this.svg.interrupt("edge-pan");
    this.hop = null;
    this.spot.style("display", "none");
  }

  edgeJump(event, e, path) {
    event.preventDefault(); event.stopPropagation();
    this.svg.interrupt("edge-pan");
    const [x,y] = d3.pointer(event, this.svg.node());
    const now = d3.zoomTransform(this.svg.node()), len = path.getTotalLength();
    if (!len) return;
    const def = path.getPointAtLength(0), use = path.getPointAtLength(len);
    const dist = p => Math.hypot(now.applyX(p.x)-x, now.applyY(p.y)-y);
    const again = this.hop?.e.id === e.id && Math.hypot(x-this.hop.x, y-this.hop.y) <= 10;
    const at = again ? (this.hop.at === "use" ? "def" : "use") :
      dist(use) < 32 && dist(use) < dist(def) ? "def" : "use";
    // Land on the edge just outside the node, keeping the mouse off its box.
    const inset = Math.min(20 / now.k, len / 3);
    const p = path.getPointAtLength(at === "def" ? inset : len-inset);
    const next = d3.zoomIdentity.translate(x-p.x*now.k, y-p.y*now.k).scale(now.k);
    this.auto = false;
    this.hop = {e, path, at, x, y};
    this.select(at === "use" ? e.use : e.def);
    this.spot.attr("cx", x).attr("cy", y).style("display", null);
    const ms = matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 :
      Math.min(450, 160 + 6*Math.sqrt(Math.hypot(next.x-now.x, next.y-now.y)));
    const ix = d3.interpolateNumber(now.x,next.x), iy = d3.interpolateNumber(now.y,next.y);
    this.svg.transition("edge-pan").duration(ms).ease(d3.easeCubicInOut)
      .tween("pan", () => t => this.svg.call(this.zoom.transform,
        d3.zoomIdentity.translate(ix(t),iy(t)).scale(now.k)));
  }

  center(id, readable = false) {
    this.clearJump();
    const dst = this.scene.cover?.get(id) || id;
    const n = this.scene.nodes.find(n => n.n.id === dst);
    if (!n) return;
    this.auto = false;
    this.select(id);
    if (readable) this.svg.call(this.zoom.scaleTo, Math.min(1.2, (this.host.clientWidth - 40) / n.width));
    this.svg.call(this.zoom.translateTo, n.x + n.width / 2, n.y + n.height / 2);
  }

  mark() {
    const evt = this.evt;
    const show = document.getElementById("near").checked;
    const near = new Set(show ? this.scene?.peep?.ids || evt?.near : []);
    this.nodes.selectAll("g.node").classed("error", n => evt?.kind === "ERROR" && n.n.id === evt.node)
      .classed("near", n => near.has(n.n.id) ||
      n.n.fold && [...n.n.fold.members].some(id => near.has(id)))
      .classed("focus", n => show && (n.n.id === evt?.node || n.n.id === evt?.repl ||
        n.n.fold && (n.n.fold.members.has(evt?.node) || n.n.fold.members.has(evt?.repl))));
  }

  // Save the whole graph at its layout size, independent of pan/zoom.
  save(step) {
    const svg = this.svg.node().cloneNode(true);
    svg.querySelectorAll(".edge-hit,.edge-jump").forEach(n => n.remove());
    svg.setAttribute("xmlns", "http://www.w3.org/2000/svg");
    svg.setAttribute("width", this.scene.width);
    svg.setAttribute("height", this.scene.height);
    svg.setAttribute("viewBox", `0 0 ${this.scene.width} ${this.scene.height}`);
    svg.style.fontFamily = getComputedStyle(this.host).fontFamily;
    svg.querySelector("g").removeAttribute("transform");
    // Keep styles, markers and labels in the file; no viewer assets are needed.
    const style = document.createElementNS(svg.namespaceURI, "style");
    style.textContent = document.querySelector("style").textContent;
    svg.prepend(style);
    const bg = document.createElementNS(svg.namespaceURI, "rect");
    bg.setAttribute("width", "100%"); bg.setAttribute("height", "100%");
    bg.setAttribute("fill", getComputedStyle(this.host.parentElement).backgroundColor);
    svg.insertBefore(bg, svg.querySelector("g"));
    const blob = new Blob([new XMLSerializer().serializeToString(svg)], {type: "image/svg+xml"});
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = `simple-${step}.svg`;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  fit() {
    this.clearJump();
    if (!this.scene || !this.host.clientWidth || !this.host.clientHeight) return;
    this.svg.call(this.zoom.transform, this.view());
  }
}
