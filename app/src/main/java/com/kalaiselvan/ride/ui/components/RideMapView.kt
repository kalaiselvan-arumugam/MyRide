package com.kalaiselvan.ride.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kalaiselvan.ride.logs.RideBreadcrumb
import com.kalaiselvan.ride.theme.BgCard
import com.kalaiselvan.ride.theme.TextMuted
import org.json.JSONArray
import org.json.JSONObject

enum class RideMapColorMode {
    SPEED,
    LEAN
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RideMapView(
    breadcrumbs: List<RideBreadcrumb>,
    colorMode: RideMapColorMode = RideMapColorMode.SPEED,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Read Leaflet directly from APK assets for 100% inlined offline capability
    val leafletCss = remember {
        try {
            context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("RideMapView", "Failed to load leaflet.css", e)
            ""
        }
    }

    val leafletJs = remember {
        try {
            context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("RideMapView", "Failed to load leaflet.js", e)
            ""
        }
    }

    val centerLat = remember(breadcrumbs) {
        if (breadcrumbs.isNotEmpty()) breadcrumbs[breadcrumbs.size / 2].lat else 12.9716
    }
    val centerLon = remember(breadcrumbs) {
        if (breadcrumbs.isNotEmpty()) breadcrumbs[breadcrumbs.size / 2].lon else 77.5946
    }

    val breadcrumbsJson = remember(breadcrumbs) {
        val arr = JSONArray()
        breadcrumbs.forEach { bc ->
            arr.put(JSONObject().apply {
                put("lat", bc.lat)
                put("lon", bc.lon)
                put("speed", bc.speedKmh.toDouble())
                put("lean", bc.leanAngle.toDouble())
                put("ts", bc.timestamp)
            })
        }
        arr.toString()
    }

    val mapHtml = remember(leafletCss, leafletJs) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <style>
            $leafletCss
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body {
              width: 100%; height: 100%; margin: 0; padding: 0;
              background-color: #121417; overflow: hidden;
              border-radius: 14px;
            }
            #map {
              position: absolute; top: 0; bottom: 0; left: 0; right: 0;
              width: 100%; height: 100%;
              background-color: #121417;
            }
            /* Clean Crisp High-Contrast Cartographic Styling */
            .leaflet-tile {
              filter: brightness(92%) contrast(105%);
              -webkit-filter: brightness(92%) contrast(105%);
            }
            .pin-marker {
              width: 14px; height: 14px; border-radius: 50%;
              border: 2px solid #FFFFFF;
              box-shadow: 0 0 8px rgba(0,0,0,0.8);
            }
            .pin-start { background-color: #00E676; }
            .pin-finish { background-color: #FF1744; }
          </style>
          <script>
            $leafletJs
          </script>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var map = null;
            var tileLayer = null;
            var routeLayers = [];
            var startMarker = null;
            var endMarker = null;
            var pendingData = null;
            var pendingMode = 'SPEED';
            window.isMapReady = false;

            function initMap() {
              if (typeof L === 'undefined') {
                setTimeout(initMap, 40);
                return;
              }
              try {
                map = L.map('map', {
                  zoomControl: false,
                  attributionControl: false,
                  center: [$centerLat, $centerLon],
                  zoom: 14
                });

                // Pure OpenStreetMap Tile Server
                tileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                  maxZoom: 19,
                  minZoom: 3,
                  attribution: '© OpenStreetMap contributors'
                }).addTo(map);

                window.isMapReady = true;

                setTimeout(function() {
                  if (map) {
                    map.invalidateSize(false);
                    if (pendingData) {
                      drawRoute(pendingData, pendingMode);
                      pendingData = null;
                    }
                  }
                }, 150);
              } catch(e) {
                console.error("Map init error: " + e);
              }
            }

            function getColor(speed, lean, mode) {
              if (mode === 'LEAN') {
                var l = Math.abs(lean || 0);
                if (l < 12) return '#00E5FF'; // Cyan: Mild / Upright
                if (l < 22) return '#76FF03'; // Bright Green: Medium lean
                if (l < 32) return '#FFD600'; // Amber: Aggressive lean
                return '#FF1744';              // Vivid Red: Extreme knee-down lean
              } else {
                var s = speed || 0;
                if (s < 40) return '#00E676';  // Green
                if (s < 75) return '#FFEA00';  // Yellow
                if (s < 105) return '#FF9100'; // Orange
                return '#FF1744';              // Red
              }
            }

            function drawRoute(dataJson, mode) {
              if (!map || !window.isMapReady) {
                pendingData = dataJson;
                pendingMode = mode;
                return;
              }
              try {
                routeLayers.forEach(function(l) { map.removeLayer(l); });
                routeLayers = [];
                if (startMarker) { map.removeLayer(startMarker); startMarker = null; }
                if (endMarker) { map.removeLayer(endMarker); endMarker = null; }

                var rawPts = (typeof dataJson === 'string') ? JSON.parse(dataJson) : dataJson;
                if (!rawPts || rawPts.length === 0) return;

                // 1. Sanitize & filter out aberrant GPS multipath teleports / cuts
                var pts = [];
                for (var k = 0; k < rawPts.length; k++) {
                  var p = rawPts[k];
                  if (!p.lat || !p.lon) continue;
                  if (pts.length > 0) {
                    var prev = pts[pts.length - 1];
                    var distM = map.distance([prev.lat, prev.lon], [p.lat, p.lon]);
                    var dtS = (p.ts && prev.ts) ? (p.ts - prev.ts) / 1000.0 : 2.0;
                    if (dtS > 0 && dtS < 10) {
                      var impliedSpeed = (distM / dtS) * 3.6;
                      // Reject points with physically impossible jumps (> 160 km/h jump over short interval)
                      if (impliedSpeed > 160 && distM > 40) {
                        continue;
                      }
                    }
                  }
                  pts.push(p);
                }
                if (pts.length === 0) pts = rawPts;

                // 2. Compute speeds between points if recorded speed was 0 or missing
                for (var j = 0; j < pts.length - 1; j++) {
                  if (!pts[j].speed || pts[j].speed <= 0.1) {
                    var curr = pts[j];
                    var next = pts[j+1];
                    var distM = map.distance([curr.lat, curr.lon], [next.lat, next.lon]);
                    var dtS = (next.ts && curr.ts && (next.ts - curr.ts) > 0) ? (next.ts - curr.ts) / 1000.0 : 2.0;
                    if (dtS > 0 && dtS <= 10.0) {
                      curr.computedSpeed = Math.min((distM / dtS) * 3.6, 160.0);
                    } else {
                      curr.computedSpeed = 0;
                    }
                  } else {
                    pts[j].computedSpeed = pts[j].speed;
                  }
                }

                // 3. Render colored polylines
                var bounds = [];
                for (var i = 0; i < pts.length - 1; i++) {
                  var p1 = pts[i];
                  var p2 = pts[i+1];
                  var effSpeed = (p1.computedSpeed !== undefined) ? p1.computedSpeed : (p1.speed || 0);
                  var effLean = p1.lean || 0;
                  var color = getColor(effSpeed, effLean, mode);
                  var seg = L.polyline([[p1.lat, p1.lon], [p2.lat, p2.lon]], {
                    color: color,
                    weight: 5,
                    opacity: 0.95,
                    lineCap: 'round',
                    lineJoin: 'round'
                  }).addTo(map);
                  routeLayers.push(seg);
                  bounds.push([p1.lat, p1.lon]);
                }
                bounds.push([pts[pts.length - 1].lat, pts[pts.length - 1].lon]);

                if (pts.length > 0) {
                  var pStart = pts[0];
                  var pEnd = pts[pts.length - 1];

                  var startIcon = L.divIcon({
                    className: 'pin-marker pin-start',
                    iconSize: [14, 14],
                    iconAnchor: [7, 7]
                  });
                  startMarker = L.marker([pStart.lat, pStart.lon], { icon: startIcon }).addTo(map);

                  var finishIcon = L.divIcon({
                    className: 'pin-marker pin-finish',
                    iconSize: [14, 14],
                    iconAnchor: [7, 7]
                  });
                  endMarker = L.marker([pEnd.lat, pEnd.lon], { icon: finishIcon }).addTo(map);

                  map.fitBounds(L.latLngBounds(bounds), { padding: [25, 25] });
                }
                map.invalidateSize(false);
              } catch(err) {
                console.error("drawRoute error: " + err);
              }
            }

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
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard),
        contentAlignment = Alignment.Center
    ) {
        if (breadcrumbs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "NO GPS ROUTE DATA",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "GPS was inactive or the motorcycle remained stationary during this session.",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        } else {
            AndroidView(
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
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = "MyRideMotorcycleApp/1.0 (Android)"
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                val msg = cm?.message() ?: ""
                                Log.d("RideMapView", "CONSOLE [${cm?.messageLevel()}]: $msg")
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isMapLoaded = true
                                view?.postDelayed({
                                    view.evaluateJavascript("drawRoute($breadcrumbsJson, '${colorMode.name}');", null)
                                }, 100)
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                Log.e("RideMapView", "LOAD ERROR: ${error?.errorCode} - ${error?.description}")
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                Log.e("RideMapView", "SSL ERROR rejected: $error")
                                handler?.cancel()
                            }
                        }
                        loadDataWithBaseURL("https://www.openstreetmap.org/", mapHtml, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    if (isMapLoaded) {
                        webView.postDelayed({
                            webView.evaluateJavascript("drawRoute($breadcrumbsJson, '${colorMode.name}');", null)
                        }, 50)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
