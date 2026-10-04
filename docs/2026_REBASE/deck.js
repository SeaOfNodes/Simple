/* Plain scripts keep the deck usable from file:// as well as an HTTP server. */
window.REBASE = {
  "title": "A Simple Tutorial",
  "speaker": "Cliff Click",
  "youtube": "https://www.youtube.com/@compilers",
  "repo": "https://github.com/SeaOfNodes/Simple",
  "demos": [
    {
      "id": "arithmetic",
      "chapter": "chapter04",
      "title": "Local rewrites, global payoff",
      "code": "int x = arg + 0;\nint y = 1 + x + 2;\nreturn y + x + 3;\n",
      "result": "2 * arg + 6",
      "inputs": "arg = 0 → 6; arg = 5 → 16"
    },
    {
      "id": "control",
      "chapter": "chapter08",
      "title": "A value at every merge",
      "code": "int n = arg;\nif( n < 0 ) n = -n;\nint sum = 0;\nint i = 0;\nwhile( i < n ) {\n    i = i + 1;\n    sum = sum + i;\n}\nreturn sum;\n",
      "result": "Sum 1 … |arg|",
      "inputs": "arg = 0 → 0; arg = 5 → 15; arg = -5 → 15"
    },
    {
      "id": "memory",
      "chapter": "chapter17b",
      "title": "Memory is dataflow, too",
      "code": "struct Totals { int sum; int visits; };\nval t = new Totals;\nval data = new int[4];\nfor( int i = 0; i < data#; i++ )\n    data[i] = arg + i;\nfor( int i = 0; i < data#; i++ ) {\n    t.sum = t.sum + data[i];\n    t.visits++;\n}\nreturn t.sum + t.visits;\n",
      "result": "4 * arg + 10 (runtime result)",
      "inputs": "arg = 0 → 10; arg = 5 → 30"
    },
    {
      "id": "aliases",
      "chapter": "chapter17b",
      "title": "One field changes. Another does not.",
      "code": "struct Pair { int x; int y; };\nval p = new Pair { x = arg; y = 10; };\nint before = p.x;\np.y = 99;\nreturn p.x - before;\n",
      "result": "return 0;",
      "inputs": "Any arg → 0"
    },
    {
      "id": "inline",
      "chapter": "chapter18",
      "title": "Erase the call boundary",
      "code": "val add1 = { int x -> x + 1; };\nval twice = { int x -> x * 2; };\nreturn twice(1 + add1(2)) + 3;\n",
      "result": "return 11;",
      "inputs": "Any arg → 11"
    },
    {
      "id": "sccp",
      "chapter": "chapter24",
      "viewerChapter": "chapter25",
      "title": "Prove a loop-carried constant",
      "code": "int x = 1;\nfor( int i = 0; i < arg; i++ )\n    x = 2 - x;\nreturn x;\n",
      "result": "Return value is always 1",
      "inputs": "arg = 0 → 1; arg = 5 → 1"
    }
  ]
};
