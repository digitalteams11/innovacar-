# Database Safety Runbook

## Before code or AI changes

1. Back up the database.
2. Commit or otherwise preserve the current code.
3. Confirm `spring.jpa.hibernate.ddl-auto` is `update` for development or
   `validate` for production.
4. Reject migrations containing `DROP TABLE`, `DROP SCHEMA`, `TRUNCATE`,
   unscoped `DELETE FROM`, or `ALTER TABLE ... DROP COLUMN`.
5. Confirm the datasource URL and active profile before starting the backend.

## PostgreSQL backup

PowerShell:

```powershell
$env:PGPASSWORD = "<password>"
.\server\scripts\backup-postgres.ps1 -Database "location-voiture" -Username "postgres"
```

Direct command:

```text
pg_dump -U USER -d DATABASE_NAME -Fc -f backup.dump
pg_restore --list backup.dump
```

## MySQL backup

```text
mysqldump -u USER -p DATABASE_NAME > backup.sql
```

## Restore rules

Never restore over the current database first. Create a separate recovery
database, restore there, compare row counts and tenant IDs, and only then plan a
transactional merge. Preserve the pre-restore backup until verification is
complete.

## Runtime policy

- Default local profile: `dev`, with `ddl-auto=update`.
- Production profile: `prod`, with `ddl-auto=validate`.
- `create`, `create-drop`, and in-memory H2 are blocked outside the `test`
  profile.
- Demo and super-admin bootstrap are disabled unless explicitly enabled with
  environment variables.
