// Connection and playback state are independent of renderer initialization.
const program = document.getElementById("program");
const status = document.getElementById("status");
let socket;
let renderer;
let layout;
let rendererReady = false;
let rendering = false;
let frames = [];
let current = -1;
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
  document.getElementById("N").textContent = current < 0 ? "0" : String(current + 1);
  document.getElementById("len").textContent = String(frames.length);
  document.getElementById("doPrev").disabled = rendering || current <= 0;
  document.getElementById("doNext").disabled =
    !rendererReady || rendering || current + 1 >= frames.length;
  document.getElementById("compile").disabled =
    !socket || socket.readyState !== WebSocket.OPEN || compiling || rendering;
  document.getElementById("fit").disabled = rendering || current < 0;
  document.getElementById("save").disabled = rendering || !frames[current]?.snap;
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
  done = false;
  compiling = true;
  failure = "";
  document.getElementById("detail").hidden = true;
  updateUI();
  socket.send(program.value);
}

async function render(index) {
  if (!rendererReady || rendering || index < 0 || index >= frames.length) return;
  const frameGeneration = generation;
  rendering = true;
  const frame = frames[index];
  busy = !frame.layout ? "Laying out frame " + (index + 1) + "..." : "Drawing...";
  updateUI();
  try {
    if (!layout) layout = new GraphLayout();
    if (!frame.layout) frame.layout = await layout.run(frame.snap, frame.evt);
    if (generation === frameGeneration) {
      renderer.show(frame.snap, frame.layout, frame.evt);
      if (frame.pos >= 0) {
        // A newline or EOF has no visible character to highlight. Show the last
        // parsed character there, rather than losing the position indicator.
        let pos = Math.min(frame.pos, program.value.length);
        while (pos > 0 && (!program.value[pos] || /\s/.test(program.value[pos]))) pos--;
        program.setSelectionRange(pos, pos + 1);
        // Focus after selecting, so the browser brings the new position into view.
        program.focus({preventScroll: true});
      }
    }
    rendering = false;
    busy = "";
    if (generation === frameGeneration) current = index;
    updateUI();
  } catch (error) {
    reportError(error);
  }
}

function doNext() { render(current + 1); }
function doPrev() { render(current - 1); }
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
document.getElementById("fit").addEventListener("click", () => {
  renderer.auto = true;
  renderer.fit();
});
document.getElementById("assocs").addEventListener("change", event => renderer.assocs(event.target.checked));
document.getElementById("near").addEventListener("change", () => renderer.mark());
document.getElementById("save").addEventListener("click", () => renderer.save(frames[current].snap.step));
document.addEventListener("keydown", event => {
  if (event.key === "Escape") renderer.select(0);
});

try {
  renderer = new GraphView(document.getElementById("elk"));
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
