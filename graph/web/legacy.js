// Only earlier chapters need Graphviz. Load it on the first bare-DOT frame.
let dotView;
async function drawDot(dot) {
  if (!dotView) {
    dotView = (async () => {
      for (const src of ["vendor/graphviz.umd.js", "vendor/d3-graphviz.js"]) {
        await new Promise((resolve, reject) => {
          const script = document.createElement("script");
          script.src = src; script.onload = resolve;
          script.onerror = () => reject(new Error("Cannot load " + src));
          document.head.append(script);
        });
      }
      return new Promise((resolve, reject) => {
        const view = d3.select("#dot").graphviz({useWorker: false, fit: true})
          .onerror(reject).on("initEnd", () => resolve(view))
          .transition(() => d3.transition("main").ease(d3.easeLinear).duration(500));
      });
    })();
  }
  const view = await dotView;
  const host = document.getElementById("dot");
  view.width(host.clientWidth).height(host.clientHeight);
  await new Promise((resolve, reject) => view.onerror(reject).renderDot(dot, resolve));
}
