package pl.cardioscp.rehab.ui.ble

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.cardioscp.rehab.ble.BleMeasureController
import pl.cardioscp.rehab.ble.BlePermissions
import pl.cardioscp.rehab.ble.VitalMeasureType
import pl.cardioscp.rehab.ble.WhoPresentation
import pl.cardioscp.rehab.ui.theme.DeepTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Popup pomiaru BLE — układ i flow jak MeasurePopup w mobile-DSD. */
@Composable
fun MeasurePopup(controller: BleMeasureController) {
    if (!controller.measurePopupOpen) return
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> controller.onBlePermissionResult(granted.values.all { it }) }
    LaunchedEffect(controller.pendingBlePermission) {
        if (!controller.pendingBlePermission) return@LaunchedEffect
        val missing = BlePermissions.missing(context)
        if (missing.isEmpty()) controller.onBlePermissionResult(true)
        else permissionLauncher.launch(missing)
    }
    // measureEpoch — wymusza skan przy przejściu BP→waga w sesji (open może zostać true).
    LaunchedEffect(controller.measurePopupOpen, controller.measureEpoch) {
        if (controller.measurePopupOpen) controller.beginBleMeasure()
    }
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val reading = controller.pendingReading
    val who = remember(reading, controller.measureType) {
        reading?.let { WhoPresentation.assess(controller.measureType, it) }
    }
    val accent = who?.let { Color(it.argb) } ?: DeepTeal
    val timeFmt = remember { SimpleDateFormat("d.MM HH:mm", Locale("pl")) }
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val pad = if (tablet) 20.dp else 12.dp
    val gap = if (tablet) 12.dp else 8.dp
    val btnH = if (tablet) 52.dp else 40.dp
    val actionBtnH = if (tablet) 56.dp else 40.dp
    val iconBox = if (tablet) 48.dp else 36.dp
    val icon = when (controller.measureType) {
        VitalMeasureType.BLOOD_PRESSURE -> Icons.Outlined.FavoriteBorder
        VitalMeasureType.WEIGHT -> Icons.Outlined.MonitorWeight
        VitalMeasureType.SPO2 -> Icons.Outlined.Bloodtype
        VitalMeasureType.GLUCOSE -> Icons.Outlined.WaterDrop
    }

    Dialog(
        onDismissRequest = { controller.cancelMeasure() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (tablet) 0.72f else 0.96f)
                .widthIn(max = if (tablet) 440.dp else 560.dp)
                .heightIn(max = if (tablet) 640.dp else 520.dp)
                .testTag("dsd.measure.popup"),
            shape = RoundedCornerShape(if (tablet) 20.dp else 14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier
                    .padding(pad)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(iconBox)
                            .background(accent.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(if (tablet) 26.dp else 20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            controller.measureType.shortLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepTeal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val whoLabel = who?.label.orEmpty()
                        if (whoLabel.isNotBlank()) {
                            Text(
                                whoLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("dsd.measure.who"),
                            )
                        }
                    }
                    TextButton(onClick = { controller.cancelMeasure() }) {
                        Text("Zamknij", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Text(
                    controller.bleStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (reading == null) {
                    val remain = controller.bleWaitRemainingSec
                    if (remain != null) {
                        val mm = remain / 60
                        val ss = remain % 60
                        Text(
                            "${controller.bleWaitPhaseLabel}: pozostało %d:%02d".format(mm, ss),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (remain <= 15) {
                                MaterialTheme.colorScheme.error
                            } else {
                                DeepTeal
                            },
                            modifier = Modifier.testTag("dsd.ble-countdown"),
                        )
                    }
                    val progress = controller.bleProgress
                    val totalSec = controller.bleWaitTotalSec.coerceAtLeast(1)
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dsd.ble-progress"),
                        )
                    } else if (remain != null) {
                        LinearProgressIndicator(
                            progress = {
                                (1f - remain.toFloat() / totalSec).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dsd.ble-progress"),
                        )
                    }
                    if (controller.bleOfferOtherDevices && !controller.bleShowOtherDevicePicker) {
                        OutlinedButton(
                            onClick = { controller.useOtherBleDevice() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(btnH)
                                .testTag("dsd.ble.use-other"),
                        ) {
                            Text("Użyj innego urządzenia")
                        }
                    }
                    OutlinedButton(
                        onClick = { controller.simulateMeasure() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(btnH)
                            .testTag("dsd.measure.simulate"),
                    ) {
                        Text(
                            when (controller.measureType) {
                                VitalMeasureType.BLOOD_PRESSURE -> "Symuluj wynik ciśnienia"
                                VitalMeasureType.WEIGHT -> "Symuluj wynik wagi"
                                VitalMeasureType.SPO2 -> "Symuluj wynik saturacji"
                                VitalMeasureType.GLUCOSE -> "Symuluj wynik glikemii"
                            },
                        )
                    }
                    val selectable = controller.selectableBleHits()
                    if (selectable.isNotEmpty()) {
                        Text(
                            if (selectable.size > 1 || controller.bleShowOtherDevicePicker) {
                                "Wybierz urządzenie"
                            } else {
                                "Urządzenie w pobliżu"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        selectable.take(6).forEach { hit ->
                            OutlinedButton(
                                onClick = { controller.connectBle(hit) },
                                enabled = !controller.bleBusy || selectable.size > 1 ||
                                    controller.bleShowOtherDevicePicker,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dsd.ble-hit"),
                            ) {
                                Text("${hit.name} · ${hit.rssi} dBm")
                            }
                        }
                    }
                } else {
                    Text(
                        reading.summary,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("dsd.measure.result"),
                    )
                    reading.measuredAtMs?.let {
                        Text(
                            timeFmt.format(Date(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text("Komentarz", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (who?.comments.orEmpty()).forEach { chip ->
                            FilterChip(
                                selected = controller.measureComment == chip,
                                onClick = { controller.selectMeasureComment(chip) },
                                label = {
                                    Text(
                                        chip,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = controller.measureComment,
                        onValueChange = { controller.measureComment = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Notatka") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { controller.retryMeasurePopup() },
                            modifier = Modifier
                                .weight(1f)
                                .height(actionBtnH)
                                .testTag("dsd.measure.retry"),
                        ) { Text("Ponów", maxLines = 1) }
                        Button(
                            onClick = { controller.saveMeasurePopup() },
                            modifier = Modifier
                                .weight(1f)
                                .height(actionBtnH)
                                .testTag("dsd.measure.save"),
                        ) { Text("Zapisz", maxLines = 1) }
                    }
                }
            }
        }
    }
}
