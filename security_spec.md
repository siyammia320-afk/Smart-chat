# SMART CHAT Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Authentication Prerequisite**: Every read and write operation requires `request.auth != null`.
2. **Admin Identity**: A user is an Admin if and only if `(request.auth.token.email == 'siyammia320@gmail.com' && request.auth.token.email_verified == true)` or `/admins/$(request.auth.uid)` exists.
3. **Self-Role & Block Protection**: Standard users cannot create or update their `UserProfile` with `isBlocked == true` (to spoof state) or modify `isBlocked` at all on update, nor can they set `isAdminBadge == true` unless `isAdmin()` is true.
4. **Parent/Profile Gate on Messages & Calls**: A user cannot create a `ChatMessage` or `VoiceCallSession` unless their `/users/$(request.auth.uid)` profile document exists AND `isBlocked == false`.
5. **Query Enforcer**: All `list` queries on `/users`, `/messages`, and `/calls` must filter by `resource.data.groupId == 'smart_chat_main'`.
6. **Terminal State Locking**: Once a `VoiceCallSession` reaches `status == 'ended'`, no further updates are permitted by standard users (`existing().status != 'ended' || isAdmin()`).

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Scrape**: `unauthDb.collection("messages").where("groupId", "==", "smart_chat_main").get()` -> Reject (`request.auth != null`).
2. **Unfiltered List Query**: `aliceDb.collection("messages").get()` without `groupId` filter -> Reject (`resource.data.groupId == 'smart_chat_main'`).
3. **Identity Spoofing on Message Create**: Alice writes `/messages/msg_1` with `senderId: "bob_456"` -> Reject (`incoming().senderId == request.auth.uid`).
4. **Self-Admin Escalation**: Bob creates `/users/bob_456` with `isAdminBadge: true` -> Reject (`incoming().isAdminBadge == false || isAdmin()`).
5. **Email Spoofing Attack**: User with `email: "siyammia320@gmail.com"` but `email_verified: false` attempts to block Alice -> Reject (`request.auth.token.email_verified == true`).
6. **Blocked User Message Bypass**: Blocked user (`isBlocked: true`) attempts to create a message in `/messages/msg_2` -> Reject (`isNotBlocked()`).
7. **Self-Unblock Attempt**: Blocked user attempts to update `/users/bob_456` with `isBlocked: false` -> Reject (`affectedKeys()` & `incoming().isBlocked == existing().isBlocked`).
8. **Shadow Update / Ghost Field**: Alice updates `/users/alice_123` adding `isVerified: true` -> Reject (`affectedKeys().hasOnly(...)` and `keys().hasOnly(...)`).
9. **Value Poisoning**: Alice updates `/users/alice_123` with a 500-char `username` -> Reject (`isValidUserProfile(incoming())`).
10. **Immortal Field Mutation**: Alice updates `/messages/msg_1` changing `createdAt` or `senderId` -> Reject (`incoming().createdAt == existing().createdAt`).
11. **Terminal State Re-opening**: Bob updates an `'ended'` call back to `'connected'` -> Reject (`existing().status != 'ended'`).
12. **Cross-User Fresh Message Deletion**: Bob attempts to delete Alice's fresh message (< 24h old) -> Reject (`existing().senderId == request.auth.uid || isAdmin() || expired`).
