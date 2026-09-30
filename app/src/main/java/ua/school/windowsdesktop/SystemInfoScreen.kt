package ua.school.windowsdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SystemInfoScreen(onClose: () -> Unit) {
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
                    Triple("Сховище", "477 GB", "Використано 383 GB з 477 GB"),
                    Triple("Графічна плата", "6 GB", "Інстальовано кілька графічних процесорів"),
                    Triple("ОЗП", "16,0 ГБ", "Швидкість: 4800 МТ/с"),
                    Triple("Процесор", "13th Gen Intel(R)\nCore(TM) i5-13500H", "2.60 GHz"),
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
                        Text("Vivobook", style = MaterialTheme.typography.titleMedium)
                        Text("Vivobook_ASUSLaptop K6502VU_K6502VU", color = Color.DarkGray)
                    }
                }
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFAFCFE))) {
                    Text("Специфікації пристрою", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(
                            "Ім’я пристрою" to "Vivobook",
                            "Процесор" to "13th Gen Intel(R) Core(TM) i5-13500H (2.60 GHz)",
                            "ОЗП" to "16,0 ГБ (доступно для використання: 15,6 ГБ)",
                            "Ідентифікатор пристрою" to "5E2B3742-8ED9-4A3B-8066-554343B82E97",
                            "Код продукту" to "00331-20350-00000-AA545",
                            "Тип системи" to "64-розрядна операційна система, процесор на базі архітектури x64",
                            "Перо та дотики" to "Підтримка пера",
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
