"""Génère les modèles intégrés de Verso (assets/modeles/*.json) et vérifie qu'ils tiennent dans le disque."""
import json, math, os, sys

OUT = sys.argv[1]
S = 13
C = S // 2

def on(x, y):
    return (x - C) ** 2 + (y - C) ** 2 <= 42

def grid():
    return [[0] * S for _ in range(S)]

def to_dessin(g):
    rows = []
    for y in range(S):
        r = ""
        for x in range(S):
            v = g[y][x]
            r += "." if v <= 0 else ("X" if v >= 9 else str(v))
        rows.append(r)
    return rows

def art(*rows):
    assert len(rows) == S, len(rows)
    g = grid()
    for y, r in enumerate(rows):
        assert len(r) == S, (y, r)
        for x, c in enumerate(r):
            g[y][x] = 9 if c == "X" else (int(c) if c.isdigit() else 0)
    return g

def check(name, g):
    for y in range(S):
        for x in range(S):
            if g[y][x] and not on(x, y):
                raise SystemExit(f"{name}: pixel hors matrice en ({x},{y})")

def write(idx, mid, nom, frames, dynamique=None):
    for i, (g, _) in enumerate(frames):
        check(f"{mid}#{i}", g)
    typ = "anime" if len(frames) > 1 else "statique"
    data = {"id": mid, "nom": nom, "type": typ}
    if dynamique:
        data["dynamique"] = dynamique
    data |= {"frames": [{"dessin": to_dessin(g), "dureeMs": d} for g, d in frames]}
    with open(os.path.join(OUT, f"{idx:02d}-{mid}.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

def line(g, x0, y0, x1, y1, v=9):
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n); y = round(y0 + (y1 - y0) * i / n)
        g[y][x] = max(g[y][x], v)

def ring(g, r, v=9):
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - C, y - C)
            if abs(d - r) < 0.5 and on(x, y):
                g[y][x] = v

# 1. Horloge : cadran + aiguilles (10 h 10, la pose des pubs de montres)
g = grid(); ring(g, 5.8, 4)
for a in range(12):
    t = math.radians(a * 30)
    x = round(C + 5.6 * math.sin(t)); y = round(C - 5.6 * math.cos(t))
    if on(x, y): g[y][x] = 9 if a % 3 == 0 else 5
line(g, C, C, C, C - 4)       # grande aiguille sur 12
line(g, C, C, C + 3, C)       # petite aiguille sur 3
g[C][C] = 9
# Modèle dynamique : l'app dessine l'heure réelle (Horloge.kt) ; cette image ne sert que d'aperçu de secours.
write(1, "horloge", "Horloge", [(g, 1000)], dynamique="horloge")

# 2. Cœur qui bat (double battement)
petit = art(
    ".............",
    ".............",
    ".............",
    "...XX...XX...",
    "..XXXX.XXXX..",
    "..XXXXXXXXX..",
    "..XXXXXXXXX..",
    "...XXXXXXX...",
    "....XXXXX....",
    ".....XXX.....",
    "......X......",
    ".............",
    ".............",
)
grand = art(
    ".............",
    ".............",
    "..XXX...XXX..",
    ".XXXXX.XXXXX.",
    ".XXXXXXXXXXX.",
    ".XXXXXXXXXXX.",
    ".XXXXXXXXXXX.",
    "..XXXXXXXXX..",
    "...XXXXXXX...",
    "....XXXXX....",
    ".....XXX.....",
    "......X......",
    ".............",
)
write(2, "coeur", "Cœur", [(petit, 140), (grand, 140), (petit, 140), (grand, 140), (petit, 700)])

# 3. Smiley
g = art(
    ".............",
    ".............",
    ".............",
    ".............",
    "....X...X....",
    "....X...X....",
    ".............",
    ".............",
    "...X.....X...",
    "....X...X....",
    ".....XXX.....",
    ".............",
    ".............",
)
ring(g, 5.8, 3)
write(3, "smiley", "Smiley", [(g, 1000)])

# 4. Étoile
g = art(
    ".............",
    ".............",
    "......X......",
    ".....XXX.....",
    ".....XXX.....",
    "..XXXXXXXXX..",
    "...XXXXXXX...",
    "....XXXXX....",
    "....XXXXX....",
    "...XXX.XXX...",
    "...XX...XX...",
    ".............",
    ".............",
)
# rognée au disque (les pointes latérales touchent le bord)
for y in range(S):
    for x in range(S):
        if not on(x, y): g[y][x] = 0
write(4, "etoile", "Étoile", [(g, 1000)])

# 5. Onde : sinusoïde qui défile
frames = []
N = 16
for f in range(N):
    g = grid()
    for x in range(S):
        yf = C + 3.2 * math.sin(2 * math.pi * (x / S + f / N))
        for y in range(S):
            d = abs(y - yf)
            v = 9 if d < 0.6 else (4 if d < 1.3 else 0)
            if v and on(x, y): g[y][x] = max(g[y][x], v)
    frames.append((g, 90))
write(5, "onde", "Onde", frames)

# 6. Pluie : gouttes à traînée, vitesses et départs décalés
drops = [(1, 0, 1), (3, 5, 1), (5, 9, 2), (6, 2, 1), (8, 7, 2), (10, 4, 1), (11, 11, 1), (4, 12, 2), (9, 1, 1), (2, 15, 1), (7, 13, 1)]
L = 18  # longueur du cycle : S + traînée + un peu d'air
frames = []
for f in range(L):
    g = grid()
    for (x, off, speed) in drops:
        head = (off + f * speed) % L - 3
        for k, v in enumerate((9, 5, 2)):
            y = head - k
            if 0 <= y < S and on(x, y):
                g[y][x] = max(g[y][x], v)
    frames.append((g, 80))
write(6, "pluie", "Pluie", frames)

# 7. Logo NC
g = art(
    ".............",
    ".............",
    ".............",
    ".............",
    ".X..X..XXX...",
    ".XX.X.X......",
    ".X.XX.X......",
    ".X..X.X......",
    ".X..X..XXX...",
    ".............",
    ".............",
    ".............",
    ".............",
)
# centrage horizontal : le motif fait 9 colonnes (1 → 9), on le décale de 1
g = [[0] + row[:-1] for row in g]
ring(g, 5.8, 3)
write(7, "nc", "Logo NC", [(g, 1000)])

# 8. Éclair
g = art(
    ".............",
    ".......XX....",
    "......XX.....",
    ".....XX......",
    "....XX.......",
    "...XXXXXXX...",
    ".......XX....",
    "......XX.....",
    ".....XX......",
    "....XX.......",
    "...XX........",
    ".............",
    ".............",
)
write(8, "eclair", "Éclair", [(g, 1000)])

# 9. Lune (croissant) + deux étoiles
g = grid()
for y in range(S):
    for x in range(S):
        if math.hypot(x - 6, y - 6) <= 4.6 and math.hypot(x - 8.2, y - 4.6) > 4.0:
            g[y][x] = 9
g[3][10] = 4; g[9][10] = 3; g[2][7] = 2
write(9, "lune", "Lune", [(g, 1000)])

# 10. Chargement : point qui tourne avec traînée
frames = []
N = 16
for f in range(N):
    g = grid()
    ring(g, 4.5, 1)
    for k, v in enumerate((9, 6, 4, 2)):
        t = 2 * math.pi * (f - k) / N
        x = round(C + 4.5 * math.sin(t)); y = round(C - 4.5 * math.cos(t))
        g[y][x] = max(g[y][x], v)
    frames.append((g, 70))
write(10, "chargement", "Chargement", frames)

print("OK")
