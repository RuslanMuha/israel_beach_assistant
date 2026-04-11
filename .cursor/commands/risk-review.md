# /risk-review

Production-focused risk review of provided diff/context.

Hard rules:
- Do not assume; use only code shown/scanned.
- No invented issues or generic examples.
- If you can't cite a snippet, do not comment.

Scope:
- Changed files + direct call chain + relevant configs/tests.

Output:
- Top Risks (max 5 bullets)
- Fixed-format comments (Issue/Suggestion/Example/Impact/Priority)
Finish with: Tests to run + tuning knobs + metrics to watch.
