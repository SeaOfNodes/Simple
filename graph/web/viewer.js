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
  busy = frame.snap && !frame.layout ? "Laying out frame " + (index + 1) + "..." : "Drawing...";
  updateUI();
  if (frame.pos >= 0) program.setSelectionRange(frame.pos, frame.pos + 1);
  try {
    document.getElementById("elk").hidden = !frame.snap;
    document.getElementById("dot").hidden = !!frame.snap;
    if (frame.snap) {
      if (!layout) layout = new GraphLayout();
      if (!frame.layout) frame.layout = await layout.run(frame.snap);
      if (generation === frameGeneration) renderer.show(frame.snap, frame.layout, frame.evt);
    } else {
      document.getElementById("detail").hidden = true;
      await drawDot(frame.dot);
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
  if (frames[current]?.snap) { renderer.auto = true; renderer.fit(); }
  else render(current);
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
    } else if (message.startsWith("{") || message.startsWith("digraph")) {
      let frame;
      try {
        if (message.startsWith("{")) {
          frame = JSON.parse(message);
          if (frame.error) {
            failure = "Compile error: " + frame.error;
            updateUI();
            return;
          }
          if (frame.snap.ver !== 1) throw new Error("Unsupported graph version: " + frame.snap.ver);
        } else {
          // Earlier chapters still send bare DOT.
          const pos = message.match(/\/\/ POS:\s*(\d+)/);
          frame = {dot: message, pos: pos ? Number(pos[1]) : -1};
        }
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

  window.addEventListener("resize", () => {
    if (current >= 0 && !frames[current].snap) render(current);
  });
} catch (error) {
  reportError(error);
}
updateUI();
