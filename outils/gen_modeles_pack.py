"""Pack de modèles de l'étape 6 (assets/modeles/2x-…json et suivants).

Tous les modèles sont calculés ici (formes, effets, persos originaux) puis rognés au disque
de la matrice. Graines fixes : relancer le script redonne exactement les mêmes fichiers.

Usage : python3 outils/gen_modeles_pack.py app/src/main/assets/modeles
"""
import json, math, os, random, sys

OUT = sys.argv[1]
S = 13
C = S // 2

def on(x, y):
    return (x - C) ** 2 + (y - C) ** 2 <= 42

def grid():
    return [[0] * S for _ in range(S)]

def put(g, x, y, v=9):
    x = int(round(x)); y = int(round(y))
    if 0 <= x < S and 0 <= y < S and on(x, y):
        g[y][x] = max(g[y][x], int(max(0, min(9, round(v)))))

def clip(g):
    return [[g[y][x] if on(x, y) else 0 for x in range(S)] for y in range(S)]

def art(*rows):
    assert len(rows) == S, len(rows)
    g = grid()
    for y, r in enumerate(rows):
        assert len(r) == S, (y, r, len(r))
        for x, c in enumerate(r):
            g[y][x] = 9 if c in "X#" else (int(c) if c.isdigit() else 0)
    return g

def copy(g):
    return [row[:] for row in g]

def merge(a, b):
    return [[max(a[y][x], b[y][x]) for x in range(S)] for y in range(S)]

def shift(g, dx, dy):
    h = grid()
    for y in range(S):
        for x in range(S):
            if g[y][x]:
                put(h, x + dx, y + dy, g[y][x])
    return h

def scale(g, k):
    return [[round(v * k) for v in row] for row in g]

def line(g, x0, y0, x1, y1, v=9):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(int(math.ceil(n)) + 1):
        t = i / math.ceil(n)
        put(g, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, v)

def ring(g, r, v=9, cx=C, cy=C, w=0.5):
    for y in range(S):
        for x in range(S):
            if abs(math.hypot(x - cx, y - cy) - r) < w:
                put(g, x, y, v)

def disc(g, cx, cy, r, v=9):
    for y in range(S):
        for x in range(S):
            if math.hypot(x - cx, y - cy) <= r:
                put(g, x, y, v)

def to_dessin(g):
    g = clip(g)
    return ["".join("." if v <= 0 else ("X" if v >= 9 else str(v)) for v in row) for row in g]

ECRITS = []

def write(idx, mid, nom, cat, frames):
    if isinstance(frames[0], list):
        frames = [(frames[0], 1000)]
    for g, d in frames:
        assert d >= 40, (mid, d)
    typ = "anime" if len(frames) > 1 else "statique"
    data = {"id": mid, "nom": nom, "categorie": cat, "type": typ,
            "frames": [{"dessin": to_dessin(g), "dureeMs": d} for g, d in frames]}
    with open(os.path.join(OUT, f"{idx:03d}-{mid}.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    ECRITS.append((idx, mid, nom, cat, len(frames)))

def anim(n, duree, fn):
    return [(fn(f), duree) for f in range(n)]

# Police 3×5 pour chiffres et petits textes
F3 = {
    "0": "XXX X.X X.X X.X XXX", "1": ".X. XX. .X. .X. XXX", "2": "XXX ..X XXX X.. XXX",
    "3": "XXX ..X .XX ..X XXX", "4": "X.X X.X XXX ..X ..X", "5": "XXX X.. XXX ..X XXX",
    "6": "XXX X.. XXX X.X XXX", "7": "XXX ..X ..X .X. .X.", "8": "XXX X.X XXX X.X XXX",
    "9": "XXX X.X XXX ..X XXX", "Z": "XXX ..X .X. X.. XXX",
}
# Police 5×7 pour le texte défilant
F5 = {
    "A": [".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"],
    "C": [".XXXX", "X....", "X....", "X....", "X....", "X....", ".XXXX"],
    "E": ["XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"],
    "H": ["X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"],
    "I": ["XXX", ".X.", ".X.", ".X.", ".X.", ".X.", "XXX"],
    "L": ["X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"],
    "O": [".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."],
    "R": ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
    "S": [".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."],
    "T": ["XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."],
    "U": ["X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."],
    "V": ["X...X", "X...X", "X...X", "X...X", "X...X", ".X.X.", "..X.."],
    "!": ["X", "X", "X", "X", "X", ".", "X"],
    "♥": [".X.X.", "XXXXX", "XXXXX", ".XXX.", "..X..", ".....", "....."],
    " ": ["...", "...", "...", "...", "...", "...", "..."],
}

def texte_colonnes(txt):
    cols = []
    for ch in txt:
        glyph = F5[ch]
        for x in range(len(glyph[0])):
            cols.append([glyph[y][x] == "X" for y in range(7)])
        cols.append([False] * 7)
    return cols

def defilant(txt, duree=90):
    cols = [[False] * 7] * S + texte_colonnes(txt)
    n = len(cols)
    frames = []
    for f in range(n):
        g = grid()
        for x in range(S):
            col = cols[(f + x) % n]
            for y in range(7):
                if col[y]:
                    put(g, x, 3 + y)
        frames.append((g, duree))
    return frames

def chiffre(g, ch, x0, y0, v=9):
    for y, ligne in enumerate(F3[ch].split()):
        for x, c in enumerate(ligne):
            if c == "X":
                put(g, x0 + x, y0 + y, v)

# ───────────────────────── EFFETS ─────────────────────────
i = 20

# Feu : flammes qui dansent (colonnes de hauteur variable, cœur plus vif, étincelles)
def feu():
    L = 48
    rng = random.Random(7)
    phases = [(rng.uniform(0, 6.3), rng.uniform(0, 6.3)) for _ in range(S)]
    etincelles = [(rng.randrange(3, 10), rng.randrange(L)) for _ in range(5)]
    def fr(f):
        g = grid()
        t = 2 * math.pi * f / L
        for x in range(S):
            p1, p2 = phases[x]
            bruit = 0.5 * math.sin(3 * t + p1) + 0.3 * math.sin(5 * t + p2) + 0.2 * math.sin(2 * t + x)
            h = (11.0 - abs(x - C) * 1.45) * (0.8 + 0.25 * bruit)
            for k in range(S):
                y = S - 1 - k
                if k < h:
                    coeur = 1 - k / max(h, 0.1)
                    put(g, x, y, 3 + 6 * coeur ** 0.5 if abs(x - C) < 4 else 2 + 6 * coeur ** 0.7)
        for x, dep in etincelles:
            e = ((f + dep) % L) / L
            if e < 0.35:
                put(g, x + math.sin(e * 20), 4 - e * 14, 9 * (1 - e / 0.35))
        return g
    return anim(L, 70, fr)
write(i, "feu", "Feu", "Effets", feu()); i += 1

# Vagues : deux houles superposées qui défilent
def vagues():
    N = 24
    def fr(f):
        g = grid()
        for x in range(S):
            for k, (amp, base, lum, vit) in enumerate(((1.2, 5.5, 9, 1), (1.0, 8.0, 5, -1))):
                y0 = base + amp * math.sin(2 * math.pi * (x / 9 + vit * f / N))
                for y in range(S):
                    if y >= y0:
                        put(g, x, y, lum if y - y0 < 1 else lum * 0.35)
        return g
    return anim(N, 90, fr)
write(i, "vagues", "Vagues", "Effets", vagues()); i += 1

# Spirale qui tourne
def spirale():
    N = 24
    def fr(f):
        g = grid()
        for y in range(S):
            for x in range(S):
                dx, dy = x - C, y - C
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = math.cos(2 * (a - 2 * math.pi * f / N) + r * 0.9)
                if v > 0.3:
                    put(g, x, y, 3 + 6 * v)
        return g
    return anim(N, 70, fr)
write(i, "spirale", "Spirale", "Effets", spirale()); i += 1

# Champ d'étoiles (hyperespace) : points qui fuient du centre
def hyperespace():
    rng = random.Random(3)
    L = 40
    etoiles = [(rng.uniform(0, 2 * math.pi), rng.uniform(0, 1)) for _ in range(22)]
    def fr(f):
        g = grid()
        for a, phase in etoiles:
            t = (phase + f / L) % 1
            r = 0.5 + 7 * t ** 2
            put(g, C + r * math.cos(a), C + r * math.sin(a), 2 + 7 * t)
            r2 = 0.5 + 7 * max(0, t - 0.06) ** 2
            if t > 0.4:
                put(g, C + r2 * math.cos(a), C + r2 * math.sin(a), 2 + 3 * t)
        return g
    return anim(L, 60, fr)
write(i, "hyperespace", "Hyperespace", "Effets", hyperespace()); i += 1

# Pluie de code : colonnes qui tombent à des vitesses différentes
def code():
    rng = random.Random(11)
    L = 52
    cols = [(rng.choice([1, 2]), rng.randrange(L), rng.choice([4, 5, 6])) for _ in range(S)]
    def fr(f):
        g = grid()
        for x, (vit, dep, lg) in enumerate(cols):
            periode = L // vit if L % vit == 0 else L
            tete = ((f + dep) * vit) % (S + 12) - 2
            for k in range(lg):
                put(g, x, tete - k, 9 if k == 0 else 6 - k)
        return g
    return anim(L, 80, fr)
write(i, "code", "Pluie de code", "Effets", code()); i += 1

# Tunnel : anneaux qui s'élargissent depuis le centre
def tunnel():
    N = 16
    def fr(f):
        g = grid()
        for k in range(3):
            r = ((f / N + k / 3) % 1) * 7.5
            ring(g, r, 3 + 6 * (r / 7.5), w=0.55)
        return g
    return anim(N, 70, fr)
write(i, "tunnel", "Tunnel", "Effets", tunnel()); i += 1

# Radar : aiguille qui balaie avec traînée, points détectés qui s'éteignent
def radar():
    N = 36
    cibles = [(2.0, 4.0), (1.0, 2.5), (4.2, 5.0), (5.4, 3.0)]   # (angle, rayon)
    def fr(f):
        g = grid()
        ring(g, 6.2, 2)
        a0 = 2 * math.pi * f / N
        for k in range(6):
            a = a0 - k * 0.18
            for r in range(1, 7):
                put(g, C + r * math.cos(a), C + r * math.sin(a), 9 - k * 1.5)
        for ca, cr in cibles:
            ecart = (a0 - ca) % (2 * math.pi)
            if ecart < 3.5:
                put(g, C + cr * math.cos(ca), C + cr * math.sin(ca), 9 - ecart * 2.2)
        put(g, C, C, 9)
        return g
    return anim(N, 60, fr)
write(i, "radar", "Radar", "Effets", radar()); i += 1

# Ondes : ricochets d'une goutte, depuis le centre
def ondes():
    N = 30
    def fr(f):
        g = grid()
        for k in range(2):
            t = (f / N + k / 2) % 1
            r = t * 8
            ring(g, r, 9 * (1 - t) + 1, w=0.5)
        if f % N < 3:
            put(g, C, C, 9)
        return g
    return anim(N, 60, fr)
write(i, "ondes", "Ondes", "Effets", ondes()); i += 1

# Plasma : mélange de sinus, effet démo des années 90
def plasma():
    N = 32
    def fr(f):
        g = grid()
        t = 2 * math.pi * f / N
        for y in range(S):
            for x in range(S):
                v = (math.sin(x * 0.55 + t) + math.sin(y * 0.45 - t) +
                     math.sin((x + y) * 0.35 + 2 * t) + math.sin(math.hypot(x - C, y - C) * 0.7 - t))
                k = max(0.0, (v + 4) / 8 - 0.25) / 0.75
                put(g, x, y, k ** 1.6 * 9)
        return g
    return anim(N, 80, fr)
write(i, "plasma", "Plasma", "Effets", plasma()); i += 1

# Respiration : disque qui gonfle et dégonfle doucement
def respiration():
    N = 40
    def fr(f):
        g = grid()
        k = 0.5 - 0.5 * math.cos(2 * math.pi * f / N)
        r = 1.5 + 4.5 * k
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - C, y - C)
                if d <= r:
                    put(g, x, y, 3 + 6 * (1 - d / (r + 0.5)) * (0.4 + 0.6 * k) + 1)
        return g
    return anim(N, 80, fr)
write(i, "respiration", "Respiration", "Effets", respiration()); i += 1

# Orbites : une planète, deux lunes
def orbites():
    N = 48
    def fr(f):
        g = grid()
        disc(g, C, C, 1.3, 9)
        ring(g, 3.6, 1); ring(g, 5.6, 1)
        a = 2 * math.pi * f / N
        put(g, C + 3.6 * math.cos(2 * a), C + 3.6 * math.sin(2 * a), 9)
        b = -a + 1
        put(g, C + 5.6 * math.cos(b), C + 5.6 * math.sin(b), 9)
        put(g, C + 5.6 * math.cos(b - 0.2), C + 5.6 * math.sin(b - 0.2), 4)
        return g
    return anim(N, 70, fr)
write(i, "orbites", "Orbites", "Effets", orbites()); i += 1

# Feu d'artifice : fusée, explosion, retombée
def artifice():
    frames = []
    for depart, cx, cy in ((0, 6, 4), (22, 4, 6), (11, 8, 5)):
        pass
    L = 44
    tirs = [(0, 6, 4, 1.0), (15, 4, 6, 0.8), (29, 8, 5, 0.9)]
    def fr(f):
        g = grid()
        for d, cx, cy, taille in tirs:
            t = (f - d) % L
            if t < 6:                                   # montée
                put(g, cx, 12 - (12 - cy) * t / 6, 9)
                put(g, cx, 12 - (12 - cy) * t / 6 + 1, 4)
            elif t < 18:                                # explosion
                k = (t - 6) / 12
                for n in range(10):
                    a = 2 * math.pi * n / 10
                    r = 4.2 * taille * math.sqrt(k)
                    put(g, cx + r * math.cos(a), cy + r * math.sin(a) + 2 * k * k, 9 * (1 - k) + 1)
        return g
    return anim(L, 70, fr)
write(i, "artifice", "Feu d'artifice", "Effets", artifice()); i += 1

# Égaliseur : barres façon musique
def egaliseur():
    rng = random.Random(5)
    N = 32
    phases = [rng.uniform(0, 2 * math.pi) for _ in range(6)]
    def fr(f):
        g = grid()
        for b in range(4):
            x = 1 + b * 3
            h = 2 + 7 * abs(math.sin(2 * math.pi * f / N * (1 + b % 3) + phases[b])) ** 1.5
            for y in range(int(round(h))):
                put(g, x, 10 - y, 9 if y < h - 1 else 6)
                put(g, x + 1, 10 - y, 9 if y < h - 1 else 6)
        return g
    return anim(N, 90, fr)
write(i, "egaliseur", "Égaliseur", "Effets", egaliseur()); i += 1

# ADN : double hélice qui tourne
def adn():
    N = 24
    def fr(f):
        g = grid()
        for y in range(S):
            a = 2 * math.pi * (y / 9 + f / N)
            x1 = C + 3.5 * math.sin(a); x2 = C - 3.5 * math.sin(a)
            devant = math.cos(a) > 0
            put(g, x1, y, 9 if devant else 4)
            put(g, x2, y, 4 if devant else 9)
            if y % 2 == 0:
                for t in (0.33, 0.66):
                    put(g, x1 + (x2 - x1) * t, y, 2)
        return g
    return anim(N, 80, fr)
write(i, "adn", "ADN", "Effets", adn()); i += 1

# Jeu de la vie : une colonie qui évolue, puis renaît (fondu)
def vie():
    rng = random.Random(42)
    cellules = {(x, y) for y in range(S) for x in range(S) if on(x, y) and rng.random() < 0.38}
    etats = []
    for _ in range(60):
        etats.append(cellules)
        nouv = set()
        for y in range(S):
            for x in range(S):
                if not on(x, y):
                    continue
                n = sum((x + dx, y + dy) in cellules for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy)
                if n == 3 or (n == 2 and (x, y) in cellules):
                    nouv.add((x, y))
        cellules = nouv
    frames = []
    for k, e in enumerate(etats):
        g = grid()
        fondu = min(1, (len(etats) - k) / 6)
        for x, y in e:
            put(g, x, y, 9 * fondu)
        frames.append((g, 160))
    return frames
write(i, "vie", "Jeu de la vie", "Effets", vie()); i += 1

# Bulles qui montent en oscillant
def bulles():
    rng = random.Random(9)
    L = 60
    bl = [(rng.uniform(2, 10), rng.randrange(L), rng.choice([0, 0, 1])) for _ in range(7)]
    def fr(f):
        g = grid()
        for x0, dep, gros in bl:
            t = ((f + dep) % L) / L
            y = 13 - t * 16
            x = x0 + math.sin(t * 12) * 0.8
            if gros:
                for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                    put(g, x + dx, y + dy, 7)
            else:
                put(g, x, y, 9)
        return g
    return anim(L, 80, fr)
write(i, "bulles", "Bulles", "Effets", bulles()); i += 1

# Scanner : barre verticale qui va et vient
def scanner():
    N = 28
    def fr(f):
        g = grid()
        p = (1 - math.cos(2 * math.pi * f / N)) / 2 * 12
        for x in range(S):
            d = abs(x - p)
            for y in range(S):
                put(g, x, y, max(0, 9 - d * 3.5))
        return g
    return anim(N, 60, fr)
write(i, "scanner", "Scanner", "Effets", scanner()); i += 1

# Damier qui glisse
def damier():
    N = 8
    def fr(f):
        g = grid()
        for y in range(S):
            for x in range(S):
                if ((x + f // 2) // 2 + (y + f // 2) // 2) % 2 == 0:
                    put(g, x, y, 7)
        return g
    return anim(N, 120, fr)
write(i, "damier", "Damier", "Effets", damier()); i += 1

# ───────────────────────── MÉTÉO ─────────────────────────
i = 40

SOLEIL_C = (6, 6)
def soleil_frame(f, N, cx=6, cy=6, r=2.3, rayons=8, lg=(3.7, 5.6)):
    g = grid()
    disc(g, cx, cy, r, 9)
    rot = 2 * math.pi * f / N / rayons
    for k in range(rayons):
        a = 2 * math.pi * k / rayons + rot
        for t in (0, 1):
            rr = lg[0] + t * (lg[1] - lg[0])
            put(g, cx + rr * math.cos(a), cy + rr * math.sin(a), 9 if t == 0 else 5)
    return g

write(i, "soleil", "Soleil", "Météo", anim(24, 90, lambda f: soleil_frame(f, 24))); i += 1

NUAGE = art(
    ".............",
    ".............",
    ".............",
    ".............",
    ".....XXX.....",
    "....XXXXX....",
    "..XXXXXXXXX..",
    ".XXXXXXXXXXX.",
    ".XXXXXXXXXXX.",
    "..XXXXXXXXX..",
    ".............",
    ".............",
    ".............",
)

def nuage_glisse(f, N):
    dx = round(math.sin(2 * math.pi * f / N) * 1)
    return shift(NUAGE, dx, 0)
write(i, "nuage", "Nuageux", "Météo", anim(24, 160, lambda f: nuage_glisse(f, 24))); i += 1

def eclaircie(f):
    g = soleil_frame(f, 24, cx=8, cy=4, r=1.8, rayons=8, lg=(3.0, 4.2))
    n = shift(NUAGE, -1, 2)
    # le nuage passe devant : on creuse le soleil sur la forme du nuage + 1 LED de marge
    for y in range(S):
        for x in range(S):
            if any(0 <= x + dx < S and 0 <= y + dy < S and n[y + dy][x + dx]
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                g[y][x] = 0
    return merge(g, n)
write(i, "eclaircie", "Éclaircies", "Météo", anim(24, 110, eclaircie)); i += 1

def orage(f):
    g = scale(shift(NUAGE, 0, -2), 0.6)
    flash = f in (8, 9, 11)
    if flash:
        g = shift(NUAGE, 0, -2)
        eclair = [(7, 7), (6, 8), (6, 9), (7, 9), (6, 10), (5, 11)]
        for x, y in eclair:
            put(g, x, y, 9)
    for k, x in enumerate((3, 6, 9)):
        y = 9 + (f + k * 2) % 4
        put(g, x, y, 4)
    return g
write(i, "orage", "Orage", "Météo", anim(20, 100, orage)); i += 1

def neige_meteo(f):
    g = scale(shift(NUAGE, 0, -3), 0.7)
    for k, (x, dep) in enumerate(((3, 0), (6, 3), (9, 1), (4, 5), (8, 6))):
        y = 7 + ((f + dep * 2) % 12) / 2
        put(g, x + (1 if (f // 3 + k) % 2 else 0), y, 9)
    return g
write(i, "neige-nuage", "Neige", "Météo", anim(24, 150, neige_meteo)); i += 1

def vent(f):
    g = grid()
    for k, (y, lg, vit) in enumerate(((3, 6, 1), (6, 8, 2), (9, 5, 1))):
        x0 = ((f * vit + k * 7) % 22) - 8
        for d in range(lg):
            put(g, x0 + d, y + (1 if d == lg - 1 and k != 1 else 0), 3 + 6 * d / lg)
    return g
write(i, "vent", "Vent", "Météo", anim(22, 80, vent)); i += 1

FLOCON = art(
    ".............",
    "......X......",
    "....X.X.X....",
    ".....XXX.....",
    "..X...X...X..",
    "...X..X..X...",
    ".XXXXXXXXXXX.",
    "...X..X..X...",
    "..X...X...X..",
    ".....XXX.....",
    "....X.X.X....",
    "......X......",
    ".............",
)
write(i, "flocon", "Flocon", "Météo", [FLOCON]); i += 1

def arc_en_ciel():
    g = grid()
    for r, v in ((5.6, 9), (4.4, 6), (3.2, 3)):
        for y in range(S):
            for x in range(S):
                if y <= 8 and abs(math.hypot(x - C, y - 8.5) - r) < 0.55:
                    put(g, x, y, v)
    return g
write(i, "arc-en-ciel", "Arc-en-ciel", "Météo", [arc_en_ciel()]); i += 1

def thermometre(f):
    g = art(
        ".............",
        ".....XXX.....",
        ".....X.X.....",
        ".....X.X.....",
        ".....X.X.....",
        ".....X.X.....",
        ".....X.X.....",
        ".....X.X.....",
        "....XX.XX....",
        "....X...X....",
        "....X...X....",
        ".....XXX.....",
        ".............",
    )
    niveau = 2 + round(4 * (0.5 - 0.5 * math.cos(2 * math.pi * f / 30)))
    for y in range(8 - niveau, 11):
        put(g, 6, y, 9)
    for y in (9, 10):
        for x in (5, 6, 7):
            put(g, x, y, 9)
    return g
write(i, "thermometre", "Thermomètre", "Météo", anim(30, 120, thermometre)); i += 1

# ───────────────────────── ICÔNES ─────────────────────────
i = 55

def batterie(f):
    g = art(
        ".............",
        ".............",
        ".............",
        ".XXXXXXXXXX..",
        ".X........X..",
        ".X........XX.",
        ".X........XX.",
        ".X........XX.",
        ".X........X..",
        ".XXXXXXXXXX..",
        ".............",
        ".............",
        ".............",
    )
    n = f % 9
    for k in range(min(n, 8)):
        for y in range(5, 8):
            put(g, 2 + k, y, 9 if k < n - 1 or f % 9 == 8 else 5)
    return g
write(i, "batterie", "Batterie", "Icônes", anim(9, 260, batterie)); i += 1

def wifi(f):
    g = grid()
    n = f % 5
    for k, r in enumerate((1.6, 3.6, 5.6)):
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - C, y - 10)
                a = math.atan2(y - 10, x - C)
                if abs(d - r) < 0.6 and -2.45 < a < -0.69:
                    put(g, x, y, 9 if k < n else 3)
    put(g, C, 10, 9)
    return g
write(i, "wifi", "Wi-Fi", "Icônes", anim(5, 300, wifi)); i += 1

NOTE = art(
    ".............",
    ".............",
    "......XXXXX..",
    "......XXXXX..",
    "......X...X..",
    "......X...X..",
    "......X...X..",
    "......X...X..",
    "....XXX.XXX..",
    "...XXXXXXXX..",
    "...XXX..XXX..",
    ".............",
    ".............",
)
def musique(f):
    dy = [0, -1, -1, 0, 0, 0][f % 6]
    return shift(NOTE, -1, dy)
write(i, "musique", "Musique", "Icônes", anim(6, 140, musique)); i += 1

CLOCHE = art(
    ".............",
    "......X......",
    ".....XXX.....",
    "....XXXXX....",
    "....XXXXX....",
    "....XXXXX....",
    "...XXXXXXX...",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    "..XXXXXXXXX..",
    ".............",
    "......X......",
    ".............",
)
def cloche(f):
    seq = [0, 1, 0, -1, 0, 1, 0, -1, 0, 0, 0, 0, 0, 0]
    dx = seq[f % len(seq)]
    g = grid()
    for y in range(S):
        for x in range(S):
            if CLOCHE[y][x]:
                # le haut reste accroché, le bas balance
                put(g, x + (dx if y >= 6 else 0), y, 9)
    return g
write(i, "notification", "Notification", "Icônes", anim(14, 90, cloche)); i += 1

write(i, "cadenas", "Cadenas", "Icônes", [art(
    ".............",
    ".............",
    ".....XXX.....",
    "....X...X....",
    "....X...X....",
    "....X...X....",
    "...XXXXXXX...",
    "...XXXXXXX...",
    "...XXX.XXX...",
    "...XXX.XXX...",
    "...XXXXXXX...",
    ".............",
    ".............",
)]); i += 1

write(i, "maison", "Maison", "Icônes", [art(
    ".............",
    ".............",
    "......X......",
    ".....XXX.....",
    "....XX.XX....",
    "...XX...XX...",
    "..XXXXXXXXX..",
    "...X.....X...",
    "...X.XX..X...",
    "...X.XX..X...",
    "...XXXXXXX...",
    ".............",
    ".............",
)]); i += 1

def coche(f):
    pts = [(3, 7), (4, 8), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4)]
    g = grid()
    ring(g, 5.8, 3)
    n = min(len(pts), f + 1)
    for x, y in pts[:n]:
        put(g, x, y, 9)
    return g
write(i, "valide", "Validé", "Icônes", [(coche(f), 70 if f < 7 else 1500) for f in range(8)]); i += 1

def croix():
    g = grid(); ring(g, 5.8, 3)
    line(g, 4, 4, 8, 8); line(g, 8, 4, 4, 8)
    return g
write(i, "refuse", "Refusé", "Icônes", [croix()]); i += 1

def lecture():
    g = grid()
    for y in range(3, 10):
        w = 4 - abs(y - 6)
        for x in range(4, 4 + int(w * 1.6) + 1):
            put(g, x, y, 9)
    return g
write(i, "lecture", "Lecture", "Icônes", [lecture()]); i += 1

def pause():
    g = grid()
    for y in range(3, 10):
        for x in (4, 5, 7, 8):
            put(g, x, y, 9)
    return g
write(i, "pause", "Pause", "Icônes", [pause()]); i += 1

ENVELOPPE = art(
    ".............",
    ".............",
    ".............",
    ".XXXXXXXXXXX.",
    ".XX.......XX.",
    ".X.X.....X.X.",
    ".X..X...X..X.",
    ".X...XXX...X.",
    ".X.........X.",
    ".XXXXXXXXXXX.",
    ".............",
    ".............",
    ".............",
)
def message(f):
    return ENVELOPPE if f % 2 == 0 else scale(ENVELOPPE, 0.45)
write(i, "message", "Message", "Icônes", [(message(f), d) for f, d in enumerate((900, 150, 150, 150))]); i += 1

def position(f):
    g = art(
        ".............",
        "....XXXXX....",
        "...XXXXXXX...",
        "..XXX...XXX..",
        "..XX.....XX..",
        "..XXX...XXX..",
        "...XXXXXXX...",
        "....XXXXX....",
        ".....XXX.....",
        "......X......",
        ".............",
        ".............",
        ".............",
    )
    dy = [0, -1, -1, 0][f % 4]
    g = shift(g, 0, dy)
    put(g, 5, 11, 3); put(g, 6, 11, 5); put(g, 7, 11, 3)
    return g
write(i, "position", "Position", "Icônes", anim(8, 150, position)); i += 1

def sablier(f):
    N = 24
    g = art(
        ".............",
        "...XXXXXXX...",
        "...X.....X...",
        "....X...X....",
        ".....X.X.....",
        "......X......",
        "......X......",
        ".....X.X.....",
        "....X...X....",
        "...X.....X...",
        "...XXXXXXX...",
        ".............",
        ".............",
    )
    k = f / (N - 1)
    haut = [(4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (5, 3), (6, 3), (7, 3), (6, 4)]
    bas = [(4, 9), (5, 9), (6, 9), (7, 9), (8, 9), (5, 8), (6, 8), (7, 8), (6, 7)]
    reste = round(len(haut) * (1 - k))
    for x, y in haut[len(haut) - reste:]:
        put(g, x, y, 6)
    for x, y in bas[:len(bas) - reste]:
        put(g, x, y, 6)
    if 0 < reste < len(haut):
        put(g, 6, 5 + f % 2, 9)
    return g
write(i, "sablier", "Sablier", "Icônes", anim(24, 160, sablier)); i += 1

def cible(f):
    g = grid()
    ring(g, 5.6, 4); ring(g, 3.2, 6)
    disc(g, C, C, 0.8 + (f % 2) * 0.6, 9)
    return g
write(i, "cible", "Cible", "Icônes", anim(2, 500, cible)); i += 1

def telephone(f):
    g = art(
        ".............",
        "....XXXXX....",
        "....X...X....",
        "....X...X....",
        "....X...X....",
        "....X...X....",
        "....X...X....",
        "....X...X....",
        "....X...X....",
        "....XX.XX....",
        "....XXXXX....",
        ".............",
        ".............",
    )
    if f % 4 in (0, 1):
        d = 1 if f % 4 == 0 else -1
        g = shift(g, d, 0)
        put(g, 2, 4, 5); put(g, 10, 4, 5); put(g, 2, 7, 5); put(g, 10, 7, 5)
    return g
write(i, "appel", "Appel", "Icônes", [(telephone(f), 80) for f in range(4)] + [(telephone(3), 700)]); i += 1

# ───────────────────────── ÉMOTIONS ─────────────────────────
i = 75

def visage(yeux, bouche, extras=()):
    g = grid()
    ring(g, 5.8, 3)
    for x, y, v in yeux + bouche + list(extras):
        put(g, x, y, v)
    return g

YEUX_OUVERTS = [(4, 4, 9), (4, 5, 9), (8, 4, 9), (8, 5, 9)]
YEUX_FERMES = [(3, 5, 7), (4, 5, 9), (5, 5, 7), (7, 5, 7), (8, 5, 9), (9, 5, 7)]
SOURIRE = [(3, 8, 9), (4, 9, 9), (5, 10, 9), (6, 10, 9), (7, 10, 9), (8, 9, 9), (9, 8, 9)]

write(i, "cligne", "Clin d'œil", "Émotions", [
    (visage(YEUX_OUVERTS, SOURIRE), 1800),
    (visage([(4, 4, 9), (4, 5, 9), (7, 5, 9), (8, 5, 9), (9, 5, 9)], SOURIRE), 400),
]); i += 1

write(i, "regard", "Regard", "Émotions", [
    (visage([(x + dx, y, 9) for x, y, _ in YEUX_OUVERTS], [(5, 9, 6), (6, 9, 6), (7, 9, 6)]), d)
    for dx, d in ((0, 900), (-1, 700), (0, 300), (1, 700), (0, 900))
] + [(visage(YEUX_FERMES, [(5, 9, 6), (6, 9, 6), (7, 9, 6)]), 150)]); i += 1

write(i, "triste", "Triste", "Émotions", [
    (visage(YEUX_OUVERTS, [(4, 10, 9), (5, 9, 9), (6, 9, 9), (7, 9, 9), (8, 10, 9)],
            [(3, 6 + k, 5)] if k < 4 else []), 260) for k in range(6)
]); i += 1

write(i, "surpris", "Surpris", "Émotions", [visage(
    [(4, 3, 9), (4, 4, 9), (8, 3, 9), (8, 4, 9)],
    [(5, 8, 9), (6, 8, 9), (7, 8, 9), (5, 9, 9), (7, 9, 9), (5, 10, 9), (6, 10, 9), (7, 10, 9)],
)]); i += 1

def amoureux(f):
    k = 9 if f % 2 == 0 else 6
    yeux = []
    for cx in (4, 8):
        for dx, dy in ((-1, 0), (1, 0), (-1, -1), (1, -1), (0, 0), (0, 1)):
            if not (dy == -1 and dx == 0):
                yeux.append((cx + dx, 5 + dy, k))
    return visage(yeux, SOURIRE)
write(i, "amoureux", "Amoureux", "Émotions", anim(2, 350, amoureux)); i += 1

def dodo(f):
    g = visage(YEUX_FERMES, [(5, 9, 6), (6, 9, 6), (7, 9, 6)])
    N = 18
    for k in range(2):
        t = ((f + k * N // 2) % N) / N
        chiffre(g, "Z", 8 + round(t * 2), 2 - round(t * 3), 9 * (1 - t) + 1)
    return g
write(i, "dodo", "Dodo", "Émotions", anim(18, 140, dodo)); i += 1

def rire(f):
    bouche = [(3, 8, 9), (4, 8, 9), (5, 8, 9), (6, 8, 9), (7, 8, 9), (8, 8, 9), (9, 8, 9),
              (4, 9, 9), (5, 10, 9), (6, 10, 9), (7, 10, 9), (8, 9, 9)]
    yeux = [(3, 5, 9), (4, 4, 9), (5, 5, 9), (7, 5, 9), (8, 4, 9), (9, 5, 9)]
    g = visage(yeux, bouche)
    return shift(g, 0, -1 if f % 2 else 0)
write(i, "rire", "Mort de rire", "Émotions", anim(6, 120, rire)); i += 1

write(i, "colere", "Colère", "Émotions", [(visage(
    [(3, 3, 9), (4, 4, 9), (4, 5, 9), (9, 3, 9), (8, 4, 9), (8, 5, 9)],
    [(4, 9, 9), (5, 9, 9), (6, 9, 9), (7, 9, 9), (8, 9, 9)],
), 1000)]); i += 1

def coeur_brise(f):
    g = art(
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
    fissure = [(6, 3), (5, 4), (6, 5), (7, 6), (6, 7), (5, 8), (6, 9), (6, 10)]
    for x, y in fissure:
        g[y][x] = 0
    if f >= 2:
        ecart = min(f - 1, 2)
        h = grid()
        for y in range(S):
            for x in range(S):
                if g[y][x]:
                    cote = -1 if x < 6 or (x == 6 and y in (3, 5)) else 1
                    put(h, x + cote * (ecart // 2 if x != 6 else 1), y + (ecart // 2), 9)
        return h
    return g
write(i, "coeur-brise", "Cœur brisé", "Émotions",
      [(coeur_brise(0), 1200), (coeur_brise(1), 120), (coeur_brise(2), 120), (coeur_brise(3), 1500)]); i += 1

def bisou(f):
    g = visage([(3, 5, 9), (4, 4, 9), (5, 5, 9), (8, 4, 9), (8, 5, 9)],
               [(6, 8, 9), (7, 9, 9), (6, 10, 9)])
    N = 16
    t = (f % N) / N
    cx, cy = 9 + t * 2, 9 - t * 6
    for dx, dy in ((0, 0), (-1, -1), (1, -1)):
        put(g, cx + dx, cy + dy, 9 * (1 - t) + 1)
    return g
write(i, "bisou", "Bisou", "Émotions", anim(16, 110, bisou)); i += 1

def ok_main():
    return art(
        ".............",
        "......XX.....",
        "......XX.....",
        "......XX.....",
        "......XX.....",
        "..XX.XXXXXX..",
        "..XX.XXXXXXX.",
        "..XX.XXXXXXX.",
        "..XX.XXXXXXX.",
        "..XX.XXXXXX..",
        "..XX.XXXXX...",
        ".............",
        ".............",
    )
write(i, "pouce", "Pouce levé", "Émotions", [ok_main()]); i += 1

# ───────────────────────── PERSOS (originaux) ─────────────────────────
i = 90

FANTOME = art(
    ".............",
    ".....XXX.....",
    "....XXXXX....",
    "...XXXXXXX...",
    "...X..X..X...",
    "...X..X..X...",
    "...XXXXXXX...",
    "...XXXXXXX...",
    "...XXXXXXX...",
    "...XXXXXXX...",
    "...X.X.X.X...",
    ".............",
    ".............",
)
def fantome(f):
    dy = round(math.sin(2 * math.pi * f / 16) * 1)
    g = shift(FANTOME, 0, dy)
    if f % 4 >= 2:   # la bordure ondule
        for x in (3, 5, 7, 9):
            for y in range(S):
                if g[y][x] and (y + 1 >= S or not FANTOME[min(y - dy + 1, S - 1)][x]) and y > 8:
                    g[y][x] = 0
                    put(g, x + 1, y, 9)
    return g
write(i, "fantome", "Fantôme", "Persos", anim(16, 110, fantome)); i += 1

CHAT = art(
    ".............",
    "..X.......X..",
    "..XX.....XX..",
    "..XXXXXXXXX..",
    ".XXXXXXXXXXX.",
    ".XX..XXX..XX.",
    ".XX..XXX..XX.",
    ".XXXXX.XXXXX.",
    "XXXXXX.XXXXXX",
    ".XXXX...XXXX.",
    "..XXXXXXXXX..",
    "....XXXXX....",
    ".............",
)
def chat(f):
    g = copy(CHAT)
    if f == 1:
        for x in (3, 4, 8, 9):
            g[5][x] = 9
    return g
write(i, "chat", "Chat", "Persos", [(chat(0), 2200), (chat(1), 160)]); i += 1

ALIEN = art(
    ".............",
    "...X.....X...",
    "....X...X....",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    ".XX..XXX..XX.",
    ".X....X....X.",
    ".XX..XXX..XX.",
    "..XXXXXXXXX..",
    "...XXX.XXX...",
    "....XXXXX....",
    ".....XXX.....",
    ".............",
)
def alien(f):
    g = copy(ALIEN)
    if f % 2:
        g[1][3] = 0; g[1][9] = 0; g[1][2] = 9; g[1][10] = 9
    return g
write(i, "alien", "Alien", "Persos", anim(2, 400, alien)); i += 1

def robot(f):
    g = art(
        ".............",
        "......X......",
        "......X......",
        "..XXXXXXXXX..",
        "..X.......X..",
        ".XX.......XX.",
        ".XX.......XX.",
        "..X.......X..",
        "..X..XXX..X..",
        "..X.......X..",
        "..XXXXXXXXX..",
        ".............",
        ".............",
    )
    p = [0, 1, 2, 3, 4, 3, 2, 1][f % 8]
    for k in range(3):
        put(g, 3 + p + k, 5, 9 if k == 1 else 4)
        put(g, 3 + p + k, 6, 9 if k == 1 else 4)
    put(g, 6, 1, 9 if f % 4 < 2 else 3)
    return g
write(i, "robot", "Robot", "Persos", anim(8, 120, robot)); i += 1

def chauve_souris(f):
    haut = art(
        ".............",
        ".............",
        "X...........X",
        "XX....X.X..XX",
        "XXX...XXX.XXX",
        "XXXX.XXXXXXXX",
        ".XXXXX.X.XXX.",
        "..XXXXXXXXX..",
        "...X.XXXXX...",
        ".......X.....",
        ".............",
        ".............",
        ".............",
    )
    bas = art(
        ".............",
        ".............",
        ".............",
        "......X.X....",
        "......XXX....",
        ".....XXXXX...",
        "...XXX.X.XX..",
        "..XXXXXXXXXX.",
        ".XXX.XXXXX.XX",
        ".XX....X...XX",
        ".X..........X",
        ".............",
        ".............",
    )
    return shift(haut if f % 2 == 0 else bas, 0, 1 if f % 4 >= 2 else 0)
write(i, "chauve-souris", "Chauve-souris", "Persos", anim(4, 150, chauve_souris)); i += 1

POISSON = art(
    ".............",
    ".............",
    ".............",
    ".............",
    "....XXXX.....",
    "..XXXXXXX..X.",
    ".XX.XXXXXXXX.",
    "..XXXXXXX..X.",
    "....XXXX.....",
    ".............",
    ".............",
    ".............",
    ".............",
)
def poisson(f):
    N = 26
    x = 9 - f
    g = shift(POISSON, x, round(math.sin(f / 3)))
    for k, (bx, by) in enumerate(((x + 1, 4), (x + 0, 2))):
        if 0 <= f - 4 * k:
            put(g, bx + 1, by - (f % 3), 3)
    return g
write(i, "poisson", "Poisson", "Persos", anim(26, 110, poisson)); i += 1

def hibou(f):
    g = art(
        ".............",
        "..XX.....XX..",
        "...XXXXXXX...",
        "..XXXXXXXXX..",
        "..X..XXX..X..",
        "..X..XXX..X..",
        "..XXXX.XXXX..",
        "..XXXX.XXXX..",
        "..X.X.X.X.X..",
        "..XX.X.X.XX..",
        "...XXXXXXX...",
        "....X...X....",
        ".............",
    )
    if f == 1:
        for x in (3, 4, 8, 9):
            g[4][x] = 9; g[5][x] = 9 if x in (3, 9) else 0
        g[5][3] = 9; g[5][4] = 9; g[5][8] = 9; g[5][9] = 9
    return g
write(i, "hibou", "Hibou", "Persos", [(hibou(0), 2500), (hibou(1), 200)]); i += 1

def lapin(f):
    g = art(
        "...X.....X...",
        "...XX...XX...",
        "...XX...XX...",
        "...XX...XX...",
        "....XXXXX....",
        "...XXXXXXX...",
        "..XX.XXX.XX..",
        "..XXXXXXXXX..",
        "..XXXX.XXXX..",
        "...XXXXXXX...",
        "....XXXXX....",
        ".............",
        ".............",
    )
    if f % 6 == 3:
        g[0][9] = 0; g[1][9] = 0; g[1][10] = 9; g[0][10] = 0
    return shift(g, 0, -1 if f % 6 in (1, 2) else 0)
write(i, "lapin", "Lapin", "Persos", anim(6, 180, lapin)); i += 1

def pieuvre(f):
    g = art(
        ".............",
        "....XXXXX....",
        "...XXXXXXX...",
        "..XXXXXXXXX..",
        "..XX.XXX.XX..",
        "..XX.XXX.XX..",
        "..XXXXXXXXX..",
        "...XXXXXXX...",
        ".............",
        ".............",
        ".............",
        ".............",
        ".............",
    )
    for k, x in enumerate((3, 5, 7, 9)):
        for y in range(8, 12):
            put(g, x + round(math.sin((y + f * 0.8 + k * 1.7) * 0.9) * 0.7), y, 9 if y < 11 else 5)
    return g
write(i, "pieuvre", "Pieuvre", "Persos", anim(8, 130, pieuvre)); i += 1

def crane():
    return art(
        ".............",
        "...XXXXXXX...",
        "..XXXXXXXXX..",
        ".XXXXXXXXXXX.",
        ".XX...X...XX.",
        ".XX...X...XX.",
        ".XXXXX.XXXXX.",
        "..XXXX.XXXX..",
        "...XXXXXXX...",
        "...X.X.X.X...",
        "...XXXXXXX...",
        ".............",
        ".............",
    )
write(i, "crane", "Crâne", "Persos", [crane()]); i += 1

# ───────────────────────── FORMES ─────────────────────────
i = 105

def polygone(n, r, rot=0.0, plein=False, v=9):
    g = grid()
    pts = [(C + r * math.cos(rot + 2 * math.pi * k / n), C + r * math.sin(rot + 2 * math.pi * k / n)) for k in range(n)]
    if plein:
        for y in range(S):
            for x in range(S):
                dedans = True
                for k in range(n):
                    (x1, y1), (x2, y2) = pts[k], pts[(k + 1) % n]
                    if (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) < -0.5:
                        dedans = False
                if dedans:
                    put(g, x, y, v)
    else:
        for k in range(n):
            (x1, y1), (x2, y2) = pts[k], pts[(k + 1) % n]
            line(g, x1, y1, x2, y2, v)
    return g

write(i, "triangle", "Triangle", "Formes", anim(24, 90, lambda f: polygone(3, 5.5, -math.pi / 2 + 2 * math.pi * f / 24 / 3))); i += 1
write(i, "carre", "Carré", "Formes", anim(24, 90, lambda f: polygone(4, 5.5, 2 * math.pi * f / 24 / 4))); i += 1
write(i, "hexagone", "Hexagone", "Formes", [polygone(6, 5.6, 0, plein=False)]); i += 1
write(i, "losange", "Losange", "Formes", [polygone(4, 5.5, 0, plein=True)]); i += 1

def yinyang(f):
    g = grid()
    a = 2 * math.pi * f / 40
    ca, sa = math.cos(a), math.sin(a)
    for y in range(S):
        for x in range(S):
            dx, dy = x - C, y - C
            rx, ry = dx * ca + dy * sa, -dx * sa + dy * ca
            r = math.hypot(rx, ry)
            if r > 5.8:
                continue
            haut = math.hypot(rx, ry + 2.9)
            bas = math.hypot(rx, ry - 2.9)
            clair = rx > 0
            if haut < 2.9: clair = True
            if bas < 2.9: clair = False
            if haut < 0.9: clair = False
            if bas < 0.9: clair = True
            put(g, x, y, 9 if clair else (5 if r > 5.0 else 0))
    return g
write(i, "yin-yang", "Yin-yang", "Formes", anim(40, 100, yinyang)); i += 1

def cible_rot(f):
    g = grid()
    for k in range(8):
        a = 2 * math.pi * k / 8 + 2 * math.pi * f / 32
        for r in (2, 3, 4, 5):
            put(g, C + r * math.cos(a), C + r * math.sin(a), 9 if k % 2 == 0 else 3)
    return g
write(i, "rayons", "Rayons", "Formes", anim(32, 80, cible_rot)); i += 1

def points(f):
    g = grid()
    for y in range(0, S, 2):
        for x in range(0, S, 2):
            d = math.hypot(x - C, y - C)
            put(g, x, y, 3 + 6 * (0.5 + 0.5 * math.sin(d - 2 * math.pi * f / 16)))
    return g
write(i, "points", "Points", "Formes", anim(16, 90, points)); i += 1

def infini(f):
    g = grid()
    N = 40
    for k in range(60):
        t = 2 * math.pi * k / 60
        x = C + 5.2 * math.cos(t) / (1 + math.sin(t) ** 2)
        y = C + 5.2 * math.sin(t) * math.cos(t) / (1 + math.sin(t) ** 2)
        put(g, x, y, 2)
    for j in range(5):
        t = 2 * math.pi * ((f - j * 0.8) % N) / N
        x = C + 5.2 * math.cos(t) / (1 + math.sin(t) ** 2)
        y = C + 5.2 * math.sin(t) * math.cos(t) / (1 + math.sin(t) ** 2)
        put(g, x, y, 9 - j * 1.6)
    return g
write(i, "infini", "Infini", "Formes", anim(40, 60, infini)); i += 1

# ───────────────────────── FÊTES ─────────────────────────
i = 120

def citrouille(f):
    g = art(
        "......X......",
        ".....X.......",
        "...XXXXXXX...",
        "..XXXXXXXXX..",
        ".XX.XXXXX.XX.",
        ".X...XXX...X.",
        ".XXXXXXXXXXX.",
        ".XXXXX.XXXXX.",
        ".X.........X.",
        ".XX.X.X.X.XX.",
        "..XXXXXXXXX..",
        "...XXXXXXX...",
        ".............",
    )
    # corps un peu moins lumineux, yeux et bouche qui scintillent (bougie dedans)
    g = scale(g, 0.35)
    lueur = [8, 9, 7, 9, 9, 7, 9, 8][f % 8]
    for x, y in ((3, 4), (2, 5), (3, 5), (4, 5), (9, 4), (8, 5), (9, 5), (10, 5), (6, 7),
                 (2, 8), (3, 8), (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (3, 9), (5, 9), (7, 9), (9, 9)):
        g[y][x] = lueur
    g[0][6] = 9; g[1][5] = 9
    return g
write(i, "citrouille", "Citrouille", "Fêtes", anim(8, 120, citrouille)); i += 1

def sapin(f):
    g = art(
        "......X......",
        ".....XXX.....",
        "....XXXXX....",
        ".....XXX.....",
        "....XXXXX....",
        "...XXXXXXX...",
        "....XXXXX....",
        "...XXXXXXX...",
        "..XXXXXXXXX..",
        ".XXXXXXXXXXX.",
        ".....XXX.....",
        ".....XXX.....",
        ".............",
    )
    g = scale(g, 0.35)
    g[0][6] = 9
    boules = [(5, 2), (7, 4), (4, 5), (8, 7), (5, 8), (3, 9), (9, 9), (6, 6)]
    for k, (x, y) in enumerate(boules):
        g[y][x] = 9 if (k + f) % 3 == 0 else 2
    return g
write(i, "sapin", "Sapin", "Fêtes", anim(6, 300, sapin)); i += 1

write(i, "cadeau", "Cadeau", "Fêtes", [art(
    ".............",
    "....XX.XX....",
    "...X..X..X...",
    "....XXXXX....",
    ".XXXXXXXXXXX.",
    ".XXXXX.XXXXX.",
    "..XXXX.XXXX..",
    "..XXXX.XXXX..",
    "..XXXX.XXXX..",
    "..XXXX.XXXX..",
    "..XXXXXXXXX..",
    ".............",
    ".............",
)]); i += 1

def gateau(f):
    g = art(
        ".............",
        ".............",
        ".............",
        "...X.X.X.X...",
        "...X.X.X.X...",
        "..XXXXXXXXX..",
        "..X.X.X.X.X..",
        "..XXXXXXXXX..",
        ".XXXXXXXXXXX.",
        ".X.X.X.X.X.X.",
        ".XXXXXXXXXXX.",
        ".............",
        ".............",
    )
    for k, x in enumerate((3, 5, 7, 9)):
        h = (f + k) % 3
        put(g, x + (1 if h == 2 else 0), 2, 9 if h != 1 else 6)
        if h == 0:
            put(g, x, 1, 4)
    return g
write(i, "gateau", "Anniversaire", "Fêtes", anim(6, 140, gateau)); i += 1

def neige_douce():
    rng = random.Random(4)
    L = 60
    fl = [(rng.uniform(0, 12), rng.randrange(L), rng.choice([1, 1, 2])) for _ in range(14)]
    def fr(f):
        g = grid()
        for x0, dep, taille in fl:
            t = ((f + dep) % L) / L
            y = -1 + t * 15
            x = x0 + math.sin(t * 9 + x0) * 0.9
            put(g, x, y, 9 if taille == 2 else 5)
        return g
    return anim(L, 110, fr)
write(i, "neige", "Chute de neige", "Fêtes", neige_douce()); i += 1

# ───────────────────────── TEXTE ─────────────────────────
i = 135
write(i, "texte-verso", "« VERSO »", "Texte", defilant("VERSO ")); i += 1
write(i, "texte-salut", "« SALUT ! »", "Texte", defilant("SALUT ! ")); i += 1
write(i, "texte-love", "« I ♥ U »", "Texte", defilant("I ♥ U ")); i += 1

def compte(f):
    g = grid()
    n = 9 - f
    if n >= 0:
        # gros chiffre 3×5 agrandi ×2
        for y, ligne in enumerate(F3[str(n)].split()):
            for x, c in enumerate(ligne):
                if c == "X":
                    for dx in (0, 1):
                        for dy in (0, 1):
                            put(g, 3 + x * 2 + dx + (0 if x < 2 else 0), 1 + y * 2 + dy)
        ring(g, 6.0, 2)
    else:
        disc(g, C, C, 6, 9)
    return g
write(i, "decompte", "Décompte", "Texte", [(compte(f), 1000 if f < 10 else 600) for f in range(11)]); i += 1

for idx, mid, nom, cat, n in ECRITS:
    print(f"{idx:3d} {cat:9s} {mid:16s} {n:3d} image(s)")
print(len(ECRITS), "modèles")
