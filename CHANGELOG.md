## 1.0.2 (5 Aug 2026)

* feat: Chrome-style drag & drop tab grouping — long-press a tab and drop it on another to create a group
* feat: tab groups with colored cards, expand/collapse, and ungroup
* feat: groups auto-dissolve when fewer than 2 tabs remain

## 1.0.1 (4 Aug 2026)

* feat: complete multi-tab browser rewrite with Chrome/Firefox-style UI
* feat: NullWeb start page with shortcuts (Google, Wikipedia, GitHub) and search history
* feat: omnibox with real-time Google suggestions and local search history
* feat: bottom toolbar with Firefox-style expand-on-focus behavior
* feat: Firefox-style bottom sheet menu with Back/Forward/Share/Refresh
* feat: native developer tools — console with JS execution, elements inspector, network monitor, resources, sources, info, snippets
* feat: removed Eruda dependency in favor of native devtools
* feat: tab switcher overlay with visual tab previews
* feat: download support with Downloads screen
* feat: history sections (Last hour / Today / Yesterday / Last 7 days / Older) with range delete
* feat: desktop site mode with forced desktop viewport for responsive sites
* feat: external app link confirmation dialog
* feat: back button navigation (goes back in history → returns to NullWeb → closes app)
* fix: performance optimization — inactive tabs now pause JavaScript execution
* fix: clean URL display (search terms show in omnibox, not Google search URLs)
* refactor: migrated from XML/viewBinding to Jetpack Compose
* refactor: upgraded to AGP 8.2.2, Kotlin 1.9.24, Java 17
