#!/usr/bin/env bash
# Dumps the production database to a dated, compressed file. Run from the project folder on the server,
# for example daily from cron:  0 3 * * * /srv/growbie/scripts/backup-db.sh
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env.prod; set +a
mkdir -p backups
FILE="backups/growbie-$(date +%Y%m%d-%H%M).sql.gz"
docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U "$DATABASE_USERNAME" launchmate | gzip > "$FILE"
echo "Saved $FILE"
# Keep the last 14 backups.
ls -1t backups/growbie-*.sql.gz | tail -n +15 | xargs -r rm --
