package com.example.gemma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Records Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(vm: RecordsViewModel = viewModel()) {
    val records     by vm.records.collectAsState()
    val total       by vm.totalCount.collectAsState()
    val pending     by vm.pendingCount.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

    // ── Confirmation dialog ────────────────────────────────────────────────────
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon    = { Icon(Icons.Default.Delete, contentDescription = null) },
            title   = { Text("Delete all records?") },
            text    = { Text("This will permanently remove all ${total} observations from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAll(); showConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("My Records 📋") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor    = Color(0xFF282828),
                titleContentColor = Color(0xFFF2EEE4)
            ),
            expandedHeight = 40.dp,
            windowInsets = WindowInsets(0),
            actions = {
                if (total > 0) {
                    IconButton(onClick = { showConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete all records",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        // ── Summary banner ────────────────────────────────────────────────────
        SummaryBanner(total = total, pending = pending)

        if (records.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f).fillMaxWidth(),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.occurrenceID }) { record ->
                    RecordCard(record = record, onDelete = { vm.deleteRecord(record.occurrenceID) })
                }
            }
        }
    }
}

// ─── Summary banner ────────────────────────────────────────────────────────────

@Composable
private fun SummaryBanner(total: Int, pending: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryChip(
            label = "$total",
            description = if (total == 1) "observation" else "observations",
            color = MaterialTheme.colorScheme.primary
        )
        VerticalDivider(modifier = Modifier.height(32.dp))
        SummaryChip(
            label = "$pending",
            description = if (pending == 1) "pending sync" else "pending sync",
            color = if (pending > 0)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun SummaryChip(label: String, description: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text  = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Record card ───────────────────────────────────────────────────────────────

@Composable
private fun RecordCard(record: DarwinRecord, onDelete: () -> Unit) {
    val isSynced  = record.status == "SYNCED"
    val hasPhoto  = record.photoPath.isNotBlank()
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title  = { Text("Delete observation?") },
            text   = {
                val name = record.vernacularName.ifBlank { record.scientificName.ifBlank { "this record" } }
                Text("\"$name\" will be permanently deleted.")
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {

            // ── Photo header ──────────────────────────────────────────────────
            // AsyncImage handles missing/corrupt files gracefully (shows nothing).
            // File.exists() is intentionally omitted — doing I/O in a composable
            // blocks the main thread and causes jank.
            if (hasPhoto) {
                AsyncImage(
                    model              = File(record.photoPath),
                    contentDescription = "Specimen photo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )
            }

            // ── Text content ──────────────────────────────────────────────────
            Column(modifier = Modifier.padding(14.dp)) {

                // ── Top row: name + sync badge ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val displayName = record.vernacularName.ifBlank {
                            record.scientificName.ifBlank { "Unknown species" }
                        }
                        Text(
                            text       = displayName,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (record.scientificName.isNotBlank() &&
                            record.scientificName != record.vernacularName) {
                            Text(
                                text      = record.scientificName,
                                style     = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    SyncBadge(synced = isSynced)
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick  = { showConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Delete,
                            contentDescription = "Delete record",
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                // ── Detail rows ───────────────────────────────────────────────
                DetailRow(
                    icon  = Icons.Default.Nature,
                    label = buildString {
                        append("×${record.individualCount}")
                        if (record.habitat.isNotBlank()) append(" · ${record.habitat}")
                    }
                )

                val hasLocality = record.locality.isNotBlank()
                val hasGps = record.decimalLatitude != null && record.decimalLongitude != null
                if (hasLocality || hasGps) {
                    Spacer(Modifier.height(4.dp))
                    DetailRow(
                        icon  = Icons.Default.LocationOn,
                        label = buildString {
                            if (hasLocality) append(record.locality)
                            if (hasGps) {
                                if (hasLocality) append("  ")
                                append("(%.4f, %.4f)".format(record.decimalLatitude, record.decimalLongitude))
                            }
                        }
                    )
                }

                if (record.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text  = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                // ── Date ──────────────────────────────────────────────────────
                Text(
                    text  = formatDate(record.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SyncBadge(synced: Boolean) {
    val bgColor  = if (synced) MaterialTheme.colorScheme.tertiaryContainer
                   else        MaterialTheme.colorScheme.errorContainer
    val fgColor  = if (synced) MaterialTheme.colorScheme.onTertiaryContainer
                   else        MaterialTheme.colorScheme.onErrorContainer
    val icon     = if (synced) Icons.Default.CloudDone else Icons.Default.CloudOff
    val text     = if (synced) "Synced" else "Pending"

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(12.dp),
                tint               = fgColor
            )
            Text(
                text  = text,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(14.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "🌿",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "No observations yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Head to the Chat tab and tell Taina\nwhat species you spotted!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun formatDate(millis: Long): String {
    return try {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) { "" }
}
