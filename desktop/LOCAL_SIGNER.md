# Local socket signer (NIP-5F)

Amber Desktop can sign for command-line tools such as nak, ngit, scripts and AI
agents over a local Unix domain socket. This follows the NIP-5F draft
([nostr-protocol/nips#1862](https://github.com/nostr-protocol/nips/pull/1862)),
with NIP-55's login and account model.

You configure every account once in Amber. Any tool on the machine can then use
them without its own key storage, relay setup or pairing string. Every request
still goes through Amber's normal approval and permission system.

It's off by default. Turn it on in **Settings → Desktop → Local socket signer**.

## Contents

- [Why a socket](#why-a-socket)
- [Where the socket is](#where-the-socket-is)
- [Session walkthrough](#session-walkthrough)
- [Message format](#message-format)
- [Methods](#methods)
- [Logging in with get_public_key](#logging-in-with-get_public_key)
- [The tool secret](#the-tool-secret)
- [Choosing the account](#choosing-the-account)
- [Approval and permissions](#approval-and-permissions)
- [Errors](#errors)
- [Security model](#security-model)
- [Writing a client](#writing-a-client)
- [How it works inside Amber](#how-it-works-inside-amber)
- [Testing](#testing)

## Why a socket

- **Local only.** The socket is a file on your disk with owner-only permissions.
  No relay sees the requests, and nothing is exposed on the network.
- **Fast.** NIP-46 sends each request as an encrypted kind-24133 event through a
  relay and waits for the reply event. A request over the socket is a local
  read and write.
- **Works offline.** No relays are needed.
- **Configure once.** One Amber serves every tool and every account.
- **Cross-platform.** AF_UNIX sockets work on Linux, macOS and Windows 10 and
  later. The desktop app runs on JDK 21, which supports them on all three.

## Where the socket is

| Build | Path |
|-------|------|
| Release (installed app) | `~/.local/share/nostr/signer.sock` |
| Debug (`./gradlew :desktop:run`) | `~/.local/share/nostr/signer-debug.sock` |

The path is the same on every OS. On Windows `~` is the user profile folder,
for example `C:\Users\you\.local\share\nostr\signer.sock`.

What happens to the file:

- **Start:** Amber creates `~/.local/share/nostr/` with mode `0700` and the
  socket with mode `0600`.
- **Stop:** Amber deletes the socket when the setting is turned off and when
  the app exits.
- **Leftover file:** a socket file left behind by a crash is replaced.
- **Path already in use:** if another program is listening on that path,
  Amber doesn't take it over, and Settings shows "Another signer is already
  using …".

The socket stays open while Amber is locked. Requests made while it's locked
are answered with error `10` ("signer locked") instead of hanging.

## Session walkthrough

First run of a tool:

```
tool                                      Amber
  |  connect to signer.sock                 |
  |<-- {"name":"Amber","supported_methods":[...]}      handshake
  |--> {"client":"nak"}                                 hello
  |--> {"id":"1","method":"get_public_key","params":[]}
  |                                         |  unknown tool → approval prompt
  |                                         |  you approve and pick an account
  |<-- {"id":"1","result":"<hex pubkey>","secret":"<64 hex>","error":null}
  |    (the tool saves the secret)          |
  |--> {"id":"2","method":"sign_event","params":[{...}]}
  |                                         |  stored permission → sign now
  |                                         |  denied → error 5
  |                                         |  no rule → approval prompt
  |<-- {"id":"2","result":{signed event},"error":null}
```

Every run after that:

```
  |<-- {"name":"Amber",...}
  |--> {"client":"nak","secret":"<saved secret>"}     recognized, no prompt
  |--> {"id":"1","method":"sign_event","params":[{...}]}
  |<-- {"id":"1","result":{signed event},"error":null}
```

## Message format

Each message is one JSON object on one line: UTF-8, ending in `\n`, at most
1 MiB.

**Handshake.** Amber sends it first:

```json
{"name":"Amber","supported_methods":["get_public_key","sign_event","nip04_encrypt","nip04_decrypt","nip44_encrypt","nip44_decrypt","nip44v3_encrypt","nip44v3_decrypt","decrypt_zap_event","ping"]}
```

**Hello.** The client's first line:

```json
{"client":"nak"}
{"client":"nak","secret":"<secret from an earlier approval>"}
```

The name is only a label for prompts and the Applications screen. It never
identifies the tool; [the secret](#the-tool-secret) does. If a client skips
the hello and sends a request straight away, Amber treats it as an unnamed
client ("Local client").

**Request:**

```json
{"id":"<string or number>","method":"<method>","params":[...]}
```

**Response.** The `id` is echoed back as a string:

```json
{"id":"1","result":"<value>","error":null}
{"id":"1","result":"<hex pubkey>","secret":"<64 hex>","error":null}
{"id":"1","result":null,"error":{"code":5,"message":"user rejected"}}
```

The second form, with `secret`, only appears on the `get_public_key` answer
that got the tool approved.

**Rules for clients:**

- **Requests can overlap.** A client can send more requests while an earlier
  one is still waiting for approval. Answers come back as each one finishes, so
  match them by `id`, not by order.
- **One-shot pipes work for answers that need no prompt.** A client can send
  everything and then close its sending side, as `echo … | nc -U` does. Amber
  still answers every request it already received before closing the
  connection. Amber can't tell a half-close from a tool that exited, so a
  request that would need a prompt is answered at once with error `10`
  "connection closed before approval" instead of waiting. Keep the
  connection open until the answers you're waiting for arrive.
- **Many clients at once.** Each connection is a separate session. Prompts
  from different tools queue side by side, and each answer goes back only to
  the connection that asked.

## Methods

The optional `account` param is described in
[Choosing the account](#choosing-the-account).

| Method | Params | Result |
|--------|--------|--------|
| `get_public_key` | `[account?]` | hex pubkey; logs the tool in (see below) |
| `sign_event` | `[event, account?]` | the signed event (object) |
| `nip04_encrypt` | `[pubkey, plaintext, account?]` | ciphertext |
| `nip04_decrypt` | `[pubkey, ciphertext, account?]` | plaintext |
| `nip44_encrypt` | `[pubkey, plaintext, account?]` | ciphertext |
| `nip44_decrypt` | `[pubkey, ciphertext, account?]` | plaintext |
| `nip44v3_encrypt` | `[pubkey, kind, scope, base64 plaintext, account?]` | ciphertext |
| `nip44v3_decrypt` | `[pubkey, kind, scope, ciphertext, account?]` | base64 plaintext |
| `decrypt_zap_event` | `[zap request event, account?]` | the decrypted zap request (object) |
| `ping` | `[account?]` | `"pong"` |

**Event params.** For `sign_event`, the event is an unsigned event object with
`kind`, `content`, `tags` and `created_at`. A JSON string of the event is also
accepted. The result is a full signed event object with `id`, `pubkey` and
`sig`:

```json
{"id":"2","method":"sign_event","params":[{"kind":1,"content":"hello","tags":[],"created_at":1700000000}]}
```

**NIP-44 v3 params** use the same layout as Amber's NIP-46 bunker:

- `kind` is the context kind, a JSON number or a numeric string.
- `scope` is the context scope string.
- Plaintext travels as base64 in both directions: you send it base64 to
  encrypt, and decrypt returns it base64.
- Grants are per context kind: approving with "this kind only" or "all kinds"
  behaves as for NIP-46.

**Not implemented:** the draft's `list_public_keys` and any `connect` method.
`get_public_key` is the only way in, as in NIP-55.

## Logging in with get_public_key

As in NIP-55, `get_public_key` is the login.

- **Unknown tool calls it:** Amber shows an approval prompt with an account
  picker, the sign policy (basic, manual or full trust) and a "delete after"
  time.
- **You approve it:** Amber creates an application ("Local socket" under
  Applications) for the account you picked. It returns that account's pubkey,
  plus a fresh [secret](#the-tool-secret).
- **Anything else from a tool that isn't logged in** (`sign_event`, encrypt,
  …) is refused with error `5`, "not approved: call get_public_key first",
  and no prompt appears.

## The tool secret

A socket doesn't say which program is on the other end, so a tool's name can't
be trusted. Any program running as you could claim to be `nak`. Amber
identifies tools by a secret instead.

- **Issued on approval.** When a `get_public_key` approval goes through, Amber
  generates a random 32-byte secret, returns it beside the pubkey, and stores
  only its SHA-256 hash on the app.
- **Presented in every later hello.** `{"client":"nak","secret":"…"}` is
  recognized at once, with no prompt. The name it uses doesn't matter.
- **The name alone gets you nothing.** A connection without the right secret
  is a new tool. Its requests are refused until it logs in with
  `get_public_key`, which means a new approval from you. A guessed or wrong
  secret is the same as none.
- **On the connection that got approved,** the tool stays recognized even if
  it ignores the secret. A tool that never saves the secret simply needs
  approving once per connection.
- **One secret per tool.** If a tool that already has a secret logs in to a
  second account, the same secret covers both. No new secret is returned.
- **Treat it like a password.** Store it only where your user can read it, for
  example in a `0600` config file. To revoke it, delete the app under
  Applications.

## Choosing the account

Every method takes an optional last param that names the account, as a hex
pubkey or an npub.

**With an account:**

- Amber uses that account.
- If the tool isn't logged in to it, only `get_public_key` can fix that. It
  brings up the approval for exactly that account, with the picker locked.
  Other methods are refused with `5`.
- If the account isn't loaded in Amber, the answer is error `4`.

**Without an account:**

- Amber uses an account the tool is logged in to. If one of them is the
  current account, that one wins.
- For a tool that isn't logged in anywhere, `get_public_key` brings up the
  approval with the picker starting on the current account. **The account you
  pick is the one the tool gets.**

A tool that wants to act as a specific identity calls `get_public_key` once,
keeps the pubkey, and passes it as the last param of later calls. For example,
an agent told to "post this as soapbox" passes soapbox's npub.

## Approval and permissions

After login, a tool's requests are checked against its app's stored
permissions, exactly like a NIP-46 connection:

| Stored rule | What happens |
|-------------|--------------|
| allow (until a time, or always) | answered immediately |
| deny | answered with error `5` |
| none | an approval prompt in Incoming requests, with a notification if enabled |

Your choice can be remembered for a set time or always. Encryption and
decryption grants are scoped by content type, as on Android. NIP-44 v3 grants
are scoped by context kind.

**Pending requests** expire after 10 minutes. If one is dropped instead of
being answered, the tool still gets a reply:

| Reason | Error |
|--------|-------|
| Expired | `10` "request expired" |
| Amber locked | `10` "signer locked" |
| Account removed | `10` "account removed" |
| Sending side closed | `10` "connection closed before approval" |

If the tool disconnects, or closes its sending side, its pending prompts are
removed. Other tools' prompts stay.

**Activity** for each tool is recorded in its app history. Requests also
appear in the account log as `socket` / `local-signer`.

## Errors

| Code | Meaning | Examples |
|------|---------|----------|
| 1 | Invalid request | not JSON, missing `id` or `method`, line over 1 MiB |
| 2 | Method not supported | `sign_psbt`, `connect`, `list_public_keys` |
| 3 | Invalid params | wrong param count, wrong types, a v3 kind that isn't a number, an event that can't be signed, ciphertext that can't be decrypted |
| 4 | Key not found | unknown account param, no accounts in Amber |
| 5 | User declined | rejected prompt, a remembered "deny" rule, a tool that isn't logged in |
| 10 | Internal error | signer locked, request expired, account removed |

Malformed requests are reported first, before lock or account checks, so a
typo gets code 1, 2 or 3 even while Amber is locked.

## Security model

- **Same user only.**
  - Socket file: `0600`, inside a `0700` directory.
  - Linux and macOS: Amber also checks the connecting process's credentials
    (`SO_PEERCRED`) and drops connections from other OS users.
  - Windows: no peer credentials. The ACLs on the user profile folder do the
    same job.
- **No impersonation.** An approved tool is identified by its secret, never by
  its name. Another program can't use an approved tool's permissions without
  stealing that secret.
- **Nothing signs without your approval.** Any process running as you can
  connect, but it only gets what you approve, or what an earlier "remember"
  choice allows for its secret.
- **Keys never leave Amber.** Tools get signatures, ciphertexts and plaintexts,
  never keys.
- **Locked means locked.** While the passphrase lock is on, key material isn't
  in memory and every request fails with `10`.
- **Debug builds stay separate.** `:desktop:run` uses `signer-debug.sock`, so a
  development build never answers for, or takes over, the installed app.

## Writing a client

Minimal Python client that logs in once and reuses its secret:

```python
import json, os, socket

SOCK = os.path.expanduser("~/.local/share/nostr/signer.sock")
STATE = os.path.expanduser("~/.config/my-tool/amber-secret")   # keep it 0600

s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
s.connect(SOCK)
f = s.makefile("rw", encoding="utf-8")

def call(id, method, *params):
    f.write(json.dumps({"id": id, "method": method, "params": list(params)}) + "\n")
    f.flush()
    return json.loads(f.readline())

json.loads(f.readline())                                  # handshake
hello = {"client": "my-tool"}
if os.path.exists(STATE):
    hello["secret"] = open(STATE).read().strip()
f.write(json.dumps(hello) + "\n")

reply = call("1", "get_public_key")                       # prompts in Amber the first time
if "secret" in reply:                                      # just approved: save it
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    fd = os.open(STATE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.write(fd, reply["secret"].encode())
    os.close(fd)
pubkey = reply["result"]

event = {"kind": 1, "content": "hello", "tags": [], "created_at": 1700000000}
print(call("2", "sign_event", event, pubkey)["result"])
```

From a shell:

```bash
socat - UNIX-CONNECT:$HOME/.local/share/nostr/signer.sock
{"client":"socat"}
{"id":"1","method":"get_public_key","params":[]}
{"id":"2","method":"sign_event","params":[{"kind":1,"content":"hi","tags":[],"created_at":1700000000}]}
```

## How it works inside Amber

| File | Role |
|------|------|
| `core/LocalSignerProtocol.kt` | Wire format: the method table, parsing params into Quartz NIP-46 `BunkerRequest`s, building responses (including the `secret` field), error codes. No I/O. |
| `core/LocalSigner.kt` | The server: starting and stopping the socket, the peer check, one session per connection, login and secret handling, the per-request flow. |
| `core/BunkerEngine.kt` | Shared with NIP-46: `computeResult` (sign, encrypt, decrypt, v3), `lookupPermission`, the `pending` queue, `approve`/`reject`, `dropPending`. |
| `core/Models.kt` | `AppRecord.transport` (`"nip46"` or `"socket"`) and `socketSecretHash`. `DesktopSettings.localSigner`. |

**Request flow.** For each line, `LocalSigner` does the following.

1. Parse the line and map the method onto the NIP-46 request shape. The event
   object becomes the JSON string NIP-46 uses.
2. Check the lock and the accounts.
3. Find the app for this tool in the target accounts. A tool matches an app
   that was approved on this connection, or whose `socketSecretHash` is the
   hash of the secret from the hello.
   - **No match, `get_public_key`:** generate a secret, unless the tool already
     has one. Queue a **connect** `PendingBunkerRequest` with no relays, the
     secret's hash, and a `responder` the server waits on. Once you approve,
     the reply carries the new secret.
   - **No match, anything else:** error 5.
4. Run `BunkerEngine.computeResult` to get the result and preview, then
   `lookupPermission` and `isRemembered` to decide:
   - **Allow:** answer now and write the history.
   - **Deny:** error 5.
   - **Ask:** queue a `PendingBunkerRequest` (with kind and scope for v3)
     whose `responder` writes the answer back to this connection.

When you approve or reject in the UI, `BunkerEngine.doApprove`/`doReject`
update permissions and history as for any connection. They then call the
request's `responder` instead of publishing a kind-24133 event. They never set
relays or per-connection keys on socket apps, and they store the secret hash
on new socket apps. `dropPending` answers waiting socket clients when requests
are removed without a decision: expiry, lock, account removal or disconnect.

**Queue ids.** Requests are queued under `socket-<session>-<seq>-<client id>`.
This keeps ids from different clients, or a client that reuses ids, from
colliding in the queue. A disconnect drops exactly that session's entries.

## Testing

- `LocalSignerProtocolTest`: parsing, the method table (no `connect` or
  `list_public_keys`), account params, error codes and response shapes.
- `LocalSignerTest`: an end-to-end run over a real socket against the real
  approval queue, with no relays. It covers:
  - socket permissions
  - login, then remembered signing
  - rejection
  - refusal before login
  - secret handout, reuse and impersonation attempts
  - NIP-44 v3 round trip with per-kind prompts
  - account selection and the NIP-55 login
  - overlapping requests
  - eight clients queuing prompts at the same instant
  - dropped requests
  - tools that exit with a prompt open
  - half-closed clients, with and without prompts
  - leftover and in-use socket files

```bash
./gradlew :desktop:test --tests '*LocalSigner*'
```
