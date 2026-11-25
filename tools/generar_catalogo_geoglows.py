#!/usr/bin/env python3
"""
Genera src/main/resources/catalogo/rios.json a partir de la hidrografía
oficial de GEOGloWS V2 (GeoPackage, EPSG:3857).

Uso:
    python3 tools/generar_catalogo_geoglows.py [directorio_de_gpkgs]

Los archivos esperados son:
    streams_701.gpkg  (Abajo, Curundú y Juan Díaz)
    streams_614.gpkg  (Tocumen)

Se pueden descargar de:
    https://geoglows-v2.s3.amazonaws.com/hydrography/vpu=701/streams_701.gpkg
    https://geoglows-v2.s3.amazonaws.com/hydrography/vpu=614/streams_614.gpkg

El script usa solo la biblioteca estándar: lee el GeoPackage con sqlite3,
decodifica el WKB del blob GPKG, convierte de Web Mercator a WGS84,
simplifica con Douglas-Peucker y escribe GeoJSON con coordenadas
[longitud, latitud]. No inventa ríos ni geometrías: si falta un LINKNO,
falla con un error.
"""

import json
import math
import sqlite3
import struct
import sys
from pathlib import Path

# Catálogo cerrado. El orden de cada cadena va aguas arriba -> aguas abajo.
RIOS = [
    {
        "nombre": "Río Abajo",
        "reachId": 710039498,
        "vpu": 701,
        "tramos": [710039498],
    },
    {
        "nombre": "Río Curundú",
        "reachId": 710037516,
        "vpu": 701,
        "tramos": [710035532, 710037516, 710039500],
    },
    {
        "nombre": "Río Juan Díaz",
        "reachId": 710051399,
        "vpu": 701,
        "tramos": [710051399, 710035528, 710055369, 710057353],
    },
    {
        "nombre": "Río Tocumen",
        "reachId": 670053261,
        "vpu": 614,
        "tramos": [670053261, 670054285, 670055309],
    },
]

RADIO_TIERRA_M = 6378137.0
TOLERANCIA_SIMPLIFICACION_M = 8.0


def leer_geometria(blob: bytes):
    """Decodifica un blob GeoPackage con geometría LineString o MultiLineString."""
    if blob[:2] != b"GP":
        raise ValueError("El blob no tiene la cabecera GeoPackage esperada.")
    flags = blob[3]
    indicador_envolvente = (flags & 0x0E) >> 1
    tamanos = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}
    if indicador_envolvente not in tamanos:
        raise ValueError(f"Indicador de envolvente desconocido: {indicador_envolvente}")
    desplazamiento = 8 + tamanos[indicador_envolvente]

    orden = "<" if blob[desplazamiento] == 1 else ">"
    tipo = struct.unpack(orden + "I", blob[desplazamiento + 1:desplazamiento + 5])[0]
    posicion = desplazamiento + 5

    def leer_linea(inicio):
        cantidad = struct.unpack(orden + "I", blob[inicio:inicio + 4])[0]
        puntos = []
        actual = inicio + 4
        for _ in range(cantidad):
            x, y = struct.unpack(orden + "dd", blob[actual:actual + 16])
            actual += 16
            puntos.append((x, y))
        return puntos, actual

    if tipo == 2:
        linea, _ = leer_linea(posicion)
        return [linea]
    if tipo == 5:
        cantidad = struct.unpack(orden + "I", blob[posicion:posicion + 4])[0]
        posicion += 4
        lineas = []
        for _ in range(cantidad):
            if blob[posicion] != 1:
                raise ValueError("Se esperaba un LineString little-endian dentro del MultiLineString.")
            tipo_interno = struct.unpack(orden + "I", blob[posicion + 1:posicion + 5])[0]
            if tipo_interno != 2:
                raise ValueError("Se esperaba un LineString dentro del MultiLineString.")
            posicion += 5
            linea, posicion = leer_linea(posicion)
            lineas.append(linea)
        return lineas
    raise ValueError(f"Se esperaba LineString (2) o MultiLineString (5) y se encontró el tipo {tipo}.")


def web_mercator_a_wgs84(puntos):
    """EPSG:3857 -> EPSG:4326 (esfera de Web Mercator)."""
    resultado = []
    for x, y in puntos:
        longitud = x / RADIO_TIERRA_M * 180.0 / math.pi
        latitud = (2.0 * math.atan(math.exp(y / RADIO_TIERRA_M)) - math.pi / 2.0) * 180.0 / math.pi
        resultado.append((longitud, latitud))
    return resultado


def distancia_m(a, b):
    """Distancia haversine entre dos pares (longitud, latitud)."""
    radio = 6371000.0
    lon1, lat1 = a
    lon2, lat2 = b
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lon2 - lon1)
    h = math.sin(dp / 2.0) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2.0) ** 2
    return 2.0 * radio * math.asin(math.sqrt(h))


def longitud_m(puntos):
    return sum(distancia_m(puntos[i], puntos[i + 1]) for i in range(len(puntos) - 1))


def simplificar(puntos, tolerancia_m):
    """Douglas-Peucker iterativo; trabaja en metros locales."""
    if len(puntos) <= 2:
        return puntos

    lat_media = sum(p[1] for p in puntos) / len(puntos)
    escala_x = math.cos(math.radians(lat_media)) * 111320.0
    escala_y = 110540.0

    def xy(punto):
        return (punto[0] * escala_x, punto[1] * escala_y)

    def distancia_a_segmento(p, a, b):
        px, py = p
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        if dx == 0 and dy == 0:
            return math.hypot(px - ax, py - ay)
        t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        t = max(0.0, min(1.0, t))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    proyectados = [xy(p) for p in puntos]
    conservar = [False] * len(puntos)
    conservar[0] = conservar[-1] = True
    pila = [(0, len(puntos) - 1)]
    while pila:
        inicio, fin = pila.pop()
        max_distancia = 0.0
        indice = None
        for i in range(inicio + 1, fin):
            d = distancia_a_segmento(proyectados[i], proyectados[inicio], proyectados[fin])
            if d > max_distancia:
                max_distancia = d
                indice = i
        if indice is not None and max_distancia > tolerancia_m:
            conservar[indice] = True
            pila.append((inicio, indice))
            pila.append((indice, fin))

    return [p for p, mantener in zip(puntos, conservar) if mantener]


def redondear(puntos):
    return [[round(lon, 6), round(lat, 6)] for lon, lat in puntos]


def cargar_tramos(directorio: Path):
    conexiones = {}
    for vpu in {rio["vpu"] for rio in RIOS}:
        ruta = directorio / f"streams_{vpu}.gpkg"
        if not ruta.exists():
            raise SystemExit(f"Falta el archivo {ruta}")
        conexiones[vpu] = sqlite3.connect(ruta)

    def obtener(vpu, reach_id):
        fila = conexiones[vpu].execute(
            f"SELECT geom, LengthGeodesicMeters FROM streams_{vpu} WHERE LINKNO = ?",
            (reach_id,),
        ).fetchone()
        if fila is None:
            raise SystemExit(f"No se encontró el LINKNO {reach_id} en streams_{vpu}.gpkg")
        lineas = [web_mercator_a_wgs84(linea) for linea in leer_geometria(fila[0])]
        return {"reachId": reach_id, "lineas": lineas, "longitudDeclarada": fila[1]}

    return {rio["nombre"]: [obtener(rio["vpu"], rid) for rid in rio["tramos"]] for rio in RIOS}


def reunir_lineas(tramos):
    """Reúne todas las líneas de los tramos conectados del río."""
    return [list(linea) for tramo in tramos for linea in tramo["lineas"]]


def punto_medio(puntos):
    objetivo = longitud_m(puntos) / 2.0
    recorrido = 0.0
    for i in range(len(puntos) - 1):
        segmento = distancia_m(puntos[i], puntos[i + 1])
        if recorrido + segmento >= objetivo:
            resto = 0.0 if segmento == 0 else (objetivo - recorrido) / segmento
            lon = puntos[i][0] + (puntos[i + 1][0] - puntos[i][0]) * resto
            lat = puntos[i][1] + (puntos[i + 1][1] - puntos[i][1]) * resto
            return lon, lat
        recorrido += segmento
    return puntos[-1]


def main():
    directorio = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/opencode/geo")
    tramos_por_rio = cargar_tramos(directorio)

    catalogo = []
    for rio in RIOS:
        tramos = tramos_por_rio[rio["nombre"]]
        lineas = reunir_lineas(tramos)
        lineas_simplificadas = [simplificar(linea, TOLERANCIA_SIMPLIFICACION_M) for linea in lineas]
        principal = next(t for t in tramos if t["reachId"] == rio["reachId"])
        linea_principal = min(
            principal["lineas"],
            key=lambda linea: abs(longitud_m(linea) - principal["longitudDeclarada"]),
        )
        lon, lat = punto_medio(linea_principal)

        geometria = {
            "type": "LineString" if len(lineas_simplificadas) == 1 else "MultiLineString",
            "coordinates": (
                redondear(lineas_simplificadas[0])
                if len(lineas_simplificadas) == 1
                else [redondear(linea) for linea in lineas_simplificadas]
            ),
        }

        catalogo.append({
            "nombre": rio["nombre"],
            "reachId": rio["reachId"],
            "latitud": round(lat, 6),
            "longitud": round(lon, 6),
            "tramos": rio["tramos"],
            "geometria": geometria,
        })

        km_cadena = sum(longitud_m(linea) for linea in lineas) / 1000.0
        puntos_geo = (
            len(geometria["coordinates"])
            if geometria["type"] == "LineString"
            else sum(len(p) for p in geometria["coordinates"])
        )
        print(f"{rio['nombre']}: reach {rio['reachId']}, "
              f"{km_cadena:.2f} km, {puntos_geo} puntos, punto ({lat:.5f}, {lon:.5f})")

    salida = Path(__file__).resolve().parent.parent / "src/main/resources/catalogo/rios.json"
    salida.parent.mkdir(parents=True, exist_ok=True)
    with salida.open("w", encoding="utf-8") as archivo:
        json.dump(catalogo, archivo, ensure_ascii=False, indent=2)
        archivo.write("\n")
    print(f"Catálogo escrito en {salida}")


if __name__ == "__main__":
    main()
