# ロードマップ

[English](ROADMAP.md) | 日本語

この文書は [ROADMAP.md](ROADMAP.md) の日本語訳です。原文の設計案と未解決の問いを、そのまま設計案として記しています。

## 分割コンパイル

未知の前方参照をどう扱うか？ H-M は使わないので、単一化も行わない。
関数以外の前方参照を認めないことにする……関数なら、定義した時点ですぐにシグネチャを提供できる。

案 A：FRefNode を削除し、すべてのバグを直す。
案 A の問題：相互再帰を使えるようにしたい。
例：`val even = { int x -> x ? odd(x-1) : true; }; val odd = { int x -> x ? even(x-1) : false; };`

案 B：型は順序どおりに定義する必要があるが、それ以外の関数には順序を要求しない。
`new T` ならエラーにできるが、`fref.field`、`fref()`、`fref op fref`、`fref[]` ではできない。

あらゆる場所で Type.BOTTOM をパーサーの要件に合わせて「引き上げる」必要がある。
FRef からフィールドをロードするには、FREF を FIELD のある TSTRUCT への TMP に引き上げる必要がある……。
実際には、FREF が正しくなると局所的に仮定するだけだ。型は任意のコードから来るので、
アクセスできず、引き上げられない。TMP は nullable かもしれない（後でエラーにすべき）。
TSTRUCT は無名にでき、無名になるだろう。これは、すべての TSTRUCT を無名にすることを意味する。

問題は、未知・遠隔・外部・前方の参照にある。

FRef を*少しでも*認めると、すべての処理が FRef 型に遭遇することを考慮しなければならない。
同じスコープの関数で認めると、上の odd/even の例が使える。
同じスコープの構造体で認めると、相互再帰的な構造体が使える。
スコープの外へ昇格させる際には外部の検索を試み、見つからなければ失敗する。

任意の使い方の例：`sys.fld`。`sys` の検索に失敗したら、次のどちらかを推測しなければならない。
前方参照で問題なく、間もなく定義されるのか……それとも今すぐ外部を検索するのか。
さらに、使い方によっては問題の発覚が遅れる。
```java
// Extern or Future `sys` being returned.
val foo { -> return sys; }

// foo() returns FRef, so Bottom, so the `.bar` has no type
// to do field lookups
val main { arg -> foo().bar }

// HERE: Parser finds `sys` OR NOT?!?!?!
val sys??? = user_stuff....;

```

行き着く先は、パーサーが一切エラーを出さず、盲目的な AST のように SSA を構築し、
不適切な型だらけのグラフを結線するだけになることだ。
必要なこと：パーサーを導く型をなくす！ 構文だけを使う！

（明らかな小さな問題：浮動小数点数へ拡張するかどうか。）
修正：たとえば整数演算を挿入する。両方の入力が isa-Int であり、isa-Float に引き上げられなくなるまでは、
戻り値の型を Int と Float の MEET とする。

メソッド呼び出しで起こり得る問題：
`UNKNOWN_STRUCT_BOT.UNKNOWN_FIELD ( ARGS ); // Insert SELF OR NOT?`
— SELF を挿入するか？
修正案：常に SELF 引数があると仮定し、静的と証明できる場合は null にする。
最適化：定数パラメータをシグネチャから削除するか？ *定数*パラメータは、
*パーサー*の時点で定数でなければならない。そうでなければ未知の呼び出し元がないことが必要になる
（したがって局所的な最適化になる）。
修正案：SELF 引数を*最初*ではなく*最後*に置くと、不要になった場合に削除しやすいか？

Load は ConFldOff を捨て、名前を保持する。
Load はスケーリング前の添字を保持し、添字入力を持つ。構造体ではこの入力は null。
Ptr Base が*確定*した後で基底の配置を行い、添字の計算を畳み込める。

- `Load(ctrl, base, null );` — フィールド名は内部に持つ。
- `Load(ctrl, base, index_math );` — フィールド名は `[]`。
- `Store(ctrl, base, null      , val);`

問題：エイリアス情報がない！

案 C：直接呼び出し `odd()` と型では FRef を認める。それ以外はすべて*引き上げ*が必要。

## 構造体と配列の統合

構造体のフィールドは名前で索引付けされ、配列の要素は数値で索引付けされる。
この2つの考え方は両立する、という発想です。
C 言語では古くからこの考え方を使っています。通常の C の構造体の後に配列を続け、
通常の配列構文で索引アクセスできます。

別の方法として、配列へのポインタをフィールドに保持すると、間接参照が1つ増える代わりに柔軟性が得られます。
特に、配列を（通常はより大きな配列に）交換できます。
これは、広く使われる汎用コンテナ `Vector` や `ArrayList` の基礎です。

```java
struct str {
    int  hash; // A cache of the strings hash code
    u32  #;    // The array length
    u8   [];   // Array of bytes
};

str X = "abc";
print(X.hash); // Read the hash field
X[0]=='a';     // Read array element 0
X#;            // Length of the array

```

ここでは、`#` というフィールドが配列の長さを保持し、`[]` というフィールドが実際の配列です。
メモリ内では、この配列がほかのフィールドの直後に続きます。
可変長配列の別の例を示します。

```java
struct vecInt {
    u32  size; // Actual number of elements
    u32  #;    // The array length or capacity
    int  [];   // Array of integers.
};
```

明らかな制約として、配列のサイズを直接変更することはできません。
先頭のフィールドと同じメモリブロックに実際に配置されているからです。
このような `vecInt` を、より大きい新しい `vecInt` に*再割り当て*することはできますが、
元の `vecInt` は解放されます。同じ `vecInt` の参照を永遠に使い続けることはできません。

名前付きフィールドはいくつあってもかまいません。

```java
struct Class {
    !str     !className;
    !str     !professor;
    int     time;
    int     credits;
    !Class   !prerequisite;
    u16     #; // Limit of 65535 students per class
    Student [];
};
```

自己参照も使えます。

```java
struct NTree {
    Some   val;
    u8     #;   // Length is 0-255
    NTree  [];  // More NTrees
};
```

構文規則は次のとおりです。

- 最後のフィールドの名前は `[]` で、どの型でもよい。
- 最後から2番目のフィールドの名前は `#` で、配列の最大長を表す符号なし整数型でなければならない。
- ` []` の前に空白が必要になるかもしれない。

### コンストラクタと配列

割り当てには配列の長さを含め、コンストラクタも含められます。
`new NTree{val=17;}[2];`

コンストラクタは省略できますが、配列の長さは省略できません。

配列を初期化するための任意の関数を使うと、一度だけ書き込む final 配列を作れます。
`new u8[buffer#,{ u32 idx -> buffer[idx].toUpperCase() }]`

### 配列の最大長の変更

範囲チェックのある小さな配列を大量に使う場合、配列の長さのために丸々4バイトを確保すると、
配列のオーバーヘッドが大きく増えることが問題になります。
Simple では最大長を小さくできます。ただし、最大長が異なる配列は別の型として扱います。

```java
struct x0 { u8  #; u8[]; }; // Array limited to 0-255 length
struct x1 { u16 #; u8[]; }; // Array limited to 0-65535 length
struct x2 { u32 #; u8[]; }; // 
struct x3 { u64 #; u8[]; }; // 

x0 x = "abc"; // 4 bytes total: [3, 'a', 'b', 'c']
x1 y = "def"; // 6 bytes total: [3,  0 , 'd', 'e', 'f', 0,]; 1 pad byte to acheieve 2-byte alignment
x = y;  // ERROR, "x1 is not x0", no auto-widening
x += y; // OK, 7 bytes total: [6, 'a', 'b', 'c', 'd', 'e', 'f']
y += x; // OK, 8 bytes total: [6,  0 , 'd', 'e', 'f', 'a', 'b', 'c']

x += "VeryLongString.... ....255_chars"; // Runtime RangeCheck

// Explicit conversion to widen string
y = "" + x;
```

### 拡張可能な配列・文字列の2つの形：間接参照を増やすかどうか

間接参照を1つ増やすと、Java の `ArrayList` や C++ の `Vector` のようになります。

増やさない場合、拡張可能な配列を伸ばす呼び出しは、新しいオブジェクトを返す可能性があります。
拡張は必要な場合だけ行います。

```java
val vec1 = new vecInt[0].add(2); // len=1, capacity=1
val vec2 = vec1.add(3); // NEW !vecInt !returned, only is deleted; len=2; capacity=2;
vec1[0]; // ERROR, vec1 has been freed by call to `vec1.add(3)`
vec2[0]; // OK
vec2[1]; // OK
vec2[2]; // Runtime error, AIOOBE
```

## 文字列の実装

既定の文字列は「str」「cstr」「ustr」などの名前を持ちます。

常に unicode8 を使い、u8 を明示的に操作します。
Unicode の「文字」は、基礎となる実装の上に関数呼び出しとして提供します。
おそらく別の文字列クラスで実装することになります。

## 既定の文字列

連結によって効率的に拡張でき、さまざまな IO/printf 関数をサポートします。
明示的に要求すれば、基礎となる配列を可変な状態で共有できます。
既定では常に、非共有時は可変、共有時は読み取り専用です。

```java
struct str {

    // Add a string to the string, possibly growing
    val add = { str str ->
        val self = this;
        for( int i=0; i<str#; i++ )
            self = self.add(str[i]);
        return self;
    };

    // Add an char to the string, possibly growing
    val add = { u8 e ->
        if( len >= # ) return copy(# ? #*2 : 1).add(e);
        [size++] = e; 
        return this;
    };
    
    // Copy 'this' to a new larger size
    val copy = { int sz2 -> 
        val v2 = new str[sz2];
        for( int i=0; i<len; i++ )
            v2[i] = [i];
        v2.len = len;
        sys.libc.free(this); // Needs a better Mem Management solution
        return v2;
    };

    val print { -> sys.libc.write(1/*stdout*/,[]/*array base as argument to C*/,len); return this; }

    u32 len; // In-use size
    u32 #;   // Max length is 4Gig, although variants can request smaller lengths
    u8  [];  // Character data
};
```

### Final な文字列

**不変**な文字列を意図しており、**安全な**文字列の基礎にも使えるかもしれません。
一度に全体を作成する必要があり、おそらく防御的コピーも必要になります。

```java
struct sstr {
    // Possible static call syntax; no references to "this"
    val make { str str -> new sstr[str#,str.at] };
    
    // Array contents are immutable.
    // Still figuring out good syntax for this.
    u8 #;
    u8 [~]; // Middlin '~'?  Makes primitive array contents *immutable* and requires constructor syntax
};

// Construction requires a function that produces the array contents.
// Here we are defining a defensive copy over `buf`.
sstr safe = new sstr[buf.len,{ int idx -> buf[idx]; }];
sstr safe = sstr.make("abc"); // Defensive copy is made
```

### 拡張可能な文字列（StringBuilder または StringBuffer）

この API は `StringBuilder` スタイルの代替としてそのまま使えることを意図しており、
使うたびに間接参照が1つ増えます。

```java
struct xstr {
    !str !_str; // The extra indirection is here
    val add = { str str -> _str = str.add2(str); };
    val write = { int fd -> _str.write(fd); };
};
```

### Unicode 文字列

この API は、通常の `str` の上で Unicode の文字を操作できるようにすることを意図しています。

```java
struct ustr {
    // Assuming idx is at the start of a character, return the character
    // as a 32b integer.  Returns junk integer if not at a character start.
    // For all ASCII strings this will amount to a simple byte load.
    // For bytes with high order bits set, this may read 1-4 characters.
    val at = { int idx -> ... };
};
// Unicode character iterator
struct ucharator { 
// Return successive unicode character codes
};
```

### 「C」文字列

ゼロ終端の文字列ですが、長さを直接取得できる必要があります。

```java
struct cstr {
    u8 #; // length including the trailing zero
    u8[]; // always ends in a zero byte; suitable for direct passing to C string functions
};
```

### 「german string」：レジスタに格納できる非常に短い文字列

メモリ内では、長さを表す1バイト（3ビット？）に続いて0〜7文字を格納し、最大8文字です。
64ビットのレジスタに収まります。

```java
struct gstr { // aka germanString, or blend string and register "streg"
    u3 #;   // Size is 0-7 bytes???
    u8 [];  // Up to 7 bytes
}
// Using inlined object (see below).  All these strings are short and are
// represented as packed integers in a 64b register.
*gstr prize1 = "gold";
*gstr prize2 = "silver";
*gstr prize3 = "bronze";

```

### Main は `val main = { str[] args -> ...}` になる

## 構造体のインライン化

構造体は通常、参照として扱いますが、「内容（contents of）」と読む `*` 演算子を使って
値として扱う形へ変換できます（この名前は、たとえば `inline` に変えるかもしれません）。

これは名前空間の変更だけです。内部の構造体の寿命を完全に消費しない限り、そのアドレスは取得できません。
「外部への流出」は認めません。

```java
// 2 64-bit words, no other overheads
struct Complex { f64 x,y; val len = { ->Math.sqrt(x*x+y*y); }; };

// Current Simple rules: always by-reference
struct ByRef {
    !Complex !c;          // 4-byte pointer to a Complex
};
print(new ByRef.c.y);   // Lookup requires 1 extra memory load from ref to c

// New behavior: by value (inlining a struct)
// The '*' syntax indicator can be something else, e.g. a keyword "inline"
// I am pronouncing '*' as "contents of"
struct ByValue {
    *!Complex !c;         // c is inlined, full 16 bytes into ByValue
};
print(new ByValue.c.y); // x,y inlined into ByValue, no extra memory load

// Mixing Refs and Struct Values.  Basically, a ref can be converted
// to a value by taking the "contents of" the ref, and values cannot
// be *converted* to a ref, although they can be copied over a ref.
ref. c =  val.c; // Error, mixing refs and values
ref.*c =  val.c; // Allowed, whole structure copy.  Does not allocate, requires ref.c not-null
val. c =  ref.c; // Error, mixing refs and values
val. c = *ref.c; // Allowed, whole structure copy
ref. c =  ref.c; // Allowed, pointer copy
val. c =  val.c; // Allowed, whole structure copy.
val *c =  val.c; // Error, cannot take "contents of" a value

// Alternative syntax, to avoid the "contents of" being on LHS and field applying to being on RHS:
ref.c = val.*c; // Assign into ref.c the "contents of" val.c

// Arrays of inlined structures
var ary = new *Complex[99]; // Array of 99 !Complex !objects, inlined

// Calling methods has the same syntax
ref.c.len();
val.c.len();

// Function arguments use the same typing, so can pass-by-value
val math.sin = { *Complex c -> ... }; // Passes a complex by value
math.sin(ref.*c); // Requires de-reference to pass by value
math.sin(val. c); // Pass by value

```

## ほかの章のロードマップ

`&&` と `||`。
`int` を `i32` に縮小する。
`TypeMemPtr` を4バイトに縮小する。`mmap` を使い、ヒープを下位4Gの領域に置く。
パーサーの既定の for ループ構築、または部分的なループ剥離。
`for( init; test; next ) body` は、次のようになる。
```java
{ init;       // Normal scope entry to bound lifetime of index variable
if( !test ) { // Zero-trip count test
    do {
        body;
        next;
    } while( test ); // Exit test at loop bottom by default
} };
```

## `&&` と `||`

論理演算子 &&（AND）と ||（OR）は短絡評価を行います。
最初の条件だけで結果が決まるときは、第2の条件の評価を省略できます。

- `&&` は最初のオペランドが偽なら評価を止めます。
- `||` は最初のオペランドが真なら評価を止めます。

これらは制御フローを読みやすくする糖衣構文であり、実行モデルに新しいノードを作りません。

`&&` と `||` の構文は、制御フローだけを変える単純な糖衣構文であり、新しいノードを導入しません。

次の例を考えます。
```java
int a = 1;
int b = 0;

if(a++ || b++ ) {
    if(b == 0 && a == 2) {
        sys.io.p("Or");
    }
} else{
    sys.io.p("And");
}
return 0;
```

- `a++` は1（真）と評価されるので、`b++` は評価されません。
- その結果、`b` は0のままで、`a` は2になり、「Or」が表示されます。

```java
int a = 1;
int b = 1;

int x=1;
int y=1;
int z=0;

int g = x++ && y++ && z++;
...
```

この例では、次のようになります。

- `x++` は真（1）なので、`y++` を評価します。それも真です。
- 続いて `z++` を評価します。これは偽（0）です。
- `&&` はすべての条件が真であることを要求するため、最終結果 `g` は0になります。
- 偽になるオペランドまでのすべてのオペランドを評価するため、`x`、`y`、`z` はすべてインクリメントされます。
