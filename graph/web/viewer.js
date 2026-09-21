// Connection and playback state are independent of renderer initialization.
const program = document.getElementById("program");
const status = document.getElementById("status");
let socket;
let renderer;
let rendererReady = false;
let rendering = false;
let frames = [];
let current = -1;
let generation = 0;
let done = false;
let compiling = false;
let connection = "Connecting to compiler...";
let failure = "";

function updateUI() {
  status.textContent = failure || (connection !== "Connected" ? connection :
    !rendererReady ? "Connected; initializing graph renderer..." :
    compiling ? "Compiling... " + frames.length + " frames received" :
    done ? frames.length + " frames ready" : "Connected");
  document.getElementById("N").textContent = current < 0 ? "0" : String(current + 1);
  document.getElementById("len").textContent = String(frames.length);
  document.getElementById("doPrev").disabled = rendering || current <= 0;
  document.getElementById("doNext").disabled =
    !rendererReady || rendering || current + 1 >= frames.length;
  document.getElementById("compile").disabled =
    !socket || socket.readyState !== WebSocket.OPEN || compiling || rendering;
}

function reportError(error) {
  failure = "Viewer error: " + (error && error.message || error || "Graph rendering failed");
  rendering = false;
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
  updateUI();
  socket.send(program.value);
}

function render(index) {
  if (!rendererReady || rendering || index < 0 || index >= frames.length) return;
  const frameGeneration = generation;
  rendering = true;
  updateUI();
  const position = frames[index].match(/\/\/ POS:\s*(\d+)/);
  if (position) program.setSelectionRange(Number(position[1]), Number(position[1]) + 1);
  try {
    renderer.renderDot(frames[index], () => {
      rendering = false;
      if (generation === frameGeneration) current = index;
      updateUI();
    });
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

try {
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
    } else if (message.startsWith("digraph")) {
      frames.push(message);
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

  // The bundled renderer contains its own WASM engine; no worker script is needed.
  const graph = document.getElementById("graph");
  renderer = d3.select("#graph").graphviz({
      useWorker: false, fit: true, width: graph.clientWidth, height: graph.clientHeight
    })
    .onerror(reportError)
    .on("initEnd", () => {
      rendererReady = true;
      updateUI();
      if (frames.length && current < 0) render(0);
    })
    .transition(() => d3.transition("main").ease(d3.easeLinear).duration(500));
  window.addEventListener("resize", () => {
    renderer.width(graph.clientWidth).height(graph.clientHeight);
    if (current >= 0) render(current);
  });
} catch (error) {
  reportError(error);
}
updateUI();
