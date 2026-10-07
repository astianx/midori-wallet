package org.astianx.midori.one.chat

/** Matches the inspected ejabberd MUC ceiling; the future MLS target stays disabled.
 * Further increases require server capacity and encrypted delivery acceptance, not just a constant.
 */
internal object GroupPolicy {
    const val MAX_ENCRYPTED_MEMBERS = 200
    const val MAX_ENCRYPTED_DEVICES = MAX_ENCRYPTED_MEMBERS * 5
    const val LARGE_GROUP_TARGET = 200_000
    fun canCreate(invitees: Set<String>, self: String) =
        invitees.isNotEmpty() && self !in invitees && invitees.size < MAX_ENCRYPTED_MEMBERS
    fun canInvite(memberCount: Int) = memberCount < MAX_ENCRYPTED_MEMBERS
}
