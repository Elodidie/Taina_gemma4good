package com.example.gemma

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Stats Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val total      by vm.totalRecords.collectAsState()
    val perDay     by vm.recordsPerDay.collectAsState()
    val topSpecies by vm.topSpecies.collectAsState()
    val geoRecords by vm.geoRecords.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Stats 📊") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        if (total == 0) {
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No data yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Start recording observations\nto see your stats here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f).fillMaxWidth(),
                contentPadding      = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (perDay.isNotEmpty()) {
                    item { TimelineCard(perDay) }
                }
                if (topSpecies.isNotEmpty()) {
                    item { TopSpeciesCard(topSpecies) }
                }
                // Always show the map card if there is at least one record —
                // it will display the no-GPS notice even when geoRecords is empty.
                item { MapCard(geoRecords = geoRecords, totalRecords = total) }
            }
        }
    }
}

// ─── Timeline card ─────────────────────────────────────────────────────────────

@Composable
private fun TimelineCard(data: List<DayCount>) {
    val primary   = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    StatsCard(title = "📅  Records timeline") {
        if (data.isEmpty()) return@StatsCard

        val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
        val barCount = data.size

        // Bar chart drawn with Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 8.dp, bottom = 24.dp) // bottom room for labels
        ) {
            val chartWidth  = size.width
            val chartHeight = size.height
            val barWidth    = (chartWidth / barCount) * 0.6f
            val gap         = (chartWidth / barCount) * 0.4f
            val labelPaint  = android.graphics.Paint().apply {
                color     = onSurface.toArgb()
                textSize  = 28f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            data.forEachIndexed { i, day ->
                val barHeight = (day.count.toFloat() / maxCount) * chartHeight
                val x         = i * (barWidth + gap) + gap / 2f
                val y         = chartHeight - barHeight

                // Bar
                drawRoundRect(
                    color       = primary,
                    topLeft     = Offset(x, y),
                    size        = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                // Date label (last 5 chars: "MM-dd")
                val label = day.date.takeLast(5)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x + barWidth / 2f,
                    chartHeight + 36f,
                    labelPaint
                )

                // Count above bar (only if bar is tall enough)
                if (barHeight > 30f) {
                    val countPaint = android.graphics.Paint().apply {
                        color     = android.graphics.Color.WHITE
                        textSize  = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        isAntiAlias    = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "${day.count}",
                        x + barWidth / 2f,
                        y + 32f,
                        countPaint
                    )
                }
            }

            // Baseline
            drawLine(
                color       = onSurface.copy(alpha = 0.3f),
                start       = Offset(0f, chartHeight),
                end         = Offset(chartWidth, chartHeight),
                strokeWidth = 1.5f
            )
        }
    }
}

// ─── Top species card ──────────────────────────────────────────────────────────

@Composable
private fun TopSpeciesCard(data: List<SpeciesCount>) {
    StatsCard(title = "🦎  Top species") {
        val maxCount = data.maxOf { it.count }.coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            data.forEach { species ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Species name
                    Text(
                        text     = species.name,
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    // Proportional bar
                    val fraction = species.count.toFloat() / maxCount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(50)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "${species.count}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.width(24.dp)
                    )
                }
            }
        }
    }
}

// ─── Map card ──────────────────────────────────────────────────────────────────
// allRecords = full list passed by the caller (may include records without GPS)
// geoRecords = filtered subset that actually has coordinates

@Composable
private fun MapCard(geoRecords: List<DarwinRecord>, totalRecords: Int) {
    val missingGps = totalRecords - geoRecords.size
    val isOnline   = isNetworkAvailable()

    StatsCard(title = "🗺️  Observation map") {

        // ── "Some records have no GPS" notice ─────────────────────────────────
        if (missingGps > 0) {
            Surface(
                color  = MaterialTheme.colorScheme.tertiaryContainer,
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = "⚠️  $missingGps observation${if (missingGps > 1) "s" else ""} " +
                            "without GPS coordinates ${if (missingGps > 1) "are" else "is"} not shown.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (geoRecords.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No GPS data available for any observation.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
            return@StatsCard
        }

        // ── Online: Leaflet map with real tiles ───────────────────────────────
        if (isOnline) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory  = { ctx ->
                    WebView(ctx).apply {
                        webViewClient              = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadDataWithBaseURL(null, buildLeafletHtml(geoRecords), "text/html", "UTF-8", null)
                    }
                },
                update   = { webView ->
                    webView.loadDataWithBaseURL(null, buildLeafletHtml(geoRecords), "text/html", "UTF-8", null)
                }
            )
            Text(
                text     = "Tap a pin to see the species name · © OpenStreetMap",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            // ── Offline: Canvas dot map with coordinate grid ──────────────────
            OfflineDotMap(geoRecords)
            Text(
                text     = "🔴  Offline — map tiles unavailable. Pins are plotted at their GPS coordinates.",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ─── Offline Canvas dot map ────────────────────────────────────────────────────

@Composable
private fun OfflineDotMap(records: List<DarwinRecord>) {
    val primary      = MaterialTheme.colorScheme.primary
    val gridColor    = MaterialTheme.colorScheme.outlineVariant
    val bgColor      = MaterialTheme.colorScheme.surfaceVariant
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        val w = size.width
        val h = size.height

        // Bounding box with padding
        val lats  = records.map { it.decimalLatitude!! }
        val lons  = records.map { it.decimalLongitude!! }
        val latSpan = (lats.max() - lats.min()).coerceAtLeast(0.005)
        val lonSpan = (lons.max() - lons.min()).coerceAtLeast(0.005)
        val minLat = lats.min() - latSpan * 0.25
        val maxLat = lats.max() + latSpan * 0.25
        val minLon = lons.min() - lonSpan * 0.25
        val maxLon = lons.max() + lonSpan * 0.25

        fun xOf(lon: Double) = ((lon - minLon) / (maxLon - minLon) * w).toFloat()
        // Latitude increases northward but y increases downward, so invert
        fun yOf(lat: Double) = ((1.0 - (lat - minLat) / (maxLat - minLat)) * h).toFloat()

        // Background
        drawRect(bgColor)

        // Grid (5×5)
        val gridPaint = android.graphics.Paint().apply {
            color       = gridColor.toArgb()
            strokeWidth = 1f
            isAntiAlias = true
        }
        for (i in 1..4) {
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(w * i / 5f, 0f),
                end   = Offset(w * i / 5f, h),
                strokeWidth = 1f
            )
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(0f, h * i / 5f),
                end   = Offset(w, h * i / 5f),
                strokeWidth = 1f
            )
        }

        // Corner coordinate labels
        val coordPaint = android.graphics.Paint().apply {
            color       = labelColor.toArgb()
            textSize    = 24f
            isAntiAlias = true
        }
        val nLabel = "%.3f°N".format(maxLat)
        val sLabel = "%.3f°N".format(minLat)
        val wLabel = "%.3f°E".format(minLon)
        drawContext.canvas.nativeCanvas.apply {
            drawText(nLabel, 8f, 28f, coordPaint)
            drawText(sLabel, 8f, h - 8f, coordPaint)
            drawText(wLabel, 8f, h / 2f, coordPaint)
        }

        // Pins
        records.forEach { r ->
            val x = xOf(r.decimalLongitude!!)
            val y = yOf(r.decimalLatitude!!)
            drawCircle(Color.White,   radius = 12f, center = Offset(x, y))
            drawCircle(primary,       radius = 9f,  center = Offset(x, y))
        }
    }
}

// ─── Network helper ────────────────────────────────────────────────────────────

@Composable
private fun isNetworkAvailable(): Boolean {
    val ctx = LocalContext.current
    val cm  = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val cap = cm.getNetworkCapabilities(net) ?: return false
    return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// ─── Leaflet HTML ──────────────────────────────────────────────────────────────

private fun buildLeafletHtml(records: List<DarwinRecord>): String {
    val markers = records.mapIndexed { i, r ->
        val name = (r.vernacularName.ifBlank { r.scientificName.ifBlank { "Unknown" } })
            .replace("'", "\\'")
        """
        var m$i = L.circleMarker([${r.decimalLatitude}, ${r.decimalLongitude}], {
            radius: 9, color: '#1b5e20', fillColor: '#4caf50',
            fillOpacity: 0.85, weight: 2
        }).bindPopup('<b>$name</b><br/>${r.eventDate}<br/>${r.locality.ifBlank { "" }}').addTo(map);
        markers.push(m$i);
        """.trimIndent()
    }.joinToString("\n")

    val fitBounds = if (records.size == 1) {
        "map.setView([${records[0].decimalLatitude}, ${records[0].decimalLongitude}], 14);"
    } else {
        "map.fitBounds(L.featureGroup(markers).getBounds().pad(0.3));"
    }

    return """
<!DOCTYPE html><html>
<head>
  <meta charset='utf-8'>
  <meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>
  <link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
  <script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>
  <style>html,body,#map{margin:0;padding:0;width:100%;height:100%;}</style>
</head>
<body>
  <div id='map'></div>
  <script>
    var map = L.map('map',{zoomControl:true});
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
      attribution:'© <a href="https://openstreetmap.org">OSM</a>'
    }).addTo(map);
    var markers=[];
    $markers
    $fitBounds
  </script>
</body></html>
    """.trimIndent()
}

// ─── Shared card wrapper ───────────────────────────────────────────────────────

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
