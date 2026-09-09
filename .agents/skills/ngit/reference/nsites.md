# Nsites — publish static sites

Read before publishing an already-built website with `ngit nsite` or
diagnosing its Blossom uploads and NIP-5A manifest.
Guide: https://ngit.dev/releases/nsites (nsyte comparison, PR previews).

## Publish

Pass the build output directory, not the source tree. ngit uploads every
regular file, runs no build, applies no ignore files, and rejects symlinks,
unsafe paths, and filenames without extensions.

```bash
ngit nsite publish dist --json                               # reads nsyte's .nsite/config.json when present
ngit nsite publish dist --title "My site" --json             # root kind-15128 site
ngit nsite publish dist --id docs \
  --description-file site-description.txt \
  --source "nostr://<npub>/<identifier>" \
  --blossom-server https://blossom.example.com \
  --blossom-server https://mirror.example.com \
  --relay wss://relay.example.com --json                     # named kind-35128 site
ngit --signer <alias> nsite publish dist --json
ngit --nbunksec-file /run/secrets/publisher-nbunksec nsite publish dist --json   # unattended NIP-46
```

- Config: `.nsite/config.json` (JSON, not YAML) fields `id`, `title`,
  `description`, `source`, `fallback`, `servers`, and `relays` are read;
  `--config PATH` selects another file and `--no-config` ignores it. Explicit
  CLI values win, and any repeated `--blossom-server` or `--relay` replaces
  that whole config array. Unsupported nsyte publication options
  (`publishProfile`, `publishRelayList`, `publishServerList`,
  `publishAppHandler`) produce a warning; nsyte signer fields are ignored.
- Metadata: `--title`, `--description` or `--description-file`, and
  `--source` (`https://` or `nostr://`; omitted, ngit infers the selected
  public repository and never a private one). NIP-5A has no logo tag; ship a
  `favicon.ico` or `favicon.svg` in the build output.
- `--fallback SITE_PATH` (or config `fallback`) maps an existing HTML file in
  the output to `/404.html` without another upload.
- Servers: omit `--blossom-server` to use the account's latest kind-10063
  list; repeat it for replication. `--concurrency` (default 4, range 1–64) is
  a global limit across presence checks and uploads.

## Guarantees

ngit snapshots the directory before network work, deduplicates content, checks
every blob on every selected server, and uploads missing copies with BUD-11
authorization. It signs the manifest only after every blob has at least one
confirmed copy, so a failed deployment cannot point the live manifest at
missing content. A server that fails three consecutive initial checks is
skipped for the rest of that pass while the others continue.

Rerun the same command after a failure: blobs already on a server are
confirmed with `HEAD` and skipped, so continuation is per blob and server. An
unchanged deployment reuses the current manifest without a new signature or
relay write.

## JSON

Check `command_status`, then:

- `result.changed`: publication versus an unchanged no-op;
- `result.config_path`, `result.fallback`, `result.relays`: resolved settings;
- `result.blossom.blobs[].servers[]`: each blob and server outcome;
- `result.publication.relays[]`: manifest acknowledgements (at least one
  relay must accept);
- `warnings[]`: unknown MIME types, unsupported config publications, and
  failed uploads or post-upload verification per server.

On a Blossom failure inspect `error.details.blobs` and
`error.details.possible_orphan_blobs`, fix the server or signer problem, and
rerun.
