# Simple のモジュール

[English](module.md) | 日本語

この文書は [Modules for Simple](module.md) の日本語訳です。原文はモジュールの設計案です。

目標：

- 有効なプログラムを1行で書けるようにする（チュートリアルや初心者の導入を簡単にする）。
  - 最小限の手間でコンパイルして実行する。
- 分割コンパイル、名前空間、外部ライブラリを扱う。
  - ELF など、リンク可能・実行可能な形式へコンパイルする。
- フィールドやクラスを簡単に非公開にする。
- 出力ファイルを、既存の .o/.exe/lib/dll の仕組みと互換にする。
- `#include` や `import` 用のファイルを不要にする。
- モジュールごとに構造・ファイルシステムの階層を持たせる。
  - 複数のファイルで1つのモジュールを構成することも考えられる。
  - モジュール内で簡単に名前空間を使う。
  - 親ファイルまたは入れ子のファイルに、入れ子の名前を置く。
- クラス初期化子について次を保証する。
  - 正確に1回だけ実行する。
  - 最初にアクセスされた時点で遅延実行する。
  - 最初のインスタンスが作られる前、またはその時点までに完了する。
    - `<clinit>` 内の特別な原型インスタンス（golden instance）は例外として認める。

---
### 目標：最小限の手間で有効な1行プログラムを書く

現在の作業ディレクトリの名前から、モジュールとクラス `hello` を作ります。
追加の引数なしでコンパイルすると、リンクと実行も行います。

ファイル：`hello.smp`
```
sys.io.p("Hello World!");
```

コンパイルして実行します。
```
> smp hello.smp
Hello World!
```

モジュール名とクラス名は、ファイル名から取り出した `hello` です。
メモリ内でコンパイルし、`exec` で実行します。ファイルは作りません。
これは、その場でコンパイルして実行するためだけのモードです。

---
### 目標：分割コンパイル

コンパイル時に出力先のビルドディレクトリを指定すると、`.o` ファイルを得られます。
出力先ディレクトリの構造はソースと一致し、場所は自由に選べます。

前と同様に、コンパイルのコマンドでモジュール名を指定していないため、
`Pet` がモジュールにもクラスにもなります。

ファイル：Pet.smp
```
// Class initializer <clinit> is any code outside a struct declaration
// and is guaranteed to run-once on first touch.

// static fields listed here:
int default_leggies = 4;

// A default (no argument) <init> is code inside struct with same name as the 
// file; this is the same behavior as field initializers for Java, although 
// any code is allowed, not just expressions.

struct Pet {
  str name;
  int leggies = default_leggies;
}

float yet_another_static_field;
```

`build/` ディレクトリ内の `Pet.o` へコンパイルします。
```
> smp Pet.smp -o build/
> ls build
Pet.o
```

---
### 目標：入れ子の名前空間

`myRepo` 内のファイル名から、モジュールとクラス `Pet` を作ります。
入れ子のクラス `Pet.Vet` も作ります。これらを別のモジュール `Owner` から使います。

ディレクトリ：`~/myRepo/`
```
~/myRepo/
  Pet.smp
  build/

~/otherRepo/
  Owner.smp
```

ファイル：`myRepo/Pet.smp`
```
int default_leggies = 4;

struct Pet {
  str name;
  int leggies = default_leggies;
}

// Nested class Vet inside of file Pet
struct Vet {
  str name;
  str address;
  long credit_card_num;
}
```

ファイル：`otherRepo/Owner.smp`
```
Pet dog = Pet{name="Fido"};
Pet snake = Pet{name="slither", leggies = 0; };
Pet.Vet vet = Pet.Vet{name="Linda"; address="101 Main St"; credit_card_num=1234};

sys.io.p("I took "+dog.name+" to "+vet.name+"\n");
```

`build/` ディレクトリ内の `Pet.o` へコンパイルします。
コマンドで別のモジュールのルートを指定していないため、`Pet` がモジュールであり、クラスでもあります。
`Pet` がモジュールなので、`Vet` はモジュール `Pet` 内のクラスです。

```
> smp Pet.smp -o build/
> ls build
Pet.o
```

`Pet` と `Pet.Vet` のコードは、同じ `Pet.o` ファイルに入ります。

自由変数 `Pet` と `Pet.Vet` を解決するために `Pet` のリポジトリを指定して、
`Owner.smp` をコンパイルし、実行します。

```
> smp Owner.smp -lib ~/myRepo/build
I took Fido to Linda
```

---
### 目標：入れ子のファイル内の入れ子の名前空間

前の例と同じですが、ファイルを入れ子にします。

```
~/myRepo
  Pet.smp
  Pet/
    Vet.smp
  build/
    Pet.o
    Pet/
      Vet.o

~/otherRepo
  Owner.smp
```

ファイル：Pet.smp
```
int default_leggies = 4;

struct Pet {
  str name;
  int leggies = default_leggies;
}
```

ファイル：Pet/Vet.smp
```
// Nested class Vet inside of directory Pet
struct Vet {
  str name;
  str address;
  long credit_card_num;
}
```

`Owner.smp` をコンパイルし、実行します。
```
> smp Owner.smp -lib ~/myRepo/build
I took Fido to Linda
```

---
### 目標：クラス初期化子の遅延実行

```
Parent.smp
Parent/
  ChildA.smp
  ChildB.smp
```

ファイル：`Parent.smp`
```
sys.io.p("In Parent <clinit>");
sys.io.p(ChildB.str);
```

ファイル：`ChildA.smp`
```
// <clinit> for ChildA
sys.io.p("In ChildA <clinit>");
str str = "A";
```

ファイル：`ChildB.smp`
```
// <clinit> for ChildB
sys.io.p("In ChildB <clinit>");
str str = "B";
```

コンパイルして実行します。
```
> smp Parent.smp
In Parent <clinit>
In ChildB <clinit>
B

```

### 目標：簡単な非公開フィールド

名前の先頭にアンダースコアを付けると、フィールドやクラスが非公開になります。

### 目標：C コンパイラのドライバに似た構文

```
Simple --cpu x86_64_v2 --abi Win64 --norun -o lib/lib/sys_x86_64_v2_win64.o src/main/smp/sys.smp
```

モジュールを指定していないため、最上位のファイルがモジュール `sys` になります。
モジュールディレクトリも指定していないため、ファイル名のディレクトリ部分 `src/main/smp` になります。
ビルドディレクトリを指定していないため、出力ファイルのディレクトリ部分 `lib/` になります。
出力ファイル名の既定値は `smp.o` ですが、ここでは別名を指定しています。

ある `<clinit>` は別の `<clinit>` を参照でき、最初のアクセス時に実行されます。
設計上、子より先に親へアクセスする必要があります。ただし、子 A は子 B を参照できます。
Parent.B へのアクセスは、次を意味します。

- 最初に Parent にアクセスし、`<clinit>` を開始する。
- そこからアクセスするものにも、A や B を含め、再帰的に `<clinit>` を実行する。
- Parent の処理が完了する。
- `<clinit>` 内では別の*モジュール*を参照できない。
- 静的関数は別のモジュールを参照*できる*が、その場合、実行される可能性がある……。

目標：モジュール内の `<clinit>` を決定的にする。

- 背景：複雑な `<clinit>` の順序のせいで、子が使う時点で参照先の static final フィールドがまだ初期化されていないことが、何度もあった。
- 副目標：親が子より先になるなど、`<clinit>` の*順序*を決定的にする。
  - 問題：Parent の `<clinit>` の途中で Child を参照すると、Parent が完了する前に Child の `<clinit>` が走る。
    - 部分的な回答：親が先で「親は子を知っている」ため、これはよい。
    - 追加の問題：モジュールをまたぐと成り立たない。あるモジュールは別のモジュールの親ではない。
  - 問題：`<clinit>` 中に呼び出す Parent の関数が、非決定的に子を初期化できる。
    - `fcn = { -> rand ? A.fld : B.fld }`
- 回答案：Parent の `<clinit>` は、直接にも間接にも子を参照しては*ならない*。
- 例：`B  b = new B; // Illegal ref to child in Parent.<clinit>`
  — Parent の初期化子から子への不正な参照。
- 例：`B? b = fcn(); fcn = { -> rand ? null : new B; } // Illegal call to fcn referring to B inside Parent <clinit>`
  — Parent の初期化子内で B を参照する関数への不正な呼び出し。
- 例：`B? b; fcn = { -> new B; } // OK, no B.<clinit> during Parent.<clinit>`
  — Parent の初期化中に B の初期化子を実行しないため、これは許される。

目標：外部とのリンクでも、1回限りの初期化機能を使えるようにする。

回答案：外部から `Foo.foo()` を呼ぶときは、まず `Foo.<clinit>` が実行済みであることを確認してから、
`Foo.foo()` を呼び出す。任意の最適化として、成功後は呼び出し元を変更し、
`<clinit>` の確認を省略する。
