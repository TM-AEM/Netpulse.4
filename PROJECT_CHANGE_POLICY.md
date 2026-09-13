# NetPulse Project Change Policy

## Core Rules

1. This is an existing Android Native project.
   NEVER start the project from scratch.

2. NEVER delete an existing source file without explicit authorization.

3. NEVER delete an existing test without explicit authorization.

4. NEVER replace a real production/UI test with a template, sample, Greeting, placeholder, or simplified test.

5. NEVER perform broad cleanup while implementing an unrelated feature.

6. NEVER remove an existing dependency/file/configuration merely because it appears unused unless:
   - all references were checked,
   - Git history was checked,
   - the removal is explicitly within the current task.

7. Preserve all previously verified behavior.

8. The NetPulse source of truth must remain Android NetworkStatsManager.

9. NEVER introduce:
   - a custom usage counter
   - polling-based usage tracking
   - background tracking
   - a database that accumulates network usage
   - TrafficStats as the dashboard source of truth

10. Wi-Fi, Mobile Data, Download, Upload, and Total behavior must not be changed unless explicitly requested.

11. Date range, timezone, DST, permission, daily breakdown, and refresh behavior must not be changed unless explicitly requested.

## Change Isolation

12. Every task must have a single clearly defined scope.

13. Do not modify files outside that scope unless a real compile/test dependency requires it.

14. Before modifying anything:
    - inspect git status
    - inspect recent git history
    - inspect relevant file history when restoring/replacing files.

15. After modifying anything:
    - inspect git diff
    - inspect git diff --stat
    - run the relevant tests
    - run the relevant build/verification task.

16. Report:
    - modified files
    - added files
    - deleted files
    - files outside task scope that changed
    - tests executed and results
    - build/verification results.

17. By default:
    DELETED FILES MUST BE ZERO.

18. If a deletion appears necessary:
    STOP before deleting it and request explicit authorization.

## Checkpoints

19. Each completed task must create one dedicated Git commit unless the task is explicitly verification-only.

20. Never combine unrelated changes into the same commit.

21. Use descriptive commit messages.

22. Never rewrite or squash previous verified commits unless explicitly requested.

## Testing Protection

23. Existing tests are part of the project's permanent safety net.

24. Before changing production code, inspect relevant existing tests.

25. After changing production code, run both:
    - existing relevant tests
    - newly added regression tests.

26. A successful build alone does NOT mean the task is correct.

27. Do not report "all tests preserved" unless Git/file comparison confirms it.

## Cleanup Protection

28. Do not use broad cleanup operations such as:
    - "clean everything"
    - "remove all unused files"
    - "refactor the whole project"
    - "optimize everything"
    unless explicitly requested.

29. Cleanup must always be isolated into its own task and commit.

## Final Safety Requirement

Before every task completion, verify:
`git status`
and confirm that there are no unexpected modifications.

If unexpected modifications are found:
DO NOT silently include them in the task commit.
Investigate and report them first.

This policy is mandatory for all future NetPulse modifications.
