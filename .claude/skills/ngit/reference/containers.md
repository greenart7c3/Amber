# Containers — publish OCI images

Read before publishing an OCI image, updating a container tag, choosing
Blossom storage, or constructing a gateway pull reference. Protocol
background: https://ngit.dev/protocol/software-publishing

## Model

`ngit container publish` (alias `ngit oci publish`) uploads the OCI blobs
reachable from the tagged entries of an OCI image layout to Blossom, then
signs a kind-30624 addressable event mapping tags to manifest digests. The
event is bound to the current kind-30617 git repository, so run it inside that
repository with a signer who is a confirmed maintainer. Gateways are read-only:

```bash
docker pull ncontainer.io/<npub>/<repository>:<tag>
```

## Publish

Export an OCI image layout first, for example
`podman push myimage oci:/tmp/myimage:latest`, then publish every tagged root:

```bash
ngit container publish myimage \
  --layout /tmp/myimage \
  --blossom-server https://blossom-one.example \
  --blossom-server https://blossom-two.example \
  --relay wss://relay.example \
  --source https://example.com/myimage \
  --json
```

A checked-in `.ngit/containers.yaml` lets `ngit container publish myimage
--json` select an entry:

```yaml
schema: 1
publication:
  blossom_servers: [https://blossom-one.example, https://blossom-two.example]
  relays: [wss://relay.example]
containers:
  myimage:
    layout: artifacts/myimage
    source: https://example.com/myimage
```

Relative paths resolve from the repository root. `--manifest PATH` selects
another file; `--no-manifest` ignores the default and requires `--layout`. A
loaded manifest must define `NAME`. CLI layout and metadata override the
entry, a non-empty CLI Blossom list replaces the configured list, and CLI
relays extend configured relays. Signer selection, `--replace`, and output
mode stay on the command line.

Behaviour to know:

- `NAME` is one lowercase OCI repository-name component.
- Tags come from `org.opencontainers.image.ref.name` annotations in
  `index.json`; filenames and git tags are irrelevant. `index.json` itself is
  never uploaded; ngit merges the layout's tags into the tag map fetched from
  the latest kind-30624 event.
- Without `--blossom-server`, ngit uses the publisher's latest kind-10063
  server list. A single server means no redundancy. Every blob is checked on
  every server, missing copies are uploaded with bounded retries and
  verified, and the event is signed once each blob has at least one confirmed
  copy; incomplete replication is reported per server.
- `--relay` extends the repository's relays; account and default relays are
  not added. ngit reads the repository relays before and after uploading and
  needs at least one success each time. A total preflight failure or a
  concurrent-update refusal is safe to retry: uploaded blobs are
  content-addressed. Keep a known state-bearing repository relay reachable
  when changing relay sets, because a healthy empty relay cannot reveal an
  event stranded elsewhere and a publish could then omit old tags.

## Merge versus replace

Ordinary publication updates the tags found in the new layout and retains
older tags, previous server hints, omitted metadata, and unknown event tags.
`--replace` publishes only the new layout's tags and selected servers, drops
omitted description, source, and unknown tags, and sets the title to
`--title` or `NAME`. Use it only when the user explicitly wants complete
replacement.

## JSON

A successful result has `command: "container.publish"`, a `warnings` array,
and `result` fields: `repository`, `git_repository`, `npub`, `name`, `naddr`,
`manifest_path` (or `null`), raw-hex `event_id` (unlike collaboration
commands' `nevent` ids), `tags` and `updated_tags`, per-blob SHA-256, size,
and per-server placement, final `blossom_servers`, and per-relay `accepted`.
Success means at least one relay accepted the event; inspect every
`result.relays[].accepted` when full fanout matters. Failures use
`command_status: "error"` with `error.details` holding per-blob and
per-server outcomes and possible orphan blobs.

## Limits

ngit accepts OCI and Docker v2 manifests and indexes using SHA-256, uploads
only blobs reachable from tagged roots, and rejects missing, oversized, deeply
nested, or mismatched graphs. It snapshots one blob at a time, so allow
temporary disk roughly equal to the largest layer. It does not build images,
push to registries, run a gateway, chunk layers, pull, list, or delete remote
blobs or tags. Software release assets use the separate `ngit release` model.
