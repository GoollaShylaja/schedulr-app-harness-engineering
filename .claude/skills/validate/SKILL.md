---
name: validate
description: Run the full quality gate (spotless + compile + mvn test + tsc + vitest) and report PASS/FAIL for each command. Run before any commit or PR.
disable-model-invocation: true
---

# /validate — Full Validation Gate

Run the complete quality gate and report PASS/FAIL for each command.

This is the same gate the `Stop` hook enforces automatically. Run it explicitly before any commit or PR.

---

## Gate Commands

```bash
# Backend
cd app/backend && ./mvnw spotless:check
cd app/backend && ./mvnw compile
cd app/backend && ./mvnw test

# Frontend
cd app/frontend && npx tsc --noEmit
cd app/frontend && npm run test
```

---

## Report Format

After running all commands, output:

```
Validation Gate Results
=======================

Backend
  mvnw spotless:check : PASS / FAIL
  mvnw compile         : PASS / FAIL
  mvnw test            : PASS (N tests) / FAIL (N failed, N passed)

Frontend
  tsc --noEmit       : PASS / FAIL
  vitest (npm test)  : PASS / FAIL

Overall: PASS / FAIL
```

If **FAIL**: list each failing command with the first error or failure message. Fix the issue and re-run `/validate` before proceeding.

If **PASS**: the working tree is clean and ready to commit.

---

## Notes

- The `Stop` hook (`StopValidate.java`) runs the backend portion of this gate
  automatically when Claude finishes a turn (skipped until `./mvnw` wrapper exists). If
  it blocks, you'll see the reason in the hook output — fix and let Claude continue.
- If you're only touching backend code, the frontend commands will still run but
  failures there do not indicate a regression in your change — investigate and fix
  anyway before merging.
- Spotless config lives in `app/backend/pom.xml`; run `./mvnw spotless:apply` to
  auto-fix formatting issues before re-checking.
