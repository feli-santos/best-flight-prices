# Testing

TDD. Pyramid: core golden tests (JVM) -> contract golden JSON -> server routes (testApplication, MockEngine) -> ViewModel tests -> screen tests (Compose desktop, every state x 3 languages x font 1.0/2.0) -> Maestro e2e on Android and iOS -> real-data structural checks.
Golden cases: CNF->GRU 3 nights; LIS 7; Tokyo >=10; SYD >=12; cyclone cap; seismic no effect; renormalization; explore rules. Real-data checks never assert on prices. Full matrix: docs/LOCAL_LAB.md.

`make test` is `./gradlew check`: every unit, contract, route and screen suite, in all modules. Running `jvmTest` alone skips `:server` and `:ui`.
`MainDispatcherTest` (`:desktopApp`) guards the runtime classpath: ViewModel scopes dispatch on `Dispatchers.Main`, which the desktop shell gets from `kotlinx-coroutines-swing`. Screen tests inject `Dispatchers.Unconfined`, so they cannot catch a missing main dispatcher.
