#!/bin/bash
set +e

# Midscene env vars - bashrc has non-interactive guard so source won't work
export MIDSCENE_MODEL_BASE_URL="https://ark.cn-beijing.volces.com/api/v3"
export MIDSCENE_MODEL_API_KEY="0c48f289-676e-48de-8610-80ffbf7d40ef"
export MIDSCENE_MODEL_NAME="doubao-seed-2-0-lite-260428"
export MIDSCENE_MODEL_FAMILY="doubao-seed"

DEVICE_MAIN="emulator-5554"
APP_PACKAGE="com.example.aiaiavatardemo"
REPORT_DIR="midscene_run"
LOG_FILE="midscene_run/test_results.log"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=5

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log_tc() {
  echo "" | tee -a "$LOG_FILE"
  echo "========================================" | tee -a "$LOG_FILE"
  echo "[TC-$1] $2" | tee -a "$LOG_FILE"
  echo "========================================" | tee -a "$LOG_FILE"
}

tc_pass() {
  log "RESULT TC-$1: PASSED - $2"
  PASS_COUNT=$((PASS_COUNT + 1))
}

tc_fail() {
  log "RESULT TC-$1: FAILED - $2"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

mkdir -p "$REPORT_DIR/log" "$REPORT_DIR/report"
echo "Test Run - $(date)" > "$LOG_FILE"

# Connect
log "Connecting Android device..."
npx -y @midscene/android@1 connect --deviceId "$DEVICE_MAIN"
sleep 3

# Launch the app using Midscene's launch command
log "Launching app via Midscene..."
npx -y @midscene/android@1 launch --uri "$APP_PACKAGE" --device-id "$DEVICE_MAIN" 2>&1 | tee -a "$LOG_FILE"
sleep 5

# ===== TC-01: Login =====
log_tc "01" "Login"
R=$(npx -y @midscene/android@1 act --device-id "$DEVICE_MAIN" --prompt "In the current app screen, type 'testuser' in the username input field and tap the Login button. Confirm the main page loads with a video area and a 'Start Conversation' button visible. The status may show 'Ready' or 'Click Start Conversation to begin' — either is acceptable." 2>&1 | tee -a "$LOG_FILE")
if echo "$R" | grep -qi "Task finished" && ! echo "$R" | grep -qi "Error executing act\|Task failed"; then
  tc_pass "01" "Login successful"
else
  tc_fail "01" "Login failed"
fi

# ===== TC-02: Start Conversation =====
log_tc "02" "Start Conversation"
R=$(npx -y @midscene/android@1 act --device-id "$DEVICE_MAIN" --prompt "Tap the 'Start Conversation' button. Wait for the status to change through 'Registering', 'Creating', 'Getting token', 'Logging into room' and finally reach 'Playing'. Confirm the AI Avatar video stream is visible in the video area and the 'End Conversation' button appears." 2>&1 | tee -a "$LOG_FILE")
if echo "$R" | grep -qi "Task finished" && ! echo "$R" | grep -qi "Error executing act\|Task failed"; then
  tc_pass "02" "Start conversation successful"
else
  tc_fail "02" "Start conversation failed"
fi

# ===== TC-03: Mic Toggle Off =====
log_tc "03" "Mic Toggle Off"
R=$(npx -y @midscene/android@1 act --device-id "$DEVICE_MAIN" --prompt "Tap the 'Mic ON' button. Confirm the button text changes to 'Mic OFF' and the button color changes to orange." 2>&1 | tee -a "$LOG_FILE")
if echo "$R" | grep -qi "Task finished" && ! echo "$R" | grep -qi "Error executing act\|Task failed"; then
  tc_pass "03" "Mic toggled off"
else
  tc_fail "03" "Mic toggle off failed"
fi

# ===== TC-04: Mic Toggle On =====
log_tc "04" "Mic Toggle On"
R=$(npx -y @midscene/android@1 act --device-id "$DEVICE_MAIN" --prompt "Tap the 'Mic OFF' button. Confirm the button text changes back to 'Mic ON' and the button color changes to green." 2>&1 | tee -a "$LOG_FILE")
if echo "$R" | grep -qi "Task finished" && ! echo "$R" | grep -qi "Error executing act\|Task failed"; then
  tc_pass "04" "Mic toggled on"
else
  tc_fail "04" "Mic toggle on failed"
fi

# ===== TC-05: End Conversation =====
log_tc "05" "End Conversation"
R=$(npx -y @midscene/android@1 act --device-id "$DEVICE_MAIN" --prompt "Tap the 'End Conversation' button. Confirm the status returns to 'Ready' or 'Click Start Conversation to begin', the video area shows placeholder text, the 'Start Conversation' button reappears, and the 'Mic ON' button is disabled." 2>&1 | tee -a "$LOG_FILE")
if echo "$R" | grep -qi "Task finished" && ! echo "$R" | grep -qi "Error executing act\|Task failed"; then
  tc_pass "05" "End conversation successful"
else
  tc_fail "05" "End conversation failed"
fi

# ===== Summary =====
log ""
log "========================================"
log "TEST SUMMARY"
log "========================================"
log "Total: $TOTAL_COUNT | Passed: $PASS_COUNT | Failed: $FAIL_COUNT"
if [ "$FAIL_COUNT" -eq 0 ]; then
  log "All $TOTAL_COUNT test cases PASSED!"
else
  log "$FAIL_COUNT test case(s) FAILED out of $TOTAL_COUNT"
fi
log "========================================"

npx -y @midscene/android@1 disconnect 2>/dev/null || true
log "Test run completed at $(date)"
