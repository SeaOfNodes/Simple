# 第23章: メソッドと型の再検討

[English](README.md) | 日本語

この文書は [原文](README.md) の日本語訳です。

[前の章: 第22章](../chapter22/README.ja.md) |
[次の章: 第24章](../chapter24/README.ja.md)

この章では *メソッド* を追加します。構造体内で定義し、隠れた `self` 引数を取り、構造体のフィールドへアクセスできる関数です。文字列のようなクラスの `indexOf` メソッドを示します。

```java
// A String-like class, with methods
struct String {
    u8[~] buf; // Buffer of read-only characters

    // Return the index of the first character 'c' or -1
    val indexOf = { u8 c ->           // Hidden 'self' argument
        for( int i=0; i<buf#; i++ )   // Direct access to `buf` field
            if( buf[i]==c )
                return i;
        return -1;                    // 'c' not in 'self'
    };
};
```

この章では型も再検討し、大きく変更します。

- 型は *循環* するようになります。型は間接的に自身を指せます。
- 型は引き続き [*インターン*](https://en.wikipedia.org/wiki/Interning_(computer_science)) または [*ハッシュコンシング*](https://en.wikipedia.org/wiki/Hash_consing) されます。
- 型オブジェクトはオブジェクトプールで管理します。
- 新しく作った型はインターン表を検索し、見つかれば元のものを使い、新しい型をプールへ返します。
- インターン表のヒット率は99.9%ほどです。作られる型のほとんどは、すでに作られたことがあるものです。この点は興味深い性能向上につながります。最適化コンパイラの作業の多くは型の操作なので、型の数を小さく保ち、高速な階層のキャッシュに収めることには大きな見返りがあります。
- インターン処理の多くは、何らかの「訪問ビット」で循環を扱う必要があります。簡単な方法の1つは、各型に *一意な ID*（UID）を追加することです。小さな密な整数をビット集合で使い、同じ型を複数回訪問しないようにします。UID が小さければビット集合も小さくなり、これも高速なキャッシュ階層に収まります。
- 結果として、型は循環し（これが目標です）、**高速** になり、実装は複雑になります。型の **使い方** は同じです。作成、MEET、読み取り専用化、表示などでは同じ Type API を使い、実装の詳細だけが変わります。

第11章の遅延メモリモデルは、新しい型システムでも続きます。BulkMemPhi/MemPhi が最適化中にエイリアスを発見します。各 New はインスタンスフィールドだけを覆い、クラス全体の（`_one`）フィールドは部分メモリから除外します。フィールドオフセットは配置まで記号的なままであり、Load は正確なエイリアスの内容を問い合わせながら、循環するフィールド型と深い final 情報を保持します。

[linear](https://github.com/SeaOfNodes/Simple/tree/linear) ブランチ上の直線的な Git リビジョン履歴で [この章](https://github.com/SeaOfNodes/Simple/tree/linear-chapter23) を読み、前の章と [比較](https://github.com/SeaOfNodes/Simple/compare/linear-chapter22...linear-chapter23) することもできます。

この章の[完全な言語文法](23-grammar.ja.md)はこちらです。

## なぜ循環する型なのか

循環する型は代替手法より精密な解析を可能にし、より多くのプログラムや、よりよい最適化、あるいは両方を許します。Simple では型を型検査にも使うので、より精密な型により、より多くの正しいプログラムを許容します。

**なぜ今、循環する型なのか**。構造体を引数に取る関数定義を構造体内で許すとすぐ、循環が現れるからです。つまり *self* 引数を取る *メソッド* です。

上の `String.indexOf` の例を見ましょう。ここでは `indexOf` メソッドを持つ `String` を定義します。隠れた文字列引数 `self` が渡され、検索されます。`struct String` の型は何でしょうか。

`struct String { u8[~] buf; { !String !self, u8 c -> int } indexOf; }`

これは `String` という名前の型で、フィールド `u8[~] buf` と、final で代入済みの定数フィールド `indexOf` を持ちます。後者自身の型は `{ !String !self, u8 c -> int }` です。つまり `String` の型は、`indexOf` の型の内側に自分自身への参照を持ちます。`String` の型は *循環* しています。

## 循環の少ない型

循環する型の問題は古くからあり、対処のための実績ある方法がいくつもあります。簡単なものの1つは、型の *定義* と、それとは別の型の *参照* を持つ方法です。参照は何らかの検索で型を参照します。たとえば型名とパーサーのシンボル表を使う、ハッシュ表検索です。

このモデルでは循環を実質的に回避します。型の循環内の「後退辺」は、すべて参照辺、場合によってはハッシュ表検索で実現します。

では、なぜこの方法を採用しないのでしょうか。

すべての型参照が同じ型定義へ戻るためです。型情報を特化しても失われます。*すべての* 参照が同じ定義へ戻るため、*すべての* 経路の MEET を取ることになるのです。

Java の `Object`、または C の `void*` をデータとして持つ連結リストの例を示します。

```java
struct List {
  !List !next;
  Object payload;  // equivalently for C: void*
}
// Then walk a collection of ints and build a List:
List nums = null;
for( int x : ary_ints )
    nums = new List{next=nums; payload=x; }

// Again for strings:
List strs = null;
for( String x : ary_strs )
    strs = new List{next=strs; payload=x; }
```

`nums` の型は何でしょうか。`*List`、つまり次の型です。

```
struct List { List *next; Object payload }
```

`Object` を `int` や `String` に精密化できません。すべての List オブジェクトのデータ型は `Object` で、`Object` MEET `int` は `Object` に戻るためです。

真に循環する型を許せば、このような場面で `*List<int> { *List<int> next, int payload }` という精密な型を発見、あるいは推論できます。基本的に最適化器内で *ジェネリクス* を構築するのです。言語のジェネリクスや型変数そのものではありませんが、それにかなり近づきます。

## 循環する型の扱い

この章で Simple は循環する型を直接扱います。子の型が元の型へ戻る `Type` オブジェクトを持つため、型に対して [*再帰下降*](https://en.wikipedia.org/wiki/Recursive_descent_parser) がそのままでは使えなくなります。

これまでは型構築に再帰下降を使ってきました。子の型を先に作ってインターンし、インターン済みの子で次の階層を構築します。各階層がインターンされ、再帰下降がインターン済みの型で止まるので、共有する型での重複作業を避けられます。このため現在の型に対する演算は、旧型と新型の *差分* に対して線形となり、増分的で高速です。

新たな循環する型では、循環全体を見るまでインターンできない問題に直面します。またハッシュ表は等値検査を必要としますが、循環を等値比較するのに通常の再帰下降は使えません。

グラフの循環を扱う標準的な方法は、「以前ここに来た」ことを示す *訪問済み* の概念です。一意な型 ID をキーにするハッシュ表を使います。「訪問済み」は、一意な ID が表のキーにあるという意味です。各 Type は大域カウンタから一意な ID を得ます。型をインターンするので、必要な一意 ID 数は一意な型の数と、循環を構築・インターンするためのわずかな予備だけです。過去の経験では通常数千程度が最大なので、小さな整数で十分です。

もう1つ、既存の基盤をできる限り残したいので、既存の再帰下降による巡回を少しだけ修正してすべて保持します。

すべての型循環には `TypeStruct` が存在します。ただし関数が *自身* を引数に取る純粋な関数型の循環は例外です。それは奇妙なので、どう扱うかはそれほど気にしません。各 `TypeStruct` 生成器の中核で大域 `VISIT` ハッシュ表に要素を設定し、循環型作成の開始を示します。そしてその要素を検査し、循環を止めます。

例を見ましょう。

異なるデータ型の循環を持つかもしれない連結リストを使います。ただし、ここでは文字列だけです。連結リストは、簡単な代替と比べてひどく非効率なので、ほぼどんな仕事でも間違った構造であることが多いのですが、教材用のデータ構造としては優れています。

```java
struct List {
    !List? !next; // Next pointer or null
    str !name;   // Payload
};
```

この構造体の型は宣言に一致します。

`struct List { List*? next, str !name };`

これで文字列リストを構築できます。

```java
var list = new List{ name="Hello"; };
list = new List { list, name="World"; };
```

### 循環する型の読み取り専用／final の検査

どこかで読み取り専用の不変な版を作りたいとします。

```java
val hello = list; // Make a read-only version
print(hello);     // Pass along the read-only version
```

`hello` の型は何でしょうか。`List` の読み取り専用型です。しかし `List` は循環する型です。`Type.makeRO` を見ましょう。

> 訳注：原文では上の `val hello = list` を読み取り専用版の作成と説明しています。一方、第17b章では `val` は束縛を固定し、初期化式のアクセス権限を保持すると説明しています。この訳では、原文の例と説明を保持しています。

```java
public final Type makeRO() {
    if( isFinal() ) return this; // First check if already read-only
    return recurOpen()._makeRO().recurClose();
}
```

そして `isFinal()`:

```java
// Are all reachable struct Fields are final?
public final boolean isFinal() { return recurClose(recurOpen()._isFinal()); }
boolean _isFinal() { assert _type < TCYCLIC; return true; }
```

`recurOpen()` を呼んで再帰走査を開始／開き、`_isFinal()` を呼びながら再帰し、走査を終了／閉じて、真偽値を返します。`_isFinal()` は Java の final ではなく、Type のサブクラスでオーバーライドします。大半のサブクラスは単に問題を転送します。

`@Override boolean _isFinal() { return _obj._isFinal(); }`

そして `TypeStruct` に到達します。

```java
@Override boolean _isFinal() {
    if( _open ) return false;     // May have more non-final fields
    if( VISIT.containsKey(_uid) ) // Test: been here before?
        return true;              // Cycles assume final
    VISIT.put(_uid,this);         // Set: don't do this again
    for( Field fld : _fields )
        if( !fld._isFinal() )
            return false;
    return true;
}
```

ここには循環処理の共通パターン、**test-and-set** があります。まず確定できる答えを調べます。開いた構造体は部分的にしか定義されず、未解析の non-final フィールドが現れる可能性があるので、決して読み取り専用ではありません。次に以前訪問したかを **検査** し、そうなら再帰を止めて何らかの答えを返します。`isFinal` では、循環のほかのものもすべて final なら「final である」を返します。

まだこの型を調べていなければ、ここを訪れたことを示す訪問ビットを **設定** し、通常の再帰下降でフィールドに同じ質問をします。

この例では、`VISIT` に番兵を置いて再帰を開き、`struct List` に `makeRO` を呼びます。それが `_isFinal` を呼び、`_open`（開いていない）、`VISIT`（未訪問）を確認し、UID を `VISIT` に設定してフィールドを走査します。フィールドの検査は次のとおりです。

`@Override boolean _isFinal() { return _final && _t._isFinal(); }`

最初のフィールド `List*? !next` は non-final なので、すぐ `false` を返します。これで `List` の `_isFinal` が止まり、`isFinal()` 全体が `false` を返して再帰を終了／閉じます。`VISIT` もクリアします。

### 循環する読み取り専用／final 型の作成

構築してからインターンする手順を明確にするため、すべての型に一意 ID を付けましょう。一意な型の後ろに UID の例を付けた `List` 型を示します。フィールドも型なので UID を持ちます。

```
struct List#2 {       // struct List has UID#2
  List#2*?#3 !next#4, // Field  next has UID#3, type ptr-or-null#3 to List#2
  str#1 !name#5       // Field  name has UID#5, type str#1
};
```

同じ UID が繰り返されることから、`List#2` が2回現れています。List が循環しているためです。

今度は実際に循環する型を作る必要があり、`makeRO()` は次の行に進みます。

`return recurOpen()._makeRO().recurClose();`.

再び `recurOpen` が再帰走査を開始／開き、それから子の Type クラスそれぞれでオーバーライドする `_makeRO()` を呼びます。`TypeStruct` 版を見ます。

```java
// Make a read-only version
@Override TypeStruct _makeRO() {
    // Check for already visited
    TypeStruct ts = (TypeStruct)VISIT.get(_name);
    if( ts!=null ) return ts;   // Already visited
    ts = recurPre(_name,_open); // Make a new type with blank fields
    Field[] flds = ts._fields;
    for( Field fld : flds ) fld._final = true;

    // Now start the recursion
    for( int i=0; i<flds.length; i++ )
        flds[i].setType(_fields[i]._t._makeRO());

    return ts;
}
```

最初は **test-and-set** です。**検査** は型名に基づきます。`VISIT` から `List` TypeStruct の取得を試し、成功したら再帰せず早期に返します。つまり読み取り専用の型循環内では `List` オブジェクトは1つだけです。「整数の List」や「List の List の……」を含む複数の入れ子循環の絡み合いも考えられますが、ここでは簡単さのため、`List` のインスタンスを1つだけに近似します。

この例で初めて `List` を訪問すると `VISIT` に見つかりません。次の `ts = recurPre(_name,_open)` は共通の再帰前処理です。新しい `TypeStruct` を作り、`VISIT` 表に **設定** します。新しいものは、すべてのフィールドを備え、まだその型が接続されていない、空の `List` 版です。すぐにフィールドを final にしますが、型はまだありません。

> 訳注：原文の「all !fields !attached」は、続く説明と図に合わせて、フィールドの型が未設定である状態として訳しています。

```
struct List#12 {
  ____ next#14,
  ____ name#15
}
```

最後にすべてのフィールド型で `_makeRO` を再帰呼び出しし、設定します。`TypeMemPtr` に `_fields[i]._t._makeRO()` を呼ぶと、再帰する `List` の `_makeRO()` を呼んだ後に、新しい TMP を作ります。

この再帰は `TypeStruct._makeRO()` へ戻り、同じ型名で `VISIT` 表にヒットします。最初のフィールド設定を巻き戻すと、次になります。

```
struct List#12 {
  List#12 *? #13 next#14, // Recursive next field makes a cycle
  ____  name#15
}
```

すべての型が新しい UID になっていることに注目してください。まだインターンできないので、以前の型にはできません。2番目のフィールド `str name` はすでに存在するため、次になります。

```
struct List#12 {
  List#12 *? #13 next#14, // Recursive next field makes a cycle
  str#2 name#15           // Reuse the interned type str#2
}
```

ここで再帰を巻き戻し、複雑な `Type.recurClose()` に入ります。いくつかの例でコードを1ステップずつ実行してみるとよいでしょう。`TypeTest.testList` がこれらの型を構築します。高水準での概要は次のとおりです。

管理を容易にするため、訪問したすべての型を `VISIT` からコピーします。すでにインターン済みのものを発見したら後続の処理から除去しますが、HashMap 内にない方が簡単です。

**走査 #1:** 循環に属さない既存のインターン済み部分を探し、置き換えます。新しい型循環から古いインターン済み型へポインタが出ているのに、そのコピーを作ってしまっている場合があります。この段階の後も新しい循環は残りますが、出力辺は可能なら以前の型を指します。置き換えた型は、遅延した `FREES` リストへ入ります。

**走査 #2:** すべての型は *双対* を事前計算して直接使えるようにしています。ここで再帰的な循環する双対を作ります。

**走査 #3:** 新しい型を登録します。新しくインターンされた型への共通ポインタをここで発見する *可能性がある* ので、進みながら再び検査・置換し、使わない型を再度遅延 `FREES` リストへ入れます。この段階はハッシュ表検索を必要とし、それには循環ハッシュと循環 equals が必要です。

**走査 #4:** 遅延解放するすべての型をここで解放し、将来の型生成で再利用します。

最終的に、新しい `List` の循環型は完全にインターンされます。読み書き可能版 `List#2` は同じ読み書き可能版 `List#2` を指し、読み取り専用版 `List#12` は同じ読み取り専用版 `List#12` を指します。

### 循環する HashCode と Equals

循環する `hashCode` と `equals` はさらに説明が必要です。2つの循環を等価比較するとき、比較の開始位置に関係なく等価であってほしいのです。つまり循環 `A<->B` は循環 `B<->A` と等しいべきです。両方のハッシュコードは同じでなければならず、順序で変化するハッシュは使えません。

たとえば A の静的ハッシュ値が1で、さらに B のハッシュから再帰的な成分があり、逆に B の静的値が3で A からの再帰成分があるとします。`parent.hash*7 + child.hash` で計算すると、A からは `A.hash * 7 + B.hash`、つまり `1 * 7 + 3 == 10` です。後で B から始めると `B.hash * 7 + A.hash`、つまり `3 * 7 + 1 == 22` になります。ハッシュが異なるため、ハッシュ表が等価性を見逃す可能性があります。また循環で単純に再帰下降すれば、スタックオーバーフローまで再帰してクラッシュします。

`TypeStruct.hash()` が再帰しないようにして解決します。そのためハッシュ関数はやや弱く、フィールド名とエイリアスだけに依存します。

*循環 equals* は `VISIT` 表が空でないことにより発動し、`VISIT` と関心を分離するため、別の訪問表 `CEQUALS` を使います。循環 equals 検査は通常の `eq` と同様、構成部分を再帰下降し、最初に確定的な静的検査を行い、再帰前にはおなじみの **test-and-set** を使います。

同じ2型を再び循環 equals で比較したら、ほかがすべて等しい限り、循環についても等しいと仮定します。

## メソッド、final フィールド、クラス

メソッドは、構造体内で宣言され、暗黙の `self` 引数を取る、単なる関数値（final フィールド）です。

元の構造体定義で宣言した final フィールドは *値* であり、変化できません。すべてのインスタンスで同じです。構造体のメモリ占有部分から、final フィールドの値の集まりである *クラス* へ移動します。クラスは単なる名前空間で具体的な実装を持たず、実体化されません。

```java
struct vecInt {
    u32 !len;   // Actual number of elements
    int[] !buf; // Array of integers.

    // Since "add" is declared with `val` in the original struct definition,
    // it is a final field and is moved out of here to the class.

    // Method: Internal: copy buf to a new size
    val _grow = { int sz ->
        var buf2 = new int[sz];
        for( int i=0; i<len; i++ )
            buf2[i] = buf[i];
        buf = buf2;
    };

    // Method: Add an element, growing the backing array when needed.
    val add = { int e ->
        if( len >= buf# ) _grow(buf#*2);
        buf[len++] = e;
        return self;
    };


};

val primes = new vecInt{buf = new int[4];}.add(2).add(3).add(5).add(7).add(11);
```

ここで `val add` フィールドは、定数関数値である *値* フィールドです。基本的な `vecInt` オブジェクトから `vecInt` の *クラス* へ移り、オブジェクト内で記憶領域を占めません。`_grow` も同様です。そのため `vecInt` インスタンスのサイズは、`u32` の長さと配列ポインタに、配置調整のパディングを加えたものです。配列ヘッダと要素は別の割り当てを占めます。拡張後の古いバッファの回収は、この例の範囲外です。`return primes.len*100+primes.buf[4];` を追加すると、RISC-V と ARM の両方で511になります。

## RegAlloc の改良: 多数の使用のグループ化

第22章では色の優先度と安いスピルの順序を改善しました。この章では、使用が互換性のないレジスタを要求する、よく使う値を分割する方法を追加します。たとえば複数のシフトが同じカウントレジスタを必要とし、別の使用が異なるレジスタを必要とする場合です。互換グループごとに1コピーを共有すると、使用ごとに別のコピーを挿入するのを避けられます。

割り当て器は、空のレジスタマスクと、2つより多い固定レジスタ使用を持つ、単一定義の区間にこれを適用します。重なる使用マスクの共通部分を取り、グループ化します。グループを狭めても以前の使用と互換なので、1走査で十分です。交わらないマスクは別グループにします。入力を接続し直す前に異なる使用側のスナップショットを取り、スケジューリング専用辺は無視し、値が複数引数を供給しても呼び出しを1回だけ数えます。

複数の呼び出しで使う値は、レジスタ破壊が共有コピーの利点を打ち消しがちなので、グループ化しません。すべての使用のループ深さが同じ場合に既存コピーを分割することも含め、通常のループ境界分割を代替として保持します。低頻度側を先にするループ分割は第24章、面積／コストのスピル順位付けは第25章に残します。

第21章の隣接コピー転送も引き継ぎます。彩色後、次の命令に唯一の使用がある場合、オペランドマスクが許し、2アドレスの結び付きを壊さなければコピー元レジスタを読めます。以下の表は、この整理と遅延メモリの前方移植を含みます。第22章の群には、以前の監査後に追加した移動0の C 戻り値 ABI 確認2件も含むようになりました（24ではなく26項目）。

このディレクトリで `make spill-stats` を実行してください。以下の全行は **この章のコンパイラ**、シード123、各群の固定されたソース／対象の組み合わせを使います。Windows 実行は x86 SystemV/Win64 と RISC-V/ARM SystemV を合わせます。診断マシングラフは別に確認し、数には寄与しません。

| プログラム群 | コンパイル数 | 残存する移動 | ループ重み付き移動 |
|---|---:|---:|---:|
| 第20章 | 39 | 323 | 442 |
| 第21章 | 52 | 433 | 965 |
| 第22章 | 26 | 67 | 67 |
| 第23章 | 30 | 78 | 225 |
| **合計** | **147** | **901** | **1,699** |

`_spills` はレジスタ移動を含む残存 SplitNode を数え、`_spillScaled` は `8^loopDepth` で重み付けします。生成移動のコンパイラによる推定であり、実行時メモリアクセスではありません。報告器は個々のコンパイルと CPU/ABI 合計を表示し、スピル期待値や実行確認が失敗すれば、依然として失敗を報告します。

以前の監査では、隣接コピー転送前にグループ化を無効にしても、**同じ933移動／1,745重み付き移動** でした。元のグループ化コードも同じ合計です。したがってこのスイートには、グループ化によるスピル改善は見られません。縮小したマシングラフの回帰テストは、旧実装の null マスクによるクラッシュも含め、互換性と呼び出しの規則を検証します。もっともらしい割り当て器のヒューリスティックであっても、効果を主張するには、それが実際に適用される処理対象での測定が必要です。

以前の群だけでは、第22章コンパイラは838移動／1,496重み付き移動で、このコンパイラは849／1,514でした。この比較は割り当て以外の変更を含みます。特に第20章の固定した String 入力は、ここでは最適化後も残り16重み付き移動を要しますが、第22章はこのシードで除去しました。その差をグループ化に帰するのは誤解を招きます。

元の64ビット `person21` とガード付き `stringHash21` 入力は、第21章の群を維持します。改訂した String 入力とこの章で有効にした2つのエンコーディングテストは第23章の群に属します。群を固定することで、言語とテストが成長しても、以後の比較に意味が保たれます。
