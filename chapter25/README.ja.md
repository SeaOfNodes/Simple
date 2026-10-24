# 第25章: 型が確定する前の SSA 構築

[English](README.md) | 日本語

[Previous: Chapter 24b](../chapter24b/README.ja.md) |
[Next: Chapter 26](../chapter26/README.ja.md)

この章では、前方参照やループのために型がまだ分からない場合でも、演算と
フィールド参照をグラフに記録します。数値演算のモード、Convert、Guard、
シンボルによるフィールド位置を使い、型と配置が確定した後に解決します。
値の Phi と Load は、パーサが与える仮の型の下限を使いません。

コンストラクタは前章の方式を維持します。初期化用の呼び出し、非公開の
メモリ、オブジェクトの公開は次の章で扱います。詳しい説明と例は
[英語版](README.md)を参照してください。

このディレクトリで `make lib tests CTAGS=` を実行できます。
