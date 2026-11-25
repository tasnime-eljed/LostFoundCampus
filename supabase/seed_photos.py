"""
LostFound Campus — envoie une photo d'illustration pour chaque annonce de démonstration.

À lancer APRÈS seed_demo.sql, depuis la racine du projet :
    python supabase/seed_photos.py

Lit SUPABASE_URL et SUPABASE_PUBLISHABLE_KEY dans local.properties, se connecte avec chaque
compte de test (DEMO_PASSWORD dans local.properties), lit ses annonces et envoie dans le bucket privé
« announcement-photos » une photo libre de droits
(dossier demo_photos, voir CREDITS.md), puis ajuste l'annonce pour qu'elle corresponde à la photo.
Les règles Storage s'appliquent : chaque compte n'écrit que dans son propre dossier.
Nécessite Pillow (pip install pillow).
"""
import io
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

EMAILS = [
    "amine.bensalah@universite.tn", "sarra.trabelsi@universite.tn", "youssef.gharbi@universite.tn",
    "ines.jaziri@universite.tn", "mehdi.chaabane@universite.tn", "rania.bouazizi@universite.tn",
]


def read_props():
    props = {}
    for line in Path("local.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    return props


def read_config():
    props = read_props()
    url = props.get("SUPABASE_URL", "").rstrip("/")
    key = props.get("SUPABASE_PUBLISHABLE_KEY", "")
    if not url.startswith("https://") or not key:
        sys.exit("SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY manquants dans local.properties")
    return url, key


def call(method, url, key, token=None, body=None, content_type="application/json", extra=None):
    headers = {"apikey": key, "Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    if extra:
        headers.update(extra)
    data = body if isinstance(body, (bytes, type(None))) else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{method} {url} -> {e.code} {e.read().decode('utf-8', 'replace')}") from None


PASSWORD = read_props().get("DEMO_PASSWORD", "")  # jamais versionné


def font(size):
    for name in ("arialbd.ttf", "DejaVuSans-Bold.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def make_card():
    """Carte étudiant fictive posée sur une table (aucune donnée personnelle)."""
    w, h = 1200, 900
    img = Image.new("RGB", (w, h), (150, 112, 78))
    d = ImageDraw.Draw(img)
    for y in range(0, h, 6):
        d.line([(0, y), (w, y)], fill=(142 + y % 18, 106 + y % 12, 74), width=2)
    card = Image.new("RGBA", (760, 480), (0, 0, 0, 0))
    k = ImageDraw.Draw(card)
    k.rounded_rectangle([0, 0, 759, 479], radius=36, fill=(250, 250, 248), outline=(200, 200, 200), width=3)
    k.rectangle([0, 60, 759, 130], fill=(40, 90, 160))
    k.text((380, 95), "UNIVERSITÉ", anchor="mm", font=font(48), fill="white")
    k.rectangle([45, 170, 235, 420], fill=(205, 212, 220))
    k.ellipse([100, 205, 180, 290], fill=(150, 160, 172))
    k.pieslice([70, 300, 210, 480], 180, 360, fill=(150, 160, 172))
    k.text((275, 185), "CARTE ÉTUDIANT", font=font(44), fill=(40, 40, 40))
    k.text((275, 270), "SPÉCIMEN", font=font(40), fill=(170, 60, 60))
    k.text((275, 350), "2026 - 2027", font=font(34), fill=(90, 90, 90))
    card = card.rotate(8, expand=True, resample=Image.BICUBIC)
    img.paste(card, ((w - card.width) // 2, (h - card.height) // 2), card)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=85)
    return buf.getvalue()


PHOTOS_DIR = Path(__file__).parent / "demo_photos"
PREFIX = "a0000000-0000-4000-8000-0000000000"
# Numéro d'annonce de seed_demo.sql : (photo, champs ajustés pour correspondre à la photo)
DEMO = {
    "01": ("iphone", {"title": "iPhone 13 mini bleu"}),
    "02": ("iphone", {}),
    "03": ("keys", {"title": "Clé avec porte-clés rouge", "description": "Clé de voiture noire sur un porte-clés rectangulaire rouge."}),
    "04": ("keys", {"title": "Clé de voiture", "description": "Clé trouvée par terre devant la cafétéria, avec un porte-clés rouge."}),
    "05": ("backpack", {"title": "Sac à dos Eastpak bleu marine", "color": "Bleu marine"}),
    "06": ("backpack", {"title": "Sac à dos bleu marine", "color": "Bleu marine", "description": "Sac à dos bleu marine resté dans l'amphi A après le cours de 14 h. Je l'ai confié au service de scolarité."}),
    "07": ("card", {}),
    "08": ("card", {}),
    "09": ("airpods", {}),
    "10": ("airpods_case", {}),
    "11": ("wallet", {}),
    "12": ("jacket", {}),
    "13": ("laptop", {"color": "Noir", "description": "Dell Latitude noir dans une housse noire. Contient mon mémoire de fin d'études, merci !"}),
    "14": ("glasses", {}),
    "15": ("bottle", {"color": "Bleu", "description": "Gourde isotherme bleue avec bouchon à anse."}),
    "16": ("umbrella", {"title": "Parapluie lilas", "color": "Lilas", "description": "Parapluie lilas laissé à l'arrêt de bus un jour de pluie."}),
    "17": ("calculator", {}),
    "18": ("scarf", {"title": "Écharpe orange", "color": "Orange", "description": "Écharpe en laine orange, encore sur ses aiguilles à tricoter, trouvée à la cafétéria."}),
}


def main():
    url, key = read_config()
    sent = 0
    for email in EMAILS:
        session = call("POST", f"{url}/auth/v1/token?grant_type=password", key,
                       body={"email": email, "password": PASSWORD})
        token, uid = session["access_token"], session["user"]["id"]
        query = urllib.parse.urlencode({"select": "id,title", "author_id": f"eq.{uid}", "id": f"gte.{PREFIX}01"})
        for a in call("GET", f"{url}/rest/v1/announcements?{query}", key, token) or []:
            if not a["id"].startswith(PREFIX) or a["id"][-2:] not in DEMO:
                continue
            name, changes = DEMO[a["id"][-2:]]
            jpeg = make_card() if name == "card" else (PHOTOS_DIR / f"{name}.jpg").read_bytes()
            # Nouveau nom de fichier : l'ancienne image en cache (Glide) n'est pas réaffichée.
            path = f"{uid}/{a['id']}/{name}.jpg"
            call("POST", f"{url}/storage/v1/object/announcement-photos/{urllib.parse.quote(path)}", key, token,
                 body=jpeg, content_type="image/jpeg", extra={"x-upsert": "true"})
            call("PATCH", f"{url}/rest/v1/announcements?id=eq.{a['id']}", key, token,
                 body={**changes, "photo_paths": [path]}, extra={"Prefer": "return=minimal"})
            if "color" in changes:
                call("PATCH", f"{url}/rest/v1/photo_analyses?announcement_id=eq.{a['id']}", key, token,
                     body={"dominant_color": changes["color"]}, extra={"Prefer": "return=minimal"})
            sent += 1
            print(f"  {email:32} {changes.get('title', a['title'])}")
    print(f"{sent} annonce(s) mise(s) à jour.")


if __name__ == "__main__":
    main()
