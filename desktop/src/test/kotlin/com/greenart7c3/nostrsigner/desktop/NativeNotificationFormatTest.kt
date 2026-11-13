package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.MacNotifications
import com.greenart7c3.nostrsigner.desktop.core.WindowsToasts
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.xml.sax.InputSource

/**
 * What the Windows and macOS request notifications are built from: the parts
 * that take client-chosen text (the app name is the title) and can be checked
 * without the OS.
 */
class NativeNotificationFormatTest {
    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement

    @Test
    fun toastCarriesTheTextAndOneButtonPerAction() {
        val toast = parse(WindowsToasts.toastXml("Nostrudel", "Sign a note\nas alice", listOf("approve" to "Approve", "reject" to "Reject")))
        assertEquals("default", toast.getAttribute("launch"))
        val texts = toast.getElementsByTagName("text")
        assertEquals(listOf("Nostrudel", "Sign a note", "as alice"), (0 until texts.length).map { texts.item(it).textContent })
        val actions = toast.getElementsByTagName("action")
        assertEquals(
            listOf("approve" to "Approve", "reject" to "Reject"),
            (0 until actions.length).map { actions.item(it).attributes.getNamedItem("arguments").nodeValue to actions.item(it).attributes.getNamedItem("content").nodeValue },
        )
    }

    @Test
    fun toastKeepsAClientChosenTitleAsText() {
        val title = "</text></binding></visual><actions><action content=\"x\" arguments=\"approve\"/></actions> & \"'\u0001"
        val toast = parse(WindowsToasts.toastXml(title, "body", listOf("reject" to "Reject")))
        // The control character XML cannot carry is dropped; nothing becomes markup.
        assertEquals(title.dropLast(1), toast.getElementsByTagName("text").item(0).textContent)
        assertEquals(1, toast.getElementsByTagName("action").length)
        assertEquals("reject", toast.getElementsByTagName("action").item(0).attributes.getNamedItem("arguments").nodeValue)
    }

    @Test
    fun toastWithoutActionsHasNoButtons() {
        val toast = parse(WindowsToasts.toastXml("Amber", "one\n\ntwo\nthree", emptyList()))
        assertEquals(0, toast.getElementsByTagName("actions").length)
        // Title plus two lines: a generic toast shows three.
        assertEquals(3, toast.getElementsByTagName("text").length)
    }

    @Test
    fun macCategoryFollowsTheButtonsAndTheirLabels() {
        val both = listOf("approve" to "Approve", "reject" to "Reject")
        assertEquals(MacNotifications.categoryId(both), MacNotifications.categoryId(both.toList()))
        assertNotEquals(MacNotifications.categoryId(both), MacNotifications.categoryId(listOf("reject" to "Reject")))
        assertNotEquals(MacNotifications.categoryId(both), MacNotifications.categoryId(listOf("approve" to "Aprovar", "reject" to "Rejeitar")))
    }
}
