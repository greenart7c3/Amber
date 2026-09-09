# Pull requests — open, update, stack, review, merge

Read before opening, updating, reviewing, or merging PRs.
Guide: https://ngit.dev/pull-requests

## Open or update a PR

The branch name MUST start with `pr/`. No push option turns another branch
into a PR.

```bash
git checkout -b pr/my-feature
git push -u origin pr/my-feature                     # one commit: its subject and body become title and description
git push -u origin pr/my-feature \
  -o 'title=My feature' \
  -o 'description=First paragraph.\n\nSecond paragraph.'   # literal \n; push options cannot carry real newlines
git push -u origin pr/release-fix -o target-branch=release/2.x
git push -u origin pr/second-part -o base=<commit|branch|nevent>   # pin or override the stack parent
git push --force origin pr/my-feature                # update the PR after amending or rebasing
```

- `-d`/`--defaults` accepts the single-commit title and description without a
  prompt.
- Do not use `$'…\n…'` for push options, and do not pre-escape a Markdown
  file into `-o description=`; open the PR with `ngit send` instead.
- Stacks are inferred: a branch that contains the unique latest tip of one of
  your other open or draft PRs becomes that PR's child and follows the parent
  as it advances. Rebase the child onto the parent's latest tip before updating
  it; ngit refuses stale children and ambiguous candidates rather than
  guessing. Use `base=` for a cross-author, historical, or ambiguous parent,
  and repeat it on each update if the child should stay pinned.
- To push as another stored identity, use
  `git -c nostr.signer=<alias|npub|profile-name> push …`; `--signer` applies
  to `ngit` commands only. `ngit account login --local <alias>` makes an
  identity the repository default instead.

## ngit send

`ngit send` takes ordinary shell arguments, so `--description` accepts real
newlines from `$'…'` or `"$(cat file.md)"`. Inside double quotes `\n` stays a
literal backslash-n.

```bash
ngit send HEAD~2 --subject "My feature" --description "$(cat pr-description.md)" --json
ngit send HEAD~2 --in-reply-to <PR-ID|nevent> --json          # new revision of an existing PR
ngit send --defaults --target-branch release/2.x --json
ngit send --defaults --base <commit|branch|nevent> --json
```

Do not also push a `pr/` branch for the same proposal.

## Read, comment, check out

```bash
ngit pr list --json                                          # default filter: open,draft
ngit pr list --json --status open,draft,closed,applied --label bug
ngit pr view <ID|nevent> --json --comments
ngit pr comment <ID|nevent> --body "Looks good" --json
ngit pr comment <ID|nevent> --body "Fixed!" --reply-to <comment-ID|nevent> --json
ngit pr checkout <ID|nevent> --json                          # local tracking branch that git pull/push understand
```

## Merge (maintainer)

```bash
ngit pr merge <ID|nevent> --json                             # no-ff merge commit on the PR's target; does not push
ngit pr merge --json                                         # PR inferred from the checked-out pr/ branch
ngit pr merge <ID|nevent> --require-ci-trust maintainer-directed --json   # refuse unless CI is green at this trust floor
ngit pr merge <ID|nevent> --exclude-description --json       # summary line and PR reference only
git push origin <target-branch>                              # publishes the merge and the applied status
```

`ngit merge` is a compatibility alias with the same options. The merge lands
on the PR's declared target, or the default branch, resolved against the
latest Nostr repository state rather than a local tracking ref, with the
message `Merge #<8-hex>: <PR title>`. Closed and applied PRs are refused
before any git change. On conflicts, resolve them and run `git commit`; the
message is already prepared, and JSON reports `action: "conflicted"` instead
of `"merged"`.

Before merging or adding maintainer fixes, run
`git log --merges --oneline origin/<target>..HEAD`. A prior `Merge #…` means a
merge would nest merge history; rebase or cherry-pick the PR commits onto the
current target first unless that history is intentional.

## Lifecycle

```bash
ngit pr close <ID|nevent> --reason "blocked by upstream" --json
ngit pr reopen <ID|nevent> --reason "fix was incomplete" --json
ngit pr ready <ID|nevent> --reason "addressed review feedback" --json
ngit pr draft <ID|nevent> --reason "needs more work" --json
ngit pr label <ID|nevent> --label bug --label enhancement --json
ngit pr set-subject <ID|nevent> --subject "New title" --json
ngit pr set-cover-note <ID|nevent> --body "Updated description. See nostr:nevent1abc…" --json
```
