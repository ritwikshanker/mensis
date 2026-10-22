package com.deutschdreamers.mensis.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The plan's budget for one Argon2id derivation with the default parameters (64 MiB, 3 passes): about
 * 3 s on a phone, otherwise the default drops to 32 MiB. The time is printed for the record; the
 * assertion only catches a derivation that would make "Turn on backup" feel broken.
 */
@RunWith(AndroidJUnit4::class)
class Argon2TimingTest {
    @Test
    fun defaultParametersDeriveWithinBudget() {
        val started = System.nanoTime()
        val key = EncryptedContainer.deriveKey("synthetic backup password".toCharArray(), KdfParams.DEFAULT)
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        key.destroy()
        println("Argon2id default derivation took $millis ms")
        assertTrue("Argon2id default derivation took $millis ms", millis < BUDGET_MILLIS)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUDGET_MILLIS = 10_000L
    }
}
