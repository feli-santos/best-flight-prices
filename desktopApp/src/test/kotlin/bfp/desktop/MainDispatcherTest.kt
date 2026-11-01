package bfp.desktop

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue

class MainDispatcherTest {
    /**
     * SearchViewModel defaults to viewModelScope, which dispatches on Dispatchers.Main. On the JVM that
     * provider comes from kotlinx-coroutines-swing; without it the first "Find flights" click crashes
     * the window with "Module with the Main dispatcher is missing".
     */
    @Test fun mainDispatcherIsProvidedByTheDesktopApp() {
        assertTrue(Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext), "Dispatchers.Main must dispatch on the AWT event thread")
    }
}
