# 第33章：自動メモリ管理

[English](README.md) | 日本語

[前の章：第32章 — コンパイラ内部の高速化](../chapter32/README.ja.md)

この章では自動メモリ管理を検討します。設計は未定で、実装はまだ始めていません。
完成した第32章のコンパイラから始まります。
コンパイラ、ライブラリ、実行例、テストを含む、独立して実行できるスナップショットです。
高速化の説明と測定結果は[第32章](../chapter32/README.md)にあります。

`chapter33` ディレクトリから実行します。

```sh
make -j8 release
make -j8 sanity
make -j8 tests
```

`sanity` にも型の格子の全検証が含まれます。変更を終える前に全テストを実行してください。
MiniFB と miniaudio を無効にするには `make -j8 tests_nographics` を使います。

[文法](docs/33-grammar.ja.md)、[実行例](docs/examples/README.md)、
[ロードマップ](../docs/roadmap.ja.md)も参照してください。
詳しい実行手順は[英語版](README.md)にあります。
