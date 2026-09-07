# Security policy

## Reporting

Do not open a public issue containing credentials, personal information or an exploitable security defect. Report it privately to the repository owner.

## Secrets

Never commit Firebase service accounts, Cloudflare tokens, signing keys, admin tokens, `.dev.vars`, `google-services.json` or local configuration. Store deployment secrets in GitHub Environments and Cloudflare secrets.

The current shared organiser PIN is suitable only for controlled testing. Production must use short-lived authenticated sessions with organiser, scorer, team-admin and viewer roles.

## Personal data

Player mobile numbers and photos are personal data. Production access must be authenticated, logged and limited to the minimum required fields. Provide retention and deletion controls before public registration.
