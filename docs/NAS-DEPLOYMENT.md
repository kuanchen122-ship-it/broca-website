# Synology NAS Deployment

This document is a deployment checklist. Do not paste real passwords or tokens into it.

## 1. Prepare MariaDB 10

1. Create database `broca_website` with UTF-8/utf8mb4 encoding.
2. Create a dedicated database user such as `broca_app`.
3. Grant that user access only to `broca_website`.
4. Save the database password in the NAS service/container environment, not in this repository.
5. Back up MariaDB to a separate NAS backup destination on a schedule.

## 2. Build and run Spring Boot

1. Build with Java 17: `.\mvnw.cmd clean package`.
2. Copy the resulting JAR to the private `broca-web` application folder on the NAS.
3. Configure the variables listed in `.env.example` in Container Manager or the service environment.
4. Start with `SPRING_PROFILES_ACTIVE=mariadb`.
5. Keep port 8080 internal; public traffic should enter through HTTPS reverse proxy only.
6. Confirm `/`, `/login.html`, director login, teacher login, Excel preview/import, and database persistence after restart.

## 3. Configure Synology HTTPS

1. Keep Synology DDNS `broca.synology.me` active.
2. Create a reverse-proxy/Web Station portal for host `www.broca-english.com`.
3. Forward the portal internally to the Spring Boot service on port 8080.
4. Request a Let's Encrypt certificate for `www.broca-english.com` and assign it to the portal.
5. Forward router ports 80 and 443 to the NAS only when the DSM firewall and reverse proxy are ready.
6. Do not expose MariaDB port 3306 or Spring Boot port 8080 to the internet.

## 4. Configure Namecheap DNS

After the NAS portal is ready and reachable:

| Type | Host | Value | Purpose |
| --- | --- | --- | --- |
| CNAME | `www` | `broca.synology.me` | Send the website hostname to Synology DDNS |
| URL Redirect (301, unmasked) | `@` | `https://www.broca-english.com` | Redirect the bare domain to `www` |

DNS propagation can take time. Test both `https://www.broca-english.com` and `https://broca-english.com` before sharing the address.

## 5. Release check

- No database, Excel, cookies, logs, or `.env` are tracked by Git.
- Director and teacher accounts use different long passwords.
- MariaDB survives an application restart.
- HTTPS certificate is valid and HTTP redirects to HTTPS.
- Admin pages require login and teacher-only permissions are enforced.
- Public registration submissions persist and duplicate handling works.
- Backups can be restored, not merely created.

