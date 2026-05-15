# Test Cases - Interactive AI Avatar (Android)

> Platform: Android | Users: 1 (single user) | Core features only

## Test Cases

| TC# | Device/Tab | Test Case | Steps | Expected Result |
|-----|-----------|-----------|-------|-----------------|
| TC-01 | Device: emulator-5554 | Login | Enter username `testuser` in the username field and tap the Login button | Main page loads with video area, status showing "Ready" or "Click Start Conversation to begin", and "Start Conversation" button visible |
| TC-02 | Device: emulator-5554 | Start Conversation | Tap the "Start Conversation" button and wait for the flow to complete | Status changes from "Registering AI Agent" through "Creating digital human instance", "Getting token", "Logging into room" to "Playing"; AI Avatar video stream appears in the video area |
| TC-03 | Device: emulator-5554 | Mic Toggle | Tap the "Mic ON" button | Button text changes to "Mic OFF" and button color changes to orange |
| TC-04 | Device: emulator-5554 | Mic Restore | Tap the "Mic OFF" button | Button text changes back to "Mic ON" and button color changes back to green |
| TC-05 | Device: emulator-5554 | End Conversation | Tap the "End Conversation" button | Status returns to "Ready" or "Click Start Conversation to begin", video area shows placeholder text, "Start Conversation" button reappears, and "Mic ON" button is disabled |
