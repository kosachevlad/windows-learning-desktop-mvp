package ua.school.windowsdesktop

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DeviceSystemInfo(
    val processor: String,
    val processorDetails: String,
    val totalRam: String,
    val totalStorage: String,
    val storageDetails: String,
    val system: String,
    val systemDetails: String,
    val deviceName: String,
)

@Composable
internal fun SystemInfoScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val info = remember(context) { readDeviceSystemInfo(context) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color(0xFFEEF4F9))) {
        WindowTitle("Цей ПК", onClose)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = when { maxWidth >= 900.dp -> 4; maxWidth >= 480.dp -> 2; else -> 1 }
            val compact = maxWidth < 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Система  ›  Про систему", style = MaterialTheme.typography.headlineMedium)
                val cards = listOf(
                    Triple("Сховище", info.totalStorage, info.storageDetails),
                    Triple("ОЗП", info.totalRam, "Загальний обсяг оперативної пам’яті"),
                    Triple("Процесор", info.processor, info.processorDetails),
                    Triple("Система", info.system, info.systemDetails),
                )
                cards.chunked(columns).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.forEach { (title, value, detail) ->
                            OutlinedCard(Modifier.weight(1f).heightIn(min = 178.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFAFCFE))) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                    Text(value, fontWeight = FontWeight.SemiBold)
                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFAFCFE))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(info.deviceName, style = MaterialTheme.typography.titleMedium)
                        Text("Відомості отримано з цього пристрою", color = Color.DarkGray)
                    }
                }
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFAFCFE))) {
                    Text("Специфікації пристрою", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(
                            "Пристрій" to info.deviceName,
                            "Процесор" to "${info.processor} (${info.processorDetails})",
                            "ОЗП" to info.totalRam,
                            "Сховище" to info.storageDetails,
                            "Операційна система" to "${info.system} (${info.systemDetails})",
                        ).forEach { (label, value) ->
                            if (compact) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label)
                                Text(value, color = Color.DarkGray)
                            } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text(label, Modifier.width(210.dp))
                                Text(value, Modifier.weight(1f), color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readDeviceSystemInfo(context: Context): DeviceSystemInfo {
    val memoryInfo = ActivityManager.MemoryInfo()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.getMemoryInfo(memoryInfo)
    val storage = StatFs(Environment.getDataDirectory().path)
    val storageTotal = storage.blockCountLong * storage.blockSizeLong
    val storageFree = storage.availableBlocksLong * storage.blockSizeLong
    val storageUsed = storageTotal - storageFree
    val cores = Runtime.getRuntime().availableProcessors()
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    return DeviceSystemInfo(
        processor = processorName(),
        processorDetails = "$cores ядер • $abi",
        totalRam = Formatter.formatFileSize(context, memoryInfo.totalMem),
        totalStorage = Formatter.formatFileSize(context, storageTotal),
        storageDetails = "Використано ${Formatter.formatFileSize(context, storageUsed)} з ${Formatter.formatFileSize(context, storageTotal)} • вільно ${Formatter.formatFileSize(context, storageFree)}",
        system = "Android ${Build.VERSION.RELEASE}",
        systemDetails = "API ${Build.VERSION.SDK_INT}",
        deviceName = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "),
    )
}

private fun processorName(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val soc = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() }.joinToString(" ")
        if (soc.isNotBlank()) return soc
    }
    return Build.HARDWARE.takeIf { it.isNotBlank() } ?: Build.BOARD
}
