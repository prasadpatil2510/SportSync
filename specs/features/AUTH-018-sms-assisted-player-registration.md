# AUTH-018 — SMS-assisted player registration

Status: draft

## Intent

Guide an unknown mobile number into the auction-style player profile form and allow a trusted super-user device to complete registration from a narrowly formatted proof message.

## Scope

- Mobile-first sign-in and registration decision.
- Name, mandatory mobile number, photo and cricket role fields.
- A pending, expiring, one-use registration challenge.
- Launch the user's SMS app with a prefilled destination and signed challenge message.
- A super-user workflow above tournament admin for approving a verified challenge.

## Rules and invariants

- No organiser phone number is committed to source code or changed without explicit owner approval.
- The user must press Send in their chosen SMS app; SportSync does not silently send SMS.
- A challenge is single use, expires quickly, and is bound to the normalized mobile number.
- A received message alone must not create an elevated account.
- Streamlined testing must not weaken production authentication.
- Production must not request broad SMS read/receive permissions unless the app qualifies under store policy.

## Failure behavior

- Invalid/registered numbers produce clear, non-enumerating responses.
- Replayed, expired or mismatched challenges are rejected.
- If SMS cannot be verified, the pending registration stays incomplete and offers a retry.

## Acceptance criteria

- [ ] An unknown valid mobile number opens the full player profile form.
- [ ] Submitting the form creates a pending challenge and opens an explicit SMS compose screen.
- [ ] A verified, non-replayed challenge creates one player account with no manual organiser approval.
- [ ] A super user can see registration status without seeing unrelated SMS messages.
- [ ] Production contains no hard-coded personal phone number or restricted broad SMS permissions.

## Compatibility

Requires a forward-only migration for normalized mobile identities, pending challenges and a super-user role. The exact receiver mechanism is blocked pending the approved organiser number and a distribution decision (sideload-only testing versus Play-compatible production).

## Decisions

- 2026-09-23: SMS compose must use an Android intent so the player knowingly sends the message.
- 2026-09-23: Broad automatic inbox reading is not accepted for production because account authentication is not an eligible Google Play SMS-permission use case.
