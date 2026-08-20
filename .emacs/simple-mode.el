;;; simple-mode.el --- Major mode for Simple source files -*- lexical-binding: t; -*-

(require 'cc-mode)

(defconst simple-mode-primitive-types
  '("int" "i8" "i16" "i32" "i64" "u8" "u16" "u32" "u64"
    "byte" "bool" "flt" "f32" "f64" "val" "var")
  "Primitive and built-in type names in Simple.")

(defconst simple-mode-font-lock-keywords
  `((,(regexp-opt simple-mode-primitive-types 'symbols) . font-lock-type-face)
    (,(regexp-opt '("boolean" "long" "short" "char" "float" "double" "void") 'symbols) (0 nil t))
    ("\\_<new\\_>\\s-+\\(?:[[:alpha:]_][[:alnum:]_$]*\\.\\)*\\([[:alpha:]_][[:alnum:]_$]*\\)" (1 nil t)))
  "Additional Simple keywords using Java's type face.")

(defun simple-mode-set-indentation ()
  (setq-local c-basic-offset 4)
  (setq-local indent-tabs-mode nil))

(define-derived-mode simple-mode java-mode "Simple"
  "Major mode for Simple .smp source files."
  (simple-mode-set-indentation)
  (setq-local c-primitive-type-key
              (concat "\\(" (regexp-opt simple-mode-primitive-types) "\\)"
                      "\\([^[:alnum:]_$]\\|$\\)"))
  (setq-local font-lock-defaults
              `((,@java-font-lock-keywords ,@simple-mode-font-lock-keywords)
                nil nil ((?_ . "w") (?$ . "w"))
                c-beginning-of-syntax
                (font-lock-mark-block-function . c-mark-function)))
  (setq-local font-lock-keywords nil)
  (font-lock-set-defaults)
  (font-lock-mode 1))

(add-hook 'simple-mode-hook #'simple-mode-set-indentation)

(provide 'simple-mode)
;;; simple-mode.el ends here
