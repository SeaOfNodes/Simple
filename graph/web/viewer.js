// Connection and playback state are independent of renderer initialization.
const program = document.getElementById("program");
const status = document.getElementById("status");
const frameNo = document.getElementById("N");
const scrub = document.getElementById("scrub");
let socket;
let renderer;
let layout;
let rendererReady = false;
let rendering = false;
let frames = [];
let current = -1;
let wanted = -1;
const folded = new Set();
const foldKey = () => [...folded].sort((a,b) => a-b).join(",");
let generation = 0;
let done = false;
let compiling = false;
let connection = "Connecting to compiler...";
let failure = "";
let busy = "";
let peep = null;
let stepping = false;
let motion = 0;

// Nested captures describe work inside an enclosing edit, not stable stops.
// Keep capture numbers, but make navigation land on the outer before/result.
function stable(index) {
  while (index > 0 && frames[index]?.evt?.up) index--;
  const end = resultEnd(index);
  if (end < 0 || end > index) {
    const id = frames[index].evt.peep;
    while (index > 0 && !(frames[index].evt.kind === "BEFORE" && frames[index].evt.peep === id)) index--;
  }
  return index;
}

function resultEnd(index) {
  const frame = frames[index], evt = frame?.evt;
  if (evt?.kind !== "RETURN" || evt.up || !evt.repl || evt.repl === evt.node) return index;
  if (frame.attach !== undefined) return frame.attach;
  if (!frame.snap.nodes.some(n => n.id === evt.node)) return frame.attach = index;
  const next = frames[index+1];
  if (!next) return done || failure ? index : -1;
  return frame.attach = !next.evt.up && ["BEFORE", "PHASE"].includes(next.evt.kind) &&
    next.evt.phase === evt.phase && GraphPeep.attached(frame.snap, next.snap, evt) ? index+1 : index;
}

function editEvent(from, to) {
  const evt = frames[to-1]?.evt;
  return evt?.peep === frames[from].evt?.peep && resultEnd(to-1) === to ? evt : frames[to].evt;
}

function nextFrame(index, dir) {
  const evt = frames[index]?.evt;
  const pair = dir > 0 ? evt?.kind === "BEFORE" : ["RETURN", "APPLY"].includes(evt?.kind);
  if (pair) {
    for (let i = index+dir; i >= 0 && i < frames.length; i += dir) {
      const e = frames[i].evt;
      if (e?.peep === evt.peep && (dir > 0 ? ["RETURN", "APPLY"].includes(e.kind) : e.kind === "BEFORE"))
        return dir > 0 ? resultEnd(i) : i;
    }
    // A streaming capture hasn't delivered this peep's result yet.
    return -1;
  }
  const dest = index+dir;
  return dest >= 0 && dest < frames.length ? stable(dest) : -1;
}

function updateUI() {
  status.textContent = failure || (connection !== "Connected" ? connection :
    !rendererReady ? "Connected; initializing graph renderer..." :
    busy ? busy : compiling ? "Compiling... " + frames.length + " frames received" :
    done ? frames.length + " frames ready" : "Connected");
  const at = wanted >= 0 ? wanted : current;
  if (document.activeElement !== frameNo) frameNo.value = String(at + 1);
  frameNo.max = scrub.max = String(Math.max(1, frames.length));
  frameNo.disabled = scrub.disabled = !rendererReady || !frames.length;
  scrub.value = String(Math.max(1, at + 1));
  scrub.setAttribute("aria-valuetext", `Frame ${at + 1} of ${frames.length}`);
  document.getElementById("len").textContent = String(frames.length);
  document.getElementById("doFirst").disabled = !rendererReady || !frames.length || (!peep && !stepping && at <= 0);
  document.getElementById("doLast").disabled = !rendererReady || !frames.length || (!peep && !stepping && at >= stable(frames.length-1));
  document.getElementById("doPrev").disabled = !rendererReady || rendering || stepping || (!peep && at <= 0);
  document.getElementById("doNext").disabled = !rendererReady || rendering || stepping || (!peep && nextFrame(at,1) < 0);
  document.getElementById("compile").disabled =
    !socket || socket.readyState !== WebSocket.OPEN || compiling || rendering || stepping;
  document.getElementById("fit").disabled = rendering || current < 0;
  document.getElementById("save").disabled = rendering || stepping || !!peep || !frames[current]?.snap;
  document.getElementById("unfold").disabled = !folded.size || !!peep || stepping;
  document.getElementById("assocs").disabled = !frames[current]?.snap;
  document.getElementById("near").disabled = !frames[current]?.evt;
  const result = peep?.evt;
  const evt = peep ? (peep.stage === 1 ? {...result, kind: "BEFORE"} : result) : frames[current]?.evt;
  let label = "";
  if (evt) {
    const text = evt.kind === "ERROR" ? `Error${evt.node ? " at node #" + evt.node : ""}: ${evt.msg}` :
      evt.kind === "PHASE" ? "Phase complete" :
      evt.kind === "BEFORE" ? `Before peephole #${evt.peep} at node #${evt.node}` :
      evt.kind === "RETURN" ? `Return #${evt.repl} for #${evt.node}` :
      !evt.repl ? `Removed #${evt.node}` : evt.repl === evt.node ? `Updated #${evt.node}` :
      `Replaced #${evt.node} with #${evt.repl}`;
    label = `${evt.phase} · ${text}${evt.up ? " (inside peephole #" + evt.up + ")" : ""}`;
  }
  if (peep) label += ` · ${peep.stage === 1 ? "Gathered — next: rewrite" : "Rewritten — next: release"} (between frames ${peep.from+1} and ${peep.to+1})`;
  document.getElementById("event").textContent = label;
  document.getElementById("event").classList.toggle("error", evt?.kind === "ERROR");
}

function reportError(error) {
  cancelPeep();
  failure = "Viewer error: " + (error && error.message || error || "Graph rendering failed");
  rendering = false;
  wanted = -1;
  busy = "";
  updateUI();
  console.error(error);
}

// Surface startup failures instead of leaving a permanent "Connecting..." label.
window.addEventListener("error", event => reportError(event.error || event.message));
window.addEventListener("unhandledrejection", event => reportError(event.reason));

function get_program() {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    connection = "Compiler is not connected. Start make view and use its new tab.";
    updateUI();
    return;
  }
  if (compiling || rendering) return;
  cancelPeep();
  generation++;
  frames = [];
  current = -1;
  wanted = -1;
  done = false;
  folded.clear();
  compiling = true;
  failure = "";
  document.getElementById("detail").hidden = true;
  updateUI();
  socket.send(program.value);
}

async function render(index) {
  if (!rendererReady || index < 0 || index >= frames.length) return;
  index = stable(index);
  cancelPeep();
  wanted = index;
  if (rendering) { updateUI(); return; }
  const frameGeneration = generation;
  rendering = true;
  try {
    if (!layout) layout = new GraphLayout();
    while (wanted >= 0 && generation === frameGeneration) {
      index = wanted;
      const frame = frames[index];
      const key = foldKey();
      busy = "Laying out frame " + (index + 1) + "...";
      updateUI();
      const scene = await sceneFor(frame);
      if (generation !== frameGeneration) break;
      // Scrubbing can change the destination while ELK works. Draw only the
      // latest request, without computing all the intervening frames.
      if (wanted !== index || foldKey() !== key) continue;
      renderer.show(frame.snap, scene, frame.evt);
      if (frame.evt.kind === "ERROR" && frame.evt.node) renderer.center(frame.evt.node, true);
      cursor(frame);
      current = index;
      wanted = -1;
    }
    rendering = false;
    busy = "";
    updateUI();
  } catch (error) {
    reportError(error);
  }
}

async function sceneFor(frame, folds = folded) {
  if (!layout) layout = new GraphLayout();
  // Reveal the diagnostic even inside a folded function/loop. Keep the user's
  // fold choices for earlier frames.
  if (frame.evt.kind === "ERROR" && frame.evt.node && folds.size) {
    folds = new Set(folds);
    const view = GraphGroups.view(frame.snap, new Set());
    for (const id of folds)
      if (view.groups.get(id)?.members.has(frame.evt.node)) folds.delete(id);
  }
  const key = [...folds].sort((a,b) => a-b).join(",");
  let scene = key ? (frame.foldKey === key ? frame.foldLayout : null) : frame.layout;
  if (!scene) {
    scene = await layout.run(frame.snap, frame.evt, new Set(folds));
    // Ordinary layout plus only the most recent folded variant.
    if (key) { frame.foldKey = key; frame.foldLayout = scene; }
    else frame.layout = scene;
  }
  return scene;
}

function cursor(frame) {
  if (frame.pos < 0) return;
  // Keep the last visible parsed character selected at whitespace/EOF.
  let pos = Math.min(frame.pos, program.value.length);
  while (pos > 0 && (!program.value[pos] || /\s/.test(program.value[pos]))) pos--;
  program.setSelectionRange(pos, pos + 1);
  // Keep navigation focus: moving the source cursor must not turn the next
  // playback arrow into a text-editing key.
}

function cancelPeep() {
  motion++;
  stepping = false;
  renderer?.cancelMove();
  if (peep) {
    renderer.auto = peep.auto;
    renderer.svg.call(renderer.zoom.transform, peep.view);
    peep = null;
  }
}

function releaseView(p, scene) {
  if (p.auto) return renderer.view(scene);
  // Keep a nearby surviving node at the same screen position after relayout.
  const [x,y] = p.view.invert([renderer.host.clientWidth/2, renderer.host.clientHeight/2]);
  const nodes = new Map(scene.nodes.map(n => [n.id,n]));
  let anchor, dist = Infinity;
  for (const n of p.entry.nodes) if (nodes.has(n.id)) {
    const d = Math.hypot(n.x+n.width/2-x, n.y+n.height/2-y);
    if (d < dist) { anchor = n; dist = d; }
  }
  if (!anchor) return p.view;
  const n = nodes.get(anchor.id);
  return p.view.translate(anchor.x+anchor.width/2-n.x-n.width/2,
    anchor.y+anchor.height/2-n.y-n.height/2);
}

async function step(dir) {
  if (!rendererReady || rendering || stepping) return;
  const ticket = ++motion;
  try {
    if (!peep) {
      const dest = nextFrame(current,dir);
      if (current < 0 || dest < 0 || dest >= frames.length) return;
      const from = Math.min(current,dest), to = Math.max(current,dest);
      const evt = editEvent(from,to);
      // Pair the enclosing attempt's before/result, including any recursive
      // construction of replacement nodes. Never expose those nodes early.
      const diff = ["RETURN", "APPLY"].includes(evt?.kind) && GraphPeep.diff(frames[from].snap, frames[to].snap, evt);
      if (!diff?.structural) return render(dest);
      stepping = true;
      busy = "Gathering peephole neighborhood...";
      const view = d3.zoomTransform(renderer.svg.node()), auto = renderer.auto, entry = renderer.scene;
      updateUI();
      const local = await GraphPeep.make(frames[from], frames[to], diff, layout, folded, sceneFor, evt);
      if (ticket !== motion) return;
      peep = {...local, from, to, evt, stage: dir > 0 ? 0 : 3, view, auto, entry};
    }
    stepping = true;
    const p = peep, stage = p.stage+dir;
    const index = stage <= 1 ? p.from : p.to, frame = frames[index];
    busy = stage === 1 ? "Gathering..." : stage === 2 ? "Rewriting..." : "Returning to graph...";
    updateUI();
    const scene = stage === 0 || stage === 3 ? await sceneFor(frame) : p.scenes[stage-1];
    if (ticket !== motion) return;
    const view = scene.peep ? renderer.view(p.area) : releaseView(p, scene);
    p.stage = stage;
    current = index;
    cursor(frame);
    updateUI();
    await renderer.move(frame.snap, scene, p.evt, view);
    if (ticket !== motion) return;
    if (!scene.peep) {
      renderer.auto = p.auto;
      renderer.evt = frame.evt;
      renderer.mark();
      peep = null;
    }
    stepping = false;
    busy = "";
    updateUI();
  } catch (error) {
    if (ticket === motion) reportError(error);
  }
}

function doFirst() { render(0); }
function toggleFold(id) {
  if (peep || stepping) return;
  if (folded.has(id)) folded.delete(id); else folded.add(id);
  render(wanted >= 0 ? wanted : current);
}
function doLast() { render(frames.length - 1); }
function doNext() { return step(1); }
function doPrev() { return step(-1); }
function jump() {
  const n = frameNo.valueAsNumber;
  const index = stable(Number.isFinite(n) ? Math.max(0, Math.min(frames.length - 1, Math.trunc(n) - 1)) : current);
  frameNo.value = String(index + 1);
  render(index);
}
function doExit() {
  if (socket && socket.readyState === WebSocket.OPEN) socket.send("null");
}

program.value = "return 0;";
program.addEventListener("keydown", event => {
  if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    get_program();
  }
});
document.getElementById("compile").addEventListener("click", get_program);
document.getElementById("unfold").addEventListener("click", () => {
  folded.clear(); render(wanted >= 0 ? wanted : current);
});
frameNo.addEventListener("change", jump);
frameNo.addEventListener("keydown", event => {
  if (event.key === "Enter") { event.preventDefault(); jump(); }
});
scrub.addEventListener("input", () => render(scrub.valueAsNumber - 1));
scrub.addEventListener("keydown", event => {
  const dir = {ArrowLeft: -1, ArrowDown: -1, ArrowRight: 1, ArrowUp: 1}[event.key];
  if (!dir) return;
  event.preventDefault();
  // A native one-frame increment could land inside the same enclosing peep
  // and snap back forever. Slider keys skip directly to the next stable stop.
  const index = nextFrame(wanted >= 0 ? wanted : current, dir);
  if (index >= 0) render(index);
});
document.getElementById("fit").addEventListener("click", () => {
  if (peep) {
    renderer.svg.interrupt("peep-view");
    renderer.svg.call(renderer.zoom.transform, renderer.view(peep.area));
    return;
  }
  renderer.auto = true;
  renderer.fit();
});
document.getElementById("assocs").addEventListener("change", event => renderer.assocs(event.target.checked));
document.getElementById("near").addEventListener("change", () => renderer.mark());
document.getElementById("save").addEventListener("click", () => renderer.save(frames[current].snap.step));
document.addEventListener("keydown", event => {
  if (!event.defaultPrevented && !event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey &&
      !event.target.closest("input, textarea, select") && !event.target.isContentEditable &&
      (event.key === "ArrowLeft" || event.key === "ArrowRight")) {
    event.preventDefault();
    document.getElementById(event.key === "ArrowLeft" ? "doPrev" : "doNext").click();
  }
  if (event.key === "Escape") {
    if (peep || stepping) render(current);
    renderer.clearJump(); renderer.select(0);
  }
});

try {
  renderer = new GraphView(document.getElementById("elk"));
  renderer.onFold = toggleFold;
  rendererReady = true;
  socket = new WebSocket("ws://" + (location.hostname || "127.0.0.1") + ":12345");
  socket.onopen = () => {
    connection = "Connected";
    updateUI();
  };
  socket.onmessage = event => {
    const message = event.data;
    if (message === "!") {
      get_program();
    } else if (message === "#") {
      done = true;
      compiling = false;
      updateUI();
    } else if (message.startsWith("{")) {
      let frame;
      try {
        frame = JSON.parse(message);
        if (frame.error) {
          failure = "Compile error: " + frame.error;
          updateUI();
          return;
        }
        if (frame.snap.ver !== 1) throw new Error("Unsupported graph version: " + frame.snap.ver);
      } catch (error) {
        reportError(error);
        return;
      }
      frames.push(frame);
      socket.send("+");
      updateUI();
      if (frames.length === 1) render(0);
    } else {
      reportError("Unexpected compiler response: " + message);
    }
  };
  socket.onclose = () => {
    compiling = false;
    connection = "Compiler disconnected. Restart make view and use its new tab.";
    updateUI();
  };
  socket.onerror = () => {
    connection = "Cannot connect to the compiler. Check the make view terminal.";
    updateUI();
  };

} catch (error) {
  reportError(error);
}
updateUI();
