// SVG rendering and interaction. Layouts are cached by the playback controller.
class GraphView {
  constructor(host) {
    this.host = host;
    this.comp = "";
    this.pick = 0;
    this.auto = true;
    this.scene = null;
    this.colors = {CTRL: "#b63838", DATA: "#536477", MEM: "#2867b2", ASSOC: "#9396a3"};
    this.svg = d3.select(host).append("svg").attr("aria-label", "Compiler graph");
    const defs = this.svg.append("defs");
    for (const [role, color] of Object.entries(this.colors)) {
      defs.append("marker").attr("id", "arrow-" + role)
        .attr("viewBox", "0 -4 8 8").attr("refX", 8).attr("refY", 0)
        .attr("markerWidth", 7).attr("markerHeight", 7).attr("orient", "auto-start-reverse")
        .append("path").attr("d", "M0,-4L8,0L0,4Z").attr("fill", color);
    }
    this.world = this.svg.append("g");
    this.links = this.world.append("g").attr("class", "edges");
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
    this.links.selectAll("path.edge").data(scene.edges, e => e.id).join("path")
      .attr("class", e => "edge " + e.role).attr("id", e => e.id)
      .attr("d", e => e.path).attr("stroke", e => this.colors[e.role])
      .attr("marker-start", e => "url(#arrow-" + e.role + ")")
      .each(function(e) {
        d3.select(this).selectAll("title").data([e]).join("title")
          .text(`#${e.use}[${e.idx}] → #${e.def} · ${e.role}${e.label ? " · " + e.label : ""}`);
      });
    const boxes = this.nodes.selectAll("g.node").data(scene.nodes, n => n.id).join(enter => {
      const g = enter.append("g").attr("class", "node");
      g.append("rect").attr("class", "box");
      g.append("text").attr("class", "head").attr("x", 8).attr("y", 15);
      g.append("text").attr("class", "label").attr("text-anchor", "middle").attr("y", 33);
      g.append("text").attr("class", "type").attr("text-anchor", "middle").attr("y", 51);
      g.append("title");
      return g;
    });
    boxes.attr("id", n => n.id).attr("data-id", n => n.n.id)
      .attr("transform", n => `translate(${n.x},${n.y})`)
      .on("click", (event, n) => { event.stopPropagation(); this.select(n.n.id); });
    boxes.select("rect.box").attr("width", n => n.width).attr("height", n => n.height)
      .attr("rx", n => ["CTRL", "REGION", "LOOP", "FUN", "UNIT"].includes(n.n.kind) ? 3 : 14)
      .attr("fill", n => ({CTRL: "#fff1c4", REGION: "#fff1c4", LOOP: "#ffe0ab", FUN: "#ffe0ab",
        UNIT: "#ffe0ab", MEM: "#dcecff", PHI: "#f5e4fa", SCOPE: "#e5e0ff", DATA: "#edf3f7"})[n.n.kind]);
    boxes.select("text.head").text(n => `#${n.n.id} · ${n.n.kind}${n.n.proj ? " · p" + n.n.proj.idx : ""}`);
    boxes.select("text.label").attr("x", n => n.width / 2).text(n => n.label);
    boxes.select("text.type").attr("x", n => n.width / 2).text(n => n.type);
    boxes.select("title").text(n => this.info(n.n));
    boxes.each((n, i, groups) => {
      const ports = n.ports.filter(p => p.id !== n.id + "o");
      const group = d3.select(groups[i]);
      group.selectAll("circle.port").data(ports, p => p.id).join("circle")
        .attr("class", "port").attr("cx", p => p.x + 3).attr("cy", p => p.y + 3)
        .attr("r", 3).attr("fill", (p, j) => n.n.edges[j].def ? "#536477" : "white");
      group.selectAll("text.slot").data(ports, p => p.id).join("text")
        .attr("class", "slot").attr("x", p => p.x + 3).attr("y", p => p.y - 4)
        .attr("text-anchor", "middle").text((p, j) => n.n.edges[j].idx);
    });
    this.assocs(document.getElementById("assocs").checked);
    this.mark();
    this.select(this.pick);
    if (this.auto) this.fit();
  }

  info(n) {
    return `#${n.id} ${n.label}\n${n.kind}${n.proj ? " · projection " + n.proj.idx + " of #" + n.proj.par : ""}` +
      `\n${n.type || "Type not computed"}\n\n` + n.edges.map(e =>
        `[${e.idx}] → ${e.def ? "#" + e.def : "—"}  ${e.role}${e.label ? "  " + e.label : ""}`).join("\n");
  }

  select(id) {
    this.pick = id;
    const node = this.scene?.nodes.find(n => n.n.id === id);
    this.nodes.selectAll("g.node").classed("selected", n => n.n.id === id);
    this.links.selectAll("path.edge").classed("selected", e => e.use === id || e.def === id);
    const detail = document.getElementById("detail");
    detail.hidden = !id;
    detail.textContent = node ? this.info(node.n) : `#${id} is absent in this frame.`;
  }

  assocs(show) { this.links.selectAll(".ASSOC").style("display", show ? null : "none"); }

  mark() {
    const evt = this.evt;
    const show = document.getElementById("near").checked;
    const near = new Set(show ? evt?.near : []);
    this.nodes.selectAll("g.node").classed("near", n => near.has(n.n.id))
      .classed("focus", n => show && (n.n.id === evt?.node || n.n.id === evt?.repl));
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
