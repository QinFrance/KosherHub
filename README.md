# KosherHub

An MIT-licensed, English-first home for kosher phones, custom ROMs, useful sites, and community knowledge.

- **Home:** featured selections from the four directories.
- **Phones, ROMs, Sites, Blog:** searchable pages and resource details.
- **Submit:** anonymous suggestions for phones, ROMs, sites, articles, topics, and categories.
- **Studio:** protected content management and a private moderation inbox.

## Community submissions

Visitors can submit a title, description, source link, and optional contact email without an account. Submissions are rate limited and held in a private review queue. Approval creates an editable **draft**, never an automatic public listing. The contact email stays in the private submission record. Public uploads are disabled; ROM contributions use source links. Administrators can upload files up to 50 MB through Studio; larger files can use external URLs.

## Backend and authentication

Vinext on Cloudflare Workers, Cloudflare D1, and R2. Schema and append-only migrations are in `db/` and `drizzle/`. Email sign-in uses an 8-digit, single-use code valid for 10 minutes with at most five attempts. Sessions expire after eight hours, are stored as hashes, and use Secure, HttpOnly, SameSite=Strict cookies. Login codes are hashed with the server secret. Resend credentials saved through owner settings are encrypted with AES-GCM.

Set `ADMIN_EMAIL` and a cryptographically random `AUTH_SECRET` as runtime secrets **before allowing public traffic**. All administration routes fail closed without an authorized owner or valid email session. The trusted ChatGPT owner identity remains available for initial email setup and recovery; it is checked against `ADMIN_EMAIL` or the optional `ADMIN_USER_ID`, not simply any signed-in account.

Connect Resend using `/studio/settings` while signed in as the owner. Alternatively set `RESEND_API_KEY` and `EMAIL_FROM` as server secrets. Use a verified sender, or follow Resend's test-sender restrictions. The interface does not claim to send a code when no service is configured. Never commit credentials.

- Public proposal page: `/submit`
- Email login: `/studio/login`
- Administration: `/studio`
- Owner email setup: `/studio/settings`

## Development

Requires Node >=22.13.0. Run `npm run install:ci`, then `npm run dev`. The local loopback sign-in uses the starter's `seedy@sites.test` test identity and is absent from production builds. Put local-only values in ignored `.dev.vars` (see `.env.example`).

Generate schema migrations with `npm run db:generate`. Build with `npm run build`; apply pending migrations to the local database before previewing. Hosted and local data are separate. Configure Sites with `d1: "DB"` and `r2: "BUCKET"`; Sites provisions storage and applies migrations during deployment. New deployments begin private; public submission access requires a public audience.

`node scripts/export-source.mjs` generates the downloadable source archive and sanitized GitHub source tree. Runtime files, uploads, credentials, project identity, and screenshots are excluded. The repository includes no ROM binaries or user submissions.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Phone records are community references, not kosher certifications or compatibility guarantees. Device drawings are illustrative concepts. Links to manufacturers, sellers, and communities imply no affiliation or endorsement.
