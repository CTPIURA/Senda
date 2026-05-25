package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.TransactionEntity
import com.example.TransactionRow
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val mockTransaction = TransactionEntity(
        id = 42L,
        concept = "Supermercado Wong",
        amount = 185.50,
        type = "EXPENSE",
        category = "Comida",
        dateMillis = 1779717142000L,
        notes = "Compras semanales de víveres",
        isSynced = false
    )

    composeTestRule.setContent {
        MyApplicationTheme(darkTheme = true) {
            TransactionRow(tx = mockTransaction, onEdit = {})
        }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
