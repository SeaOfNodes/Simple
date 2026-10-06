# 第17b章の文法の追加

[English](17-grammar.md) | 日本語

この文書は [原文](17-grammar.md) の日本語訳です。

[第17a章の宣言構文](../../chapter17a/docs/17-grammar.ja.md)から始めます。この章は、型推論、代入の短縮形、条件式、`for` ループを追加します。アクセスと束縛の権限の意味は変わりません。

```text
inferredDeclaration = ("var" | "val") ("!" | "~")? identifier "=" assignment
assignment          = expression (assignmentOperator assignment)?
assignmentOperator  = "=" | "+=" | "-=" | "*=" | "/=" | "&=" | "|=" | "^="
                    | "<<=" | ">>=" | ">>>="
expression          = bitwise ("?" assignment (":" assignment)?)?
prefixUpdate        = ("++" | "--") identifier
postfixUpdate       = lvalue ("++" | "--")
forStatement        = "for" "(" declarationOrExpression? ";" assignment? ";"
                      assignment? ")" statement
```

宣言には、コンマで区切った複数の名前を含められます。`var` は再代入を許し、`val` は束縛を固定します。両方とも初期化子の参照の権限を維持します。後置の更新はフィールドと配列要素もサポートします。前置の更新は、今のところ局所名をサポートします。評価順序については [README](../README.ja.md) を参照してください。
