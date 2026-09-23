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
    this.links = this.world.append("g").attr("class", "edges");
    this.parser = this.world.append("g").attr("class", "parser");
    this.nodes = this.world.append("g").attr("class", "nodes");
    this.zoom = d3.zoom().scaleExtent([.02, 4]).on("zoom", event => {
      if (event.sourceEvent) this.auto = false;
      this.world.attr("transform", event.transform);
    });
    this.svg.call(this.zoom).on("dblclick.zoom", null).on("click", () => this.select(0));
    this.resize = new ResizeObserver(() => {
      this.svg.attr("width", host.clientWidth).attr("height", host.clientHeight);
      if (this.auto) this.fit();
    });
    this.resize.observe(host);
  }

  show(snap, scene, evt) {
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
      g.append("text").attr("class", "label").attr("x", 34).attr("y", 22);
      g.append("title");
      return g;
    }).attr("transform", g => `translate(${g.x},${g.y})`);
    regions.select("rect.region-box").attr("width", g => g.width).attr("height", g => g.height)
      .attr("fill", g => g.n.kind === "LOOP" ? "#f1f6fc" : "#fff8ec");
    regions.select("text.label").text(g => (`#${g.gid} ${g.n.label}`).slice(0, Math.max(12, Math.floor((g.width-48)/7))));
    regions.select("title").text(g => `${g.n.label} · ${g.members.size} nodes`);
    regions.each((g, i, items) => this.foldButton(d3.select(items[i]), g.gid, false, 8, 8));
    this.links.selectAll("path.edge").data(scene.edges, e => e.id).join("path")
      .attr("class", e => "edge " + e.role).attr("id", e => e.id)
      .attr("d", e => e.path).attr("stroke", e => this.colors[e.role])
      .attr("marker-start", e => "url(#arrow-" + e.role + ")")
      .each(function(e) {
        d3.select(this).selectAll("title").data([e]).join("title")
          .text(e.role === "PARSER" ? (e.scope ? `Parser holds ${e.active ? "active" : "saved"} scope #${e.def}` :
            `Parser holds #${e.def}; no graph users yet`) :
            `#${e.orig?.use ?? e.use}[${e.idx}] → #${e.orig?.def ?? e.def} · ${e.role}${e.label ? " · " + e.label : ""}`);
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
      g.append("text").attr("class", "head").attr("x", 8).attr("y", 15);
      g.append("text").attr("class", "label").attr("text-anchor", "middle").attr("y", 33);
      g.append("text").attr("class", "type").attr("text-anchor", "middle").attr("y", 51);
      g.append("title");
      return g;
    });
    boxes.attr("id", n => n.id).attr("data-id", n => n.n.id)
      .classed("held", n => !!n.held)
      .classed("folded", n => !!n.n.fold)
      .attr("transform", n => `translate(${n.x},${n.y})`)
      .on("click", (event, n) => { event.stopPropagation(); this.select(n.n.id); });
    boxes.select("path.box").attr("d", n => this.shape(n))
      .attr("fill", n => ({CTRL: "#fff1c4", REGION: "#fff1c4", LOOP: "#ffe0ab", FUN: "#ffe0ab",
        UNIT: "#ffe0ab", START: "#fff1c4", STOP: "#fff1c4", MEM: "#dcecff", PHI: "#f5e4fa", SCOPE: "#e5e0ff", DATA: "#edf3f7"})[n.n.kind]);
    boxes.select("text.head").attr("y", n => n.scope ? 47 : 15)
      .text(n => n.compact ? "" : `#${n.n.id}${n.n.proj ? " · p" + n.n.proj.idx : ""}`);
    boxes.select("text.label").attr("x", n => n.width / 2).attr("y", n => n.compact ? 17 : n.scope ? 51 : 33)
      .text(n => n.compact ? `#${n.n.id} ${n.label}` : n.label);
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
        .attr("text-anchor", "middle").text(p => n.n.fold ? `${p.edge.orig.use}:${p.edge.orig.idx}` : p.edge.idx);
    });
    this.assocs(document.getElementById("assocs").checked);
    this.mark();
    this.select(this.pick);
    if (this.auto) this.fit();
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
      .on("click", (event, id) => { event.stopPropagation(); this.onFold?.(id); })
      .on("keydown", (event, id) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault(); event.stopPropagation(); this.onFold?.(id);
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
    const r = n.n.kind === "LOOP" ? 24 : n.n.kind === "SCOPE" ? 4 : 14;
    return `M${r},0H${w - r}Q${w},0 ${w},${r}V${h - r}Q${w},${h} ${w - r},${h}` +
      `H${r}Q0,${h} 0,${h - r}V${r}Q0,0 ${r},0Z`;
  }

  info(n) {
    const fold = this.scene.nodes.find(v => v.n.id === n.id)?.n.fold;
    n = this.scene.raw?.get(n.id) || n;
    return `#${n.id} ${n.label}${n.proj ? "\nProjection " + n.proj.idx + " of #" + n.proj.par : ""}` +
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
      ((e.orig?.use ?? e.use) === id || (e.orig?.def ?? e.def) === id || e.use === id || e.def === id));
    const detail = document.getElementById("detail");
    detail.hidden = !id;
    const box = this.scene?.cover?.get(id);
    detail.textContent = node ? this.info(node) + (box && box !== id ? `\nInside folded region #${box}` : "") : `#${id} is absent in this frame.`;
  }

  assocs(show) { this.links.selectAll(".ASSOC").style("display", e => show || e.bind ? null : "none"); }

  mark() {
    const evt = this.evt;
    const show = document.getElementById("near").checked;
    const near = new Set(show ? evt?.near : []);
    this.nodes.selectAll("g.node").classed("near", n => near.has(n.n.id) ||
      n.n.fold && [...n.n.fold.members].some(id => near.has(id)))
      .classed("focus", n => show && (n.n.id === evt?.node || n.n.id === evt?.repl ||
        n.n.fold && (n.n.fold.members.has(evt?.node) || n.n.fold.members.has(evt?.repl))));
  }

  // Save the whole graph at its layout size, independent of pan/zoom.
  save(step) {
    const svg = this.svg.node().cloneNode(true);
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
    if (!this.scene || !this.host.clientWidth || !this.host.clientHeight) return;
    const w = this.host.clientWidth, h = this.host.clientHeight;
    const scale = Math.max(.02, Math.min(1.5, (w - 32) / this.scene.width, (h - 32) / this.scene.height));
    this.svg.call(this.zoom.transform, d3.zoomIdentity
      .translate((w - this.scene.width * scale) / 2, (h - this.scene.height * scale) / 2).scale(scale));
  }
}
