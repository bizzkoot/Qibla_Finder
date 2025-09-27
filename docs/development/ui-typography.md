# Qibla Finder Typography Guidelines

_Last updated: September 27, 2025_

## Overview
The app now exposes a centralized typography system that adapts text sizes across phones, tablets, and foldables. Compose screens must rely on this system to ensure minimum readable sizes (≥12sp for copy) and consistent hierarchy when the window size changes.

## Key Components
- `QiblaTypography.current`: Composition-local access to semantic text styles (e.g., `titlePrimary`, `bodySecondary`, `labelSmall`).
- `QiblaFinderTheme`: Wraps app content, injecting both Material 3 typography overrides and the `QiblaTypography` provider.
- Breakpoints: Compact (<600dp), Medium (600–839dp), Expanded (≥840dp). Styles scale by 8% on medium and 15% on expanded widths, dampened automatically when `fontScale > 1.0f` to avoid double scaling.

## Usage Rules
1. **Always use semantic tokens** – replace literal `fontSize` assignments with `QiblaTypography.current.*` or `MaterialTheme.typography.*` as appropriate.
2. **Respect minimum sizes** – body copy must use at least `bodySecondary`; captions and badges use `labelSmall`/`badge` (12sp effective).
3. **Customize via copy** – prefer `Text(style = token, fontWeight = …)` over redefining font sizes.
4. **Testing** – verify screens in Compact, Medium, Expanded previews and with system font scales 1.0x, 1.3x, 1.6x to ensure no truncation.

## Mapping Cheatsheet
| Semantic Token | Typical Use | Material3 Alias |
| --- | --- | --- |
| `titlePrimary` | Hero headings, screen titles on tablets | `Typography.displayMedium` |
| `titleSecondary` | Section headers, prominent dialogs | `Typography.titleLarge` |
| `titleTertiary` | Card headers, dialog headings | `Typography.titleMedium` |
| `bodyPrimary` | Primary body copy | `Typography.bodyLarge` |
| `bodySecondary` | Secondary copy, supporting text | `Typography.bodyMedium` |
| `bodyEmphasis` | Highlighted metrics/stats | `Typography.titleSmall` |
| `labelLarge` | Buttons, chips | `Typography.labelLarge` |
| `labelMedium` | Inline labels, metadata | `Typography.labelMedium` |
| `labelSmall` | Badges, compact metadata, FAB labels | `Typography.labelSmall` |
| `badge` | Status pills (Update Available, etc.) | n/a |
| `caption` | Helper text, timestamps | `Typography.bodySmall` |

## Implementation Checklist for New Screens
- Import `com.bizzkoot.qiblafinder.ui.theme.QiblaTypography`.
- Grab `val typography = QiblaTypography.current` at the top of the composable.
- Apply `style = typography.<token>` on all `Text` calls.
- Avoid reintroducing raw `fontSize` values; add lint notes if necessary.

## Future Enhancements
- Compose preview parameters to visualize all breakpoints in a single @PreviewGroup.
- Static analysis rule to flag new literal `fontSize` usages (pending Detekt rule update).

