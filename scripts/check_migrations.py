#!/usr/bin/env python3
"""Confere as migrações do Room sem emulador.

Cria o banco da v1 a partir de app/schemas/<db>/1.json, aplica em ordem as instruções
execSQL de Migrations.kt e compara o esquema resultante (tabelas, colunas, índices,
chaves estrangeiras) com o JSON da versão mais nova.
"""
import json, re, sqlite3, sys, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
schemas = root / "app/schemas/app.meubolso.data.db.AppDatabase"
mig_src = (root / "app/src/main/java/app/meubolso/data/db/Migrations.kt").read_text()

def load(v):
    return json.loads((schemas / f"{v}.json").read_text())["database"]

def create(db, schema):
    for e in schema["entities"]:
        t = e["tableName"]
        db.execute(e["createSql"].replace("${TABLE_NAME}", t))
        for i in e.get("indices", []):
            db.execute(i["createSql"].replace("${TABLE_NAME}", t))

def describe(db):
    out = {}
    tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'")]
    for t in tables:
        cols = sorted((r[1], r[2].upper(), r[3], r[4], r[5]) for r in db.execute(f"PRAGMA table_info(`{t}`)"))
        idx = sorted((r[1], r[2]) for r in db.execute(f"PRAGMA index_list(`{t}`)") if not r[1].startswith("sqlite_"))
        idx_cols = {n: [c[2] for c in db.execute(f"PRAGMA index_info(`{n}`)")] for n, _ in idx}
        fks = sorted((r[2], r[3], r[4], r[6]) for r in db.execute(f"PRAGMA foreign_key_list(`{t}`)"))
        out[t] = (cols, idx, idx_cols, fks)
    return out

versions = sorted(int(p.stem) for p in schemas.glob("*.json"))
latest = versions[-1]

migrated = sqlite3.connect(":memory:")
create(migrated, load(versions[0]))
# Blocos "Migration(a, b)" em ordem, cada um com seus execSQL("...").
blocks = re.split(r"object\s*:\s*Migration\(", mig_src)[1:]
for b in blocks:
    a, z = map(int, re.match(r"\s*(\d+)\s*,\s*(\d+)", b).groups())
    for sql in re.findall(r'execSQL\("((?:[^"\\]|\\.)*)"\)', b):
        migrated.execute(sql.encode().decode("unicode_escape"))
    print(f"aplicada migração {a} -> {z}")

fresh = sqlite3.connect(":memory:")
create(fresh, load(latest))

a, b = describe(migrated), describe(fresh)
ok = True
for t in sorted(set(a) | set(b)):
    if a.get(t) != b.get(t):
        ok = False
        print(f"DIFERENÇA em {t}:\n  migrado: {a.get(t)}\n  esperado: {b.get(t)}")
print("OK: esquema migrado == esquema v%d" % latest if ok else "FALHOU")
sys.exit(0 if ok else 1)
