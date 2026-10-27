#!/usr/bin/env bash

set -euo pipefail

# Remove sample code from the generated project
echo "Cleaning project sample code..."

# Ensure the script is run from a generated project root.
if [ ! -f "pom.xml" ]; then
    echo "Error: run this script from the generated project root."
    exit 1
fi

# Resolve the generated Java package path from the bootstrap class.
PACKAGE_PATH=$(find . -name "Bootstrap.java" | head -1 | sed 's|/Bootstrap.java||' | sed 's|./start/src/main/java/||')

if [ -z "$PACKAGE_PATH" ]; then
    echo "Error: unable to detect the generated Java package path."
    exit 1
fi

echo "Detected package path: $PACKAGE_PATH"

# =============================================================================
# API layer sample code
# =============================================================================
echo " clean API layer sample code..."
rm -f api/src/main/java/${PACKAGE_PATH}/api/dto/request/UserQueryRequest.java
rm -f api/src/main/java/${PACKAGE_PATH}/api/dto/request/UserCreateRequest.java
rm -f api/src/main/java/${PACKAGE_PATH}/api/dto/response/UserResponse.java
rm -f api/src/main/java/${PACKAGE_PATH}/api/facade/UserFacade.java

# =============================================================================
# Domain layer sample code
# =============================================================================
echo " clean Domain layer sample code..."
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/entity/User.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/model/UserStatus.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/repository/UserRepository.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/service/UserDomainService.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/service/impl/UserDomainServiceImpl.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/event/UserCreatedEvent.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/event/UserStatusChangedEvent.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/exception/UserAlreadyExistsException.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/exception/UserDomainException.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/exception/UserNotFoundException.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/factory/UserFactory.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/policy/PasswordPolicy.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/valueobject/Email.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/valueobject/PhoneNumber.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/valueobject/PasswordHash.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/valueobject/UserId.java
rm -f domain/src/main/java/${PACKAGE_PATH}/domain/valueobject/Username.java

# =============================================================================
# Application layer sample code
# =============================================================================
echo " clean Application layer sample code..."
rm -f application/src/main/java/${PACKAGE_PATH}/application/service/UserService.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/service/impl/UserServiceImpl.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/service/impl/UserCacheService.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/vo/UserVO.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/assembler/UserAssembler.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/config/DomainConfiguration.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/event/DomainEventPublisherImpl.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/event/listener/UserEventListener.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/port/out/UserNotificationPort.java
rm -f application/src/main/java/${PACKAGE_PATH}/application/operation/UseCaseOperation.java

# =============================================================================
# Infrastructure layer sample code
# =============================================================================
echo " clean Infrastructure layer sample code..."

# Persistence
rm -f infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/converter/UserPOConverter.java
rm -f infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/mysql/po/UserPO.java
rm -f infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/mysql/mapper/UserMapper.java
rm -f infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/repository/UserRepositoryImpl.java
rm -f infra/persistence/src/main/resources/mapper/UserMapper.xml

# Rest
rm -f infra/rest/src/main/java/${PACKAGE_PATH}/infra/rest/controller/UserController.java

# Facade
rm -f infra/facade/src/main/java/${PACKAGE_PATH}/infra/facade/UserFacadeImpl.java

# External
rm -f infra/external/src/main/java/${PACKAGE_PATH}/infra/external/LoggingUserNotificationAdapter.java

# Remove User-specific authorization rules while retaining the secure generic API boundary.
SECURITY_CONFIG="infra/rest/src/main/java/${PACKAGE_PATH}/infra/rest/config/SecurityConfig.java"
if [ -f "$SECURITY_CONFIG" ]; then
    SECURITY_CONFIG_TMP="${SECURITY_CONFIG}.tmp"
    awk '
        /^[[:space:]]*import org\.springframework\.http\.HttpMethod;[[:space:]]*$/ { next }
        /\.requestMatchers\(HttpMethod\.GET, "\/api\/v1\/users\/\*\*"\)/ {
            dropping_user_rules = 1
            next
        }
        dropping_user_rules && /\.hasAuthority\("users:delete"\)/ {
            dropping_user_rules = 0
            next
        }
        dropping_user_rules { next }
        { print }
    ' "$SECURITY_CONFIG" > "$SECURITY_CONFIG_TMP"
    if cmp -s "$SECURITY_CONFIG" "$SECURITY_CONFIG_TMP"; then
        rm -f "$SECURITY_CONFIG_TMP"
    else
        mv "$SECURITY_CONFIG_TMP" "$SECURITY_CONFIG"
    fi
fi

# Development identities remain available, but no sample-specific authorities are granted.
for CONFIG_FILE in conf/application-dev.yml conf/application-test.yml; do
    if [ -f "$CONFIG_FILE" ]; then
        CONFIG_FILE_TMP="${CONFIG_FILE}.tmp"
        awk '{
            gsub(/authorities: "users:read,users:write,users:delete"/, "authorities: \"\"")
            print
        }' "$CONFIG_FILE" > "$CONFIG_FILE_TMP"
        if cmp -s "$CONFIG_FILE" "$CONFIG_FILE_TMP"; then
            rm -f "$CONFIG_FILE_TMP"
        else
            mv "$CONFIG_FILE_TMP" "$CONFIG_FILE"
        fi
    fi
done

# =============================================================================
# database and configuration file (retain base configuration, delete sample data)
# =============================================================================
echo " clean database and sample configuration..."
rm -f infra/persistence/src/main/resources/db/migration/V1__create_user_table.sql
rm -f docs/api-reference.md

# Remove links and executable quick-start promises that only apply to the deleted User sample.
for README_FILE in README.md README.en.md; do
    if [ -f "$README_FILE" ]; then
        README_FILE_TMP="${README_FILE}.tmp"
        awk '
            /\(docs\/api-reference\.md\)/ { next }
            /^Call an authenticated development endpoint:/ { dropping_user_example = 1; next }
            /^调用需要认证的开发接口：/ { dropping_user_example = 1; next }
            dropping_user_example && /^In development, API documentation/ {
                dropping_user_example = 0
            }
            dropping_user_example && /^开发环境的 API 文档地址为/ {
                dropping_user_example = 0
            }
            dropping_user_example { next }
            { print }
        ' "$README_FILE" > "$README_FILE_TMP"
        if cmp -s "$README_FILE" "$README_FILE_TMP"; then
            rm -f "$README_FILE_TMP"
        else
            mv "$README_FILE_TMP" "$README_FILE"
        fi
    fi
done

if [ -f llms.txt ]; then
    LLMS_FILE_TMP="llms.txt.tmp"
    awk '
        /\(docs\/api-reference\.md\)/ { next }
        /^- `User/ { next }
        /`V1__create_user_table\.sql`/ { next }
        /`UserStatus\.DELETED`/ { next }
        { print }
    ' llms.txt > "$LLMS_FILE_TMP"
    if cmp -s llms.txt "$LLMS_FILE_TMP"; then
        rm -f "$LLMS_FILE_TMP"
    else
        mv "$LLMS_FILE_TMP" llms.txt
    fi
fi

# =============================================================================
# test code
# =============================================================================
echo " clean test code..."
rm -f domain/src/test/java/${PACKAGE_PATH}/domain/entity/UserTest.java
rm -f domain/src/test/java/${PACKAGE_PATH}/domain/policy/PasswordPolicyTest.java
rm -f domain/src/test/java/${PACKAGE_PATH}/domain/package-info.java
rm -f application/src/test/java/${PACKAGE_PATH}/application/service/impl/UserCacheServiceTest.java
rm -f application/src/test/java/${PACKAGE_PATH}/application/service/impl/UserServiceImplTest.java
rm -f infra/persistence/src/test/java/${PACKAGE_PATH}/infra/persistence/converter/UserPOConverterTest.java
rm -f infra/persistence/src/test/java/${PACKAGE_PATH}/infra/persistence/repository/UserRepositoryImplTest.java
rm -f infra/rest/src/test/java/${PACKAGE_PATH}/infra/rest/advice/RestExceptionAdviceTest.java
rm -f start/src/test/java/${PACKAGE_PATH}/UserControllerIntegrationTest.java
rm -f start/src/test/java/${PACKAGE_PATH}/PersistenceIntegrationTest.java

# =============================================================================
# clean directory (but retain important of directory structure)
# =============================================================================
echo " clean directory..."

# define need retain of important directory
KEEP_DIRS=(
    "api/src/main/java/${PACKAGE_PATH}/api/dto/request"
    "api/src/main/java/${PACKAGE_PATH}/api/dto/response"
    "api/src/main/java/${PACKAGE_PATH}/api/context"
    "api/src/main/java/${PACKAGE_PATH}/api/facade"
    "domain/src/main/java/${PACKAGE_PATH}/domain/entity"
    "domain/src/main/java/${PACKAGE_PATH}/domain/repository"
    "domain/src/main/java/${PACKAGE_PATH}/domain/service"
    "domain/src/main/java/${PACKAGE_PATH}/domain/service/impl"
    "domain/src/main/java/${PACKAGE_PATH}/domain/valueobject"
    "domain/src/main/java/${PACKAGE_PATH}/domain/event"
    "domain/src/main/java/${PACKAGE_PATH}/domain/factory"
    "domain/src/main/java/${PACKAGE_PATH}/domain/policy"
    "application/src/main/java/${PACKAGE_PATH}/application/service"
    "application/src/main/java/${PACKAGE_PATH}/application/service/impl"
    "application/src/main/java/${PACKAGE_PATH}/application/vo"
    "application/src/main/java/${PACKAGE_PATH}/application/assembler"
    "infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/mysql/po"
    "infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/mysql/mapper"
    "infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/repository"
    "infra/persistence/src/main/resources/mapper"
    "infra/rest/src/main/java/${PACKAGE_PATH}/infra/rest/controller"
    "infra/facade/src/main/java/${PACKAGE_PATH}/infra/facade"
    "infra/external/src/main/java/${PACKAGE_PATH}/infra/external"
)

# new create important directory
for dir in "${KEEP_DIRS[@]}"; do
    mkdir -p "$dir"
done

# in important directory in create.gitkeep file
for dir in "${KEEP_DIRS[@]}"; do
    if [ -d "$dir" ] && [ -z "$(ls -A "$dir")" ]; then
        touch "$dir/.gitkeep"
    fi
done

# clean directory
find . -type d -empty -not -path "./.git/*" -delete 2>/dev/null || true

# =============================================================================
# create base sample file (optional)
# =============================================================================
echo " create base sample file..."

# create base of package-info.java file
cat > "api/src/main/java/${PACKAGE_PATH}/api/dto/request/package-info.java" << EOF
/**
 * request DTO package
 */
package ${PACKAGE_PATH//\//.}.api.dto.request;
EOF

cat > "api/src/main/java/${PACKAGE_PATH}/api/dto/response/package-info.java" << EOF
/**
 * response DTO package
 */
package ${PACKAGE_PATH//\//.}.api.dto.response;
EOF

cat > "domain/src/main/java/${PACKAGE_PATH}/domain/entity/package-info.java" << EOF
/**
 * domain entity package
 */
package ${PACKAGE_PATH//\//.}.domain.entity;
EOF

cat > "domain/src/main/java/${PACKAGE_PATH}/domain/repository/package-info.java" << EOF
/**
 * domain repository ports
 */
package ${PACKAGE_PATH//\//.}.domain.repository;
EOF

cat > "application/src/main/java/${PACKAGE_PATH}/application/service/package-info.java" << EOF
/**
 * application service package
 */
package ${PACKAGE_PATH//\//.}.application.service;
EOF

cat > "application/src/main/java/${PACKAGE_PATH}/application/assembler/package-info.java" << EOF
/**
 * boundary assemblers
 */
package ${PACKAGE_PATH//\//.}.application.assembler;
EOF

cat > "application/src/main/java/${PACKAGE_PATH}/application/event/listener/package-info.java" << EOF
/**
 * application event listeners
 */
package ${PACKAGE_PATH//\//.}.application.event.listener;
EOF

cat > "infra/persistence/src/main/java/${PACKAGE_PATH}/infra/persistence/repository/package-info.java" << EOF
/**
 * persistence repository adapters
 */
package ${PACKAGE_PATH//\//.}.infra.persistence.repository;
EOF

cat > "infra/rest/src/main/java/${PACKAGE_PATH}/infra/rest/controller/package-info.java" << EOF
/**
 * REST controller package
 */
package ${PACKAGE_PATH//\//.}.infra.rest.controller;
EOF

echo ""
echo "Project sample code cleanup complete!"
echo ""
echo "Removed:"
echo "1. Executable User-management sample code and authorization rules"
echo "2. User-management tests"
echo "3. The sample user-table migration"
echo ""
echo "Retained:"
echo "1. Architecture building blocks and application ports"
echo "2. Environment configuration templates"
echo "3. MyBatis, Redis, security, and observability configuration"
echo "4. Module structure and package documentation"
echo "5. The written guides, which may still use User as an architecture example"
echo ""
echo "The executable scaffold is clean and ready for new development."
