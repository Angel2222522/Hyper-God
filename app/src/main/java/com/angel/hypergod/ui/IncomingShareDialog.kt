package com.angel.hypergod.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun IncomingShareDialog(
    uris: List<Uri>,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Εισαγωγή από άλλη εφαρμογή;") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Η άλλη εφαρμογή ζητά να προστεθούν ${uris.size} ${if (uris.size == 1) "αρχείο" else "αρχεία"}. " +
                        "Με την αποδοχή θα ελεγχθούν, θα αποθηκευτούν κρυπτογραφημένα και θα ξεκινήσει τοπικό OCR."
                )
                uris.take(5).forEachIndexed { index, uri ->
                    Text("• ${safeLabel(uri, index)}")
                }
                if (uris.size > 5) Text("• και ${uris.size - 5} ακόμη")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("Έλεγχος και εισαγωγή") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Απόρριψη") }
        }
    )
}

private fun safeLabel(uri: Uri, index: Int): String {
    val raw = uri.lastPathSegment?.substringAfterLast('/')
        ?.replace(Regex("[\\p{Cntrl}\\r\\n\\t]"), " ")
        ?.trim()
        ?.take(80)
        .orEmpty()
    return raw.ifBlank { "Αρχείο ${index + 1}" }
}
