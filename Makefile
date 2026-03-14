MVN = mvn
JAR = target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar
MAIN_CLASS = com.sqlens.App

include .env
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
	docker compose exec mysql mysql -u $(MYSQL_USER) -p$(MYSQL_PASSWORD) $(MYSQL_DATABASE)
