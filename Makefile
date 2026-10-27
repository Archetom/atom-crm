MVN ?= sh ./mvnw
VERSION ?= 1.0.0

.PHONY: default clean compile test integration-test verify install package release-verify run infra-up infra-down clean-sample version

default: verify

clean:
	@$(MVN) clean

compile:
	@$(MVN) compile

test:
	@CI=false $(MVN) test

integration-test:
	@CI=true $(MVN) test

verify:
	@CI=false $(MVN) verify

install:
	@CI=false $(MVN) clean install

package:
	@CI=false $(MVN) clean package

release-verify:
	@EXPECTED_VERSION=$(VERSION) sh scripts/release-r1-verify.sh

run:
	@$(MVN) -f start/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev

infra-up:
	@docker compose up -d mysql redis

infra-down:
	@docker compose down

clean-sample:
	@bash clean.sh

version:
	@$(MVN) versions:set -DgenerateBackupPoms=false -DnewVersion=$(VERSION)
