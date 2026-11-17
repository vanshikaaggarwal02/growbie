# Deploying Growbie to the GoDaddy VPS

The stack is three containers: the database, the backend and a web server (Caddy) that serves the site, forwards `/api` to the backend and gets the HTTPS certificate by itself. Only ports 80 and 443 are open to the internet.

This was smoke-tested on a fresh database: all migrations apply, the site and deep links load, and the API works through the web server.

## 1. Point the domain at the server (GoDaddy DNS)

In GoDaddy, open **growbie.in → DNS**. Add:

| Type | Name | Value |
|---|---|---|
| A | `@` | the VPS's public IP address |
| A | `www` | the VPS's public IP address |

Leave the email records (MX, SPF, DKIM) exactly as they are, otherwise support@growbie.in stops working. DNS changes can take a few minutes to an hour.

## 2. Prepare the server

Log in over SSH, then:

```bash
# Docker (skip if already installed)
curl -fsSL https://get.docker.com | sh

# Firewall: only SSH and the web
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable

# Can the server send mail? This should say "succeeded"
nc -vz smtpout.secureserver.net 465
```

If the mail check fails, ask GoDaddy to open outbound port 465, or the reset and contact emails cannot be sent.

## 3. Get the code onto the server

```bash
git clone <your repository address> /srv/growbie
cd /srv/growbie
```

## 4. Create the production settings

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
nano .env.prod
```

Fill in every value. Use a long random `DATABASE_PASSWORD` and a random `JWT_SECRET` of at least 32 characters:

```bash
openssl rand -base64 32     # run twice, one for each
```

Never commit `.env.prod`; it is already git-ignored.

## 5. Start it

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

The first build takes a few minutes. Then open `https://growbie.in`. The certificate is requested automatically once DNS points at the server.

Check on it:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

## 6. Back up the database

```bash
./scripts/backup-db.sh                       # saves backups/growbie-<date>.sql.gz
crontab -e                                   # to run it daily at 03:00, add:
0 3 * * * /srv/growbie/scripts/backup-db.sh
```

Copy backups off the server now and then.

## 7. Updating later

```bash
cd /srv/growbie && git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Database changes apply automatically on start.

## Before you demo

- Register an account on the live site, run one audit, and check the reset email arrives.
- Send a message through the contact form and check it reaches support@growbie.in.
- Watch the Anthropic credit balance: every audit, plan, studio and assessment run spends some.
