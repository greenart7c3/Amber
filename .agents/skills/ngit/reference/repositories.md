# Repositories — publish, clone, hosting, membership

Read when publishing or cloning a repository, resolving `nostr://` URL forms,
or changing an announcement's hosting, metadata, or roster. Guides:
https://ngit.dev/repositories (hosting choices, migrating from a forge,
mirrors, private repositories), https://ngit.dev/maintainers, and
https://ngit.dev/maintainers/going-deeper (leadless repositories, delegated
trust, removal, roster repair).

## URLs

```
nostr://<npub>/<identifier>
nostr://<npub>/<relay-hint>/<identifier>   # relay-hint is a bare domain, e.g. relay.ngit.dev
nostr://<user>@<domain>/<identifier>       # NIP-05, only when explicitly provided
nostr://<domain>/<repository-path>         # NIP-AD: the full /path is sent URL-encoded to /.well-known/nostr.json?path=
```

## Clone

```bash
git clone nostr://<npub>/<relay-hint>/<identifier>   # relay hint skips discovery
git clone nostr://<npub>/<identifier>
git clone nostr://user@domain.com/<identifier>       # NIP-05, only if given to you
git clone nostr://ngit.dev/ngit.git                  # NIP-AD bare-domain path
```

Open and draft PRs are not fetched as branches unless `nostr.auto-pr-branches`
is `true`; `ngit pr checkout <ID|nevent>` materialises one on demand.

## Inspect

```bash
ngit repo --json --offline      # run git fetch origin first when the cache may be stale
```

The output reports `nostr_url`, effective `git_servers`, `relays`, `hashtags`,
and `grasp_servers` detected from paired clone and relay entries, plus the
roster: `members`, `lead_source`, `lead_path`, `pending_actions`, and
`health`. Follow the actionable error or `pending_actions` rather than
replacing an announcement wholesale.

## Publish and host

`ngit init` declares the complete initial announcement. Grasp hosting supplies
both a git server and a relay; additional infrastructure is explicit, empty by
default, and supplements grasp hosting rather than replacing it:

```
relays = grasp-derived relays + additional relays
clones = grasp-derived clones + additional clones
```

```bash
ngit init --name "My Project" --description "What it does" --defaults --json   # preferred grasp servers, else ngit defaults
ngit init --name "My Project" --grasp-server grasp.example.com --defaults --json
ngit init --name "My Project" --additional-relay wss://relay.example.com \
  --additional-clone https://git.example.com/my-project.git --defaults --json
ngit init --name "My Project" --grasp-server "" \
  --additional-relay wss://relay.example.com \
  --additional-clone https://git.example.com/my-project.git --defaults --json   # no grasp: both halves required
```

Every announcement needs at least one relay and one git server; `init` and
`repo edit` refuse to publish otherwise, including a metadata-only edit of an
announcement that already lacks one half. Repair it in the same command, e.g.
`ngit repo edit --name "New name" --add-grasp-server grasp.example.com`.

`--identifier` is set at initial publication only: changing the `d` tag
creates a different repository coordinate.

## Edit

`ngit repo edit` preserves omitted settings. Collections use targeted,
repeatable actions that can be combined in one command:

| Setting | Add | Remove |
| ------- | --- | ------ |
| Grasp server | `--add-grasp-server URL` | `--remove-grasp-server URL` |
| Additional relay | `--add-additional-relay URL` | `--remove-additional-relay URL` |
| Additional clone | `--add-additional-clone URL` | `--remove-additional-clone URL` |
| Hashtag | `--add-hashtag TAG` | `--remove-hashtag TAG` |

Scalars use replacement flags: `--name`, `--description`, `--web`, `--u`,
`--earliest-unique-commit`. A grasp-derived relay or clone cannot be removed
as an additional entry; remove the grasp server and its pair goes with it. To
empty a collection, remove every value currently reported.

Each successful edit publishes a fresh announcement and, when the repository
has Nostr state, republishes that state once so new relays and servers hold
the authoritative refs. If that fails, follow the reported `ngit sync`
recovery guidance.

## Roles and membership

- **Co-maintainer**: publishes git state, merges, manages issues and PRs, and
  changes the roster at the protocol level.
- **Lead maintainer**: the same authority plus responsibility for the roster.
  When a lead exists, ngit restricts routine roster changes to the lead
  workflow.
- **Moderator**: publishes issue, PR, and patch status events (including
  recording an existing merge) but cannot publish git state or merge.

Membership is reciprocal: a listing is an invitation until the invitee
publishes an announcement acknowledging the role, and an invited member's
events are not authoritative until then.

```bash
ngit repo edit --add-maintainer <npub> --json                 # a sole maintainer's first add makes them lead
ngit repo accept --json                                       # invitee confirms the role and the lead; --grasp-server <url> also hosts the git data there
ngit repo follow-lead --json                                  # members retain history and follow a changed lead or roster
ngit repo leave --json                                        # end your own role and republish
ngit repo edit --remove-maintainer <npub> --json
ngit repo edit --lead-maintainer <npub> --json                # handover: the new lead publishes the full roster first
ngit repo edit --acknowledge-maintainer-change <npub> --json
```

Change one relationship at a time. In the deliberately leadless case, pass
`--no-lead-maintainer` with every `--add-maintainer` or `--remove-maintainer`.
