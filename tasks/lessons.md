# Lessons

## i18n: derive message keys from enums/constants, not assumptions
- **Mistake:** When externalizing role labels (`role.<NAME>`), I only created keys for the roles I remembered (USER, ADMINISTRATOR, SURVEY_MANAGER) and missed `SURVEY_ANALYST`, producing `??role.SURVEY_ANALYST_en??` in the UI.
- **Root cause:** Wrote message keys for enum-driven values without reading the enum's full set; the parity test only compared the two bundles to each other, not against the source of truth (the enum).
- **Rule:** When message keys mirror an enum or other code constant, (1) read the enum and cover every value, and (2) add a test that iterates the enum and asserts a key resolves for each value in every locale. Bundle-vs-bundle parity is necessary but not sufficient.

## Auth: never use Authentication#getName() as the OIDC subject
- **Mistake:** `CurrentUser` resolved the logged-in user via `OAuth2AuthenticationToken.getName()`. In production that returned the provider's configured name attribute (the display name "Guido Laures"), not the `sub` claim the user was provisioned with → "User ... not found".
- **Root cause:** `Authentication#getName()` returns the value of the configured *user-name-attribute*, which often is not `sub`. Provisioning stored `oidcUser.getSubject()`. The two diverged. Tests used `oauth2Login()` with the default `sub=user` (name attribute == sub), which masked the bug.
- **Rule:** To key on identity, read the claim explicitly: `principal instanceof OidcUser oidc ? oidc.getSubject() : principal.getName()`. When testing identity resolution, mirror the real principal type (`oidcLogin()`) and make the name attribute differ from `sub` so the test actually exercises the divergence.

## JPA: open-in-view is false — initialise lazy collections in the service, and don't let @Transactional tests mask it
- **Mistake:** Controller/view read `Survey.getQuestions()` (and nested `Question.getOptions()`) after the service's read transaction had closed → `LazyInitializationException` in production. The MockMvc render tests passed because the `@Transactional` test method kept one session open for the whole request.
- **Root cause:** This project sets `spring.jpa.open-in-view: false` (see application-persistence.yml). So lazy associations MUST be initialised inside the transactional service method; the view cannot trigger loads. `@Transactional` on a test joins one long session, hiding the gap.
- **Rule:** With OSIV disabled, return view-ready data from `@Transactional` service methods — touch every collection the view needs (incl. nested ones) before returning. Add at least one regression test that is NOT `@Transactional`, so the service tx actually commits and lazy access happens detached, exactly like production.

## UX: user/validation errors belong on the page, not on a generic error screen
- **Mistake:** Business/validation exceptions (open empty survey, add question without options) rendered the global "Something went wrong" page, losing context — the user couldn't tell what to fix.
- **Rule:** Split exception handling by audience. Expected user errors (`LocalizedException` / validation) → redirect back to the originating page (same-origin `Referer`) with the message as a flash attribute shown inline (alert box). Reserve the generic error page for `NotFoundException` and truly unexpected exceptions. Always log handled exceptions (WARN without stack trace for expected ones) so they're diagnosable.
