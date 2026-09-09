# Issues — create, view, comment, close

Read when working with issues. Guide: https://ngit.dev/issues

```bash
ngit issue create --subject "Bug title" --body "Details as markdown" --label bug --json
ngit issue list --json                                       # add --status closed or --label bug to filter
ngit issue view <ID|nevent> --json --comments
ngit issue view <ID|nevent> --json --history                 # subject, cover-note, label, and status changes
ngit issue comment <ID|nevent> --body "Reproduced on v2.1" --json
ngit issue comment <ID|nevent> --body "Thanks!" --reply-to <comment-ID|nevent> --json
ngit issue close <ID|nevent> --reason "wontfix" --json
ngit issue resolved <ID|nevent> --reason "fixed in abc123" --json
ngit issue reopen <ID|nevent> --reason "regression in v2.3" --json
ngit issue label <ID|nevent> --label bug --label enhancement --json
ngit issue set-subject <ID|nevent> --subject "New title" --json
ngit issue set-cover-note <ID|nevent> --body "$(cat cover-note.md)" --json
```

`resolved` records that the problem was fixed; `close` records that it will
not be. Reference other events in `--body` as `nostr:nevent1…`.

## Auto-resolve from commits

A commit pushed to the declared default branch resolves an issue when its
message contains a form of `close`, `fix`, `resolve`, or `implement` followed
by a unique hex ID or prefix or a `nostr:nevent1…` reference, for example
`Fixes #deadbeef`. The status is published only when the pusher is the issue
author or a confirmed repository member.
