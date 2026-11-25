#!/usr/bin/env python3
"""
Reemplaza la geometria de src/main/resources/catalogo/rios.json con los cauces
de OpenStreetMap (Overpass API), para que el resaltado del mapa coincida con
el mapa base.

No toca reachId, latitud, longitud ni tramos: la alerta se sigue calculando
sobre el tramo de GEOGLOWS y el punto de referencia no cambia.

Uso (desde la raiz del repositorio):
    python tools/generar_catalogo_osm.py --dry-run --ajustar-puntos   # solo vista previa
    python tools/generar_catalogo_osm.py --ajustar-puntos             # escribe rios.json

--ajustar-puntos mueve latitud/longitud (punto de referencia) al punto mas
cercano del cauce de OpenStreetMap, para que el circulo del mapa quede sobre
la linea. El reachId no cambia: el pronostico sigue siendo del mismo tramo.

Solo usa la biblioteca estandar. Datos (c) colaboradores de OpenStreetMap,
licencia ODbL: https://www.openstreetmap.org/copyright
"""

import argparse
import json
import math
import re
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
RIOS = RAIZ / "src/main/resources/catalogo/rios.json"
RESPALDO = RAIZ / "tools/respaldo_rios_geoglows.json"
VISTA_PREVIA = RAIZ / "tools/vista_previa_osm.geojson"

# Nombre en rios.json -> expresion regular del nombre en OpenStreetMap (sin distinguir mayusculas).
NOMBRES_OSM = {
    "Río Abajo": r"R[ií]o Abajo",
    "Río Curundú": r"Curund[uú]",
    "Río Juan Díaz": r"Juan D[ií]az",
    "Río Tocumen": r"Tocumen",
}

SERVIDORES = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://lz4.overpass-api.de/api/interpreter",
]
USER_AGENT = "alerta-rios-panama/1.0 (proyecto academico UTP)"
TOLERANCIA_M = 2.0  # simplificacion Douglas-Peucker
AVISO_DISTANCIA_M = 1000.0


def mostrar(texto=""):
    print(texto, flush=True)


def corto(ruta):
    try:
        return ruta.relative_to(RAIZ)
    except ValueError:
        return ruta


# ---------- geometria ----------
def a_metros(lon, lat, lat0):
    """Proyeccion plana local (suficiente para distancias de pocos km)."""
    k = 111320.0
    return lon * k * math.cos(math.radians(lat0)), lat * k


def largo_m(linea):
    lat0 = linea[0][1]
    total = 0.0
    for (x1, y1), (x2, y2) in zip(linea, linea[1:]):
        ax, ay = a_metros(x1, y1, lat0)
        bx, by = a_metros(x2, y2, lat0)
        total += math.hypot(bx - ax, by - ay)
    return total


def dist_punto_segmento(p, a, b):
    px, py = p
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def simplificar(linea, tolerancia_m):
    """Douglas-Peucker iterativo sobre una linea de [lon, lat]."""
    if len(linea) < 3:
        return linea
    lat0 = linea[0][1]
    pts = [a_metros(lon, lat, lat0) for lon, lat in linea]
    conservar = [False] * len(linea)
    conservar[0] = conservar[-1] = True
    pila = [(0, len(linea) - 1)]
    while pila:
        i, j = pila.pop()
        maximo, indice = 0.0, None
        for k in range(i + 1, j):
            d = dist_punto_segmento(pts[k], pts[i], pts[j])
            if d > maximo:
                maximo, indice = d, k
        if indice is not None and maximo > tolerancia_m:
            conservar[indice] = True
            pila.append((i, indice))
            pila.append((indice, j))
    return [p for p, c in zip(linea, conservar) if c]


def unir(lineas):
    """Une lineas que comparten un extremo exacto (los ways de OSM comparten nodos)."""
    lineas = [list(l) for l in lineas if len(l) >= 2]
    unido = True
    while unido:
        unido = False
        for i in range(len(lineas)):
            for j in range(len(lineas)):
                if i == j:
                    continue
                a, b = lineas[i], lineas[j]
                if a[-1] == b[0]:
                    nueva = a + b[1:]
                elif a[-1] == b[-1]:
                    nueva = a + b[::-1][1:]
                elif a[0] == b[0]:
                    nueva = b[::-1] + a[1:]
                elif a[0] == b[-1]:
                    nueva = b + a[1:]
                else:
                    continue
                lineas[i] = nueva
                del lineas[j]
                unido = True
                break
            if unido:
                break
    return lineas


def distancia_a_lineas(lat, lon, lineas):
    """Distancia minima en metros del punto a las lineas ([lon, lat])."""
    mejor = float("inf")
    p = a_metros(lon, lat, lat)
    for linea in lineas:
        pts = [a_metros(x, y, lat) for x, y in linea]
        for a, b in zip(pts, pts[1:]):
            mejor = min(mejor, dist_punto_segmento(p, a, b))
    return mejor


def punto_mas_cercano(lat, lon, lineas):
    """Punto de las lineas ([lon, lat]) mas cercano a (lat, lon): (lat, lon, distancia_m)."""
    k = 111320.0
    cosl = math.cos(math.radians(lat))
    px, py = lon * k * cosl, lat * k
    mejor = (float("inf"), px, py)
    for linea in lineas:
        pts = [(x * k * cosl, y * k) for x, y in linea]
        for a, b in zip(pts, pts[1:]):
            dx, dy = b[0] - a[0], b[1] - a[1]
            largo2 = dx * dx + dy * dy
            t = 0.0 if largo2 == 0 else max(0.0, min(1.0, ((px - a[0]) * dx + (py - a[1]) * dy) / largo2))
            qx, qy = a[0] + t * dx, a[1] + t * dy
            d = math.hypot(px - qx, py - qy)
            if d < mejor[0]:
                mejor = (d, qx, qy)
    d, qx, qy = mejor
    return qy / k, qx / (k * cosl), d


def limites(geometria):
    pts = [p for linea in geometria["coordinates"] for p in linea]
    lons = [p[0] for p in pts]
    lats = [p[1] for p in pts]
    return min(lats), min(lons), max(lats), max(lons)


# ---------- Overpass ----------
def consultar_overpass(consulta):
    datos = urllib.parse.urlencode({"data": consulta}).encode("utf-8")
    ultimo_error = None
    for intento in range(3):
        for url in SERVIDORES:
            try:
                req = urllib.request.Request(url, data=datos, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=90) as resp:
                    return json.loads(resp.read().decode("utf-8"))
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as e:
                ultimo_error = e
        time.sleep(5 * (intento + 1))
    raise RuntimeError(f"No se pudo consultar Overpass: {ultimo_error}")


def descargar_rio(nombre, regex, caja, sin_tuneles):
    s, w, n, e = caja
    consulta = (
        "[out:json][timeout:60];"
        f'way["waterway"~"^(river|stream|canal|drain)$"]["name"~"{regex}",i]'
        f"({s:.5f},{w:.5f},{n:.5f},{e:.5f});out geom;"
    )
    resultado = consultar_overpass(consulta)
    lineas, nombres, tuneles = [], {}, 0
    for el in resultado.get("elements", []):
        if el.get("type") != "way" or not el.get("geometry"):
            continue
        etiquetas = el.get("tags", {})
        if etiquetas.get("tunnel"):
            tuneles += 1
            if sin_tuneles:
                continue
        nombres[etiquetas.get("name", "(sin nombre)")] = nombres.get(etiquetas.get("name", "(sin nombre)"), 0) + 1
        lineas.append([[round(p["lon"], 7), round(p["lat"], 7)] for p in el["geometry"]])
    return lineas, nombres, tuneles


# ---------- principal ----------
def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--margen", type=float, default=0.05, help="grados que se amplia la zona de busqueda (def. 0.05, unos 5 km)")
    ap.add_argument("--sin-tuneles", action="store_true", help="omite tramos con tunnel=* (alcantarillas)")
    ap.add_argument("--ajustar-puntos", action="store_true", help="mueve el punto de referencia al cauce de OSM mas cercano")
    ap.add_argument("--dry-run", action="store_true", help="no modifica rios.json; solo crea la vista previa")
    args = ap.parse_args()

    catalogo = json.loads(RIOS.read_text(encoding="utf-8"))
    cambios = 0
    features = []

    for rio in catalogo:
        nombre = rio["nombre"]
        regex = NOMBRES_OSM.get(nombre)
        mostrar(f"== {nombre} (reachId {rio['reachId']})")
        if regex is None:
            mostrar("   Sin regla en NOMBRES_OSM: se deja la geometria actual.")
            continue
        s, w, n, e = limites(rio["geometria"])
        s = min(s, rio["latitud"]) - args.margen
        n = max(n, rio["latitud"]) + args.margen
        w = min(w, rio["longitud"]) - args.margen
        e = max(e, rio["longitud"]) + args.margen
        lineas, nombres, tuneles = descargar_rio(nombre, regex, (s, w, n, e), args.sin_tuneles)
        if not lineas:
            mostrar("   OpenStreetMap no devolvio cauces: se deja la geometria de GEOGLOWS para este rio.")
            continue

        unidas = unir(lineas)
        finales = [simplificar(l, TOLERANCIA_M) for l in unidas]
        finales = [[[round(x, 6), round(y, 6)] for x, y in l] for l in finales]
        km = sum(largo_m(l) for l in finales) / 1000
        dist = distancia_a_lineas(rio["latitud"], rio["longitud"], finales)
        mostrar(f"   Nombres en OSM: {', '.join(f'{k} ({v})' for k, v in sorted(nombres.items()))}")
        mostrar(f"   {len(lineas)} ways -> {len(finales)} lineas, {sum(len(l) for l in finales)} puntos, {km:.1f} km"
                + (f", {tuneles} con tunnel" if tuneles else ""))
        mostrar(f"   Punto de referencia a {dist:.0f} m del cauce de OSM")
        if dist > AVISO_DISTANCIA_M:
            mostrar("   AVISO: el punto queda lejos. Revisa que el tramo de GEOGLOWS y este cauce sean el mismo rio.")
        if len(finales) > 6:
            mostrar("   AVISO: muchas lineas sueltas. Puede haber cauces con el mismo nombre ajenos al rio; revisa la vista previa.")

        if args.ajustar_puntos:
            lat2, lon2, movido = punto_mas_cercano(rio["latitud"], rio["longitud"], finales)
            rio["latitud"], rio["longitud"] = round(lat2, 6), round(lon2, 6)
            mostrar(f"   Punto de referencia movido {movido:.0f} m hacia el cauce de OSM")

        nueva = {"type": "MultiLineString", "coordinates": finales}
        features.append({"type": "Feature", "properties": {"nombre": nombre, "reachId": rio["reachId"]}, "geometry": nueva})
        rio["geometria"] = nueva
        cambios += 1
        time.sleep(1)  # cortesia con el servidor de Overpass

    for rio in catalogo:
        features.append({
            "type": "Feature",
            "properties": {"nombre": f"Punto de referencia: {rio['nombre']}"},
            "geometry": {"type": "Point", "coordinates": [rio["longitud"], rio["latitud"]]},
        })
    VISTA_PREVIA.write_text(json.dumps({"type": "FeatureCollection", "features": features}, ensure_ascii=False), encoding="utf-8")
    mostrar(f"\nVista previa: {corto(VISTA_PREVIA)} (puedes arrastrarla a https://geojson.io)")

    if args.dry_run:
        mostrar("--dry-run: rios.json NO se modifico.")
        return
    if cambios == 0:
        mostrar("No hubo cambios que guardar.")
        return
    if not RESPALDO.exists():
        shutil.copyfile(RIOS, RESPALDO)
        mostrar(f"Respaldo de la geometria original: {corto(RESPALDO)}")
    RIOS.write_text(json.dumps(catalogo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    mostrar(f"rios.json actualizado ({cambios} rios). Reinicia el backend para cargar la nueva geometria.")


if __name__ == "__main__":
    main()
