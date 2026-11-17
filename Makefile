MVN = mvn
JAR = target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar
MAIN_CLASS = com.sqlens.App

-include .env
export

.PHONY: build test clean run install db-up db-down db-logs db-cli

build:
	$(MVN) compile

test:
	$(MVN) test

package: $(JAR)

$(JAR):
	$(MVN) package

clean:
	$(MVN) clean

install:
	$(MVN) install

run: $(JAR)
	java -jar $(JAR) $(ARGS)

db-up:
	docker compose up -d

db-down:
	docker compose down

db-logs:
	docker compose logs -f mysql

db-cli:
	MYSQL_PWD="$$MYSQL_PASSWORD" docker compose exec -e MYSQL_PWD mysql mysql -u $(MYSQL_USER) $(MYSQL_DATABASE)
