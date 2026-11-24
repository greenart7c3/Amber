package com.greenart7c3.nostrsigner.ui.actions

import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.password_is_required
import com.greenart7c3.nostrsigner.commonsui.password_too_short
import com.greenart7c3.nostrsigner.commonsui.passwords_do_not_match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPasswordErrorTest {
    @Test
    fun aBlankPasswordIsRequired() {
        assertEquals(Res.string.password_is_required, exportPasswordError("", ""))
        assertEquals(Res.string.password_is_required, exportPasswordError("   ", "   "))
    }

    @Test
    fun aPasswordNeedsEightCharacters() {
        assertEquals(Res.string.password_too_short, exportPasswordError("1234567", "1234567"))
    }

    @Test
    fun theConfirmationMustMatch() {
        assertEquals(Res.string.passwords_do_not_match, exportPasswordError("12345678", "12345679"))
    }

    @Test
    fun aLongConfirmedPasswordIsAccepted() {
        assertNull(exportPasswordError("12345678", "12345678"))
    }

    @Test
    fun lengthIsCheckedBeforeTheConfirmation() {
        assertEquals(Res.string.password_too_short, exportPasswordError("short", "other"))
    }
}
