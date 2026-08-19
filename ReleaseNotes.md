<img align="right" width="250" height="47" src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" /> <br />     

# Release Notes npm-proxy

## Release 1.9.2 (2026-08)

### changed

- Extended Project with OSPO resources

## Release 1.9.1 (2026-08)

### changed

- Updated dependencies

## Release 1.9.0 (2026-06)

### changed

- Changed the validity check for the missing annotations of the download conditions of packages

## Release 1.8.4 (2026-06)

### changed

- Updated dependencies

## Release 1.8.3 (2026-06)

### changed

- Updated dependencies

## Release 1.8.2 (2026-05)

### changed

- Updated dependencies

## Release 1.8.1 (2026-04)

### changed

- Updated dependencies

## Release 1.8.0 (2026-04)

### changed

- Updated dependencies, bumped HAPI to 8.8.1

## Release 1.7.3 (2026-03)

### changed

- Updated dependencies

## Release 1.7.2 (2026-02-02)

### changed

- Updated dependencies

## Release 1.7.1 (2026-01-08)

### changed

- Various dependency upgrades to address security vulnerabilities
- Remove file-logging capabilities
- Change Docker base image from `eclipse-temurin:21-jre-alpine` to `gematik1/osadl-alpine-openjdk21-jre` to streamline
  with other projects
- Updated dependencies

### fixed

- Fixed document retrieval to use exact instead of prefix queries, which caused an issue with updating pre-release
  package documents

## Release 1.7.0 (2025-12-11)

### changed

- Fix multiple known CVEs by upgrading core libraries and adding explicit dependency management for vulnerable
  transitive dependencies
- Align project with Spring Boot 4.0.0 as new parent
- Updated dependencies

### fixed

- Explicitly set `reactor-netty` dependencies to 1.3.1 which caused an error in version 1.3.0 with Spring Boot 4.0.0
  upgrade on Linux systems

## Release 1.6.5 (2025-11-24)

### changed

- Pentest fixes: introduce and refine `HostValidationFilter` and use `SecureUrlBuilder` to generate safe request URLs
- Exclude health endpoints from the host header filter to ensure availability of monitoring checks
- Extend the package API with an `altTitle` field for improved package descriptions
- Adopting new CI/CD Pipelines for the gematik Software Factory
- Updated dependencies

## Release 1.6.3 (2025-09-23)

### changed

- Introduce dynamic keywords in the catalog API including initial re-index and fix for the first indexing run
- Updated dependencies

## Release 1.6.2 (2025-08-26)

### changed

- Enhance logging with a request logging filter for error cases and dedicated exception handlers
- Rework exception handling, including dedicated handling for media type errors and reduced error details returned to
  clients
- Extract Google Cloud settings (project, location, repository) directly from `proxy.target-url` to simplify
  configuration
- Adjust Jenkins/Jira release jobs and build pipelines
- Updated dependencies

## Release 1.6.1 (2025-07-24)

### changed

- Introduce dynamic keyword handling in the catalog (search & indexing) and support for alternative canonicals including
  search by `urn:oid`
- Implement a dynamic feed API based on Google Artifact Registry including status handling (unlisted/deprecate) and
  additional feed attributes such as `artifactType`, `publishAction` and `protected`
- Migrate configuration of monitored/protected packages to Google Artifact Registry and refactor internals to simplify
  feed/catalog implementation
- Increase test coverage and remove obsolete code
- Apply minor documentation and Swagger improvements
- Updated dependencies

## Release 1.6.0 (2025-07-22)

### changed

- Prepare for semantic version comparison
- Updated dependencies

## Release 1.5.0 (2025-05-22)

### changed

- Technical groundwork for upcoming semantic versioning logic
- Improvements to CI/CD pipelines
- Upgraded `hapi-fhir-structures-r4` to 8.0.0
- Updated dependencies

## Release 1.4.1 (2025-03-17)

### changed

- Bugfix and maintenance release with minor fixes
- Switch Docker base image to Java 17

## Release 1.4.0 (2025-03-14)

### changed

- Functional enhancements of the npm-proxy (catalog and feed features, among others)
- Technical improvements and refactorings

## Release 1.3.1 (2025-02-17)

### changed

- Stability and bugfix release based on 1.3.0
- Updated dependencies

## Release 1.3.0 (2025-01-14)

### changed

- Extended catalog API and improved package handling
- Updated and cleaned up API documentation plus internal refactorings
- Updated dependencies

## Release 1.2.0 (2024-11-22)

### changed

- Logging improvements and configuration changes for clearer and more concise log output
- Version bump to align with new release process

## Release 1.1.0 (2024-11-22)

### changed

- Logging configuration refinements (structured JSON logging, file logger support)
- Minor internal cleanups and preparations for further hardening

## Release 1.0.0 (2024-11-22)

### added

- First stable version of npm-proxy with JWT token issuing and NPM package proxying against the backend registry
- Initial health endpoint, basic rate limiting and improved error handling
- Initial CORS support and basic access control
