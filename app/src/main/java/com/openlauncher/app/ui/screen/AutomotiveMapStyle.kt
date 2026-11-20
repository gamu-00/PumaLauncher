package com.openlauncher.app.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import java.util.Locale

/**
 * Tesla Navigation & Apple Maps inspired high-contrast automotive vector style for MapLibre.
 *
 * Features:
 * 1. Aggressive decluttering: hides pedestrian underpasses, footpaths/trails, minor/metro railways,
 *    parking lots, and suppresses service roads until zoom >= 16.
 *    Reduces road label density via wide symbol-spacing, text-padding, and crisp text-halo-width.
 * 2. Balanced automotive color hierarchy + compact road shields (A22, SS12, SS45bis):
 *    - Night theme: obsidian asphalt (#0E1117), deep abyss water (#0B1E36), subtle parks/woods (0.25 opacity),
 *      refined gold-amber motorways & trunks (#C9933B, -30% width), crisp primary/secondary roads (#9AA5B4
 *      with dark casing), discreet anthracite local roads (#3B4252).
 *    - Day theme (Cool Slate anti-glare): light stone slate (#E5E9F0), clean map blue water (#8AB4D8),
 *      soft muted sage parks (#D8E2DC at 0.50 opacity, no sharp borders), satin amber motorways (#E09F5A,
 *      -30% width with thin casing), pure white primary/secondary roads (#FFFFFF) with #B0BAC5 casing.
 * 3. Lightweight 3D buildings (`fill-extrusion` at zoom >= 15, opacity 0.45, gradual scaled height,
 *    tone-on-tone color) and Apple Maps style electric-cyan (#00D2FF) dual-tone route ribbon.
 */
object AutomotiveMapStyle {

    private const val TAG = "PumaNavStyle"

    // Night Palette (Tesla / Apple Maps Dark)
    const val NIGHT_BG_HEX = "#0E1117"
    const val NIGHT_WATER_HEX = "#0B1E36"
    const val NIGHT_PARK_HEX = "#111F1A"
    const val NIGHT_MOTORWAY_HEX = "#C9933B"
    const val NIGHT_MOTORWAY_CASING_HEX = "#2B1E08"
    const val NIGHT_PRIMARY_HEX = "#9AA5B4"
    const val NIGHT_PRIMARY_CASING_HEX = "#141A24"
    const val NIGHT_SECONDARY_HEX = "#9AA5B4"
    const val NIGHT_SECONDARY_CASING_HEX = "#141A24"
    const val NIGHT_LOCAL_HEX = "#3B4252"
    const val NIGHT_LOCAL_CASING_HEX = "#131720"
    const val NIGHT_BUILDING_3D_HEX = "#19202B"
    const val NIGHT_LABEL_PRIMARY_HEX = "#ECEFF4"
    const val NIGHT_LABEL_SECONDARY_HEX = "#CBD5E1"
    const val NIGHT_LABEL_SUBURB_HEX = "#94A3B8"
    const val NIGHT_LABEL_MOTORWAY_HEX = "#E5C07B"
    const val NIGHT_LABEL_HALO_HEX = "#080A0F"

    // Day Palette (Cool Slate Anti-Glare)
    const val DAY_BG_HEX = "#E5E9F0"
    const val DAY_WATER_HEX = "#8AB4D8"
    const val DAY_PARK_HEX = "#D8E2DC"
    const val DAY_MOTORWAY_HEX = "#E09F5A"
    const val DAY_MOTORWAY_CASING_HEX = "#9A6329"
    const val DAY_PRIMARY_HEX = "#FFFFFF"
    const val DAY_PRIMARY_CASING_HEX = "#B0BAC5"
    const val DAY_SECONDARY_HEX = "#FFFFFF"
    const val DAY_SECONDARY_CASING_HEX = "#B0BAC5"
    const val DAY_LOCAL_HEX = "#EDF2F7"
    const val DAY_LOCAL_CASING_HEX = "#C2CBD6"
    const val DAY_BUILDING_3D_HEX = "#CFD7E3"
    const val DAY_LABEL_PRIMARY_HEX = "#1E293B"
    const val DAY_LABEL_SECONDARY_HEX = "#334155"
    const val DAY_LABEL_SUBURB_HEX = "#64748B"
    const val DAY_LABEL_MOTORWAY_HEX = "#7C2D12"
    const val DAY_LABEL_HALO_HEX = "#FFFFFF"

    // Apple Maps Active Route Ribbon Palette
    const val ROUTE_RIBBON_CORE_HEX = "#00D2FF"
    const val ROUTE_RIBBON_CASING_NIGHT_HEX = "#033B52"
    const val ROUTE_RIBBON_CASING_DAY_HEX = "#04435C"
    const val ROUTE_ALT_CORE_NIGHT_HEX = "#7B96B0"
    const val ROUTE_ALT_CORE_DAY_HEX = "#6B86A0"
    const val ROUTE_ALT_CASING_NIGHT_HEX = "#1E293B"
    const val ROUTE_ALT_CASING_DAY_HEX = "#475569"
    const val ROUTE_TRAVELED_CORE_HEX = "#64748B"
    const val ROUTE_TRAVELED_CASING_HEX = "#334155"

    private const val LAYER_BUILDING_3D = "building-3d"
    private const val SHIELD_IMAGE_NIGHT = "puma-road-shield-night"
    private const val SHIELD_IMAGE_DAY = "puma-road-shield-day"

    fun buildFallbackStyleJson(isLightTheme: Boolean): String {
        val bg = if (isLightTheme) DAY_BG_HEX else NIGHT_BG_HEX
        val name = if (isLightTheme) "PumaCoolSlateFallback" else "PumaObsidianFallback"
        return """
        {
          "version": 8,
          "name": "$name",
          "sources": {},
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "$bg"
              }
            }
          ]
        }
        """.trimIndent()
    }

    /**
     * Registers compact high-contrast road shield badge images into the loaded [Style].
     */
    fun ensureRoadShieldImages(style: Style) {
        if (style.getImage(SHIELD_IMAGE_NIGHT) == null) {
            style.addImage(
                SHIELD_IMAGE_NIGHT,
                createShieldBitmap(
                    fillHex = "#1B2432",
                    strokeHex = "#64748B"
                )
            )
        }
        if (style.getImage(SHIELD_IMAGE_DAY) == null) {
            style.addImage(
                SHIELD_IMAGE_DAY,
                createShieldBitmap(
                    fillHex = "#1E293B",
                    strokeHex = "#FFFFFF"
                )
            )
        }
    }

    private fun createShieldBitmap(fillHex: String, strokeHex: String): Bitmap {
        val width = 56
        val height = 28
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(1.5f, 1.5f, width - 1.5f, height - 1.5f)
        val radius = 7f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(fillHex)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(strokeHex)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        return bitmap
    }

    /**
     * Builds the complete MapLibre GL Style v8 JSON pointing to OpenFreeMap's Planet vector tiles.
     */
    fun buildAutomotiveStyleJson(isLightTheme: Boolean): String {
        val styleName = if (isLightTheme) "PumaAutomotiveCoolSlate" else "PumaAutomotiveNight"
        val bgHex = if (isLightTheme) DAY_BG_HEX else NIGHT_BG_HEX
        val waterHex = if (isLightTheme) DAY_WATER_HEX else NIGHT_WATER_HEX
        val parkHex = if (isLightTheme) DAY_PARK_HEX else NIGHT_PARK_HEX
        val parkOpacity = if (isLightTheme) 0.50 else 0.25
        val motorwayHex = if (isLightTheme) DAY_MOTORWAY_HEX else NIGHT_MOTORWAY_HEX
        val motorwayCasingHex = if (isLightTheme) DAY_MOTORWAY_CASING_HEX else NIGHT_MOTORWAY_CASING_HEX
        val primaryHex = if (isLightTheme) DAY_PRIMARY_HEX else NIGHT_PRIMARY_HEX
        val primaryCasingHex = if (isLightTheme) DAY_PRIMARY_CASING_HEX else NIGHT_PRIMARY_CASING_HEX
        val secondaryHex = if (isLightTheme) DAY_SECONDARY_HEX else NIGHT_SECONDARY_HEX
        val secondaryCasingHex = if (isLightTheme) DAY_SECONDARY_CASING_HEX else NIGHT_SECONDARY_CASING_HEX
        val localHex = if (isLightTheme) DAY_LOCAL_HEX else NIGHT_LOCAL_HEX
        val localCasingHex = if (isLightTheme) DAY_LOCAL_CASING_HEX else NIGHT_LOCAL_CASING_HEX
        val building3dHex = if (isLightTheme) DAY_BUILDING_3D_HEX else NIGHT_BUILDING_3D_HEX
        val labelPrimaryHex = if (isLightTheme) DAY_LABEL_PRIMARY_HEX else NIGHT_LABEL_PRIMARY_HEX
        val labelSecondaryHex = if (isLightTheme) DAY_LABEL_SECONDARY_HEX else NIGHT_LABEL_SECONDARY_HEX
        val labelSuburbHex = if (isLightTheme) DAY_LABEL_SUBURB_HEX else NIGHT_LABEL_SUBURB_HEX
        val labelMotorwayHex = if (isLightTheme) DAY_LABEL_MOTORWAY_HEX else NIGHT_LABEL_MOTORWAY_HEX
        val labelHaloHex = if (isLightTheme) DAY_LABEL_HALO_HEX else NIGHT_LABEL_HALO_HEX
        val shieldImage = if (isLightTheme) SHIELD_IMAGE_DAY else SHIELD_IMAGE_NIGHT

        return """
        {
          "version": 8,
          "name": "$styleName",
          "sources": {
            "openmaptiles": {
              "type": "vector",
              "url": "https://tiles.openfreemap.org/planet"
            }
          },
          "sprite": "https://tiles.openfreemap.org/sprites/ofm_f384/ofm",
          "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "$bgHex"
              }
            },
            {
              "id": "park",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "park",
              "filter": [
                "match",
                ["geometry-type"],
                ["MultiPolygon", "Polygon"],
                true,
                false
              ],
              "paint": {
                "fill-antialias": true,
                "fill-color": "$parkHex",
                "fill-opacity": $parkOpacity
              }
            },
            {
              "id": "landuse_park",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landuse",
              "filter": [
                "all",
                ["match", ["geometry-type"], ["MultiPolygon", "Polygon"], true, false],
                ["match", ["get", "class"], ["park", "grass", "cemetery", "pitch"], true, false]
              ],
              "paint": {
                "fill-antialias": true,
                "fill-color": "$parkHex",
                "fill-opacity": $parkOpacity
              }
            },
            {
              "id": "landcover_wood",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "minzoom": 8,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["MultiPolygon", "Polygon"], true, false],
                ["match", ["get", "class"], ["wood", "grass"], true, false]
              ],
              "paint": {
                "fill-antialias": true,
                "fill-color": "$parkHex",
                "fill-opacity": $parkOpacity
              }
            },
            {
              "id": "water",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "water",
              "filter": [
                "all",
                ["match", ["geometry-type"], ["MultiPolygon", "Polygon"], true, false],
                ["!=", ["get", "brunnel"], "tunnel"]
              ],
              "paint": {
                "fill-antialias": true,
                "fill-color": "$waterHex"
              }
            },
            {
              "id": "waterway",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "waterway",
              "minzoom": 9,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["!=", ["get", "brunnel"], "tunnel"]
              ],
              "paint": {
                "line-color": "$waterHex",
                "line-width": ["interpolate", ["linear"], ["zoom"], 9, 1, 16, 3.5]
              }
            },
            {
              "id": "highway_service_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 16,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["==", ["get", "class"], "service"],
                ["!=", ["get", "brunnel"], "tunnel"]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$localCasingHex",
                "line-opacity": 0.72,
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 16, 2.4, 19, 8.0]
              }
            },
            {
              "id": "highway_service_inner",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 16,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["==", ["get", "class"], "service"],
                ["!=", ["get", "brunnel"], "tunnel"]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$localHex",
                "line-opacity": 0.76,
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 16, 1.4, 19, 5.6]
              }
            },
            {
              "id": "highway_local_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 11.5,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["minor", "tertiary"], true, false]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$localCasingHex",
                "line-width": ["interpolate", ["exponential", 1.45], ["zoom"], 11.5, 1.8, 15, 5.2, 19, 15.0]
              }
            },
            {
              "id": "highway_local_inner",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 11.5,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["minor", "tertiary"], true, false]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$localHex",
                "line-width": ["interpolate", ["exponential", 1.45], ["zoom"], 11.5, 1.1, 15, 3.6, 19, 12.2]
              }
            },
            {
              "id": "highway_secondary_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 9,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["==", ["get", "class"], "secondary"]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$secondaryCasingHex",
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 9, 2.0, 13, 5.2, 16, 9.4, 19, 19.0]
              }
            },
            {
              "id": "highway_secondary_inner",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 9,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["==", ["get", "class"], "secondary"]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$secondaryHex",
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 9, 1.1, 13, 3.3, 16, 6.6, 19, 14.8]
              }
            },
            {
              "id": "highway_primary_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 6,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["==", ["get", "class"], "primary"]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$primaryCasingHex",
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 6, 1.8, 11, 4.8, 15, 9.8, 19, 21.5]
              }
            },
            {
              "id": "highway_primary_inner",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 6,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["==", ["get", "class"], "primary"]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$primaryHex",
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 6, 1.0, 11, 3.0, 15, 6.8, 19, 16.8]
              }
            },
            {
              "id": "highway_motorway_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 5,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["motorway", "trunk"], true, false]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$motorwayCasingHex",
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 5, 1.5, 10, 3.8, 15, 7.8, 19, 18.0]
              }
            },
            {
              "id": "highway_motorway_inner",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 5,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["motorway", "trunk"], true, false]
              ],
              "layout": {
                "line-cap": "round",
                "line-join": "round"
              },
              "paint": {
                "line-color": "$motorwayHex",
                "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 5, 0.9, 10, 2.4, 15, 5.8, 19, 14.6]
              }
            },
            {
              "id": "$LAYER_BUILDING_3D",
              "type": "fill-extrusion",
              "source": "openmaptiles",
              "source-layer": "building",
              "minzoom": 15,
              "filter": [
                "match",
                ["geometry-type"],
                ["MultiPolygon", "Polygon"],
                true,
                false
              ],
              "paint": {
                "fill-extrusion-color": "$building3dHex",
                "fill-extrusion-opacity": 0.45,
                "fill-extrusion-height": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  15, 0,
                  15.5, ["*", ["min", ["coalesce", ["get", "render_height"], 8], 24], 0.35],
                  16.5, ["*", ["min", ["coalesce", ["get", "render_height"], 8], 28], 0.65]
                ],
                "fill-extrusion-base": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  15, 0,
                  15.5, ["*", ["min", ["coalesce", ["get", "render_min_height"], 0], 10], 0.35],
                  16.5, ["*", ["min", ["coalesce", ["get", "render_min_height"], 0], 10], 0.65]
                ]
              }
            },
            {
              "id": "highway_name_local",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 15.5,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["tertiary", "minor"], true, false]
              ],
              "layout": {
                "symbol-placement": "line",
                "symbol-spacing": 680,
                "text-padding": 26,
                "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]],
                "text-font": ["Noto Sans Regular"],
                "text-max-angle": 25,
                "text-pitch-alignment": "viewport",
                "text-rotation-alignment": "map",
                "text-size": ["interpolate", ["linear"], ["zoom"], 15.5, 11, 18, 13]
              },
              "paint": {
                "text-color": "$labelSecondaryHex",
                "text-halo-color": "$labelHaloHex",
                "text-halo-width": 2.0,
                "text-halo-blur": 0.2
              }
            },
            {
              "id": "highway_name_major",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 12,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["primary", "secondary"], true, false]
              ],
              "layout": {
                "symbol-placement": "line",
                "symbol-spacing": 600,
                "text-padding": 22,
                "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]],
                "text-font": ["Noto Sans Bold"],
                "text-max-angle": 28,
                "text-pitch-alignment": "viewport",
                "text-rotation-alignment": "map",
                "text-size": ["interpolate", ["linear"], ["zoom"], 12, 11.5, 16, 13.5, 18, 15]
              },
              "paint": {
                "text-color": "$labelPrimaryHex",
                "text-halo-color": "$labelHaloHex",
                "text-halo-width": 2.2,
                "text-halo-blur": 0.2
              }
            },
            {
              "id": "highway_name_motorway",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 10,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
                ["match", ["get", "class"], ["motorway", "trunk"], true, false]
              ],
              "layout": {
                "symbol-placement": "line",
                "symbol-spacing": 640,
                "text-padding": 24,
                "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]],
                "text-font": ["Noto Sans Bold"],
                "text-max-angle": 25,
                "text-pitch-alignment": "viewport",
                "text-rotation-alignment": "map",
                "text-size": ["interpolate", ["linear"], ["zoom"], 10, 11, 15, 13]
              },
              "paint": {
                "text-color": "$labelMotorwayHex",
                "text-halo-color": "$labelHaloHex",
                "text-halo-width": 2.4,
                "text-halo-blur": 0.2
              }
            },
            {
              "id": "highway_shield",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 8,
              "filter": [
                "all",
                ["has", "ref"],
                ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]
              ],
              "layout": {
                "symbol-placement": "line",
                "symbol-spacing": 520,
                "icon-image": "$shieldImage",
                "icon-text-fit": "both",
                "icon-text-fit-padding": [2.5, 6, 2.5, 6],
                "icon-rotation-alignment": "viewport",
                "icon-pitch-alignment": "viewport",
                "text-field": ["to-string", ["get", "ref"]],
                "text-font": ["Noto Sans Bold"],
                "text-rotation-alignment": "viewport",
                "text-pitch-alignment": "viewport",
                "text-size": 10.5,
                "text-padding": 18
              },
              "paint": {
                "text-color": "#FFFFFF"
              }
            },
            {
              "id": "place_town",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "place",
              "minzoom": 8,
              "maxzoom": 15,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["MultiPoint", "Point"], true, false],
                ["match", ["get", "class"], ["town", "suburb"], true, false]
              ],
              "layout": {
                "text-anchor": "center",
                "text-padding": 22,
                "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]],
                "text-font": ["Noto Sans Regular"],
                "text-size": ["interpolate", ["linear"], ["zoom"], 8, 9.5, 14, 11.5]
              },
              "paint": {
                "text-color": "$labelSuburbHex",
                "text-halo-color": "$labelHaloHex",
                "text-halo-width": 1.8,
                "text-halo-blur": 0.2
              }
            },
            {
              "id": "place_city",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "place",
              "minzoom": 4,
              "maxzoom": 14,
              "filter": [
                "all",
                ["match", ["geometry-type"], ["MultiPoint", "Point"], true, false],
                ["==", ["get", "class"], "city"]
              ],
              "layout": {
                "text-anchor": "center",
                "text-padding": 24,
                "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]],
                "text-font": ["Noto Sans Bold"],
                "text-size": ["interpolate", ["linear"], ["zoom"], 4, 11, 10, 15, 14, 17]
              },
              "paint": {
                "text-color": "$labelPrimaryHex",
                "text-halo-color": "$labelHaloHex",
                "text-halo-width": 2.2,
                "text-halo-blur": 0.2
              }
            }
          ]
        }
        """.trimIndent()
    }

    /**
     * Enforces Tesla / Apple Maps decluttering, color hierarchy, 3D building extrusion,
     * road shields, and crisp road typography on any loaded MapLibre [Style].
     */
    fun applyAutomotiveStyleToLoadedLayers(style: Style, isLightTheme: Boolean) {
        ensureRoadShieldImages(style)

        val bgHex = if (isLightTheme) DAY_BG_HEX else NIGHT_BG_HEX
        val waterHex = if (isLightTheme) DAY_WATER_HEX else NIGHT_WATER_HEX
        val parkHex = if (isLightTheme) DAY_PARK_HEX else NIGHT_PARK_HEX
        val parkOpacity = if (isLightTheme) 0.50f else 0.25f
        val motorwayHex = if (isLightTheme) DAY_MOTORWAY_HEX else NIGHT_MOTORWAY_HEX
        val motorwayCasingHex = if (isLightTheme) DAY_MOTORWAY_CASING_HEX else NIGHT_MOTORWAY_CASING_HEX
        val primaryHex = if (isLightTheme) DAY_PRIMARY_HEX else NIGHT_PRIMARY_HEX
        val primaryCasingHex = if (isLightTheme) DAY_PRIMARY_CASING_HEX else NIGHT_PRIMARY_CASING_HEX
        val secondaryHex = if (isLightTheme) DAY_SECONDARY_HEX else NIGHT_SECONDARY_HEX
        val secondaryCasingHex = if (isLightTheme) DAY_SECONDARY_CASING_HEX else NIGHT_SECONDARY_CASING_HEX
        val localHex = if (isLightTheme) DAY_LOCAL_HEX else NIGHT_LOCAL_HEX
        val localCasingHex = if (isLightTheme) DAY_LOCAL_CASING_HEX else NIGHT_LOCAL_CASING_HEX
        val building3dHex = if (isLightTheme) DAY_BUILDING_3D_HEX else NIGHT_BUILDING_3D_HEX
        val labelPrimaryHex = if (isLightTheme) DAY_LABEL_PRIMARY_HEX else NIGHT_LABEL_PRIMARY_HEX
        val labelSuburbHex = if (isLightTheme) DAY_LABEL_SUBURB_HEX else NIGHT_LABEL_SUBURB_HEX
        val labelHaloHex = if (isLightTheme) DAY_LABEL_HALO_HEX else NIGHT_LABEL_HALO_HEX
        val shieldImage = if (isLightTheme) SHIELD_IMAGE_DAY else SHIELD_IMAGE_NIGHT

        val hiddenLayerTokens = listOf(
            "path", "pedestrian", "footway", "steps", "cycleway", "pier",
            "railway", "transit", "aeroway", "parking", "oneway", "poi",
            "place_other", "place_village", "label_other", "label_village"
        )

        for (layer in style.layers) {
            val id = layer.id.lowercase(Locale.ROOT)
            if (id.startsWith("puma-") || id.startsWith("mapbox-location")) continue

            // 1. Aggressive decluttering: hide pedestrian paths/underpasses, railways, parking, POIs
            if (hiddenLayerTokens.any { it in id }) {
                runCatching {
                    layer.setProperties(PropertyFactory.visibility(Property.NONE))
                }
                continue
            }

            if ("service" in id) {
                runCatching { layer.minZoom = 16f }
            }

            when (layer) {
                is BackgroundLayer -> {
                    runCatching {
                        layer.setProperties(PropertyFactory.backgroundColor(AndroidColor.parseColor(bgHex)))
                    }
                }

                is FillLayer -> {
                    runCatching {
                        when {
                            "water" in id -> layer.setProperties(
                                PropertyFactory.fillColor(AndroidColor.parseColor(waterHex))
                            )
                            "park" in id || "wood" in id || "grass" in id -> layer.setProperties(
                                PropertyFactory.fillAntialias(true),
                                PropertyFactory.fillColor(AndroidColor.parseColor(parkHex)),
                                PropertyFactory.fillOpacity(parkOpacity)
                            )
                            id == "building" -> {
                                layer.setProperties(PropertyFactory.visibility(Property.NONE))
                            }
                        }
                    }
                }

                is FillExtrusionLayer -> {
                    runCatching {
                        layer.minZoom = 15f
                        layer.setProperties(
                            PropertyFactory.fillExtrusionColor(AndroidColor.parseColor(building3dHex)),
                            PropertyFactory.fillExtrusionOpacity(0.45f)
                        )
                    }
                }

                is LineLayer -> {
                    runCatching {
                        when {
                            "waterway" in id -> layer.setProperties(
                                PropertyFactory.lineColor(AndroidColor.parseColor(waterHex))
                            )
                            "motorway" in id || "trunk" in id -> {
                                val c = if ("casing" in id) motorwayCasingHex else motorwayHex
                                layer.setProperties(PropertyFactory.lineColor(AndroidColor.parseColor(c)))
                            }
                            "primary" in id -> {
                                val c = if ("casing" in id) primaryCasingHex else primaryHex
                                layer.setProperties(PropertyFactory.lineColor(AndroidColor.parseColor(c)))
                            }
                            "secondary" in id -> {
                                val c = if ("casing" in id) secondaryCasingHex else secondaryHex
                                layer.setProperties(PropertyFactory.lineColor(AndroidColor.parseColor(c)))
                            }
                            "local" in id || "minor" in id || "service" in id -> {
                                val c = if ("casing" in id) localCasingHex else localHex
                                layer.setProperties(PropertyFactory.lineColor(AndroidColor.parseColor(c)))
                            }
                        }
                    }
                }

                is SymbolLayer -> {
                    runCatching {
                        when {
                            id == "highway_shield" -> {
                                layer.setProperties(
                                    PropertyFactory.iconImage(shieldImage),
                                    PropertyFactory.textColor(AndroidColor.WHITE)
                                )
                            }
                            "highway" in id || "road" in id -> {
                                layer.setProperties(
                                    PropertyFactory.symbolSpacing(600f),
                                    PropertyFactory.textPadding(22f),
                                    PropertyFactory.textHaloColor(AndroidColor.parseColor(labelHaloHex)),
                                    PropertyFactory.textHaloWidth(2.2f),
                                    PropertyFactory.textHaloBlur(0.2f)
                                )
                            }
                            id == "place_town" -> {
                                layer.setProperties(
                                    PropertyFactory.textColor(AndroidColor.parseColor(labelSuburbHex)),
                                    PropertyFactory.textHaloColor(AndroidColor.parseColor(labelHaloHex)),
                                    PropertyFactory.textHaloWidth(1.8f),
                                    PropertyFactory.textHaloBlur(0.2f)
                                )
                            }
                            "place" in id || "label" in id -> {
                                layer.setProperties(
                                    PropertyFactory.textColor(AndroidColor.parseColor(labelPrimaryHex)),
                                    PropertyFactory.textHaloColor(AndroidColor.parseColor(labelHaloHex)),
                                    PropertyFactory.textHaloWidth(2.1f),
                                    PropertyFactory.textHaloBlur(0.2f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ensure 3D building extrusion layer exists if a remote style without it was loaded
        if (style.getSource("openmaptiles") != null && style.getLayer(LAYER_BUILDING_3D) == null) {
            runCatching {
                val extrusionLayer = FillExtrusionLayer(LAYER_BUILDING_3D, "openmaptiles").apply {
                    sourceLayer = "building"
                    minZoom = 15f
                    setProperties(
                        PropertyFactory.fillExtrusionColor(AndroidColor.parseColor(building3dHex)),
                        PropertyFactory.fillExtrusionOpacity(0.45f),
                        PropertyFactory.fillExtrusionHeight(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(15, 0f),
                                Expression.stop(
                                    16.5,
                                    Expression.product(
                                        Expression.min(
                                            Expression.coalesce(Expression.get("render_height"), Expression.literal(8f)),
                                            Expression.literal(28f)
                                        ),
                                        Expression.literal(0.65f)
                                    )
                                )
                            )
                        ),
                        PropertyFactory.fillExtrusionBase(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(15, 0f),
                                Expression.stop(
                                    16.5,
                                    Expression.product(
                                        Expression.min(
                                            Expression.coalesce(Expression.get("render_min_height"), Expression.literal(0f)),
                                            Expression.literal(10f)
                                        ),
                                        Expression.literal(0.65f)
                                    )
                                )
                            )
                        )
                    )
                }
                val firstLabelId = style.layers.firstOrNull { it is SymbolLayer }?.id
                if (firstLabelId != null) {
                    style.addLayerBelow(extrusionLayer, firstLabelId)
                } else {
                    style.addLayer(extrusionLayer)
                }
            }
        }

        Log.d(
            TAG,
            "Stile vettoriale automotive Tesla/Apple Maps applicato (${if (isLightTheme) "Giorno Cool Slate" else "Notte Obsidian"})"
        )
    }

    fun routeRibbonCoreWidthExpression(): Expression =
        Expression.interpolate(
            Expression.exponential(1.35f),
            Expression.zoom(),
            Expression.stop(5, 4.5f),
            Expression.stop(10, 6.5f),
            Expression.stop(13, 8.8f),
            Expression.stop(16, 11.8f),
            Expression.stop(19, 16.0f)
        )

    fun routeRibbonCasingWidthExpression(): Expression =
        Expression.interpolate(
            Expression.exponential(1.35f),
            Expression.zoom(),
            Expression.stop(5, 8.0f),
            Expression.stop(10, 10.8f),
            Expression.stop(13, 13.8f),
            Expression.stop(16, 17.8f),
            Expression.stop(19, 23.0f)
        )
}
