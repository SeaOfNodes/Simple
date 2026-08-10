# 第30章：テキスト整形と診断

[English](README.md) | 日本語

[前の章：第29章 — 技術的負債の整理](../chapter29/README.ja.md)

ASCII の[三目並べ](examples/TicTacToe.smp)を題材に、コンパイラと
標準ライブラリを改善します。ゲーム自体ではなく、実装中に必要になった
言語機能、診断、ランタイムが章のテーマです。実行可能な例は
[`examples`](examples/)にあります。

- `sys.io.getch()` による Enter 不要の1バイト入力と、端末モードの復元。
- EOFまでの可変長読み込みと、部分書き込み・割り込みの再試行。
  読み込みエラーは `null`、書き込みエラーは `-1` と errno で通知します。
  `aryu8` と `aryi64` の長さは `u32` とし、拡張時の桁あふれを防ぎます。
- `file:line:column`、ソース行、位置マーカーを含む構文・型エラーの診断。
- `int` を `i32` の別名に変更。型推論と中間演算の規則は維持し、
  代入時に32ビットへ縮小します。ポインタの幅は変わりません。
- `"CPU plays %(reply+1).\n"` のような文字列補間と、`sys.text` の型別変換。
  整数などの整形は Simple で実装し、浮動小数点変換だけ C を呼び出します。
- `sys.rt.simple_args` がネイティブの起動引数を Simple の配列へコピー。
- 関数ごとのレジスタ割り当てと再試行。レジスタを使わない依存を生存解析から
  除外し、レジスタ破壊をまたぐ必要な転送を分割時に保持します。

リポジトリのルートからゲームをビルドします。

```sh
make -C chapter30 tictactoe CTAGS=
```

Windows では `chapter30/build/examples/x86_64_v2_win64/TicTacToe.exe`、
Linux では `chapter30/build/examples/x86_64_v2_SystemV/TicTacToe` を実行します。
詳しい書式指定、テスト、WSL でのビルドは[英語版](README.md)を、
バックエンドなどの未完了の作業は[ロードマップ](../roadmap.ja.md)
を参照してください。
