.PHONY: app app-dev dev lab lab-down lab-deploy docs-check secret-scan test catalog-build catalog-validate test-tools
docs-check:
	python3 tools/checks/check_docs.py
secret-scan:
	@command -v gitleaks >/dev/null || { echo "install gitleaks: brew install gitleaks"; exit 1; }
	gitleaks git --redact -v
test:
	./gradlew jvmTest
export JAVA_HOME ?= /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
catalog-build:
	python3 tools/catalog/build_catalog.py
catalog-validate:
	python3 tools/catalog/validate_catalog.py
test-tools:
	python3 tools/checks/tests/test_check_docs.py
	python3 tools/catalog/tests/test_catalog.py
app:
	./scripts/app.sh
app-dev:
	./scripts/app.sh dev
dev:
	FARE_PROVIDER=sample ./gradlew :server:run
lab:
	@echo "fake provider :8090 + server :8080 (see docs/LOCAL_LAB.md)"
	@./scripts/lab.sh up
lab-down:
	@./scripts/lab.sh down
lab-deploy:
	podman machine start 2>/dev/null || true
	podman compose up --build
