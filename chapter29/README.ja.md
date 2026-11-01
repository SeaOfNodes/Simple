# 第29章: 技術的負債の整理

[English](README.md) | 日本語

[前の章: 第28b章 — 分離コンパイル](../chapter28b/README.ja.md)

この章では、第28b章の言語と分離コンパイルの動作を保ちながら、コンパイラを
読みやすく、変更しやすく整理します。最初の変更は、3,000行を超えたパーサを
文法のまとまりに沿って `parser/` パッケージへ分割することです。

`Parser` がカーソル、スコープ、型の登録情報を持ち、各部品は `Parser P` を
共有します。構文解析は引き続き Sea-of-Nodes IR を直接生成します。
`Var` は `parser/` に、反復ピープホール最適化の `IterPeeps` は `codegen/` に移ります。

[パーサの構成（英語）](src/main/java/com/seaofnodes/simple/parser/README.md)と
[文法と実装の対応表（英語）](docs/29-grammar.md)からコードをたどれます。
初期化完了チェックは、構文解析後のヘルパー関数の Return を調べます。
未初期化の必須フィールドを private memory に残すため、終了経路の別の一覧は不要です。
ソース上のプライバシー制約は今回は追加せず、[ロードマップ](../docs/roadmap.md)に残します。

リポジトリのルートからビルドとテストを実行します。

```sh
make lib tests release CHAPTERS=chapter29 CTAGS=
```

IntelliJ では `chapter29` モジュールと **chapter29 tests** 実行設定を使用します。
ネイティブ実行を使うテストの前に、Make でランタイムと `sys` ライブラリをビルドしてください。
