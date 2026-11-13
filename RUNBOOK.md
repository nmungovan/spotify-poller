# Runbook: production deployment

The poller runs 24/7 on a free Oracle Cloud (OCI) VM. This file is how to operate it.
Real values (IP, key path, OCIDs) are deliberately **not** in this file; keep them in a password
manager. Placeholders look like `<this>`.

## What is running

| Piece | Where | Notes |
|---|---|---|
| VM | OCI, region US East (Ashburn), `VM.Standard.A1.Flex` (ARM/aarch64), Ubuntu 24.04 Minimal | Always Free; account upgraded to Pay As You Go to get capacity |
| App + Postgres | Docker Compose, project dir `~/spotify-poller` on the VM | `docker compose --profile app up -d --build` |
| Data | Named Docker volume `postgres_data` | Survives container recreation and reboots; deleted by `down -v` |
| Spotify refresh token | `spotify_auth` table in Postgres | Not in `.env`; obtained through `/login` (see below) |
| Secrets | `~/spotify-poller/.env` on the VM only | Never committed |
| Backups | `~/backups` on the VM (14 days) + OCI Object Storage bucket `spotify-poller-backups` | Nightly at 03:00 server time via cron |

The poller runs every 10 minutes (`poller.interval`, default `PT10M`) and logs
`Polled user=... fetched=N new=M`.

## Connect

```
ssh -i <path-to-private-key> ubuntu@<server-ip>
cd ~/spotify-poller
```

If SSH **times out**, check the OCI network path in this order: the instance has a public IP; the
subnet's route table has `0.0.0.0/0 -> Internet Gateway`; the subnet's security list allows TCP 22
ingress. (The VCN originally had no internet gateway and an empty route table.)

## Check health

```
docker compose ps                                  # both containers Up, postgres healthy
docker compose logs --tail 50 app
docker compose logs app | grep -E "Polled|Poll failed"
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*), max(played_at) from plays"'
```

- `Poll failed for user=... (ExceptionType)`: only the exception type is logged on purpose. A bad
  or revoked refresh token or wrong client credentials are the usual causes; see "Re-authorize".
- `max(played_at)` should be within the last poll interval or two of your latest listening.

## Deploy an update

```
cd ~/spotify-poller
git pull
docker compose --profile app up -d --build
docker compose logs -f app          # watch for "Started SpotifyPollerApplication"
```

Flyway runs pending migrations on startup. The data volume is untouched.

## Change `.env`

Edit `~/spotify-poller/.env`, then `docker compose --profile app up -d` to recreate the app.

- Avoid `$`, quotes and spaces in values (Compose interpolates `$`).
- **Postgres credentials only apply on first initialization of an empty volume.** Changing
  `POSTGRES_PASSWORD` in `.env` afterwards does not change the database. Either
  `ALTER USER ... PASSWORD '...'` inside Postgres and update `.env` to match, or accept losing the
  data (`down -v`) and restore from a backup.

## Re-authorize Spotify (new token needed)

The redirect URI is `http://127.0.0.1:8080/callback` (`application.properties`), so log in
through an SSH tunnel; no public port is needed.

1. Stop any local app using port 8080.
2. On your laptop: `ssh -L 8080:localhost:8080 -i <path-to-private-key> ubuntu@<server-ip>`
3. Open `http://127.0.0.1:8080/login` in a browser and approve in Spotify. It should say success.
4. Close the tunnel. Within 10 minutes the logs should show a `Polled` line.

## Backups

Script `~/backup.sh` on the VM: `pg_dump | gzip` to `~/backups/plays-<date>.sql.gz` (via a temp
file, so a failed dump never leaves a corrupt file), prune local files older than 14 days, then
upload to the bucket using the instance principal (no API keys on the box). Cron:

```
0 3 * * * /home/ubuntu/backup.sh >> /home/ubuntu/backups/backup.log 2>&1
```

Verify: `tail ~/backups/backup.log` says `backup ok`, and
`~/ocienv/bin/oci os object list --auth instance_principal -ns <namespace> -bn spotify-poller-backups --query 'data[].name'`
shows a recent object.

### Restore

Into an empty database (schema and data are both in the dump):

```
zcat ~/backups/plays-<date>.sql.gz | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

From the off-site copy, download first (works from any machine with OCI CLI credentials, or from
the VM):

```
oci os object get -ns <namespace> -bn spotify-poller-backups --name plays-<date>.sql.gz --file plays.sql.gz
```

If the target database already has tables (for example Flyway already ran on a fresh volume),
drop and recreate the database first, or restore into a scratch database and copy rows over.

### Importing play history from another database

`plays.id` is `GENERATED ALWAYS`, so do not copy the `id` column. Export every column except `id`,
load into a temp staging table, then `INSERT ... ON CONFLICT DO NOTHING` (the natural key
`(user_id, track_uri, played_at)` makes this idempotent; see `decisions/0001`). Only user ids that
exist in the target's `spotify_auth` can load (foreign key).

## Reboot and recovery

- `restart: unless-stopped` is set on both services, so a VM reboot brings everything back. Tested.
- Containers stopped by hand (`docker compose down`) stay stopped. `down -v` deletes the volume.
- **Never run `docker compose down -v` on the server** unless you intend to wipe the database.

## Rebuild from scratch (lost or replaced VM)

1. Create the instance: Ubuntu aarch64 image, `VM.Standard.A1.Flex`, public IPv4, in a **public
   subnet whose route table has a `0.0.0.0/0 -> Internet Gateway` rule**. "Out of capacity" errors
   for A1 are common; retry across availability domains, or upgrade to Pay As You Go.
2. Install Docker: `sudo apt update && sudo apt install -y curl ca-certificates`, then
   `curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu`; log out and in.
3. `git clone` the repo, create `.env` (see `.env.example`), run
   `docker compose --profile app up -d --build`.
4. **Update the IAM dynamic group `poller-instance`**: its rule matches the old instance OCID
   (`instance.id = '...'`). Put the new instance's OCID in it, or uploads will fail with
   `NotAuthorizedOrNotFound`. The existing policy (`Allow dynamic-group poller-instance to manage
   objects in tenancy where target.bucket.name = 'spotify-poller-backups'`) stays as is.
5. Install the OCI CLI for backups: `sudo apt install -y python3-venv`,
   `python3 -m venv ~/ocienv && ~/ocienv/bin/pip install oci-cli`; recreate `~/backup.sh` and the
   cron line above.
6. Restore the latest dump from the bucket (above), or re-authorize Spotify and let the poller
   backfill the last 50 plays.

## Gotchas

- Spotify's recently-played returns at most 50 plays. If the VM is down for long enough that you
  listen to more than 50 tracks, those plays are lost for good; that is why the interval is short.
- Postgres is published only as `127.0.0.1:<random port>`; it is not reachable from the network.
  Port 8080 is published on all interfaces but OCI's security list only opens 22.
- Always Free limits and Always Free terms change; recheck the OCI free-tier page occasionally and
  keep the $1 budget alert on.
