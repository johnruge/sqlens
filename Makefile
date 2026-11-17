MVN = mvn
JAR = target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar
MAIN_CLASS = com.sqlens.App

# .env is optional (CI has none); values are exported to sqlens and docker compose.
-include .env
export

.PHONY: build test verify package clean run install db-up db-down db-logs db-cli

build:
	$(MVN) -q compile

# unit tests only
test:
	$(MVN) test

# unit + integration tests (integration tests need Docker)
verify:
	$(MVN) verify

package:
	$(MVN) -q package -DskipTests

clean:
	$(MVN) clean

install:
	$(MVN) install

# rebuilds the jar, then starts sqlens; pass options with ARGS="..."
run: package
	java -jar $(JAR) $(ARGS)

db-up:
	docker compose up -d

db-down:
	docker compose down

db-logs:
	docker compose logs -f mysql

db-cli:
	MYSQL_PWD="$$MYSQL_PASSWORD" docker compose exec -e MYSQL_PWD mysql mysql -u $(MYSQL_USER) $(MYSQL_DATABASE)
