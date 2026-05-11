package com.example.gemma

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        CenterAlignedTopAppBar(
            title = { Text("Stats 📊") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor    = Color(0xFF282828),
                titleContentColor = Color(0xFFF2EEE4)
            ),
            expandedHeight = 40.dp,
            windowInsets = WindowInsets(0)
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

@Composable
private fun MapCard(geoRecords: List<DarwinRecord>, totalRecords: Int) {
    val missingGps = totalRecords - geoRecords.size

    StatsCard(title = "🗺️  Observation map") {
        if (missingGps > 0) {
            Surface(
                color    = MaterialTheme.colorScheme.tertiaryContainer,
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = "⚠️  $missingGps observation${if (missingGps > 1) "s" else ""} " +
                            "without GPS ${if (missingGps > 1) "are" else "is"} not shown.",
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
                    "No GPS data available yet.\nGPS coordinates are captured automatically\nwhen you record an observation.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
            return@StatsCard
        }

        ObservationMap(geoRecords)
        Text(
            text     = "Pins plotted from GPS coordinates · works offline",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

// ─── Canvas observation map ────────────────────────────────────────────────────

@Composable
private fun ObservationMap(records: List<DarwinRecord>) {
    val primary   = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val bgColor   = Color(0xFF1E2020)          // dark map-like background
    val gridColor = Color(0xFF2E3535)          // subtle grid
    val axisColor = Color(0xFF8A9A9A)          // coordinate label color
    val labelBg   = Color(0xCC1E2020)          // semi-transparent label background

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
        .clip(RoundedCornerShape(10.dp))
    ) {
        val w = size.width
        val h = size.height

        // Bounding box — expand span so a single point isn't a 0×0 box
        val lats    = records.map { it.decimalLatitude!! }
        val lons    = records.map { it.decimalLongitude!! }
        val latSpan = (lats.max() - lats.min()).coerceAtLeast(0.01)
        val lonSpan = (lons.max() - lons.min()).coerceAtLeast(0.01)
        val pad     = 0.3
        val minLat  = lats.min() - latSpan * pad
        val maxLat  = lats.max() + latSpan * pad
        val minLon  = lons.min() - lonSpan * pad
        val maxLon  = lons.max() + lonSpan * pad

        fun xOf(lon: Double) = ((lon - minLon) / (maxLon - minLon) * w).toFloat()
        fun yOf(lat: Double) = ((1.0 - (lat - minLat) / (maxLat - minLat)) * h).toFloat()

        // Background
        drawRect(bgColor)

        // Grid lines (4×4)
        for (i in 1..3) {
            drawLine(gridColor, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), strokeWidth = 1f)
        }

        val canvas = drawContext.canvas.nativeCanvas

        // Coordinate axis labels
        val axisPaint = android.graphics.Paint().apply {
            color       = axisColor.toArgb()
            textSize    = 22f
            isAntiAlias = true
            typeface    = android.graphics.Typeface.MONOSPACE
        }
        canvas.drawText("%.4f°N".format(maxLat), 10f, 26f, axisPaint)
        canvas.drawText("%.4f°N".format(minLat), 10f, h - 8f, axisPaint)
        canvas.drawText("%.4f°E".format(minLon), 10f, h / 2f, axisPaint)
        canvas.drawText("%.4f°E".format(maxLon), w - 180f, h / 2f, axisPaint)

        // Species label paint
        val labelPaint = android.graphics.Paint().apply {
            color       = primary.toArgb()
            textSize    = 26f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val labelBgPaint = android.graphics.Paint().apply {
            color = labelBg.toArgb()
        }

        // Draw pins + species labels
        records.forEach { r ->
            val x = xOf(r.decimalLongitude!!)
            val y = yOf(r.decimalLatitude!!)

            // Outer glow ring
            drawCircle(primary.copy(alpha = 0.25f), radius = 20f, center = Offset(x, y))
            // White border
            drawCircle(Color.White, radius = 13f, center = Offset(x, y))
            // Filled pin
            drawCircle(primary, radius = 10f, center = Offset(x, y))

            // Species label
            val name = (r.vernacularName.ifBlank { r.scientificName.ifBlank { "?" } })
                .take(16)
            val labelX = (x + 18f).coerceAtMost(w - 200f)
            val labelY = (y + 8f).coerceIn(30f, h - 8f)
            // Label background for readability
            val textWidth = labelPaint.measureText(name)
            canvas.drawRoundRect(
                labelX - 4f, labelY - 22f, labelX + textWidth + 8f, labelY + 6f,
                6f, 6f, labelBgPaint
            )
            canvas.drawText(name, labelX, labelY, labelPaint)
        }
    }
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
