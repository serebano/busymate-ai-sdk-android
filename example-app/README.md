# Android example app

This buildable Gradle app consumes `project(":sdk")`, the actual SDK from this repository. Build `./gradlew :example-app:assembleDebug :example-app:testDebugUnitTest`, install `example-app/build/outputs/apk/debug/example-app-debug.apk` on a supported Android device, and launch **Busymate SDK example**.

The default route `https://busymate.ai/support/busyproxy?channel=android&locale=en` is real hosted guest chat. The assistant root marketing website is not used as a chat route. Your own tenant's actual chat URL and assistant slug can be configured in Settings. The top settings panel scrolls; hosted chat fills the remaining screen.

Controls: assistant, HTTPS chat URL, exact origins, optional authenticated-backend URL/account/session bearer; enable microphone adapter, ready/close handlers; Apply safely recreates the Activity/WebView; sign in/out and account state refresh; reload; OS app Settings; reset to guest defaults. The bearer is your own backend session credential, never a Busymate service key. Values live only in process memory and are never persisted/logged. Configure and Apply before using Sign in. Setting an account ID does not authenticate you: your real backend must authenticate the supplied session, mint a fresh nonce-bound assertion and return `{token, nonce}`. The demo makes a real HTTPS POST with the SDK mint fields; it does not fake successful identity.

Callbacks/lifecycle and media requests appear in a bounded event log without JWTs, nonces or audio. Open URL/auth requests use the frozen SDK's native external URL flow. Close/ready callbacks are controllable. Native microphone permission is requested by the user tapping the actual hosted microphone/voice controls; Retry, denial, grant and cancellation occur in the real hosted chat. The OS Settings control opens this app's Settings; the SDK itself never opens Settings automatically.

History, knowledge retrieval, tools, human handoff, themes, locale and voice availability are hosted tenant capabilities. Configure and enable them in the Busymate workspace; this demo has no invented native switches that claim to enable backend services. Test your configured tenant and authenticated backend separately. The public busyproxy route does not prove every tenant service is enabled.

Renderer crash recovery: the v2 identity SDK exposes `onRenderProcessGone` and `onReplaced`, but its frozen recovery only reinstalls identity. With microphone active, recreate the Activity early so its Activity Result registration remains valid; do not install a microphone adapter late inside `onReplaced`. The actual WebViewClient forwards renderer termination: microphone-enabled mode recreates the Activity; microphone-disabled mode invokes the frozen SDK recovery and tracks onReplaced. Apply/recreation also exercises safe app-owned reconstruction. Disabling the microphone adapter causes this example host to deny media requests; it does not silently enable an alternate native permission flow. It has no arbitrary process-kill button or fake renderer event.

Ten unit tests validate settings boundaries (HTTPS, exact origins, assistant syntax), actual SDK callback enable/disable semantics, backend nonce mapping and a real loopback redirect refusal. CI additionally compiles actual SDK, APK and runs SDK and app Android lint. Authenticated mint calls refuse all HTTP redirects; configure the final HTTPS backend endpoint directly. Physical OS prompt/device behavior and your backend integration require the acceptance checklist in `../docs/microphone.md`.

## Reproducible emulator runtime smoke

CI runs `scripts/test-runtime.sh` on the existing Linux SDK-check runner with an
isolated Android35 emulator and then uploads instrumentation results/screenshots.
The actual example APK is tested: native microphone starts ON, OFF survives Apply
and real Activity recreation, Reset restores the guest defaults, and foreground
resume leaves `RECORD_AUDIO` ungranted without a permission dialog. The test opens
the hosted guest WebView but does not claim its backend response is verified.

This test sends no chat message, grants no microphone permission and records no
audio. It is emulator lifecycle/configuration coverage; first-tap grant/refusal,
revocation, cancellation, physical audio and real authenticated backend flows
remain separate acceptance checks in the microphone guide. For local execution,
use a dedicated Linux Android SDK with the required system image and KVM; the
script owns and removes only the AVD it creates.

## First-tap OS permission acceptance (explicit live build only)

After the release owner confirms the hosted native microphone implementation is
live, dispatch **SDK checks** with both `hosted_commit` (the verified 9–40 hex
commit) and `hosted_build` (matching build number). Leaving both fields empty
runs the ordinary smoke test. The permission suite checks the fixed public
`https://busymate.ai/api/version` before and after each case; a different build,
missing inputs, zero executed tests or skipped cases fails acceptance.

For an equivalent isolated run, set `BUSYMATE_HOSTED_COMMIT` and
`BUSYMATE_HOSTED_BUILD`, then run `bash scripts/test-permissions.sh`
from the repository root. Use the CI runtime specified above with English OS
permission labels. Existing `.permission-results/` evidence is preserved; move it
before a subsequent run.

Four cases each start with this disposable demo's microphone permission reset:
dictation grant, dictation deny, voice-mode grant and voice-mode deny. Each case
requires the actual hosted controls to be visible and no opening permission
dialog, taps that real control, captures the OS dialog naming this demo, and
chooses Allow or Deny through the system UI. Permission is never pre-granted by
adb, simctl, browser scripting or a mocked bridge. Screenshots and test reports
are retained under `.permission-results/`.
Android additionally verifies the app's actual RECORD_AUDIO permission and retains dumpsys readback after each choice.

The app/WebView is closed promptly after the choice, including on assertion
failure. Grant cases can briefly begin a real virtual capture or voice session;
no chat message or deliberate transcription is sent. These tests prove the
simulator/emulator OS-permission boundary, not physical microphone/audio quality,
all tenant features or authenticated backend behavior. Do not run against an old
hosted build or on a shared physical device. No published SDK tag is changed by
this harness.
