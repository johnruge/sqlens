MVN = mvn
JAR = target/sqlens-1.0-SNAPSHOT-jar-with-dependencies.jar
MAIN_CLASS = com.sqlens.App

.PHONY: build test clean run install

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
