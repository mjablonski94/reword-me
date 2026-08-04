package dev.mjablonski.rewordme.platform

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaterializedTransferableTests {
    @Test
    fun capturesTextFilesAndReplayableStreams() {
        val streamFlavor = DataFlavor("application/x-rewordme-test;class=java.io.InputStream")
        val readerFlavor = DataFlavor("text/x-rewordme-test;class=java.io.Reader")
        val files = listOf(File("first.txt"), File("second.png"))
        val source = MapTransferable(
            linkedMapOf(
                DataFlavor.stringFlavor to "plain text",
                DataFlavor.javaFileListFlavor to files,
                streamFlavor to ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                readerFlavor to StringReader("rich text")
            )
        )

        val captured = assertNotNull(MaterializedTransferable.capture(source))

        assertEquals("plain text", captured.getTransferData(DataFlavor.stringFlavor))
        assertEquals(files, captured.getTransferData(DataFlavor.javaFileListFlavor))
        val firstStream = captured.getTransferData(streamFlavor) as InputStream
        val secondStream = captured.getTransferData(streamFlavor) as InputStream
        assertNotSame(firstStream, secondStream)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), firstStream.readBytes())
        assertContentEquals(byteArrayOf(1, 2, 3, 4), secondStream.readBytes())
        assertEquals("rich text", (captured.getTransferData(readerFlavor) as Reader).readText())
    }

    @Test
    fun refusesAPartialSnapshot() {
        val broken = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.stringFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = true
            override fun getTransferData(flavor: DataFlavor): Any = error("owner disappeared")
        }

        assertNull(MaterializedTransferable.capture(broken))
    }

    @Test
    fun delayedRestoreOnlyRunsWhileTemporaryClipboardIsStillCurrent() {
        assertTrue(ClipboardRestorePolicy.shouldRestore(42, 42))
        assertFalse(ClipboardRestorePolicy.shouldRestore(42, 43))
    }

    @Test
    fun selectionRestoreAlsoPreservesANewerClipboardOwner() {
        val sequenceAfterSyntheticCopy = 100
        assertTrue(ClipboardRestorePolicy.shouldRestore(sequenceAfterSyntheticCopy, 100))
        assertFalse(ClipboardRestorePolicy.shouldRestore(sequenceAfterSyntheticCopy, 101))
    }

    @Test
    fun selectionCopyUsesThePostModifierClipboardAsItsBaseline() {
        assertFalse(ClipboardCopyPolicy.observedSyntheticCopy(43, 43))
        assertTrue(ClipboardCopyPolicy.observedSyntheticCopy(43, 44))
        assertFalse(ClipboardRestorePolicy.shouldRestore(44, 45))
    }

    private class MapTransferable(
        private val values: LinkedHashMap<DataFlavor, Any>
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = values.keys.toTypedArray()
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in values
        override fun getTransferData(flavor: DataFlavor): Any = values.getValue(flavor)
    }
}
