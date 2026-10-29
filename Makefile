MVN ?= sh ./mvnw
VERSION ?= 1.0.0
SCOPE ?= full

.PHONY: default clean compile test integration-test verify install package release-verify run infra-up infra-down version agent-check

default: verify

clean:
	@$(MVN) clean

compile:
	@$(MVN) compile

test:
	@$(MVN) test -Datom.test.skip-database-tests=true

integration-test:
	@CI=true $(MVN) test

verify:
	@CI=false $(MVN) verify

install:
	@CI=false $(MVN) clean install

package:
	@CI=false $(MVN) clean package

release-verify:
	@EXPECTED_VERSION=$(VERSION) sh scripts/release-verify.sh

run:
	@$(MVN) -pl start -am spring-boot:run -Dspring-boot.run.profiles=dev

infra-up:
	@docker compose up -d mysql redis

infra-down:
	@docker compose down

version:
	@$(MVN) versions:set -DgenerateBackupPoms=false -DnewVersion=$(VERSION)

agent-check:
	@bash scripts/agent-check.sh "$(SCOPE)"
