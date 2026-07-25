import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/** Desktop already gets a numeric keyboard/IME for free from [KeyboardType.Number]. */
@Composable
actual fun PointsAmountField(
    amountText: String,
    onAmountChange: (String) -> Unit,
    enabled: Boolean,
    displayColor: Color,
    placeholderColor: Color,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    BasicTextField(
        value = amountText,
        onValueChange = onAmountChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontSize = fontSize, color = displayColor, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(displayColor),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (amountText.isEmpty()) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = fontSize,
                        color = placeholderColor,
                    )
                }
                innerTextField()
            }
        },
    )
}
