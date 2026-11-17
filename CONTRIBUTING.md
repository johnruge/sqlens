# Contributing to sqlens

Thanks for helping! Bug reports, new analyzer rules and fixes are all welcome.

## Getting set up

1. Java 21 and Maven 3.9+ (Docker is optional; it is needed for integration tests).
2. Fork and clone the repository, then run the unit tests:

   ```bash
   make test
   ```

3. With Docker running, also run the integration tests against a real MySQL:

   ```bash
   make verify
   ```

See the README for running sqlens against the Sakila sample database.

## Making a change

- Open an issue first for anything larger than a small fix, so we can agree on the approach.
- Keep pull requests focused on one change, and add or update tests for it.
  New analyzer rules go in `PlanAnalyzer` with a test in `PlanAnalyzerTest`.
- `make test` (and ideally `make verify`) must pass.

## Security rules for contributions

sqlens must only ever **read** from a database. Pull requests must keep it that way:

- User-typed SQL only reaches the database through `QueryValidator`; never bypass it.
- Every other query uses `PreparedStatement` with `?` placeholders. No string-building
  SQL from input.
- No credentials, API keys or `.env` files in commits. Configuration comes from
  environment variables.
- Text from the database or the AI service is passed through `TerminalText.clean`
  before printing.

Found a vulnerability? Please follow [SECURITY.md](SECURITY.md) instead of opening an issue.

## License

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE).
