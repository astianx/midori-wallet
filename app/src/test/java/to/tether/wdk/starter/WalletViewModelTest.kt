package to.tether.wdk.starter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletViewModelTest {
    @Test
    fun acceptsPrimitiveBooleanResults() {
        assertTrue(isValidVerificationResult(true))
        assertFalse(isValidVerificationResult(false))
    }

    @Test
    fun rejectsStringAndStructuredResults() {
        assertFalse(isValidVerificationResult("true"))
        assertFalse(isValidVerificationResult("1"))
        assertFalse(isValidVerificationResult(mapOf("valid" to true)))
    }
}
