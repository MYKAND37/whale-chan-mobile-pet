package com.dsh.mobile

/**
 * Process-wide state the control panel reads on resume.
 *
 * The pet lives in an overlay window, so when it fails to appear there is
 * nothing on screen to inspect. The service records what happened here and
 * the activity renders it, which turns a silent blank overlay into something
 * the user can read back.
 */
object PetState {

    /** True while [PetService] holds a visible overlay. */
    @Volatile
    var running: Boolean = false

    /** Human-readable outcome of the last attach attempt, or null. */
    @Volatile
    var diagnostic: String? = null
}
