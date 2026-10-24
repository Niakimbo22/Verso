"""Génère les calques du widget Verso (res/drawable-nodpi/widget_*.png).

Le widget empile trois images carrées, de la même taille :
  1. widget_disque.png        disque plein, teinté couleur « LED éteinte »
  2. l'image du modèle        bitmap ALPHA_8 calculé par l'app (RenduWidget), teinté couleur « LED allumée »
  3. widget_masque_<forme>_<taille>.png
                              disque percé d'un trou par LED, teinté couleur du disque

Le canevas fait 15 cases (13 LEDs + 1 case de marge de chaque côté), le disque a un rayon de 7 cases :
les LEDs du bord (centre à 6,48 cases au plus) tiennent dedans. Doit rester aligné avec RenduWidget.kt.

Usage : python3 outils/gen_masques_widget.py app/src/main/res/drawable-nodpi
"""
import os, sys
from PIL import Image, ImageDraw

OUT = sys.argv[1]
CASES = 15
PX = 32            # pixels par case dans le PNG final
SS = 4             # suréchantillonnage pour l'anticrénelage
RAYON_DISQUE = 7.0
TAILLES = {"petit": 0.24, "moyen": 0.30, "grand": 0.36}  # rayon du point, en cases
N = 13

def on(x, y):
    return (x - 6) ** 2 + (y - 6) ** 2 <= 42

def disque(draw, u, rayon=RAYON_DISQUE):
    c = CASES * u / 2
    r = rayon * u
    draw.ellipse([c - r, c - r, c + r, c + r], fill=255)

def calque(trous, forme, rayon):
    u = PX * SS
    a = Image.new("L", (CASES * u, CASES * u), 0)
    d = ImageDraw.Draw(a)
    # Le disque du dessous est un peu plus petit que le masque : son bord ne déborde pas en liseré.
    disque(d, u, RAYON_DISQUE if trous else RAYON_DISQUE - 0.1)
    if trous:
        for y in range(N):
            for x in range(N):
                if not on(x, y):
                    continue
                cx = (x + 1.5) * u
                cy = (y + 1.5) * u
                if forme == "rond":
                    r = rayon * u
                    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0)
                else:
                    h = rayon * 0.92 * u
                    d.rounded_rectangle([cx - h, cy - h, cx + h, cy + h], radius=h * 0.3, fill=0)
    a = a.resize((CASES * PX, CASES * PX), Image.LANCZOS)
    img = Image.new("RGBA", a.size, (255, 255, 255, 0))
    img.putalpha(a)
    return img

os.makedirs(OUT, exist_ok=True)
calque(False, None, 0).save(os.path.join(OUT, "widget_disque.png"), optimize=True)
for forme in ("rond", "carre"):
    for nom, rayon in TAILLES.items():
        calque(True, forme, rayon).save(os.path.join(OUT, f"widget_masque_{forme}_{nom}.png"), optimize=True)
print("ok")
