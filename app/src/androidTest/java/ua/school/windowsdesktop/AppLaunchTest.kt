package ua.school.windowsdesktop

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun app_shows_desktop() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("Робочий стіл").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Робочий стіл").assertExists()
    }

    @Test fun opens_explorer_and_notepad() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Мої файли").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Мої файли").performClick()
        rule.onNodeWithText("Ім’я").assertExists()
        rule.onNodeWithText("Дата змінення").assertExists()
        rule.onNodeWithText("Новий текстовий документ").assertExists()
        rule.onNodeWithContentDescription("Закрити").performClick()
        rule.onNodeWithText("Блокнот").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("Редактор тексту").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Редактор тексту").assertExists()
    }

    @Test fun closing_untouched_notepad_creates_no_file() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Блокнот").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Блокнот").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("Редактор тексту").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Закрити").performClick()
        rule.onNodeWithText("Мої файли").performClick()
        rule.onNodeWithText("Ця папка порожня").assertExists()
        rule.onNodeWithText("Новий текстовий документ.txt").assertDoesNotExist()
    }
}
