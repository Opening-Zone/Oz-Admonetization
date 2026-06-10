.PHONY: help build clean publish publish-local bundle list-keys

# Default target showing help info
help:
	@echo "========================================================================"
	@echo " Opening Zone Mobile Ads - Build & Publication Makefile"
	@echo "========================================================================"
	@echo "Available commands:"
	@echo "  make build         - Clean and compile debug & release sources"
	@echo "  make publish       - Build, sign, bundle, and upload to Sonatype Central"
	@echo "  make publish-local - Publish library to Maven Local (~/.m2)"
	@echo "  make bundle        - Clean, sign, and build the ZIP upload bundle"
	@echo "  make clean         - Clear all build directories and local repo caches"
	@echo "  make list-keys     - List your generated GPG keys and IDs"
	@echo "========================================================================"

build:
	./gradlew clean compileDebugSources compileReleaseSources

clean:
	./gradlew clean

publish:
	./gradlew publish

publish-local:
	./gradlew publishToMavenLocal

bundle:
	./gradlew clean zipReleaseBundle

list-keys:
	/usr/local/bin/gpg --list-keys --keyid-format short
