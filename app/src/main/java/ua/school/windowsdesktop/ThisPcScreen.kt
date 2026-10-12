package ua.school.windowsdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private data class LearningDrive(
    val name: String,
    val freeSpace: String,
    val usedFraction: Float,
    val icon: Int,
)

@Composable
internal fun ThisPcScreen(onClose: () -> Unit) {
    val drives = listOf(
        LearningDrive("Локальний диск (C:)", "800 МБ вільно з 1 ГБ", 0.2f, R.drawable.drive_windows),
        LearningDrive("Локальний диск (D:)", "1 ГБ вільно з 1 ГБ", 0f, R.drawable.drive_plain),
    )
    var selectedDrive by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color.White)) {
        WindowTitle("Цей ПК", onClose)
        ThisPcToolbar()
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(278.dp).fillMaxHeight().background(Color(0xFFF8F8F8)).padding(top = 8.dp)) {
                Text("⌄   🖥  Цей ПК", Modifier.fillMaxWidth().background(Color(0xFFCDE8FF)).padding(horizontal = 10.dp, vertical = 9.dp))
                drives.forEach { drive ->
                    Text("›      ${drive.name}", Modifier.fillMaxWidth().clickable { selectedDrive = drive.name }
                        .padding(horizontal = 10.dp, vertical = 10.dp))
                }
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFFE0E0E0)))
            Column(Modifier.weight(1f).padding(horizontal = 30.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Пристрої та диски", style = MaterialTheme.typography.titleMedium)
                drives.forEach { drive ->
                    DriveTile(drive, selected = selectedDrive == drive.name, onSelect = { selectedDrive = drive.name })
                }
            }
        }
    }
}

@Composable
private fun ThisPcToolbar() = Row(
    Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    listOf("←" to "Назад", "→" to "Вперед", "↑" to "Вгору", "↻" to "Оновити").forEach { (symbol, description) ->
        IconButton(onClick = {}, modifier = Modifier.size(42.dp)) { Text(symbol, style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(2.dp))
    }
    Surface(Modifier.weight(1f).height(40.dp).padding(start = 10.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🖥   ›   Цей ПК")
        }
    }
}

@Composable
private fun DriveTile(drive: LearningDrive, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFFCDE8FF) else Color.Transparent)
        .clickable(onClick = onSelect).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(drive.icon), drive.name, Modifier.size(width = 64.dp, height = 50.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.width(260.dp)) {
            Text(drive.name)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(16.dp).border(1.dp, Color(0xFFB8B8B8))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(drive.usedFraction).background(Color(0xFF0877C9)))
            }
            Spacer(Modifier.height(4.dp))
            Text(drive.freeSpace, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
        }
    }
}
