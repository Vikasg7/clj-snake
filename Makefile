.DEFAULT_GOAL := run

ifeq ($(OS),Windows_NT)
  # Windows system
	LEIN=lein.bat
else
  # Assuming Linux
	LEIN=lein
endif

install:
	$(LEIN) install

build:
	$(LEIN) uberjar

run:
	java -jar target/uberjar/clj-snake-0.1.0-SNAPSHOT-standalone.jar

clean:
	$(LEIN) clean

test:
	$(LEIN) test
