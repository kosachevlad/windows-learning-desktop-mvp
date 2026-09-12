package ua.school.windowsdesktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun WindowsLearningDesktopApp() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0078D7))
                .semantics { contentDescription = "Робочий стіл" },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("🗀  Цей ПК", color = Color.White)
                Text("📁  Мої файли", color = Color.White, modifier = Modifier.padding(top = 20.dp))
                Text("📝  Блокнот", color = Color.White, modifier = Modifier.padding(top = 20.dp))
                Text("🎨  Paint", color = Color.White, modifier = Modifier.padding(top = 20.dp))
                Text("🗑️  Кошик", color = Color.White, modifier = Modifier.padding(top = 20.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6000000))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("⊞   ⌕   ▣   🗀   📝   🎨", color = Color.White)
                Text("УКР   10:30", color = Color.White)
            }
        }
    }
}
