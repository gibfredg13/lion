# Phase 1 Backend Changes

## Changed Files
1. `backend/src/main/java/com/github/bgalek/levels/Level1.java` through `Level7.java` and `MerlinLevel.java`
   - Replaced "Merlin" / wizard persona prompts with "Leo" / guardian lion persona prompts.
   - Updated finished level response strings to match the Leo persona context.
   - Preserved all existing logic and order while updating string messages.

2. `backend/src/main/java/com/github/bgalek/MerlinConfiguration.java`
   - Added support for the new `dgxspark` LLM provider in the `llmProvider` bean factory method.
   - Created a new `@Bean` for `DgxSparkLlmProvider` to allow dependency injection for health checks.
   - Added `LevelGateService` to the `MerlinService` bean configuration.

3. `backend/src/main/java/com/github/bgalek/auth/AuthenticationController.java`
   - Added `accessCode` string to `RegisterRequest` record.
   - Passed `accessCode` to the `AuthenticationService.register` method.

4. `backend/src/main/java/com/github/bgalek/auth/AuthenticationService.java`
   - Injected `EventAccessCodeService`.
   - Validated access code during registration, throwing `AuthenticationException` if invalid.

5. `backend/src/main/java/com/github/bgalek/admin/AdminApiController.java`
   - Added endpoints for GET/PUT `/api/admin/access-code` to manage event registration codes.
   - Added GET `/api/admin/dgx-health` endpoint returning LLM health info.
   - Added GET `/api/admin/level-stats` for level statistics (currently mocked).
   - Added PUT `/api/admin/level/{level}/enabled` for enabling/disabling game levels via `LevelGateService`.
   - Added GET `/api/admin/active-users` utilizing `JdbcClient` to query user activity.
   - Added GET `/api/admin/token-stats` to return aggregate token usage.

6. `backend/src/main/resources/application.yml`
   - Added `merlin.event.accessCode` property.
   - Updated internal naming documentation from HackMerlin to Lions Den.

7. `backend/src/main/resources/application-prod.yml`
   - Updated `merlin.llm` section to use `dgxspark` provider by default.
   - Set up `EVENT_ACCESS_CODE` environment variable override.
   - Modified Postgres connection URL database name to `lionsden`.

8. `backend/src/main/java/com/github/bgalek/MerlinService.java`
   - Injected `LevelGateService` to conditionally disable interactions.
   - Updated `respond()` and `checkSecret()` to verify if the user's current level is enabled.

## Created Files
1. `backend/src/main/java/com/github/bgalek/llm/DgxSparkLlmProvider.java`
   - OpenAI compatible proxy integration for local DGX Spark.
   - Tracks response latency via circular buffer.
   - Returns real token counts in standard Spring Boot/HttpClient manner.

2. `backend/src/main/java/com/github/bgalek/auth/EventAccessCodeService.java`
   - Thread-safe volatile variable container for access codes.
   - Performs case-insensitive matching for user signups.

3. `backend/src/main/java/com/github/bgalek/admin/LevelGateService.java`
   - In-memory service managing enabled/disabled status of each level dynamically via the Admin endpoints.
