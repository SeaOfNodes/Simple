# 命令エンコーディングの参考資料

[English](encoding-reference.md) | 日本語

この文書は [Instruction encoding reference](encoding-reference.md) の日本語訳です。
元の第21章の README から、詳細な説明を残したものです。
コンパイラのパイプラインと実験については[章の概要](../README.ja.md)を参照してください。

# 目次

1. [命令エンコーディング](#命令エンコーディング)
2. [基盤](#基盤)
3. [エンディアン](#エンディアン)
4. [RISCV](#riscv)
   - [命令形式](#命令形式)
   - [浮動小数点数](#浮動小数点数)
   - [間接参照](#間接参照memop)
   - [分岐](#分岐)
   - [即値](#即値)
   - [関数内の定数](#関数内の定数)
5. [ARM](#arm)
   - [レジスタ形式](#レジスタ形式)
   - [即値シフト](#即値シフト)
   - [論理演算の即値](#論理演算の即値)
   - [浮動小数点数](#浮動小数点数-1)
   - [大きな定数](#大きな定数)
   - [関数定数](#関数定数)
   - [条件フラグ](#条件フラグ)
   - [間接参照](#間接参照memop-1)
   - [分岐](#分岐-1)
6. [AMD64 X86-64](#amd64-x86-64)
   - [REX プレフィックス](#rex-プレフィックス)
   - [MODR/M](#modrm)
   - [SIB](#sib)
   - [変位](#変位)
   - [即値](#即値-1)
   - [浮動小数点数](#浮動小数点数-2)
   - [大きな定数](#大きな定数-1)
   - [関数定数](#関数定数-1)
   - [条件フラグ](#条件フラグ-1)
7. [再配置](#再配置)

この章では命令エンコーディングを追加します。命令エンコーディングとは、
プロセッサの命令セットアーキテクチャ（ISA）が定義する特定のバイナリ形式で機械命令を表すことです。

命令選択が完了したら、ELF オブジェクトファイルに書き出す前に、命令をエンコードします。

曖昧さを避けるため、命令のエンコードに使う情報の出典を明示します。
現在、次のアーキテクチャをサポートしています。

### RISC-V:

*riscv* では現在、[RVA23U64](https://msyksphinz-self.github.io/riscv-isadoc/html/) を対象にしています。

### ARMV8/AArch64:

*arm*（aarch64）では、この資料にまとめられた[エンコーディング規則](https://docsmirror.github.io/A64/20a23-06/index.html)を使います。
*注*：サポートするのは64ビット ARM のエンコーディングだけです。

### AMD64:

*x86-64（amd64）* では、最新の Intel マニュアルの[エンコーディング規則](https://www.felixcloutier.com/x86/)を使います。

--------------------------------

## 命令エンコーディング

## 基盤

命令選択フェーズは、理想ノードから機械向けのノードを作ります。
これらの機械向けノードには *encoding* 関数があり、CodeGen 内の Encoding ドライバから呼び出します。

```java
for( CFGNode bb : _code._cfg )
    for( Node n : bb._outputs )
        if( n instanceof MachNode mach ) {
            int off = _bits.size();
            mach.encoding(this);
            _opLen[n._nid] = (byte)(_bits.size()-off);
    }
```

選んだターゲットに応じて、リトルエンディアンまたはビッグエンディアンのエンコーディングに注意する必要があります。
これは `add4` が扱い、正しい順序でビットストリームに追加します。
現時点のビットストリームは、単純な ByteArrayOutputStream です。

```java
@Override public void encoding( Encoding enc ) {
    enc.add4(0); // adds 4 bytes to the  bitstream
}
```

資料がビッグエンディアンでエンコーディングを示す *RISC-V* では、
この関数がリトルエンディアンへの変換も行います。ほかのアーキテクチャでは変換は不要です。

## エンディアン

実際に使う ARM、RISC-V、x86 はすべてリトルエンディアンで、最下位バイトを先に格納します。
したがって、命令エンコーディングがリトルエンディアン形式で生成されるようにします。

```java 
// Little endian write of a 32b opcode
public void add4( int op ) {
    _bits.write(op    );
    _bits.write(op>> 8);
    _bits.write(op>>16);
    _bits.write(op>>24);
}
```

たとえば、固定長命令の32ビットのエンコーディング *10110101100111101011001001101101* を考えます。

最下位バイトを最初に追加し、次のバイトを追加していきます。
``` 
_bits.write(op    );
```

`_bits.write()` が追加するのは最下位バイトだけです。
値を右にシフトすると、次のバイトを最下位の位置へ移動でき、各バイトを順番に書き込めます。

--------------------------------

## RISCV

RISCV の命令はすべて32ビット幅で、常にリトルエンディアンです。
RISCV は RISC アーキテクチャなので「レジスタ間」の形式を優先し、エンコーディングは非常に規則的です。
最も単純なエンコーディングであるため、RISCV から説明します。

#### 命令形式

##### R-TYPE

このエンコーディング形式は、`AddRISC`、`MulRISC` などのレジスタ間の演算に使います。
```java 
public static int r_type(int opcode, int rd, int func3, int rs1, int rs2, int func7) {
     return (func7 << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
}
```

##### I-TYPE

この配置は、`AddIRISC` などの即値形式に使います。

```java 
 public static int i_type(int opcode, int rd, int func3, int rs1, int imm12) {
     assert opcode >= 0 && rd >=0 && func3 >=0 && rs1 >=0 && imm12 >= 0; // Zero-extend by caller
     return  (imm12 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
 }
```

##### S-TYPE

このエンコーディング形式は `StoreRISC` に使います。

```java
public static int s_type(int opcode, int func3, int rs1, int rs2, int imm12) {
  assert imm12 >= 0;      // Masked to high zero bits by caller
  int imm_lo = imm12 & 0x1F;
  int imm_hi = imm12 >> 5;
  return (imm_hi << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (imm_lo << 7) | opcode;
}
```

##### B-TYPE

このエンコーディングの配置は `BranchRISC` が使います。
```java
 // immf = first imm
 // immd = second imm
 // BRANCH
public static int b_type(int opcode, int immf, int func3, int rs1, int rs2, int immd) {
     return (immd << 25 ) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (immf << 7) | opcode;
 }
```

##### U-TYPE

この配置は `AUIPC` と `LUI` に使います（上位即値は20ビットです）。

```java 
 public static int u_type(int opcode, int rd, int imm20) {
     return (imm20 << 12) | (rd << 7) | opcode;
 }
```

##### J-TYPE

無条件ジャンプ（`UJmpRISC`）に使います。
```java 
 public static int j_type(int opcode, int rd, int imm20) {
     return imm20 << 12 | rd << 7 | opcode;
 }
```

#### 浮動小数点数

浮動小数点定数のロードには、間接メモリロード（PC 相対）を使います。
``` 
AUIPC dst,#hi20_constant_pool
Load dst,[dst+#low12_constant_pool]
```

#### 間接参照（MemOp）

[ロード](https://msyksphinz-self.github.io/riscv-isadoc/html/rvi.html#lw)：`lw rd,offset(rs1)`

[ストア](https://msyksphinz-self.github.io/riscv-isadoc/html/rvi.html#sw)：`sw rs2,offset(rs1)`

#### 分岐

RISC-V は、分岐命令が比較と分岐先を1つの命令に含むという点で特徴的です。

#### 即値

*ALU* 演算では、即値が符号付き12ビットに収まる場合だけ、即値形式をエンコードします。
それ以外では Load Upper Immediate（上位20ビット）を使うか、両方を使います。

```
addiw	a5,a5,123
```

これに対して、追加のロードを使う場合は次のようになります。
```
addw a5,a5,a4
```

### 浮動小数点定数

- **定数プール**に格納します。
- **PC 相対ロード**でアクセスします。

### 整数定数

- **12ビット定数**：`ADDI` を使います。
- **20ビット定数**：`LUI` を使います。
- **32ビット定数**：`LUI` と `ADDI` を組み合わせます。

#### 関数内の定数

RISC-V のコードで関数内の定数をロードするときは、現在の PC をレジスタに格納し、
それを基底として定数へアクセスします。ただし、加算のような単純な算術演算のほうが
メモリアクセスより速く効率的なので、通常はロードを避けるようにします。

```java 
// auipc  t0,0
// addi   t1,t0 + #0
```

--------------------------------

## ARM

Arm の命令はすべて32ビット幅で、常にリトルエンディアンです。
Arm は RISC アーキテクチャなので、「レジスタ間」の形式を優先します。

#### レジスタ形式

レジスタ形式は、[シフト付きレジスタ](https://docsmirror.github.io/A64/2023-06/orr_log_shift.html)としてエンコードします。
```
orr reg1, reg2
```
```
x0 = x0 | (x1 << 0)
```

ほかのレジスタ形式にも同じことが当てはまります。
この共通性によって、レジスタ形式のエンコーディングを共通関数にまとめられます。

```java  
short self = enc.reg(n);
short reg1 = enc.reg(n.in(1));
short reg2 = enc.reg(n.in(2));
int body = r_reg(opcode, 0, reg2, 0,  reg1, self);
enc.add4(body);
```

レジスタ形式では *imm6* と *shift* は関係しないため、両方を0に設定します。

### 即値シフト

*asri、lsli、lsri* を指します。シフト量が0以上63以下なら、即値シフトとしてエンコードします。

#### ASR（即値）

即値の算術右シフトでは、*immr* をエンコードしたい即値に、imms を `63` = `111111` に設定します。
これは [asri のエンコーディング](https://docsmirror.github.io/A64/2023-06/asr_sbfm.html)の規則に従っています。

`sf` ビットはオペコードに含まれ、`N` は `imm_shift` 関数の実行後、暗黙にビットストリームへ追加されます。
```java 
enc.add4(arm.imm_shift(0b100100110,_imm, 0b111111, rn, rd));
```

#### LSL（即値）

```java 
// UBFM <Xd>, <Xn>, #(-<shift> MOD 64), #(63-<shift>)
// immr must be (-<shift> MOD 64) = 64 - shift
enc.add4(arm.imm_shift(0b110100110, 64 - _imm, (64 - _imm) - 1, rn, rd));
```

`_imm` は負にならず、0〜63の間にあるので、`64 - _imm` を `immr` の値として安全に使えます。
この形式の即値を64から引いて、ビットを反転します。

*imms* については、条件 *imms + 1 = immr* が成り立たなければなりません。
値を代入すると、次のようになります。

> imms + 1 = 64 - _imm;

> imms = (64 - _imm) - 1;

したがって、`imms` の値は `(64 - _imm) - 1` です。

#### LSR（即値）

*ASR* と同じです。

#### 論理演算の即値

論理演算の即値は *andi、xori、orri* を指します。

```java 
// Can we encode this in ARM's 12-bit LOGICAL immediate form?
// Some combination of shifted bit-masks.
private static int imm12Logical(TypeInteger ti) {
    if( !ti.isConstant() ) return -1;
    if( !ti.isConstant() ) return -1;
    long val = ti.value();
    if (val == 0 || val == -1) return -1; // Special cases are not allowed
    int immr = 0;
    // Rotate until we have 0[...]1
    while (val < 0 || (val & 1)==0) {
        val = (val >>> 63) | (val << 1);
        immr++;
    }
    int size = 32;
    long pattern = val;
    // Is upper half of pattern the same as the lower?
    while ((pattern & ((1L<<size)-1)) == (pattern >> size)) {
        // Then only take one half
        pattern >>= size;
        size >>= 1;
    }
    size <<= 1;
    int imms = Long.bitCount(pattern);
    // Pattern should now be zeros followed by ones 0000011111
    if (pattern != (1L<<imms)-1) return -1;
    imms--;
    if (size == 64) return 0x1000 | immr << 6 | imms;
    return (32-size)<<1 | immr << 6 | imms;
}
```

imm12Logical は、エンコードできる場合は論理演算の12ビットの即値エンコーディングを返し、
できない場合は -1 を返します。
```java
int imm12;
return and.in(2) instanceof ConstantNode off && 
off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
? new AndIARM(and, imm12) 
```
```java 
arm.imm_inst(enc,this,0b100100100,_imm); 
```

#### 浮動小数点数

x86 と同じように、*D* レジスタに対応する汎用レジスタ（GPR）のペアを得るために、オフセットを引く必要があります。
``` 
short dst = (short)(enc.reg(this ) - arm.D_OFFSET);
```

浮動小数点定数をロードするには、`ldr(literal)` 命令を使います。
オフセットを PC レジスタに加算して、リテラルのアドレスを求めます。

```java 
// Any number that can be expressed as +/-n * 2-r,where n and r are integers, 16 <= n <= 31, 0 <= r <= 7.
int body = arm.load_pc(0b01011100, 0, dst);
```

#### 大きな定数

##### 整数：mov（movz、movk、movn）をさまざまに組み合わせて、PC 相対ロードを避けられます。

##### 浮動小数点数：PC 相対の *LDR（immediate, SIMD&FP）* 命令を使います。

#### 関数定数

関数定数をロードするには、現在の PC を x0 に格納し、それを使って定数へアクセスします。
一般に、加算のほうが速いためロードを避けるようにします。
```
adrp    x0, 0
add     x0, x0, 0
```

#### 条件フラグ

arm では比較後に条件フラグを設定します。これは x86 と同じように動作します。

```
int square(int a) {
// subs
// cset	w8, eq  // eq = none
bool da = a == 1;
    // bne
    if(da == 1) {
        return 1;
    }
    return 2;
}
```

arm の比較は `subss` で行い、2つのオペランドの減算とフラグの設定を同時に行います。
`CSET` はフラグを調べ、その結果をレジスタに保存します。

後で `b.ne` が条件付き分岐を実行しようとするときにも、このフラグに依存します。

#### 間接参照（MemOp）

Arm は次の間接参照形式をサポートします。

[ロード](https://docsmirror.github.io/A64/2023-06/ldr_reg_gen.html)：`ldr dst, [base + index]`。base と index はどちらもレジスタです。

[ロード](https://docsmirror.github.io/A64/2023-06/ldr_imm_gen.html)：`ldr dst, [base + #offset]`。base はレジスタ、offset は即値です。

[ストア](https://docsmirror.github.io/A64/2023-06/str_reg_gen.html)：`str dst, [base + index]`

[ストア](https://docsmirror.github.io/A64/2023-06/strb_imm.html)：`str dst, [base + #offset]`

オフセットがレジスタの場合は、即値オフセット形式ではなくレジスタオフセット形式を使います。

#### 分岐

`B.cond` は、PC 相対オフセットのラベルへ[条件付き](https://developer.arm.com/documentation/dui0802/b/A32-and-T32-Instructions/Condition-codes?lang=en)で分岐します。

> cond は標準的な条件のいずれかです。

--------------------------------

## AMD64 X86-64

命令幅が固定の riscv アーキテクチャとは異なり、*x86-64* の命令は可変長です。
これは *CISC（Complex Instruction Set Computer）* アーキテクチャで一般的であり、
命令幅は1〜15バイトまで変わります。AMD64 は多くの間接アドレッシングモードをサポートするため、
*CISC* の一般的な目標は、できるだけ少ないアセンブリの行数で処理を完了することです。

### REX プレフィックス

64ビット版の x86 を対象にしているため、このプレフィックスを扱う必要があります。
一方、32ビットモードでは通常は不要です。

一般に、次の場合は *REX* プレフィックスをエンコードしなければなりません。

- 拡張レジスタ（R8〜R15、XMM8〜XMM15、YMM8〜YMM15、CR8〜CR15、DR8〜DR15）を使う。
- オペランドサイズが64ビットで、命令の既定のオペランドサイズが64ビットではない（大部分の ALU 演算）。
- RSI、RDI、RBP で8ビットのオペランドを使う。

次の場合は *REX* プレフィックスをエンコードしてはなりません。

- 上位バイトのレジスタ AH、CH、BH、DH を使う（現在の Simple では使っていません）。

*注*：SSE 命令のエンコーディングでは、*REX* プレフィックス（0x40）を `SSE` プレフィックスの後に置く必要があります。
それ以外の場合は無視されます。

Simple では、REX プレフィックスをビットストリームの先頭に追加するだけです
（SSE の浮動小数点命令を除く）。配置は次のとおりです。

| **フィールド** | **長さ** | **説明** |
|:---------:|:----------:|---------------------------------------------------------------------------------|
| `0100` | 4ビット | 固定のビットパターン |
| `W` | 1ビット | 1なら64ビット、それ以外なら32ビットのオペランドサイズを使う |
| `R` | 1ビット | **MODRM.reg** フィールドの拡張 |
| `X` | 1ビット | **SIB.index** フィールドの拡張 |
| `B` | 1ビット | **MODRM.rm** または **SIB.base** フィールドの拡張 |

```java 
    public static int REX_W  = 0x48;
``` 

```java
public static int rex(int reg, int ptr, int idx, boolean wide) {
    // assuming 64 bit by default so: 0100 1000
    int rex = wide ? REX_W : REX;
    if( 8 <= reg && reg <= 15 ) rex |= 0b00000100; // REX.R
    if( 8 <= ptr && ptr <= 15 ) rex |= 0b00000001; // REX.B
    if( 8 <= idx && idx <= 15 ) rex |= 0b00000010; // REX.X
    return rex;
}

    ... 
    enc.add1(x86_64_v2.rex(dst, src, 0));
```

`W` ビットを設定すると、`0b01001000 = 0x48;` になります。

#### オペコード

この場合、オペコードは実行する操作を指定する1バイトのフィールドです。
```
enc.add1(opcode()); // opcode 
```

#### MODR/M

| **フィールド** | **長さ** | **説明** |
|:-------------:|:----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **MODRM.mod** | 2ビット | 一般に、このフィールドが `b11` ならレジスタ直接アドレッシングモード、それ以外ならレジスタ間接アドレッシングモードを使う |
| **MODRM.reg** | 3ビット | 2種類の値を取れる。<br><br>• 一部の命令が使う3ビットのオペコード拡張。ほかの命令と区別する以上の意味は持たない。<br><br>• 命令によってソースまたは宛先として使う3ビットのレジスタ参照。参照するレジスタは、命令のオペランドサイズと命令自体に依存する。`REX.R`、`VEX.~R`、`XOP.~R` フィールドによって最上位ビットを1つ追加し、合計4ビットに拡張できる |
| **MODRM.rm** | 3ビット | 直接または間接のレジスタオペランドを指定し、任意で変位を伴う。`REX.B`、`VEX.~B`、`XOP.~B` フィールドによって最上位ビットを1つ追加し、合計4ビットに拡張できる |

MODR/M バイトも同じように扱います。

modrm バイトに最初に必要なのは、2ビットの mod です。

##### mod

mod の配置は次のとおりです。
```
public enum MOD {
    INDIRECT,       // [mem]
    INDIRECT_disp8, // [mem + 0x12]
    INDIRECT_disp32,// [mem + 0x12345678]
    DIRECT,         //  mem
};
```

##### reg

reg フィールドは3ビットで、8個のレジスタから1個を選びます。レジスタを指定する場合、
REX.R によって4ビットに拡張され、16個のレジスタから1個を選べます。

##### r/m

r/m フィールドは3ビットです。レジスタ直接指定モードでは、REX.B によって拡張され、
16個のレジスタから1個を選べます。メモリ指定モードでは、アドレッシング形式または
ベースレジスタを選びます。一部の値は SIB バイトや RIP 相対アドレッシングを指定します。

```java
public static int modrm(MOD mod, int reg, int m_r) {
    // combine all the bits
    return (mod.ordinal() << 6) | ((reg & 0x07) << 3) | m_r & 0x07;
}
```

`modrm` 関数を使って `ModR/M` バイトを構築します。通常、これはオペコードの後に続きます。

``` 
enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
```

レジスタ形式のエンコーディングは、次のようになります。
```      
short dst = enc.reg(this ); // src1
short src = enc.reg(in(2)); // src2
enc.add1(x86_64_v2.rex(dst, src, 0));
enc.add1(opcode()); // opcode
enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
```

#### SIB

SIB バイトには次のフィールドがあります。

| **フィールド** | **長さ** | **説明** |
|:-------------:|:----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **SIB.scale** | 2ビット | `SIB.index` のスケーリング係数を示す。表で使う **s** は 2^SIB.scale^ に等しい |
| **SIB.index** | 3ビット | 使用するインデックスレジスタ。各レジスタの値は Registers を参照。`REX.X`、`VEX.~X`、`XOP.~X` フィールドによって最上位ビットを1つ追加し、合計4ビットに拡張できる |
| **SIB.base** | 3ビット | 使用するベースレジスタ。各レジスタの値は Registers を参照。`REX.B`、`VEX.~B`、`XOP.~B` フィールドによって最上位ビットを1つ追加し、合計4ビットに拡張できる |

MODR/M バイトと同じ配置です。
```java 
public static int sib(int scale, int index, int base) {
    return (scale << 6) | ((index & 0x07) << 3) | base & 0x07;
}
```

```java
enc.add1(x86_64_v2.sib(_scale, idx, x86_64_v2.RBP));
```

#### 変位

変位には2つの形式があります。
```java
public enum MOD {
INDIRECT, //  [mem]
INDIRECT_disp8, // [mem + 0x12]
INDIRECT_disp32,// [mem + 0x12345678]
DIRECT,          // mem
};
```

```java
if( mod == MOD.INDIRECT_disp8 ) {
    enc.add1(offset);
} else if( mod == MOD.INDIRECT_disp32 ) {
    enc.add4(offset);
}
```

変位は即値の前に置きます。たとえば、次のようになります。
``` 
sub [eax + 2], 4
```
``` 
bytes.write(0x83);
bytes.write(modrm(INDIRECT8, 5, 0 /* eax */));
bytes.write(2); // displcement goes first
bytes.write(4);
```

#### 即値

即値のバイト列は `ModR/M` バイトの後に置きます。必要に応じて4バイトの即値をエンコードできます。
```java
// immediate(4 bytes) 32 bits or (1 byte)8 bits
if( x86_64_v2.imm8(_imm) ) enc.add1(_imm);
else                       enc.add4(_imm);
```

#### 間接参照（MemOp）

X86-64 は複数の間接アドレッシングモードをサポートします。

#### 浮動小数点数

浮動小数点演算には、XMM レジスタまたはメモリにあるスカラーの単精度浮動小数点値を操作する
`SSE SIMD` 命令を使います。
*注*：ALU 演算で XMM レジスタを使う場合、エンコーディングは非常に似ていますが、
レジスタ番号から `XMM_OFFSET` を引く必要がある点が異なります。
レジスタを連続した番号で数えているため、XMM レジスタに対応する GPR のペアを得るには、
オフセットを引く必要があります。

```java 
short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
```

```java
public static int XMM_OFFSET = 16; 
```

| **X.Reg** | **8ビット GP** | **16ビット GP** | **32ビット GP** | **64ビット GP** | **80ビット x87** | **64ビット MMX** | **128ビット XMM** | **256ビット YMM** | **16ビット Segment** | **32ビット Control** | **32ビット Debug** |
|:--------:|:------------:|:-------------:|:-------------:|:-------------:|:-------------:|:-------------:|:--------------:|:--------------:|:-----------------:|:-----------------:|:---------------:|
| `0.000 (0)` | AL | AX | EAX | RAX | ST0 | MMX0 | XMM0 | YMM0 | ES | CR0 | DR0 |

`XMM0` が `RAX` に対応し、以降も同様であることがわかります。

#### 大きな定数

##### 整数：`MOV r64, imm64` 命令形式を使うだけです。

##### 浮動小数点数：再配置を使い、メモリ内の定数を使います。

#### 関数定数

関数定数をロードするには lea を使います。これは、命令ポインタからの相対アドレスを指定したレジスタにロードします。
`lea rax, [rip+disp32]`

#### 条件フラグ

FLAGS レジスタは、x86 CPU の現在の状態を保持するステータスレジスタです。

条件付きコードのためにエンコードする必要のあるすべての命令を示す、単純な例を考えます。

```
int square(int a) {
    // cmp    DWORD PTR [rbp-0x8],0x1
    // `sete   al`
    bool da = a == 1;
    
    //  cmp    eax,0x1 
    if(da == 1) {
        return 1;
    }
    return 2;
}
```

まず、`a` と1を比較する比較命令が必要です。
```
cmp    DWORD PTR [rbp-0x8],0x1
```

この比較命令が条件フラグを設定します。

> 結果に従って CF、OF、SF、ZF、AF、PF フラグを設定します。

続いて `sete` 命令を使い、比較の結果をレジスタへ保存します。

`sete   al`

if 条件のために、最初の真偽値の結果に基づく追加の比較を行います。
```
 cmp    eax,0x1 
```

そして、比較（`a == 1`）が設定したフラグに依存する条件付き分岐を行います。
```
jne    1159 <square(int)+0x29>
``` 

--------------------------------

## 再配置

再配置によって、コードを新しいコードオフセットへ*移動*できます。
コードは、ほかのコードやデータを参照することがよくあります。
`call` 命令はサブルーチンを、`branches` はほかの命令を対象とし、
大きな定数は多くの場合*定数プール*からロードします。

### 局所的な再配置

局所的な再配置は、局所的または自己参照的なコードを修正します。最も一般的なのは分岐先です。
`BAOS`（Byte Array Output Stream）に命令を書き込む際、後に続く命令へ向かう前方分岐のオフセットは未知です。
そのオフセットは、分岐と分岐先の間にあるエンコーディングのサイズに依存します。
`riscv` と `arm` のターゲットでは簡単に見えます（命令は4バイトなので数えればよい）……。
しかし、複数の命令を使うエンコーディングもあるため、実際には4バイトとは限りません。
X86 では命令長が擬似的に不規則に見えるほど多様なので、オペコードの長さを求めること自体が、
オペコードを書き出すのと同じくらい難しくなります。

X86 には、短形式と長形式の分岐という追加の複雑さもあります。
分岐先が符号付きバイトの範囲（+/-127）にあるなら2バイトの分岐エンコーディングを、
それ以外では6バイトのエンコーディングを使います。
Simple には、分岐を縮め、できるだけ2バイト形式を使うパスがあります。
ただし、このパスはコードをずらす必要があり、その結果、ほかの分岐オフセットもすべて変わります。
この「圧縮」パスは実際には、すべての分岐が2バイト形式を使えると楽観的に仮定して始め、
必要に応じて分岐を拡張します（したがって、実際にはコードを*拡張*します）。

コードの「形」が最終的に決まったら、局所的な再配置パスを実行して自己参照をすべて更新します。
基本的には、バイト配列内の分岐オフセットを実際の値で上書きします。

このパスの終了時点で、コードはあるオフセット（通常は0）で局所的に正しくなります。
しかし、これで終わりではありません。別のコンパイルで生成されたコードと*リンク*することが多く、
その際にもさまざまなオフセットを調整する必要があります。

### 大域的な再配置

あるコンパイル単位のコードから、別のコンパイル単位のコードを呼びたい場合、*大域的な再配置*が必要です。
よくある例は、*ローダー*が *ELF* ファイルをロードすることです。
通常は新しいプログラムの実行ごとに起こり、再配置情報をディスク（たとえば ELF ファイル内）に保存しておく必要があります。
JIT システムでも、異なるコンパイルをまたいで同じことが必要ですが、再配置情報はメモリに保持できます。

#### 再配置の整合性を保つ

再配置をディスクに書き込めるので、ディスク形式、ローダー、「コードの形」がすべて一致しなければなりません。
あるコンパイル単位から別の単位を呼ぶ場合、たとえばプログラムが `libc` の `malloc` を呼ぶ場合を考えます。
呼び出し元は Simple の汎用 `CallNode` の機械固有版を使い、呼び出し先は C でコンパイルされたコードです。
ロード中に、ローダーが `call` と `libc` の配置を決めます。
2つの異なるコードブロックを何らかの順序で（通常は多くのほかのブロックとともに）メモリに書き込みます。
そこで、`call` の呼び出し先を、`libc` のコードブロック内の `malloc` に修正する必要があります。

X86 の外部呼び出しは、1バイトのオペコードと4バイトの PC 相対宛先アドレスからなる5バイト命令です。
必要な修正値は、X86 固有の `call` 命令の*末尾*から `malloc` の先頭までの差であり、
`call` の最後の4バイトに直接書き込みます。ELF ファイル内のエンコーディングの種類は、これを記述しなければなりません。

RISCV の外部呼び出しは、ビットを分担する2つの4バイト命令でエンコードします。
`LUI` が宛先の上位20ビットをロードし、`JALR` が下位12ビットを加算します（32ビットの絶対宛先範囲）。
`LUI` を `AUIPC` に置き換えると、32ビットの PC 相対範囲を得られます。
修正では、一方の命令の20ビットと、もう一方の命令の12ビットを更新します。
ここでも ELF ファイルのエンコーディングの種類が、この方法を記述する必要があります。
X86 式の修正をここに使うと、誤ったビットを壊し、プログラムが動かなくなります。

#### 大きな定数

大きな定数とは、レジスタへ簡単に直接ロードできず、代わりにメモリからロードする定数です。
セキュリティ上の理由で、*実行*権限ではなく*読み取り専用*権限などを付けた別のアドレス空間に置くことがよくあります。
そのため、コードに対する最終的な相対アドレスは、配置によって変わります。
これは、コンパイル単位をまたぐ呼び出しと同じです。

大きな定数は、コンパイル単位間で共有することもできます（HPC コードで `pi` を繰り返し使う場合など）。
共有すると、キャッシュの占有量を減らせます。
いずれにせよ、コードと定数の配置が決まったら、正しいオフセットからロードするようにコードを修正する必要があります。
上の `call` の例のように、ロード命令の種類と修正方法の種類が一致しなければなりません。
X86 では、その種類によって4バイトまたは8バイトのアドレスを使います。
RISCV では、おそらく `LUI` と `Load` の何らかの組み合わせで、即値を20/12ビットに分けます。
また、RISCV では同じ `LUI` で定数プールの上位20ビットを「共有」し、
同じプールの異なる定数には異なるロードを使いたいでしょう。ARM には、さらに別の方法があります。
