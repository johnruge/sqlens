# Security policy

sqlens runs SQL typed by its user against a database, so security bugs matter here,
especially anything that lets a query write data, run more than one statement, read
local files, or leak credentials or API keys.

## Reporting a vulnerability

Please **do not open a public issue**. Report it privately through GitHub instead:
**Security → Report a vulnerability** on this repository
(https://github.com/johnruge/sqlens/security/advisories/new).

Include the sqlens version or commit, your MySQL version, and steps or a query that
reproduces the problem. You should get a reply within a week.

## Supported versions

Only the latest commit on `main` gets security fixes.
