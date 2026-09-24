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
  document.getElementById("doFirst").disabled = document.getElementById("doPrev").disabled =
    !rendererReady || at <= 0;
  document.getElementById("doLast").disabled = document.getElementById("doNext").disabled =
    !rendererReady || !frames.length || at + 1 >= frames.length;
  document.getElementById("compile").disabled =
    !socket || socket.readyState !== WebSocket.OPEN || compiling || rendering;
  document.getElementById("fit").disabled = rendering || current < 0;
  document.getElementById("save").disabled = rendering || !frames[current]?.snap;
  document.getElementById("unfold").disabled = !folded.size;
  document.getElementById("assocs").disabled = !frames[current]?.snap;
  document.getElementById("near").disabled = !frames[current]?.evt;
  const evt = frames[current]?.evt;
  let label = "";
  if (evt) {
    const text = evt.kind === "PHASE" ? "Phase complete" :
      evt.kind === "BEFORE" ? `Before peephole #${evt.peep} at node #${evt.node}` :
      evt.kind === "RETURN" ? `Return #${evt.repl} for #${evt.node}` :
      !evt.repl ? `Removed #${evt.node}` : evt.repl === evt.node ? `Updated #${evt.node}` :
      `Replaced #${evt.node} with #${evt.repl}`;
    label = `${evt.phase} · ${text}${evt.up ? " (inside peephole #" + evt.up + ")" : ""}`;
  }
  document.getElementById("event").textContent = label;
}

function reportError(error) {
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
      let scene = key ? (frame.foldKey === key ? frame.foldLayout : null) : frame.layout;
      busy = !scene ? "Laying out frame " + (index + 1) + "..." : "Drawing...";
      updateUI();
      if (!scene) {
        scene = await layout.run(frame.snap, frame.evt, new Set(folded));
        // Keep the ordinary layout plus only the most recent folded variant.
        if (key) { frame.foldKey = key; frame.foldLayout = scene; }
        else frame.layout = scene;
      }
      if (generation !== frameGeneration) break;
      // Scrubbing can change the destination while ELK works. Draw only the
      // latest request, without computing all the intervening frames.
      if (wanted !== index || foldKey() !== key) continue;
      renderer.show(frame.snap, scene, frame.evt);
      if (frame.pos >= 0) {
        // A newline or EOF has no visible character to highlight. Show the last
        // parsed character there, rather than losing the position indicator.
        let pos = Math.min(frame.pos, program.value.length);
        while (pos > 0 && (!program.value[pos] || /\s/.test(program.value[pos]))) pos--;
        program.setSelectionRange(pos, pos + 1);
        // Focus after selecting, so the browser brings the new position into view.
        if (document.activeElement !== frameNo && document.activeElement !== scrub)
          program.focus({preventScroll: true});
      }
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

function doFirst() { render(0); }
function toggleFold(id) {
  if (folded.has(id)) folded.delete(id); else folded.add(id);
  render(wanted >= 0 ? wanted : current);
}
function doLast() { render(frames.length - 1); }
function doNext() { render((wanted >= 0 ? wanted : current) + 1); }
function doPrev() { render((wanted >= 0 ? wanted : current) - 1); }
function jump() {
  const n = frameNo.valueAsNumber;
  const index = Number.isFinite(n) ? Math.max(0, Math.min(frames.length - 1, Math.trunc(n) - 1)) : current;
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
document.getElementById("fit").addEventListener("click", () => {
  renderer.auto = true;
  renderer.fit();
});
document.getElementById("assocs").addEventListener("change", event => renderer.assocs(event.target.checked));
document.getElementById("near").addEventListener("change", () => renderer.mark());
document.getElementById("save").addEventListener("click", () => renderer.save(frames[current].snap.step));
document.addEventListener("keydown", event => {
  if (event.key === "Escape") { renderer.clearJump(); renderer.select(0); }
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
