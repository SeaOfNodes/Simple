# 第12章: 参照

[English](README.md) | 日本語

[前: 第11章](../chapter11/README.ja.md) |
[次: 第13章](../chapter13/README.ja.md)

この文書は [原文](README.md) の日本語訳です。

第10章と第11章では、構造体に整数フィールドを追加し、そのメモリへの作用を SSA で表しました。この章では、まだ定義が現れていない構造体も含め、フィールドにほかの構造体への参照を格納できるようにします。メモリグラフを変更せずに、連結リストや互いを参照するオブジェクトを扱えます。

線形の Git 履歴で[この章](https://github.com/SeaOfNodes/Simple/tree/linear-chapter12)を読むことや、[第11章との差分](https://github.com/SeaOfNodes/Simple/compare/linear-chapter11...linear-chapter12)を見ることもできます。

## 型付きフィールドと前方参照

```java
struct Person {
    int age;
    FamilyTree? tree;
}
struct FamilyTree {
    Person? father;
    Person? mother;
}
```

パーサーの `TYPES` テーブルは名前を型に対応付けます。初期状態では `int`（およびほかのプリミティブ型。後の章でさらに登場します）が入っています。`type()` が未知の名前を使う宣言を認識すると、`_fields == null` の、名前だけの `TypeStruct` を登録します。これは構造体名だけがわかっていて、ほかの情報はない構造体です。その構造体へのポインタがあれば、参照フィールドを記述するには十分で、`?` によってポインタが null であることを許容します。

`parseStruct()` は、すべてのフィールドを収集してから完全な構造体を `TYPES` に登録します。テーブルのエントリを置き換えるのであって、古い型を変更したり、フィールドリストの途中までを順次公開したりはしません。フィールドのエイリアスは、これまでと同じく宣言が完成した時点で割り当てます。

[パーサー](../../src/main/java/com/seaofnodes/simple/Parser.java)、特に `type()`、`parseField()`、`parseStruct()` と、[文法](12-grammar.ja.md)を参照してください。

## 再帰構造体: `L0` と `L1`

```java
struct LLI { LLI? next; int i; }
```

コンパイラが実際に何を構築するかを覚えておくには、次の2つの記述が役立ちます。

```text
L0 = TypeStruct("LLI", fields = null)
L1 = TypeStruct("LLI", fields = { next: pointer-to-L0?, i: int })
TYPES["LLI"] = L1
```

`L0` は `LLI? next` フィールドをパースするときに作られます。閉じ波括弧の後で、名前テーブル内の `L0` を `L1` が置き換えます。`L1` 内の `next` フィールドは引き続き `L0` を指します。`L1` に戻るように書き換えません。

この設計は、**不変で浅い参照を、必要に応じて名前で解決する**ものです。実行時のオブジェクトはサイクルを形成しても、コンパイラの型記述は有限で非循環のままです。これは、**再帰的な型の展開を止める**意図的な決定です。すべての参照に完成した定義を再帰的に代入すると、無限に展開するか、循環する型のインターン化、等価性、ハッシュ、束の操作が必要になります（実際に、束を扱う章で登場します）。

[`TypeStruct`](../../src/main/java/com/seaofnodes/simple/type/TypeStruct.java) は、代わりに名前だけの記述で止めます。`dual()` と `glb()` はその記述をそのままにします。同じ構造体の完全な記述と名前だけの記述の meet は、名前だけの記述になります。これによってフィールドの詳細は失われますが、構造体の識別情報は失われません。`L0` をますます深い `L1`、`L2` などの列へと展開しようとするパスはありません。

## 浅い参照の解決

`head.next` のパース時、`parsePostfix()` はポインタの構造体名を取り出し、`TYPES` で現在の宣言を検索します。ポインタ自身の型にまだ `L0` が含まれていても、その宣言からフィールドの配置とエイリアスを得ます。`head.next.next.i` のような連鎖には、次のフィールドアクセスでも検索を繰り返せば十分です。

局所宣言や代入では、`parseExpressionStatement()` も、代入の互換性を確認する前に名前だけのポインタを解決します。この「深める」処理はチェックに使う一時的な型を変更するだけで、式ノードの型を書き換えたり、グラフ内の古い参照をすべて置き換えたりはしません。

```java
struct LLI { LLI? next; int i; }
LLI? head = null;
while (arg) {
    LLI x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg - 1;
}
if (!head) return 0;
LLI? next = head.next;
if (next == null) return 1;
return next.i;
```

null チェックは、生き残る制御経路でポインタの型を絞り込みます。特に、明示的な `else` がなくても、`if` の false 経路はその絞り込みを記録しなければなりません。名前の解決はフィールドの定義を与えますが、ポインタが null でないことを証明するわけではありません。

## 初期化と不完全な定義

`newStruct()` は、各フィールドをその型の `makeInit()` で初期化します。整数はゼロ、参照は null です。Load と Store は第11章と同じエイリアスとメモリ最適化を使います。したがって、初期化 Store からの Load は null に畳み込めます。

使われない前方参照は、最後まで定義する必要がありません。

```java
struct Holder { Missing? ref; }
Holder h = new Holder;
return h.ref;                  // null; no definition of Missing is needed
```

ここでは、完全な定義なしに null でない `Missing` を構築する方法はありません。`new Missing` は拒否されます。そのフィールドにアクセスするにも、null でないポインタに加えて定義が必要です。null を許容する参照型に言及するだけでは、プログラムの終わりまでに定義する義務は生じません。

この章には、初期化に関する一時的な許容事項が1つあります。

```java
struct N { N next; int i; }
N n = new N;
return n.next;                 // null, despite the non-null field declaration
```

初期化 Store は、null を許容しない参照フィールドにも null を書き込めます。通常の `n.next = null` は拒否されます。第16章ではコンストラクタを追加して初期化をチェックします。この章では、この区別を [`StoreNode`](../../src/main/java/com/seaofnodes/simple/node/StoreNode.java) に明示的に保持します。

[参照のテスト](../../src/test/java/com/seaofnodes/simple/Chapter12Test.java)は、連結リスト、相互参照、null チェック、不完全な型を検証します。`make tests` で実行でき、`make view` は同じ未スケジュールのグラフを表示します。これで第13章には、ロード/ストアの反依存も含めた大域的コード移動の導入に必要な、メモリと参照の意味論がそろいました。
