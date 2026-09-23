package com.sakura.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Neutral surfaces shared by every accent. Kept deliberately quiet so the
 * accent colour is the only saturated thing on screen.
 */
object Neutral {
    // Light
    val BackgroundLight = Color(0xFFFAFAFA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceVariantLight = Color(0xFFF1F1F5)
    val SurfaceContainerLight = Color(0xFFF6F6F9)
    val SurfaceContainerLowLight = Color(0xFFF7F7FA)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerHighestLight = Color(0xFFEDEDF2)
    val OutlineLight = Color(0xFFE3E3E9)
    val OutlineVariantLight = Color(0xFFEDEDF2)
    val OnBackgroundLight = Color(0xFF1A1B20)
    val OnSurfaceVariantLight = Color(0xFF5D5E68)
    val ErrorLight = Color(0xFFB3261E)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFF9DEDC)

    // Dark
    val BackgroundDark = Color(0xFF101014)
    val SurfaceDark = Color(0xFF17181D)
    val SurfaceVariantDark = Color(0xFF23242B)
    val SurfaceContainerDark = Color(0xFF1C1D23)
    val SurfaceContainerLowDark = Color(0xFF17181D)
    val SurfaceContainerLowestDark = Color(0xFF101014)
    val SurfaceContainerHighestDark = Color(0xFF26272E)
    val OutlineDark = Color(0xFF33343C)
    val OutlineVariantDark = Color(0xFF26272E)
    val OnBackgroundDark = Color(0xFFE7E7EC)
    val OnSurfaceVariantDark = Color(0xFFA8A9B4)
    val ErrorDark = Color(0xFFF2B8B5)
    val OnErrorDark = Color(0xFF601410)
    val ErrorContainerDark = Color(0xFF8C1D18)
}

/**
 * Selectable accent palettes. The user can switch these at runtime.
 *
 * Ordered around the colour wheel with the neutral grey last, so the picker
 * reads like a palette rather than an arbitrary list.
 */
enum class AccentTheme(val label: String) {
    Indigo("靛青"),
    Ocean("海蓝"),
    Teal("青碧"),
    Moss("苔绿"),
    Amber("琥珀"),
    Sakura("樱粉"),
    Violet("紫藤"),
    Graphite("石墨");

    companion object {
        fun fromId(id: String?): AccentTheme =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: Sakura
    }
}

/** The handful of roles an accent needs to define. */
data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
)

fun lightAccent(theme: AccentTheme): AccentColors = when (theme) {
    AccentTheme.Indigo -> AccentColors(
        primary = Color(0xFF4A5B9E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE1FF),
        onPrimaryContainer = Color(0xFF0A1548),
        secondary = Color(0xFF5B6076),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E1F9),
        onSecondaryContainer = Color(0xFF181A2C),
        tertiary = Color(0xFF6C5A7E),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Ocean -> AccentColors(
        primary = Color(0xFF2A6B9C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFE5FF),
        onPrimaryContainer = Color(0xFF001D33),
        secondary = Color(0xFF4A6274),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE5F8),
        onSecondaryContainer = Color(0xFF051F2C),
        tertiary = Color(0xFF5A5C7E),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Teal -> AccentColors(
        primary = Color(0xFF2F6F6B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDEDEA),
        onPrimaryContainer = Color(0xFF00201E),
        secondary = Color(0xFF4A6360),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E4),
        onSecondaryContainer = Color(0xFF051F1D),
        tertiary = Color(0xFF4C6178),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Moss -> AccentColors(
        primary = Color(0xFF4C6B3C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDEDB4),
        onPrimaryContainer = Color(0xFF102000),
        secondary = Color(0xFF57624C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDBE7CB),
        onSecondaryContainer = Color(0xFF151E0D),
        tertiary = Color(0xFF3A6467),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Amber -> AccentColors(
        primary = Color(0xFF8A5A1E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDEB0),
        onPrimaryContainer = Color(0xFF2C1700),
        secondary = Color(0xFF6F5B3F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFBDDBC),
        onSecondaryContainer = Color(0xFF271806),
        tertiary = Color(0xFF6B5C7E),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Sakura -> AccentColors(
        primary = Color(0xFFB9506F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E0021),
        secondary = Color(0xFF75565F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF2B151C),
        tertiary = Color(0xFF7A5734),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Violet -> AccentColors(
        primary = Color(0xFF6A4CA0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF24005A),
        secondary = Color(0xFF635B74),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE9DEF8),
        onSecondaryContainer = Color(0xFF1F182D),
        tertiary = Color(0xFF7C5266),
        onTertiary = Color(0xFFFFFFFF),
    )

    AccentTheme.Graphite -> AccentColors(
        primary = Color(0xFF4A4F58),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE2EA),
        onPrimaryContainer = Color(0xFF171B22),
        secondary = Color(0xFF5B6068),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E2E8),
        onSecondaryContainer = Color(0xFF181B20),
        tertiary = Color(0xFF5F5A6E),
        onTertiary = Color(0xFFFFFFFF),
    )
}

fun darkAccent(theme: AccentTheme): AccentColors = when (theme) {
    AccentTheme.Indigo -> AccentColors(
        primary = Color(0xFFB3C0F0),
        onPrimary = Color(0xFF16224D),
        primaryContainer = Color(0xFF323F70),
        onPrimaryContainer = Color(0xFFDEE1FF),
        secondary = Color(0xFFC4C6DC),
        onSecondary = Color(0xFF2C2F42),
        secondaryContainer = Color(0xFF424659),
        onSecondaryContainer = Color(0xFFE0E1F9),
        tertiary = Color(0xFFD8BFEA),
        onTertiary = Color(0xFF3B2A4D),
    )

    AccentTheme.Ocean -> AccentColors(
        primary = Color(0xFF8FCDF9),
        onPrimary = Color(0xFF003353),
        primaryContainer = Color(0xFF004A73),
        onPrimaryContainer = Color(0xFFCFE5FF),
        secondary = Color(0xFFB1C9DC),
        onSecondary = Color(0xFF1C3240),
        secondaryContainer = Color(0xFF33495A),
        onSecondaryContainer = Color(0xFFCDE5F8),
        tertiary = Color(0xFFC3C4E5),
        onTertiary = Color(0xFF2C2E4C),
    )

    AccentTheme.Teal -> AccentColors(
        primary = Color(0xFF96D1CC),
        onPrimary = Color(0xFF003733),
        primaryContainer = Color(0xFF14504C),
        onPrimaryContainer = Color(0xFFCDEDEA),
        secondary = Color(0xFFB1CCC8),
        onSecondary = Color(0xFF1C3532),
        secondaryContainer = Color(0xFF334B48),
        onSecondaryContainer = Color(0xFFCCE8E4),
        tertiary = Color(0xFFB4C8E0),
        onTertiary = Color(0xFF1E3145),
    )

    AccentTheme.Moss -> AccentColors(
        primary = Color(0xFFB2D398),
        onPrimary = Color(0xFF1F370C),
        primaryContainer = Color(0xFF364E22),
        onPrimaryContainer = Color(0xFFCDEDB4),
        secondary = Color(0xFFBFCBAD),
        onSecondary = Color(0xFF2A331F),
        secondaryContainer = Color(0xFF404A34),
        onSecondaryContainer = Color(0xFFDBE7CB),
        tertiary = Color(0xFFA2CFD2),
        onTertiary = Color(0xFF03373A),
    )

    AccentTheme.Amber -> AccentColors(
        primary = Color(0xFFF4BE7C),
        onPrimary = Color(0xFF4A2A00),
        primaryContainer = Color(0xFF6A3F00),
        onPrimaryContainer = Color(0xFFFFDEB0),
        secondary = Color(0xFFDDC4A3),
        onSecondary = Color(0xFF3D2E1A),
        secondaryContainer = Color(0xFF55442E),
        onSecondaryContainer = Color(0xFFFBDDBC),
        tertiary = Color(0xFFD4C1E8),
        onTertiary = Color(0xFF3A2B4D),
    )

    AccentTheme.Sakura -> AccentColors(
        primary = Color(0xFFF1AEC4),
        onPrimary = Color(0xFF54112F),
        primaryContainer = Color(0xFF723145),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFFE3BDC6),
        onSecondary = Color(0xFF422931),
        secondaryContainer = Color(0xFF5A3F47),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFE6BE96),
        onTertiary = Color(0xFF442B0D),
    )

    AccentTheme.Violet -> AccentColors(
        primary = Color(0xFFCFBCFF),
        onPrimary = Color(0xFF3A1C71),
        primaryContainer = Color(0xFF523A88),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4358),
        onSecondaryContainer = Color(0xFFE9DEF8),
        tertiary = Color(0xFFEFB8CF),
        onTertiary = Color(0xFF4A2637),
    )

    AccentTheme.Graphite -> AccentColors(
        primary = Color(0xFFC6CAD3),
        onPrimary = Color(0xFF2F343B),
        primaryContainer = Color(0xFF454A53),
        onPrimaryContainer = Color(0xFFDDE2EA),
        secondary = Color(0xFFC4C6CE),
        onSecondary = Color(0xFF2D3138),
        secondaryContainer = Color(0xFF43474F),
        onSecondaryContainer = Color(0xFFE0E2E8),
        tertiary = Color(0xFFCBC4DA),
        onTertiary = Color(0xFF332F41),
    )
}
