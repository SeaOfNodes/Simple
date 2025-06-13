# 第24a章: 関数内の SCCP

[English](README.md) | 日本語

[Previous: Chapter 23](../chapter23/README.ja.md) |
[Next: Chapter 24b](../chapter24b/README.ja.md)

この章は旧第24章から分けた、単独で実行できるコンパイラです。
関数内の SCCP を第24a章で、関数間への拡張を第24b章で説明します。
最新の説明と検証方法は [英語版](README.md) を参照してください。

## 疎な条件付き定数伝播

[SCCP](https://en.wikipedia.org/wiki/Sparse_conditional_constant_propagation) は、これまで行ってきたピープホール最適化／定数畳み込みの、上から下へ進む版です。アルゴリズムの規則は文献でよく知られ、大学で一般に教えられています。[スライドの例](https://www.cse.psu.edu/~gxt29/teaching/cse522s23/slides/08monotoneFramework.pdf) があります。

- すでに束を持っています。
- すでに SSA 形式を持っています。したがってアルゴリズムは *疎* です。
- ノードから型への対応（`Node._type`）を持っています。
- すでに伝達関数（`Node.compute`）を持っています。

すべての `_type` を、⊥ ではなく ⊤ に初期化し、すべてのノードをワークリストに置きます。そこからノードを取り、伝達関数 `node.compute()` を実行します。結果が `node._type` と変われば更新し、その出力側の隣接ノードをワークリストへ入れます。依存関係の追跡とデバッグ用の検査の大部分を省いた簡略版は次のとおりです。

```java
while( (n = code._iter._work.pop()) != null ) {
    Type nval = n.compute();
    if( n._type != nval ) {
        assert n._type.isa(nval);     // Types start high and always fall
        n._type = nval;               // Update type in Node
        code._iter.addAll(n._outputs);// Neighbors might change, need to inspect
    }
}
```

単調な伝達関数と、範囲の拡大回数を制限する widening により、アルゴリズムは *不動点* で停止します。widening は範囲の精度を落とすことがあるため、束で表せる最も精密な答えとは限りません。得られる型は悲観的解析と同等以上の精度です。その後、型が改善されたノードでピープホール規則を実行できます。たとえば新しく発見した定数が、計算を ConstantNode に置き換えます。

以下は SSA の擬似コードです。`phi` はマージを意味し、Simple の構文ではありません。

```text
int x0 = 1;          // x0 = 1
while( rand ) {
    x1 = phi(x0,x2); // x1 = BOT = phi(1,BOT)
    x2 = 2 - x1;     // x2 = BOT = 2 - BOT
}
return x1
```

SCCP がなければ、この小さなプログラムは `x` の計算を畳み込めません。見れば `x` は常に `1` とわかりますが、下から上へ進む悲観的手法は、x1 が BOT なので x2 が BOT、したがって x1 が BOT と判断します。行き詰まりを解くには楽観的手法が必要です。x1 が `1` と *仮定* すれば、x2 も `1` と発見できます。値の畳み込みは、ループ停止を証明したり、その条件と副作用を除去可能にしたりするものではありません。

表はワークリストの処理順の一例を左から右に示します。変化がなくなったら停止します。

| 変数 | 初期 | x0=1 | x1=phi(1,T) | x2=2-x1 | x1=phi(1,1) |
|---|----|----|-----------|-------|-----------|
| x0 | T | 1 | 1 | 1 | 1 |
| x1 | T | T | 1 | 1 | 1 |
| x2 | T | T | T | 1 | 1 |

### 条件付き

制御フローがデータフローと同じグラフ内にあるため（これが Sea-of-Nodes の大きな要点です）、特別なことをする必要はなく、アルゴリズムはすでに *条件付き* です。ここでも例が役に立ちます。

再び SSA の擬似コードを使います。

```text
int x0 = 1;          // x0 = 1
while( rand ) {
    x1 = phi(x0,x4); // x1 = BOT = phi(1,BOT)
    if( x1 == 1 )    // Since x1 is BOT, both true and false arms are taken
        x2 = 2 - x1; // x2 = BOT = 2 - BOT
    else x3 = 99;    // x3 = 99
    x4 = phi(x2,x3); // x4 = BOT = phi(BOT,99)
}
return x1
```

次の表も処理順の一例ですが、今度はすべての経路が到達可能とは限りません。`if` の結果は Control 値の対で、経路が生きていれば `C`、そうでなければ `X` で示します。これは `x4 = phi(x2,x3)` の計算で使います。`PhiNode` は `if` の対応する制御入力 *も* 確認し、生きた経路だけをマージします。

| 変数 | 初期 | x0=1 | x1=phi(1,T) | 1 == 1? | x2=2-x1 | x3=99 | x4=phi(C/1,X/99) | x1=phi(1,1) |
|---|-----|----|-----------|---------|---------|---------|----------------|-----------|
| x0 | T | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| x1 | T | T | 1 | 1 | 1 | 1 | 1 | 1 |
| if | T | T | T | [C,X] | [C,X] | [C,X] | [C,X] | [C,X] |
| x2 | T | T | T | T | 1 | 1 | 1 | 1 |
| x3 | T | T | T | T | T | 99 | 99 | 99 |
| x4 | T | T | T | T | T | T | 1 | 1 |

この種の条件付き定数伝播は、条件が真か偽と証明できると、最適化後のプログラムから検査と分岐を除去するので、非常に役立ちます。よくあるのが null 検査です。同じプログラムで、整数の代わりに null ポインタを使います。

```text
List head = notNullList();
while( rand ) {
    if( head ) head = new List(head,payload);
    else { head = null; default_action(); }
}
```

`head` は非 null で始まるため、決して null にならず、`default_action` も起きません。ループは簡略化できます。

```text
List head = notNullList();
while( rand ) {
    head = new List(head,payload);
}
```

## 呼び出しとの境界

この章の SCCP は各関数を独立に解析します。未知の呼び出し元を表す Start
入力を残し、呼び出し辺を外します。呼び出しの戻り値には、悲観的解析で得た
型を保持します。これは宣言された戻り値型より精密な場合があります。
呼び出しによるメモリへの影響は保守的に扱います。

ループの整数範囲は Phi で拡大し、解析を収束させます。再帰呼び出しを通る
引数と戻り値の推論・拡大は次の章で導入します。

このディレクトリで最初に `make lib`、続いて `make tests` を実行してください。
リポジトリのルートでは `make tests CHAPTERS=chapter24a` を使えます。
