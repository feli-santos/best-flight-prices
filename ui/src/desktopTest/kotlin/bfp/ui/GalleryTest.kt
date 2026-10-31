package bfp.ui

import androidx.compose.ui.test.*
import bfp.ui.i18n.Lang
import org.junit.Assume.assumeTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Writes PNG screenshots of each state to .local/artifacts/gallery when -Dbfp.gallery=<dir> is set. Not an assertion test. */
@OptIn(ExperimentalTestApi::class)
class GalleryTest {
    private val outDir: File? = System.getProperty("bfp.gallery")?.let(::File)

    @Test fun capture() {
        assumeTrue(outDir != null)
        outDir!!.mkdirs()
        val screen = ScreenTest()
        screen.gallery(outDir)
    }
}
