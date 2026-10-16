package app.inknotes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.inknotes.R

/** One variable font file, instantiated at the weights and slants the app uses. */
private fun flex(weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.slant(if (style == FontStyle.Italic) -10f else 0f),
    ),
)

val GoogleSansFlex = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).flatMap { weight ->
        listOf(flex(weight), flex(weight, FontStyle.Italic))
    }
)

private fun TextStyle.flex() = copy(fontFamily = GoogleSansFlex)

val InkTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
    )
}
