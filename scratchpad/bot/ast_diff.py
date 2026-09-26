# -*- coding: utf-8 -*-
"""AST diff: no lost names, no unexpected duplicates. / AST-сверка: нет потерянных имён, нет лишних дублей."""
import ast
import sys

before = ast.parse(open(sys.argv[1], encoding='utf-8').read())
after = ast.parse(open(sys.argv[2], encoding='utf-8').read())


def top_names(tree):
    names = {}
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names[node.name] = names.get(node.name, 0) + 1
        elif isinstance(node, ast.Assign):
            for t in node.targets:
                if isinstance(t, ast.Name):
                    names[t.id] = names.get(t.id, 0) + 1
    return names


b = top_names(before)
a = top_names(after)
lost = [n for n in b if n not in a]
added = [n for n in a if n not in b]
changed = {n: (b[n], a[n]) for n in b if n in a and b[n] != a[n]}
print('lost:', lost)
print('added:', added)
print('count-changed:', changed)
ok = not lost and not changed
print('AST_DIFF_OK' if ok else 'AST_DIFF_BAD')
sys.exit(0 if ok else 1)
