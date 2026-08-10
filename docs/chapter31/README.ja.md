# 第31章：ソフトウェア描画とグラフィカルゲーム

[English](README.md) | 日本語

[前の章：第30章 — テキスト整形と診断](../chapter30/README.ja.md)

第30章で完成したコンパイラと標準ライブラリを出発点とする章です。
Simple で描画する `sys.draw.Canvas` と、MiniFB を使うオプションの
ウィンドウ・入力 API `sys.gfx` を使って、グラフィカルな三目並べを実装します。
矩形、円、太い線、色、ビットマップ文字を扱えます。
詳細は[英語版の描画ライブラリとゲームの説明](drawing.md)を参照してください。
通常のビルドとテストに MiniFB や画面表示は必要ありません。

```sh
make -C chapter31 run-graphical-tictactoe CTAGS=
```

このスナップショットの[文法](31-grammar.ja.md)と
[実行可能な例](examples/)を用意しています。
ビルド手順は[英語版](README.md)を参照してください。
