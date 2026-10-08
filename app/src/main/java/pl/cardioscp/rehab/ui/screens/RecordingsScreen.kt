package pl.cardioscp.rehab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.R
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.ui.theme.DeepTeal
import pl.cardioscp.rehab.ui.theme.Sand
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingsScreen(
    recordings: List<ScpRecording>,
    onBack: () -> Unit,
    onOpen: (ScpRecording) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
            }
            Text(
                text = stringResource(R.string.recordings_title),
                style = MaterialTheme.typography.titleLarge,
                color = DeepTeal,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.recordings_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recordings, key = { it.file.absolutePath }) { rec ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(rec) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    ) {
                        Text(
                            text = rec.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepTeal,
                        )
                        Text(
                            text = stringResource(
                                R.string.recording_meta,
                                rec.sizeBytes,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                                    .format(Date(rec.modifiedAtMs)),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}
