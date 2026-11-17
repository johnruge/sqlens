# sqlens

A command-line tool that looks at a MySQL query's execution plan and tells you, in
plain words, why it is slow and what to do about it.

```
sqlens> SELECT * FROM film f JOIN film_actor fa ON fa.film_id = f.film_id
     -> WHERE f.length > 120 ORDER BY f.title;
+----+-------------+-------+------+----------------+----------------+---------+-----------+------+----------+-----------------------------+
| id | select_type | table | type | possible_keys  | key            | key_len | ref       | rows | filtered | Extra                       |
+----+-------------+-------+------+----------------+----------------+---------+-----------+------+----------+-----------------------------+
| 1  | SIMPLE      | f     | ALL  | PRIMARY        | NULL           | NULL    | NULL      | 1000 | 33.33    | Using where; Using filesort |
| 1  | SIMPLE      | fa    | ref  | idx_fk_film_id | idx_fk_film_id | 2       | sakila... | 5    | 100.0    | Using index                 |
+----+-------------+-------+------+----------------+----------------+---------+-----------+------+----------+-----------------------------+

Findings
  HIGH   [f] Full table scan of ~1000 rows even though index(es) PRIMARY could be used.
         -> ...rewrite the condition so the indexed column stands alone...
  MEDIUM [f] Rows have to be sorted after reading (filesort).
         -> Add an index whose columns match the ORDER BY...
```

With a Gemini API key set, sqlens also sends the query, plan and schema (never table
data) to the model and prints concrete `CREATE INDEX` / rewrite suggestions.

## Features

- **Plan table**: `EXPLAIN` output, formatted like the mysql client.
- **Rule-based analyzer** (offline, deterministic): full table and index scans,
  ignored indexes, filesort, temporary tables, joins without an index, correlated
  subqueries, poor filter selectivity and huge join products.
- **Schema lookup**: `\tables`, `\d <table>` (columns and indexes).
- **Tree plans**: `\tree <query>` shows `EXPLAIN FORMAT=TREE` with costs.
- **AI suggestions** (optional): Gemini, turned on by `GEMINI_API_KEY`.
- **Scriptable**: `-e "<query>"` or `-f queries.sql`, plus `--fail-on HIGH` to fail a
  CI job when a query regresses.
- Multi-line input, history, Ctrl-C to discard and Ctrl-D to quit, coloured output
  (`--no-color` / `NO_COLOR` to turn it off).

## Requirements

- Java 21 and Maven 3.9+
- Docker, for the bundled sample database and the integration tests
- A MySQL 8.0.16+ server (8.0.16 is needed for `\tree`)

## Quick start

```bash
cp .env.example .env          # then edit: set real passwords
```

Download the [Sakila sample database](https://dev.mysql.com/doc/index-other.html) and
unpack it into `db/sakila-db/` (git-ignored). Then:

```bash
make db-up                    # MySQL on 127.0.0.1:${MYSQL_PORT}, loads Sakila, creates read-only user
make run                      # builds, then prompts for user and password
```

Log in as the read-only user from `.env` (`SQLENS_RO_USER`). Other ways to run it:

```bash
java -jar target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar -u sqlens_ro -d sakila
java -jar target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar -e "SELECT * FROM film WHERE length > 60"
java -jar target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar -f slow-queries.sql --fail-on HIGH --no-ai
```

### Options and environment

| Option | Env var | Default |
|---|---|---|
| `-H, --host` | `MYSQL_HOST` | `127.0.0.1` |
| `-P, --port` | `MYSQL_PORT` | `3306` |
| `-d, --database` | `MYSQL_DATABASE` | `sakila` |
| `-u, --user` | `SQLENS_USER` | prompted |
| (password) | `SQLENS_PASSWORD` | prompted, hidden |
| `--url jdbc:mysql://host[:port]/db` | | |
| `--no-ai` | `GEMINI_API_KEY`, `GEMINI_MODEL` | AI off without a key; model `gemini-2.5-flash` |

Run `sqlens --help` for the full list and exit codes.

### REPL commands

| Command | |
|---|---|
| `<SELECT ...>;` | explain a query (end with `;`, can span lines) |
| `\tables`, `\t` | list tables |
| `\d <table>` | columns and indexes |
| `\tree <query>` | tree plan with costs |
| `\ai on\|off` | toggle AI suggestions |
| `help`, `exit` | |

## Architecture

```
App (picocli: options, credentials, one-shot vs REPL)
 ├─ database/ConnectionConfig   validates host/port/db, builds the JDBC URL
 ├─ database/DBConnection       hardened, read-only session with timeouts
 ├─ cli/Repl                    interactive loop and \commands
 ├─ cli/QueryRunner             explain -> analyze -> (AI) -> print
 │   ├─ services/QueryValidator only single read-only SELECT/WITH statements
 │   ├─ services/SQLService     EXPLAIN and EXPLAIN FORMAT=TREE
 │   ├─ services/SchemaService  information_schema via PreparedStatement
 │   ├─ analysis/PlanAnalyzer   rule-based findings
 │   └─ services/LLMService     Gemini HTTP client + PromptBuilder
 └─ cli/TableFormatter, FindingsFormatter, Style
```

## Security model

sqlens runs user-typed SQL, so several layers make sure it can only ever read:

1. **Validation.** `QueryValidator` accepts exactly one `SELECT` / `WITH` statement.
   It understands string literals, quoted identifiers and comments, and rejects
   stacked statements, `/*! */` executable comments, `INTO OUTFILE`, `ANALYZE`
   (which would actually run the query) and DML/DDL. The query has to be validated
   instead of bound as a `PreparedStatement` parameter, because placeholders can only
   stand for values, never for a whole statement. Every other query (schema lookups)
   uses `PreparedStatement`.
2. **Read-only session** with `max_execution_time`, statement, socket and connect
   timeouts. `allowMultiQueries`, `LOAD DATA LOCAL` and `autoDeserialize` are disabled.
3. **Least-privilege user.** `docker/initdb/03-readonly-user.sh` creates a user with
   only `SELECT, SHOW VIEW`. Don't connect as root.
4. **Connection targets are validated.** Nobody can inject extra JDBC URL parameters
   through `--host`, `--database` or `--url`.
5. **The database is only on localhost.** Docker publishes MySQL on `127.0.0.1` only.
6. **Secrets live only in environment variables.** `.env` is git-ignored, the API key
   travels in a request header (never the URL) and is never printed. Passwords are
   read without echo and are never written to `~/.sqlens_history`, which is created
   with mode 0600.
7. **AI output is sanitized.** Terminal escape sequences are stripped before printing.

**Privacy:** with AI on, the query text (including any literal values in it) and the
table schemas are sent to Google. Use `--no-ai` or `\ai off` for sensitive queries.

## Development

```bash
make test       # unit tests
make verify     # unit + integration tests (Testcontainers starts MySQL; skipped without Docker)
```

A GitHub Actions workflow that runs `mvn verify` is included. It only runs when started
by hand (`gh workflow run CI`), not on every push.
