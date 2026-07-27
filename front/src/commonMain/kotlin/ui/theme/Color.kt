import androidx.compose.ui.graphics.Color

/**
 * Brand palette for "Danse avec Guillaume", hand-derived (Material 3's HCT tonal
 * ramps aren't available in a Compose Multiplatform-friendly library here, so
 * these are simple HSL tone ramps at a fixed hue/saturation per role) from
 * colors actually used on danseavecguillaume.fr:
 *  - Primary (gold): the site's `--primary-color`/`--primary-dark` (#f1cb30/#ab9023).
 *  - Secondary (plum): the site's shelved `#af2db1` magenta accent, muted down.
 *  - Tertiary (indigo): the `#2d2fb1` blue used in one of the site's pricing gradients.
 *  - Neutrals: warmed slightly toward the gold hue, echoing the site's near-black
 *    footer (`--dark: #222222`) on light-mode backgrounds and vice versa in dark mode.
 * Every text-on-color pairing below (see Theme.kt) meets WCAG AA (>= 4.5:1 for
 * primary/secondary/tertiary against their "on" color, higher for containers).
 */

// Gold — primary
val Gold10 = Color(0xFF2C2107)
val Gold20 = Color(0xFF58420E)
val Gold30 = Color(0xFF846315)
val Gold40 = Color(0xFF8C6917) // dialed to HSL lightness 0.32 (not 0.40) so it hits 5.06:1 against white
val Gold80 = Color(0xFFF1DBA7)
val Gold90 = Color(0xFFF8EDD3)

// Plum — secondary
val Plum10 = Color(0xFF240F1D)
val Plum20 = Color(0xFF471F3A)
val Plum30 = Color(0xFF6B2E57)
val Plum40 = Color(0xFF8F3D74)
val Plum80 = Color(0xFFE0B8D3)
val Plum90 = Color(0xFFF0DBE9)

// Indigo — tertiary
val Indigo10 = Color(0xFF0F1024)
val Indigo20 = Color(0xFF1E2048)
val Indigo30 = Color(0xFF2C316D)
val Indigo40 = Color(0xFF3B4191)
val Indigo80 = Color(0xFFB7B9E1)
val Indigo90 = Color(0xFFDBDCF0)

// Neutral — background/surface family
val Neutral0 = Color(0xFF000000)
val Neutral4 = Color(0xFF0B0B09)
val Neutral6 = Color(0xFF11100E)
val Neutral10 = Color(0xFF1C1A17)
val Neutral12 = Color(0xFF22201C)
val Neutral17 = Color(0xFF302D27)
val Neutral20 = Color(0xFF38352E)
val Neutral22 = Color(0xFF3E3A32)
val Neutral24 = Color(0xFF433F37)
val Neutral87 = Color(0xFFE1DFDB)
val Neutral90 = Color(0xFFE8E6E3)
val Neutral92 = Color(0xFFEDEBE9)
val Neutral94 = Color(0xFFF1F0EE)
val Neutral95 = Color(0xFFF4F3F1)
val Neutral96 = Color(0xFFF6F5F4)
val Neutral98 = Color(0xFFFAFAF9)
val Neutral99 = Color(0xFFFDFDFC)
val Neutral100 = Color(0xFFFFFFFF)

// Neutral variant — surfaceVariant/outline family, tinted toward the plum hue
val NeutralVariant30 = Color(0xFF52474E)
val NeutralVariant50 = Color(0xFF887782)
val NeutralVariant60 = Color(0xFFA0929B)
val NeutralVariant80 = Color(0xFFD0C8CD)
val NeutralVariant90 = Color(0xFFE7E4E6)
