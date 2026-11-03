package com.kalaiselvan.ride.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.location.Location
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.navigation.OlaLatLng
import com.kalaiselvan.ride.navigation.OlaRoute

/**
 * High-performance, self-contained Visual Map component.
 * Inlines Leaflet CSS and JS directly from local assets to bypass all Android WebView
 * file security restrictions, and renders CartoDB dark matter raster tiles with
 * neon green route polyline and live rider GPS marker.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VisualMapView(
    route: OlaRoute?,
    candidateRoutes: List<OlaRoute> = emptyList(),
    selectedRouteIndex: Int = 0,
    onRouteSelected: ((Int) -> Unit)? = null,
    currentLocation: Location?,
    compassHeading: Float = 0f,
    onMapBearingChanged: ((Float) -> Unit)? = null,
    apiKey: String,
    mapStyle: String = "default-dark-standard",
    modifier: Modifier = Modifier,
    isNavigating: Boolean = false,
    recenterTrigger: Long = 0L,
    resetNorthTrigger: Long = 0L
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    val currentOnRouteSelected by rememberUpdatedState(onRouteSelected)
    val currentOnMapBearingChanged by rememberUpdatedState(onMapBearingChanged)

    val routesToRender = remember(candidateRoutes, route) {
        if (candidateRoutes.isNotEmpty()) candidateRoutes else if (route != null) listOf(route) else emptyList()
    }

    var firstKnownLocation by remember { mutableStateOf(currentLocation) }
    LaunchedEffect(currentLocation) {
        if (firstKnownLocation == null && currentLocation != null) {
            firstKnownLocation = currentLocation
        }
    }

    if (firstKnownLocation == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121417)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E676),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Acquiring GPS Satellite Lock...",
                    color = Color(0xFF9E9E9E),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    val initialLng = firstKnownLocation?.longitude ?: 0.0
    val initialLat = firstKnownLocation?.latitude ?: 0.0

    // Read Leaflet directly from APK assets to guarantee 100% inlined execution
    val leafletCss = remember {
        try {
            context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("VisualMap", "Failed to load leaflet.css from assets", e)
            ""
        }
    }

    val leafletJs = remember {
        try {
            context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("VisualMap", "Failed to load leaflet.js from assets", e)
            ""
        }
    }

    val leafletRotateCss = remember {
        try {
            context.assets.open("leaflet/leaflet-rotate.css").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    val leafletRotateJs = remember {
        try {
            context.assets.open("leaflet/leaflet-rotate.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("VisualMap", "Failed to load leaflet-rotate.js from assets", e)
            ""
        }
    }

    val mapHtml = remember(leafletCss, leafletJs, leafletRotateCss, leafletRotateJs, apiKey, mapStyle) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <style>
            $leafletCss
            $leafletRotateCss

            * { -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; box-sizing: border-box; }
            html, body {
              width: 100%; height: 100%; margin: 0; padding: 0;
              background-color: #121417; overflow: hidden;
              border-radius: 18px;
            }
            #map {
              position: absolute; top: 0; bottom: 0; left: 0; right: 0;
              width: 100%; height: 100%;
              background-color: #121417;
              border-radius: 18px;
            }
            .bike-marker-container {
              background: transparent;
              border: none;
            }
            .bike-nav-marker {
              width: 26px; height: 26px;
              display: flex; align-items: center; justify-content: center;
              transition: transform 0.25s cubic-bezier(0.2, 0, 0, 1);
              filter: drop-shadow(0 0 8px rgba(0, 255, 163, 0.85));
            }
            .dest-marker {
              width: 14px; height: 14px; background: #00FF88; border: 2px solid #FFFFFF; border-radius: 50%;
              box-shadow: 0 0 14px #00FF88, 0 0 6px #00FFA3;
            }
          </style>
          <script>
            $leafletJs
            $leafletRotateJs
          </script>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var map = null;
            var tileLayer = null;
            var activeRouteLayers = [];
            var destMarker = null;
            var riderMarker = null;
            var autoFollow = true;
            window.isMapReady = false;

            function getTileUrl(style, olaKey) {
              var key = (olaKey && olaKey.length > 5) ? olaKey : '$apiKey';
              if (key && key.length > 5) {
                var styleName = (style === 'default-light-standard' || style === 'light') ? 'default-light-standard' : 'default-dark-standard';
                return 'https://api.olamaps.io/tiles/vector/v1/styles/' + styleName + '/{z}/{x}/{y}.png?api_key=' + encodeURIComponent(key);
              }
              return 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
            }

            function ensureMapSize() {
              if (map) {
                try {
                  map.invalidateSize(false);
                } catch(e) {}
              }
            }

            function initMap() {
              if (typeof L === 'undefined') {
                console.error("Leaflet L is still undefined!");
                setTimeout(initMap, 50);
                return;
              }
              try {
                map = L.map('map', {
                  zoomControl: false,
                  attributionControl: false,
                  center: [$initialLat, $initialLng],
                  zoom: 14,
                  rotate: true,
                  touchRotate: true,
                  rotateControl: false
                });

                tileLayer = L.tileLayer(getTileUrl('$mapStyle', '$apiKey'), {
                  maxZoom: 19,
                  minZoom: 3,
                  attribution: ''
                }).addTo(map);

                tileLayer.on('tileload', function() {
                  console.log("TILE_LOAD_SUCCESS");
                });
                tileLayer.on('tileerror', function(e) {
                  console.warn("Tile load error url: " + (e.tile ? e.tile.src : 'unknown'));
                  if (!window.hasFallenBack) {
                    window.hasFallenBack = true;
                    map.removeLayer(tileLayer);
                    tileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                      maxZoom: 19
                    }).addTo(map);
                  }
                });

                map.on('rotate', function() {
                  try {
                    var b = (typeof map.getBearing === 'function') ? map.getBearing() : 0;
                    if (window.AndroidBridge && window.AndroidBridge.onMapBearingChanged) {
                      window.AndroidBridge.onMapBearingChanged(b);
                    }
                  } catch(e) {}
                });

                map.on('dragstart', function() {
                  autoFollow = false;
                });
                map.on('zoomstart', function() {
                  autoFollow = false;
                });

                window.isMapReady = true;
                console.log("Leaflet initialized successfully with Ola Maps Dark tiles & rotate support!");
                setTimeout(ensureMapSize, 50);
                setTimeout(ensureMapSize, 250);
                setTimeout(ensureMapSize, 800);
              } catch(e) {
                console.error("Map init error: " + e);
              }
            }

            function clearRouteLayers() {
              if (activeRouteLayers && activeRouteLayers.length > 0) {
                for (var i = 0; i < activeRouteLayers.length; i++) {
                  try {
                    map.removeLayer(activeRouteLayers[i]);
                  } catch(e) {}
                }
              }
              activeRouteLayers = [];
              if (destMarker) {
                try { map.removeLayer(destMarker); } catch(e) {}
                destMarker = null;
              }
            }

            function renderRoutes(routesData, selectedIdx) {
              if (!window.isMapReady || !map) {
                setTimeout(function() { renderRoutes(routesData, selectedIdx); }, 100);
                return;
              }
              try {
                ensureMapSize();
                clearRouteLayers();

                if (!routesData || routesData.length === 0) return;

                var allCoords = [];
                var selIdx = selectedIdx || 0;
                if (selIdx < 0 || selIdx >= routesData.length) selIdx = 0;

                // 1. First render NON-SELECTED alternative candidate routes (so they sit underneath)
                for (var i = 0; i < routesData.length; i++) {
                  if (i === selIdx) continue;
                  var r = routesData[i];
                  if (!r.coords || r.coords.length === 0) continue;
                  var latLngs = r.coords.map(function(c) { return [c[1], c[0]]; });
                  allCoords = allCoords.concat(latLngs);

                  // Alternative route dark casing
                  var altHalo = L.polyline(latLngs, {
                    color: '#161C24',
                    weight: 8,
                    opacity: 0.85,
                    lineCap: 'round',
                    lineJoin: 'round'
                  }).addTo(map);
                  activeRouteLayers.push(altHalo);

                  // Alternative route core line (sleek slate blue-grey)
                  var altCore = L.polyline(latLngs, {
                    color: '#5C6E82',
                    weight: 4.5,
                    opacity: 0.85,
                    lineCap: 'round',
                    lineJoin: 'round'
                  }).addTo(map);
                  activeRouteLayers.push(altCore);

                  // Wide invisible tap target for gloved finger selection
                  (function(routeIndex) {
                    var hitLine = L.polyline(latLngs, {
                      color: '#FFFFFF',
                      weight: 32,
                      opacity: 0.001,
                      lineCap: 'round',
                      lineJoin: 'round',
                      interactive: true
                    }).addTo(map);

                    hitLine.on('click', function(e) {
                      L.DomEvent.stopPropagation(e);
                      console.log("Alternative route tapped in map: " + routeIndex);
                      if (window.AndroidBridge && window.AndroidBridge.onRouteSelected) {
                        window.AndroidBridge.onRouteSelected(routeIndex);
                      }
                    });
                    activeRouteLayers.push(hitLine);
                  })(i);
                }

                // 2. Next render SELECTED route on top with neon bloom
                var selRoute = routesData[selIdx] || routesData[0];
                if (selRoute && selRoute.coords && selRoute.coords.length > 0) {
                  var selLatLngs = selRoute.coords.map(function(c) { return [c[1], c[0]]; });
                  allCoords = allCoords.concat(selLatLngs);

                  // Outer diffuse neon bloom
                  var routeGlowOuter = L.polyline(selLatLngs, {
                    color: '#00FF88',
                    weight: 16,
                    opacity: 0.22,
                    lineCap: 'round',
                    lineJoin: 'round'
                  }).addTo(map);
                  activeRouteLayers.push(routeGlowOuter);

                  // Mid bright neon halo
                  var routeGlow = L.polyline(selLatLngs, {
                    color: '#00FF88',
                    weight: 8,
                    opacity: 0.55,
                    lineCap: 'round',
                    lineJoin: 'round'
                  }).addTo(map);
                  activeRouteLayers.push(routeGlow);

                  // Crisp vibrant lime-cyan core path
                  var routeLine = L.polyline(selLatLngs, {
                    color: '#66FFB8',
                    weight: 4,
                    opacity: 1.0,
                    lineCap: 'round',
                    lineJoin: 'round'
                  }).addTo(map);
                  activeRouteLayers.push(routeLine);

                  // Wide tap target for selected route
                  (function(routeIndex) {
                    var selHitLine = L.polyline(selLatLngs, {
                      color: '#FFFFFF',
                      weight: 32,
                      opacity: 0.001,
                      lineCap: 'round',
                      lineJoin: 'round',
                      interactive: true
                    }).addTo(map);
                    selHitLine.on('click', function(e) {
                      L.DomEvent.stopPropagation(e);
                      if (window.AndroidBridge && window.AndroidBridge.onRouteSelected) {
                        window.AndroidBridge.onRouteSelected(routeIndex);
                      }
                    });
                    activeRouteLayers.push(selHitLine);
                  })(selIdx);

                  // Destination Marker on selected route end
                  var endPt = selLatLngs[selLatLngs.length - 1];
                  var destIcon = L.divIcon({
                    className: 'dest-marker',
                    iconSize: [14, 14],
                    iconAnchor: [7, 7]
                  });
                  destMarker = L.marker(endPt, { icon: destIcon, zIndexOffset: 900 }).addTo(map);
                }

                if (allCoords.length > 0) {
                  map.fitBounds(L.latLngBounds(allCoords), { padding: [35, 35], maxZoom: 16 });
                  setTimeout(ensureMapSize, 200);
                }
              } catch(e) {
                console.error("renderRoutes error: " + e);
              }
            }

            function renderRoute(coords) {
              if (!coords || coords.length === 0) {
                renderRoutes([], 0);
              } else {
                renderRoutes([{ index: 0, coords: coords }], 0);
              }
            }

            function updateRider(lng, lat, bearing) {
              if (!window.isMapReady || !map) return;
              try {
                var hasBearing = (typeof bearing === 'number' && !isNaN(bearing) && bearing >= 0);
                var rot = hasBearing ? Math.round(bearing) : 0;
                if (!riderMarker) {
                  var riderHtml = '<div class="bike-nav-marker" style="transform: rotate(' + rot + 'deg);">' +
                                    '<svg width="24" height="24" viewBox="0 0 24 24">' +
                                      '<path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z" fill="#00FFA3" stroke="#FFFFFF" stroke-width="1.8" stroke-linejoin="round"/>' +
                                    '</svg>' +
                                  '</div>';
                  var riderIcon = L.divIcon({
                    className: 'bike-marker-container',
                    html: riderHtml,
                    iconSize: [26, 26],
                    iconAnchor: [13, 13]
                  });
                  riderMarker = L.marker([lat, lng], { icon: riderIcon, zIndexOffset: 1000 }).addTo(map);
                } else {
                  riderMarker.setLatLng([lat, lng]);
                  var elem = riderMarker.getElement();
                  if (elem) {
                    var inner = elem.querySelector('.bike-nav-marker');
                    if (inner && hasBearing) {
                      inner.style.transform = 'rotate(' + rot + 'deg)';
                    }
                  }
                }
              } catch(e) {}
            }

            function centerOnRider(lng, lat, zoom, force) {
              if (!window.isMapReady || !map) return;
              if (!autoFollow && !force) return;
              try {
                ensureMapSize();
                var targetZoom = zoom || 18;
                map.flyTo([lat, lng], targetZoom, { animate: true, duration: 0.8 });
              } catch(e) {}
            }

            function setTileStyle(newStyle, key) {
              if (!map || !tileLayer) return;
              map.removeLayer(tileLayer);
              tileLayer = L.tileLayer(getTileUrl(newStyle, key), {
                maxZoom: 19,
                minZoom: 3,
                attribution: ''
              }).addTo(map);
            }

            function recenter() {
              autoFollow = true;
              if (!window.isMapReady || !map) return;
              if (riderMarker) {
                var pos = riderMarker.getLatLng();
                centerOnRider(pos.lng, pos.lat, 18, true);
              } else if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(function(pos) {
                  centerOnRider(pos.coords.longitude, pos.coords.latitude, 18, true);
                }, function(err) {
                  console.warn("Geolocation fallback error", err);
                }, { enableHighAccuracy: true, timeout: 5000 });
              }
            }

            function resetNorth() {
              if (!map) return;
              try {
                if (typeof map.setBearing === 'function') {
                  map.setBearing(0);
                }
              } catch(e) {}
            }

            window.addEventListener('resize', ensureMapSize);
            document.addEventListener('DOMContentLoaded', initMap);
            if (document.readyState === 'complete' || document.readyState === 'interactive') {
              initMap();
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121417))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#121417"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowFileAccess = true
                        allowContentAccess = true
                        setGeolocationEnabled(true)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    addJavascriptInterface(
                        MapJsBridge(
                            callbackProvider = { currentOnRouteSelected },
                            bearingCallbackProvider = { currentOnMapBearingChanged }
                        ),
                        "AndroidBridge"
                    )
                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }

                        override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                            val msg = cm?.message() ?: ""
                            Log.d("VisualMap", "CONSOLE [${cm?.messageLevel()}]: $msg (line ${cm?.lineNumber()})")
                            if (msg.contains("error", ignoreCase = true)) {
                                LogStore.logEvent("VisualMap: $msg")
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("VisualMap", "PAGE FINISHED: $url")
                            isMapLoaded = true
                            webViewRef = view
                            view?.evaluateJavascript("ensureMapSize();", null)

                            // Immediately dispatch routes if already present
                            if (routesToRender.isNotEmpty()) {
                                val routesJson = buildRoutesJson(routesToRender)
                                Log.d("VisualMap", "onPageFinished dispatching renderRoutes with ${routesToRender.size} route(s), selected=$selectedRouteIndex")
                                view?.evaluateJavascript("renderRoutes($routesJson, $selectedRouteIndex);", null)
                            }
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            Log.e("VisualMap", "LOAD ERROR: ${error?.errorCode} - ${error?.description} for ${request?.url}")
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            Log.w("VisualMap", "SSL ERROR: $error - proceeding")
                            handler?.proceed()
                        }
                    }
                    loadDataWithBaseURL("https://api.olamaps.io/", mapHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
                if (isMapLoaded) {
                    view.evaluateJavascript("ensureMapSize();", null)
                }
            }
        )

        // Render route polylines when routes or selected index updates
        LaunchedEffect(routesToRender, selectedRouteIndex, isMapLoaded) {
            val webView = webViewRef ?: return@LaunchedEffect
            if (!isMapLoaded) return@LaunchedEffect
            Log.d("VisualMap", "LaunchedEffect: routes=${routesToRender.size}, selectedIndex=$selectedRouteIndex")

            if (routesToRender.isNotEmpty()) {
                val routesJson = buildRoutesJson(routesToRender)
                webView.evaluateJavascript("renderRoutes($routesJson, $selectedRouteIndex);", null)
            } else {
                webView.evaluateJavascript("renderRoutes([], 0);", null)
            }
        }

        // Effective bearing: fuse phone compass heading when stopped / moving slow (< 12 km/h / 3.2 m/s),
        // and GPS bearing when cruising with valid satellite course.
        val effectiveBearing = remember(currentLocation, compassHeading) {
            if (currentLocation != null && currentLocation.hasSpeed() && currentLocation.speed >= 3.2f && currentLocation.hasBearing()) {
                currentLocation.bearing
            } else {
                compassHeading
            }
        }

        // Live Rider Location GPS updates with heading bearing
        LaunchedEffect(currentLocation, effectiveBearing, isMapLoaded, isNavigating) {
            val webView = webViewRef ?: return@LaunchedEffect
            if (!isMapLoaded || currentLocation == null) return@LaunchedEffect

            val lat = currentLocation.latitude
            val lng = currentLocation.longitude
            webView.evaluateJavascript("updateRider($lng, $lat, $effectiveBearing);", null)

            // When navigating, camera follows the rider at street/turn level UNLESS rider is interacting
            if (isNavigating) {
                webView.evaluateJavascript("centerOnRider($lng, $lat, 18, false);", null)
            }
        }

        // Recenter on request (Find Me circle button)
        LaunchedEffect(recenterTrigger) {
            if (recenterTrigger > 0L && isMapLoaded) {
                val webView = webViewRef ?: return@LaunchedEffect
                if (currentLocation != null) {
                    webView.evaluateJavascript(
                        "updateRider(${currentLocation.longitude}, ${currentLocation.latitude}, $effectiveBearing); centerOnRider(${currentLocation.longitude}, ${currentLocation.latitude}, 18, true);",
                        null
                    )
                } else {
                    webView.evaluateJavascript("recenter();", null)
                }
            }
        }

        // Reset map rotation to North (0 deg)
        LaunchedEffect(resetNorthTrigger) {
            if (resetNorthTrigger > 0L && isMapLoaded) {
                webViewRef?.evaluateJavascript("resetNorth();", null)
            }
        }

        // Live map style dynamic update
        LaunchedEffect(mapStyle) {
            if (isMapLoaded) {
                webViewRef?.evaluateJavascript("setTileStyle('$mapStyle', '$apiKey');", null)
            }
        }
    }
}

/**
 * JavaScript interface bridge for receiving tap events on map polylines and map rotation changes.
 */
class MapJsBridge(
    private val callbackProvider: () -> ((Int) -> Unit)?,
    private val bearingCallbackProvider: () -> ((Float) -> Unit)?
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun onRouteSelected(index: Int) {
        mainHandler.post {
            callbackProvider()?.invoke(index)
        }
    }

    @JavascriptInterface
    fun onMapBearingChanged(bearing: Float) {
        mainHandler.post {
            bearingCallbackProvider()?.invoke(bearing)
        }
    }
}

/**
 * Builds JSON array with all candidate routes:
 * [{"index":0,"coords":[[lng,lat],...]},{"index":1,"coords":[[lng,lat],...]}]
 */
private fun buildRoutesJson(routes: List<OlaRoute>): String {
    val sb = StringBuilder("[")
    for (i in routes.indices) {
        val r = routes[i]
        sb.append("{\"index\":").append(i).append(",\"coords\":[")
        val poly = r.polyline
        for (j in poly.indices) {
            val pt = poly[j]
            sb.append("[").append(pt.longitude).append(",").append(pt.latitude).append("]")
            if (j < poly.size - 1) sb.append(",")
        }
        sb.append("]}")
        if (i < routes.size - 1) sb.append(",")
    }
    sb.append("]")
    return sb.toString()
}

/**
 * Builds GeoJSON coordinates array: [[lng, lat], [lng, lat], ...]
 */
private fun buildLngLatJson(points: List<OlaLatLng>): String {
    val sb = StringBuilder("[")
    for (i in points.indices) {
        val pt = points[i]
        sb.append("[${pt.longitude},${pt.latitude}]")
        if (i < points.size - 1) sb.append(",")
    }
    sb.append("]")
    return sb.toString()
}
