# Security Policy

## Never commit operational data

This repository must not contain student names or identifiers, parent contact details, LINE user IDs, attendance, leave records, salary records, imported spreadsheets, database files, production logs, browser cookies, access tokens, or passwords.

Keep production data in the NAS MariaDB instance and keep credentials in Synology service/container environment variables. GitHub Actions secrets may hold deployment credentials when automated deployment is introduced, but must never be used as a student-information database.

If a secret is committed accidentally, remove it from Git history and rotate it immediately. Deleting only the latest file is not sufficient.

