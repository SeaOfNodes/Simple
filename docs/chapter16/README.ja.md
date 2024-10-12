# 第16章: コンストラクタ

[English](README.md) | 日本語

[前: 第15章](../chapter15/README.ja.md) |
[次: 第17a章](../chapter17a/README.ja.md)

この文書は [原文](README.md) の日本語訳です。

# 目次

1. [コンストラクタ](#コンストラクタ)
2. [初期化コード](#初期化コード)
3. [複数の宣言](#同じ型の複数の宣言)
4. [コンストラクタを通るメモリ](#コンストラクタを通るメモリ)

この章では、フィールドのデフォルト値とコンストラクタブロックを追加します。第17a章では、これらのコンストラクタで初期化する、固定の束縛と読み取り専用のアクセスを追加します。

[linear](https://github.com/SeaOfNodes/Simple/tree/linear) ブランチの線形の Git リビジョン履歴で[この章](https://github.com/SeaOfNodes/Simple/tree/linear-chapter16)を読むことや、前章との[差分](https://github.com/SeaOfNodes/Simple/compare/linear-chapter15...linear-chapter16)を見ることもできます。

## コンストラクタ

[第12章](../chapter12/README.ja.md)の参照には、null を許容しないフィールドが常に `null` から始まるという大きな問題があります。この章ではそれを修正し、null を許容しないフィールドの初期化に*コンストラクタ*構文を要求します。

フィールドの初期化には3つの方法があります。

1. 許容するフィールドには、デフォルトのゼロ/null 初期化を使います。特別な構文は不要です。

```
int x;                    // x is initialized to 0
struct Ref { Ref? ptr; }; // Instances of Ref will have ptr initialized to null
return new Ref.ptr;       // Returns a null
```

2. 型の宣言で初期値を指定できます。それ以降の割り当てはこの値から始まります。

```
int x = 5;                // x is initialized to 5
struct Point { int x=1; int y=1; }; // Point x and y will start as 1, not 0
return new Point.x;       // Returns a 1
```

3. 割り当てで初期値を指定できます。

```
struct Point { int x=1; int y=1; }; // Point x and y will start as 1, not 0
return new Point { x=3; }.x;        // Returns a 3
```

null を許容しないフィールドは、最初に使用する前、かつ割り当ての終了前に、*必ず*初期化しなければなりません。宣言でも割り当てでも初期化できます。初期化でデフォルト値を与えることはできますが、デフォルト値から始まるわけではありません。

```
struct Person { u8[] name; };
return new Person; // ERROR: 'Person' is not fully initialized, field 'name' needs to be set in a constructor
```

## 初期化コード

パーサーは、型の宣言と割り当てのどちらにも、完全な Block 文をパースすることを許します。`while` ループや `return` を含め、任意の量のコードが合法です。

```
struct Square {
    flt side = arg;
    flt diag = arg*arg/2;
    // Newton's approximation to the square root, computed in a constructor.
    // The actual allocation will copy in this result as the initial
    // value for 'diag'.
    while( 1 ) {
        // The next-guess variable "next" is not a field in Square,
        // because it does not appear at the top level
        flt next = (side/diag + diag)/2;
        if( next == diag ) break;
        diag = next;
    }
};
return new Square;
```

別の例です。

```
struct Buffer {
    if( arg < 0 || arg > 1000000 )
      return null; // Size out of bounds
    u8[] buffer = new u8[arg];
};
return new Buffer;
```

### 同じ型の複数の宣言

この章では新たに、ほかの言語でよく見られる、同じ型の複数の宣言も許します。

```
int x,y; // Two int variables declared
struct Point { int x,y,z; }; // Three fields declared
```

## コンストラクタを通るメモリ

[第11章](../chapter11/README.ja.md)の遅延的なメモリ分割は、コンストラクタを通して続きます。パーサーは、スカラー変数と並んで1つの `$mem` 変数を追跡します。分岐とループは BulkMemPhi でその束縛を合流し、フィールドアクセスは必要に応じて精密な MemPhi を分離します。宣言型の変数レコードは ScopeNode にあります。パーサーに別個のメモリエイリアステーブルはありません。

コンストラクタは割り当て前にフィールド値を計算します。本体の読み出し、書き込み、ループは、通常のコードと同じ `$mem` 束縛を更新します。その後 New は `{ctrl, $mem, size, field values...}` を取り、`{ptr, $mem}` を生成します。入力メモリは構造体のエイリアスを含む部分 MemMerge で、出力メモリも同じエイリアスを覆います。メモリ全体の MemMerge は無関係なスライスを保持します。配列も同じ配置を使い、長さとデフォルトの要素値を初期化入力として供給します。

自身の New からの Load は、対応する初期化入力を使えます。エイリアスの内容は、初期値の型と古いオブジェクトからの流入内容を組み合わせるため、新しいオブジェクトが既存オブジェクトの事実や作用を消すことはありません。ロードが迂回するのは、別のものと証明された割り当てだけです。スケジューリングは、読み出しを後続の書き込みより前に順序付ける際、部分的な集約をたどります。

1つの割り当てメモリ結果を共有することで、Store を New に畳み込む処理はより保守的になります。既存の単独使用のチェックが、覆うすべてのエイリアスのユーザーを見るようになるためです。以前の別々のメモリ射影なら定数畳み込みできたところに、フィールドの Load が残ることがあります。Phi のくくり出しも、単純な1段階の Load 安全性チェックを維持します。
