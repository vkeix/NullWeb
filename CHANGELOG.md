## 1.0.1 (4 Aug 2026)

* feat: complete multi-tab browser rewrite with Chrome/Firefox-style UI
* feat: NullWeb start page with shortcuts (Google, Wikipedia, GitHub) and search history
* feat: omnibox with real-time Google suggestions and Eruda Suggest (local search history)
* feat: bottom toolbar with Firefox-style expand-on-focus behavior
* feat: tab switcher overlay with visual tab cards
* feat: back button navigation (goes back in history → returns to NullWeb → closes app)
* fix: performance optimization — inactive tabs now pause JavaScript execution
* fix: bundle Eruda locally in assets for instant loading and offline support
* fix: clean URL display (search terms show in omnibox, not Google search URLs)
* refactor: migrated from XML/viewBinding to Jetpack Compose
* refactor: upgraded to AGP 8.2.2, Kotlin 1.9.24, Java 17
