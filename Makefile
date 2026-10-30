.PHONY: docs-check secret-scan test
docs-check:
	python3 tools/checks/check_docs.py
secret-scan:
	@command -v gitleaks >/dev/null || { echo "install gitleaks: brew install gitleaks"; exit 1; }
	gitleaks git --redact -v
test:
	./gradlew allTests
