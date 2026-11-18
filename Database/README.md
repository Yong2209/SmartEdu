# SmartEdu - SQL Server Database

## Database files

- `SmartEdu_SQLServer_SETUP_RETRY.sql`: SQL Server setup/schema reference.
  The script contains 44 `dbo` table definitions and is intended for the existing
  database setup workflow. It creates `SmartEdu_Connect_Retry` only if that
  database is absent.
- `SmartEdu_SQLServer_DEMO_SEED.sql`: demo seed data reference.

## Rules

- Do not execute the setup script on an existing database or repeatedly.
- Do not modify the 44-table database structure for backend scaffolding.
- Backend development must use these SQL files as the authoritative source for
  table names, columns, constraints, and foreign-key relationships.
- Hibernate must use `spring.jpa.hibernate.ddl-auto=none`.
- Do not add passwords, backups, or production data to this repository.

## Verification

On a connected SQL Server database, verify the table count with:

```sql
USE [SmartEdu_Connect_Retry];

SELECT COUNT(*) AS total_tables
FROM sys.tables
WHERE SCHEMA_NAME(schema_id) = N'dbo';
```

The result for the complete schema is 44.
