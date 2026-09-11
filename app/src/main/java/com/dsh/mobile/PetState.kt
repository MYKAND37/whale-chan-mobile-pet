package com.dsh.mobile

/**
 * Process-wide flag telling the control panel whether the pet is attached.
 *
 * A plain object is enough here: the service and the activity always live in
 * the same process, and the activity re-reads this on every resume.
 */
object PetState {
    /** True while [PetService] holds a visible overlay. */
    @Volatile
    var running: Boolean = false
}
