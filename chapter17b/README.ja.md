# 第17b章: 糖衣構文

[English](README.md) | 日本語

[前: 第17a章](../chapter17a/README.ja.md) |
[次: 第18章](../chapter18/README.ja.md)

この文書は [原文](README.md) の日本語訳です。

## 目次

1. [前置・後置インクリメント](#前置後置インクリメント)
2. [複合代入](#複合代入)
3. [var/val](#var-と-val)
4. [三項演算](#三項演算)
5. [for ループ](#for-ループ)

> 訳注: 原文の目次にある「Memory effects」に対応する節は、原文本文にはありません。

第17a章では、代入で確認する権限を確立しました。この章ではその権限を変えずに、代入と制御フローを短く書く方法を追加します。その後、第18章で関数と呼び出しを導入します。

## 前置・後置インクリメント

`arg++` と `arg--` を、通常の意味で許します。値を更新し、式の値は更新前の値になります。最長一致を使うので、`arg---arg` は `(arg--)-arg` としてパースします。

`s.fld++` と `ary[idx]++`、それらの `--` 版も許します。

前置インクリメントは、今のところ識別子についてだけ許します。例は `--pc` です。

## 複合代入

後置インクリメントと同じ方向で、局所変数への `op=` 代入も、ほかの言語に似た意味論で許すようになります。サポートする演算子は `+=`、`-=`、`*=`、`/=`、`&=`、`|=`、`^=`、`<<=`、`>>=`、`>>>=` です。ビット演算とシフトの代入には整数オペランドが必要です。`>>=` は符号を保持し、`>>>=` はゼロを入れます。代入は右辺を1回評価し、結果を変数の型に狭めて格納し、その格納値を式の結果とします。代入は、`x |= y <<= 2` のように右から左に結合します。

## var と val

`var` と `val` は変数を宣言し、その型を推論します。`var` は再代入可能な束縛、`val` は固定の束縛を推論します。両方とも初期化式のアクセス権限を保ちます。どちらも、書き込み可能なオブジェクトを読み取り専用にはしません。この2つの独立した権限は[第17a章](../chapter17a/README.ja.md)から来ています。

```cpp
struct Point { int x; };
val p = new Point; // Fixed binding to a writable new object
p.x = 3;           // Allowed
// p = new Point;  // Error: fixed binding
Point view = p;    // Explicit read-only view
var q = view;      // Reassignable binding, still read-only access
// q.x = 4;        // Error: cannot restore write permission by inference
```

型推論には初期化子が必須です。プリミティブでは広い数値型（`int` または `flt`）を推論し、参照ではアクセス権限を保持します。再代入可能な推論された参照は、後で空の値を保持できるように null を許容します。明示的な型は、宣言された幅と null 許容性を維持します。関数パラメータには依然として明示的な型が必要です。

## 三項演算

`pred ? e_true : e_false` を許します。`pred ? e_true` も許し、この場合の false の結果は true の結果のゼロ型版です。

## for ループ

C/C++ 形式の `for` ループを許します。

`for( init; test; next ) body`

例:

```cpp
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return sum;
```

`init`、`test`、`next` のどれも空にできます。`init` には新しい変数の宣言を許し、そのスコープは `for` 式に限定されます。

```cpp
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return i; // ERROR: Undefined name 'i'
```

誤った `find` 呼び出し:

```cpp
for( int i=0; i<ary#; i++ )
  if( ary[i]==e )
    break;
do_stuff(i); // ERROR: undefined name 'i'
```

`find` 呼び出しの例:

```cpp
for( int i=0; i<ary#; i++ )
  if( ary[i]==e )
    return i;
return -1;
```
