# 最適化とバックエンドの調整

These measurements are from the numbered snapshots before the 2026-10 split.
In this historical table, Ch 24 means the retired combined SCCP snapshot and
Ch 25 means the retired combined modules/constructor/import snapshot.
They are not new measurements of the extracted chapters. The implementation
continues in [Separate Compilation](../chapter28b/README.md).

## RegAlloc の改良: 生存区間の面積と分割コスト

第24章はループ Phi の高頻度辺を分割する前に、低頻度のコピーを試しました。この章ではさらに、スピルがスケジュール済みコードのどれだけをレジスタ圧力から解放するかを推定し、新しいコピーの推定コストで割ります。callee-save 区間は関数全体を覆います。複製可能な定数は使用の近くで再構築できます。ループ内のコピーは高コストなので、`8^loopDepth` で重み付けします。特に多数の使用を持つ値については、低コストな近似です。

複数の固定レジスタ使用を直接分割する規則や、一部の複数定義区間をより深く分割する規則など、第25章のほかの衝突処理はここに残します。サイズのしきい値はヒューリスティックであり、最初の割り当て器へバックポートすべき一般的不変条件ではありません。共有される合法性修正は、引き続き最も早い該当章から始めます。最終監査は第20〜25章の固定隣接ノードの色バイアス、第20〜21章の狭いストアマスク、第21〜25章の ARM レジスタバンク／浮動小数点メモリエンコーディングを修正しました。

第21章の隣接コピー転送も引き継ぎます。次の命令だけで使うコピーは、オペランドがコピー元レジスタを受け入れ、命令の結果に結び付けられていなければ除去します。

現在の比較は、固定したテスト群を行、コンパイラの章を列にした
[英語版の測定表](optimizer-tuning.md#regalloc-improvements-live-range-area-and-split-cost) を参照してください。
各セルは ARM/RISC-V の実行命令数、そのうち割り当て器が生成したコピーなどの命令数、
x86 のループ重み付き移動数を示します。これらは異なる指標です。
対象、入力、除外項目は [測定方法](../regalloc-spills.md) にあります。
古い測定群の集計値と混ぜて比較しないでください。

クラス初期化、インライン化、エスケープ解析、ライブラリの変更もグラフを変えます。
章をまたぐ差を割り当て器だけの品質差と解釈せず、同じ入力とコンパイラで
ヒューリスティックを比較してください。
