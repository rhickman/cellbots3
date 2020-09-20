Coding Mentality
================

1. We should move fast and break things.
2. One mind can't solve all problems.
3. We all should train junior engineers by reviewing their code.
4. All code should be consistent and easy to read.

Making code review mandatory is great to achieve #2, #3, and #4. To do #1, let's make a loophole of doing self-looks good to me.

Actually in GitHub the PR creator cannot add a review, but given the permissions we have we should be able to merge a PR without the review in the extremes cases where it's needed. This should be done with care, and making sure to go back and review that code later.

PRs best practices
==================

* Avoid convoluting core code changes with code style changes.
* Please avoid to create a large PR. Split it into smaller pieces, name them (1/3) Name, details, (2/3) same name, other details, ... By doing this, we could merge the PRs sequentially, if they don't work as expected we would work on a minimal code, and easy to review.
* Be careful for third party code change, it is ways better to do it upstream, otherwise at some moment we cannot get updated code from the community. Or clearly mention who/why/what changed the code
* Code review time is as important as code change time. Think of a way for reducing code review time. Tell exactly what has changed instead of "fixed this bug". Tell who will review which file/class when multiple reviewers are added.
* It is strongly recommended that the PRs should not have more than 1k lines of code change, except trivial function change such as refactoring, name change, file moving, or large test vector.

Code review process
===================

* The PR author will select one reviewer
* The approval of that PR (and passing automated builds) are required for merging.
* In particular cases (also decided by the PR author) multiple reviewers can be selected.
