# 第17a章: 可変性と読み取り専用ビュー

[English](README.md) | 日本語

[前: 第16章](../chapter16/README.ja.md) |
[次: 第17b章](../chapter17b/README.ja.md)

この文書は [原文](README.md) の日本語訳です。

第16章はオブジェクトにフィールドの初期値を与えます。今度は、どの束縛を再代入でき、どの参照が構築後の書き込みを許すかを区別します。フィールドや配列要素も含め、これらは独立した問いです。第17b章では、同じ規則を使って型推論と代入の短縮形を追加します。

## 2つの権限

構造体型の前の修飾子はアクセス権限を表します。`!Point` は書き込みを許し、`~Point` は深い読み取り専用ビューを与えます。変数名の直前の修飾子は、その束縛を制御します。`!p` は再代入を許し、`~p` は固定します。空白によって意味は変わりません。

| 宣言 | `p` の再代入 | `p` 経由の書き込み |
|---|---|---|
| `!Point !p` | 可 | 可 |
| `!Point ~p` | 不可 | 可 |
| `~Point !p` | 可 | 不可 |
| `~Point ~p` | 不可 | 不可 |

プリミティブ型の束縛は、デフォルトで再代入可能です。構造体参照は、デフォルトで固定の束縛と読み取り専用アクセスになります。`Point p` は `~Point ~p` を意味します。初期化子はこれらのデフォルトを変えません。局所変数とフィールドで同じ構文が使えます。

```cpp
struct Point { int x; int y; };
!Point p = new Point;   // Fixed binding, writable object
p.x = 3;                // Allowed
Point !view = p;        // Reassignable binding, read-only object view
view = new Point;       // Allowed, view is re-assignable
// view.x = 4;          // Error: view is read-only access
// p = new Point;       // Error: p is  fixed binding
int ~limit = 10;        // Explicitly fixed primitive
int count = 0;          // Mutable primitive by default
count = count + 1;
return p.x;
```

## 構築と固定フィールド

固定フィールドは、構造体の宣言または割り当てのコンストラクタブロックで初期化できます。デフォルト値のない固定プリミティブには初期化が必要で、null を許容しない参照にも（null でない）値が必要です。null 許容の参照フィールドは null から始められます。コンストラクタは特別で、普通に見えるコードの中で final フィールドを設定しますが、ほかのコードでは final フィールドを設定できません。

```cpp
struct Point { int ~x; int ~y; };  // x,y are final
Point p = new Point { x=3; y=4; }; // x,y set in constructor
// p.x = 99;                       // Error; p is final
return p.x + p.y;
```

構造体内でも束縛とアクセスの修飾子は独立しています。`!Point ~origin` は、書き込み可能なポインタを含む固定フィールドです。`origin` を置き換えることと `origin.x` を変更することは別の操作です。

## 深い読み取り専用アクセスとほかのエイリアス

読み取り専用ビューは、それを通じて到達するすべての参照から書き込み権限を取り除きます。最適化やキャストによって書き込みアクセスを復活させることはできません。オブジェクト自体は凍結されて**いません**。別の書き込み可能なエイリアスは依然として変更でき、読み取り専用ビューを通じた読み出しもその変更を観測します。

```cpp
struct Point { int x; };
!Point p = new Point;
Point view = p;
p.x = 3;
int before = view.x;
p.x = 5;       // Write to the shared object, via a mutable reference
return before * 10 + view.x; // 35
```

したがって読み取り専用アクセスは、Load を定数にしたり、そのメモリ依存を除去したりしません。背後のオブジェクトは、別の参照から依然として変更できる可能性があります。

## 配列: 各層の修飾子

新しい配列は、要素を埋められるように書き込み可能な内容から始まります。`[]` はそのアクセスを保ち、`[~]` はその配列の層で深い読み取り専用ビューを要求します。変数自体は、ほかの参照と同じくデフォルトで固定の束縛です。

| 宣言 | 意味 |
|---|---|
| `u8[] a` | 書き込み可能なバイト列への固定の束縛 |
| `u8[] !a` | 書き込み可能なバイト列への再代入可能な束縛 |
| `u8[~] a` | 読み取り専用配列への固定の束縛 |
| `Point?[] a` | 読み取り専用 Point 参照を含む書き込み可能な要素 |
| `!Point?[] a` | 書き込み可能な Point 参照を含む書き込み可能な要素 |
| `u8[~]?[] rows` | null または読み取り専用バイト配列を含む、書き込み可能な外側の要素 |
| `u8[]?[~] rows` | null 許容の内側の配列も含む、深い読み取り専用の外側のビュー |

参照配列を宣言するときは `?` が必要です。初期状態では各要素が null だからです。初期化後には、このような配列を読み取り専用にキャストできます。外側の読み取り専用ビューは、内側の配列にも書き込みを禁止します。それらの配列への書き込み可能なエイリアスが別の場所にあっても同じです。

```cpp
u8[] bytes = new u8[2];
bytes[0] = 7;
u8[~]?[] rows = new u8[~]?[1];
rows[0] = bytes;       // Outer array remains writable
// rows[0][0] = 9;     // Error: the inner view is read-only
bytes[0] = 9;          // Another alias can still write
return rows[0][0];     // 9
```

書き込み可能な配列スロットには、一致する要素の権限が必要です。たとえば `!Point?[]` を `Point?[]` に代入できません。後者が読み取り専用 Point を挿入すると、前者がそれを書き込み可能として公開してしまうためです。読み取り専用の外側ビューは、この挿入を許しません。要素に書き込み可能な Point 参照を保持させる必要があれば、`new !Point?[n]` を使います。`new Point?[n]` はデフォルトの読み取り専用参照を維持します。

## 表現

[ScopeNode](src/main/java/com/seaofnodes/simple/node/ScopeNode.java) と [Field](src/main/java/com/seaofnodes/simple/type/Field.java) は、スロットが固定かどうかを記録します。それとは独立して、[TypeMemPtr](src/main/java/com/seaofnodes/simple/type/TypeMemPtr.java) は、ポインタ経由の読み取り専用アクセスを記録します。この権限は、不完全な構造体が名前で解決されたときも付いたままで、現在わかっているフィールド数には依存しません。構造体の定義は、第12章からの不変で浅い参照を維持します。

[ReadOnlyNode](src/main/java/com/seaofnodes/simple/node/ReadOnlyNode.java) はポインタ値を変えずに書き込み権限を取り除きます。Load は、最適化が格納済みの値を転送する場合も含め、参照の結果にその権限を伝播します。Store はフィールドの固定束縛フラグと入力ポインタのアクセス権限の両方をチェックします。既存のメモリ SSA 表現は変わりません。
