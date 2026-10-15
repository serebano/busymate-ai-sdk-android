package ai.busymate.whitelabel

import ai.busymate.whitelabel.BusymateMicrophone.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class BusymateMicrophoneDecisionTest {
    private fun decide(origin: Boolean, audio: Boolean, os: Boolean) = BusymateMicrophone.decide(origin, audio, os)

    @Test fun firstAudioRequestFromAllowedOriginAsksTheOs() {
        // Issue #7: the chat's capture request must open the RECORD_AUDIO prompt, not be denied.
        assertEquals(Decision.ASK, decide(origin = true, audio = true, os = false))
    }
    @Test fun grantedAppGrantsAudioWithoutAnotherPrompt() {
        assertEquals(Decision.GRANT, decide(origin = true, audio = true, os = true))
    }
    @Test fun untrustedOriginIsDeniedWithoutPrompt() {
        assertEquals(Decision.DENY, decide(origin = false, audio = true, os = false))
        assertEquals(Decision.DENY, decide(origin = false, audio = true, os = true))
    }
    @Test fun nonAudioRequestIsDeniedWithoutPrompt() {
        assertEquals(Decision.DENY, decide(origin = true, audio = false, os = false))
        assertEquals(Decision.DENY, decide(origin = true, audio = false, os = true))
    }
}
