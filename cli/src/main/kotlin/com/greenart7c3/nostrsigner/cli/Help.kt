package com.greenart7c3.nostrsigner.cli

/**
 * The help texts. English only and hard-coded here: this is a developer tool's
 * documentation, not UI text, so it stays out of :commonsui and translations.
 * Written for an agent that has to act without a human reading along: every
 * command, its input and output shape, the flags and the exit codes.
 */
object Help {
    fun forCommand(name: String?): String = when (name) {
        null -> overview()
        "login" -> login()
        "connect" -> connect()
        "sign-event" -> signEvent()
        "get-public-key" -> getPublicKey()
        "nip44-encrypt", "nip44-decrypt", "nip04-encrypt", "nip04-decrypt" -> crypto(name)
        "ping" -> ping()
        "status" -> status()
        "logout" -> logout()
        "help" -> overview()
        else -> throw CliError("unknown help topic \"$name\" (run `amber help`)", Exit.USAGE)
    }

    fun overview(): String = """
        amber ${BuildInfo.version} - ask Amber to sign Nostr events (NIP-46)

        amber is a command-line NIP-46 client. It keeps no keys and no accounts: it
        only holds the connections you paired with Amber, and Amber (the desktop app
        on this computer, or the app on your phone) decides every request. Pair once,
        then any script, app or agent can ask for signatures and get the result on
        stdout.

        USAGE
          amber <command> [arguments] [flags]
          amber help [command]

        COMMANDS
          login <bunker://...>       pair using a bunker URI from Amber
          connect                    print a nostrconnect:// URI/QR and pair by approval
          sign-event [JSON|-]        sign an event, print the signed event
          get-public-key             print the hex public key Amber signs with
          nip44-encrypt <pubkey> [TEXT|-]
          nip44-decrypt <pubkey> [TEXT|-]
          nip04-encrypt <pubkey> [TEXT|-]
          nip04-decrypt <pubkey> [TEXT|-]
          ping                       check that Amber answers
          status                     show the saved connection and whether it answers
          logout                     tell Amber to forget the connection and delete it here

        PAIRING (once per machine, needs a human in Amber)
          With a bunker URI: in Amber, open the connection's details, copy the
          bunker:// URI, then run
            amber login 'bunker://...'
          Without one: run `amber connect`, paste or scan the printed URI into Amber,
          and approve the connection there.
          Ask for permissions up front so later requests do not prompt again:
            amber connect --perms 'sign_event:1,sign_event:30023,nip44_encrypt'
          Approvals are the human's decision in Amber; if a request waits for one
          and nobody is there, the command exits with code 5 after --timeout seconds.

        FLAGS (before or after the command)
          --profile NAME     saved connection to use (default: "default")
          --timeout SECONDS  how long to wait for Amber (default: 120)
          --json             print one {"ok":true,"result":...} or
                             {"ok":false,"error":...,"code":...} object
          --verbose          relay traffic notes on stderr
          --force            replace an already paired profile
          --relay URL        (connect) relay to pair over, repeatable; replaces the
                             defaults (Amber's own list, plus a local Amber's relay)
          --name NAME        (connect/login) the app name Amber shows
          --perms LIST       (connect) permissions to ask for, comma-separated
                             "type:kind", e.g. sign_event:1,nip44_encrypt
          --qr               (connect) also draw the URI as a QR code on stderr
          --open             (connect) hand the URI to the OS handler
          -h, --help         help for the command
          -V, --version      print the version

        INPUT AND OUTPUT
          Results go to stdout and nothing else: the signed event JSON, the public
          key, the ciphertext, "pong". Progress, warnings, the QR code and pairing
          instructions go to stderr. `connect` prints the bare nostrconnect:// URI on
          stdout first (with --json, as {"ok":true,"result":{"uri":...,"state":
          "waiting-for-approval"}}), so a script can capture it before the approval.
          JSON on stdin: pass - as the argument, e.g. `... | amber sign-event -`.

        EXIT CODES
          0  success
          1  error: an invalid answer, a broken saved file, I/O
          2  bad usage: unknown command or option, missing input, invalid JSON
          3  not paired: no connection saved for this profile
          4  refused: the user rejected in Amber, or Amber has no grant for it
          5  timeout: no answer; Amber may be offline or still waiting for a human
          6  unreachable: no relay accepted a connection

        STORAGE
          Saved connections live in AMBER_CLI_HOME (default ~/.config/amber-cli,
          %APPDATA%\amber-cli on Windows) in connections.json, encrypted with
          AES-256-GCM. The key is kept in the OS credential store (macOS Keychain,
          Windows Credential Manager, freedesktop Secret Service); where none is
          running, a 0600 key file sits next to it. Other users and apps cannot
          read the saved client key, and that key alone cannot sign anything:
          Amber approves every request.

        ENVIRONMENT
          AMBER_PROFILE    default profile when --profile is not given
          AMBER_CLI_HOME   directory holding the saved connections
                           (default: ~/.config/amber-cli, %APPDATA%\amber-cli on Windows)

        EXAMPLES
          # first time, in a terminal a human can see
          amber connect --qr --perms 'sign_event:1,sign_event:30023'

          # then, from any script or agent
          amber get-public-key
          amber sign-event '{"kind":1,"content":"hello world","tags":[]}'
          echo '{"kind":30023,"content":"# post","tags":[]}' | amber sign-event -

          # encrypt/decrypt with someone else's key
          amber nip44-encrypt <hex-pubkey> 'secret text'
          amber nip44-decrypt <hex-pubkey> <ciphertext>

          # machine-readable, for agents
          amber --json status
          amber --json sign-event '{"kind":1,"content":"hi","tags":[]}'

          # a second, independent connection
          amber connect --profile work --name 'work scripts'
    """.trimIndent()

    private fun login(): String = """
        amber login <bunker://...>

        Pairs this machine with a connection Amber already created. Get the URI in
        Amber: open the connection's details and copy its bunker:// URI (it contains
        the connection's public key, its relays and its secret).

        Amber asks you to approve the new client the first time; after that the
        connection is saved here under --profile (default "default") and every
        command works without further pairing.

        Prints the hex public key of the account Amber signs with.

        EXIT CODES
          0 paired    2 bad URI    4 rejected in Amber    5 approval timed out
          6 no relay reachable

        EXAMPLE
          amber login 'bunker://abc...?relay=wss://relay.nip46.com/&secret=...'
    """.trimIndent()

    private fun connect(): String = """
        amber connect [--relay URL]... [--name NAME] [--perms LIST] [--qr] [--open]

        Pairs this machine without a bunker URI: it makes up a client key and a
        secret, then prints a nostrconnect:// URI (and, with --qr, a scannable QR
        code) on stdout. Paste or scan it into Amber, approve the connection there,
        and this command finishes by saving the connection.

        The connection is saved (unpaired) before waiting, so if the approval takes
        longer than --timeout, running `amber connect` again shows the same URI
        instead of stranding the key and secret.

        Flags
          --relay URL    relay to pair over; repeatable, replaces the defaults.
                         The defaults are Amber's own relay list, plus the local
                         relay of a running Amber on this machine (ws://127.0.0.1).
          --name NAME    app name shown on Amber's approval card (default "amber-cli")
          --perms LIST   permissions to request, comma-separated "type:kind":
                         sign_event:1,sign_event:30023,nip44_encrypt,nip44_decrypt,
                         nip04_encrypt,nip04_decrypt,get_public_key,ping
          --qr           draw the URI as a QR code on stderr
          --open         hand the URI to the OS handler (a running Amber claims it)
          --force        replace an already paired profile

        Output
          stdout: the nostrconnect:// URI, then (only with --json) a final
          {"ok":true,...,"state":"paired"} object.
          stderr: instructions, the QR code, progress.

        EXIT CODES
          0 paired    2 bad usage    4 rejected in Amber    5 approval timed out
          6 no relay reachable

        EXAMPLES
          amber connect --qr
          amber connect --relay ws://127.0.0.1:7778 --name 'my scripts'
          amber connect --perms 'sign_event:1,sign_event:30023' --profile agent
    """.trimIndent()

    private fun signEvent(): String = """
        amber sign-event [JSON | -]

        Signs an unsigned event. The JSON needs a numeric "kind"; "content" and
        "tags" default to "" and []. "created_at" is filled with the current time
        when missing or 0.

        Pass the JSON as an argument, or - to read it from stdin. The signed event
        is printed as JSON on stdout, and checked here before printing: its id and
        signature must verify against the paired account's public key.

        EXAMPLES
          amber sign-event '{"kind":1,"content":"hello","tags":[]}'
          echo '{"kind":7,"content":"+","tags":[["p","<hex>"]]}' | amber sign-event -
          amber --json sign-event '{"kind":0,"content":"{\"name\":\"me\"}","tags":[]}'

        EXIT CODES
          0 signed    2 invalid JSON    3 not paired    4 refused in Amber
          5 approval timed out    6 no relay reachable
    """.trimIndent()

    private fun getPublicKey(): String = """
        amber get-public-key

        Prints the hex public key of the account this connection signs with: the
        same key Amber shows as the account's npub. Its event id and signatures can
        be verified with any Nostr library.

        EXIT CODES
          0 printed    3 not paired    5 no answer from Amber    6 no relay reachable
    """.trimIndent()

    private fun crypto(command: String): String {
        val nip = command.substringBefore('-')
        val verb = if (command.endsWith("encrypt")) "Encrypts" else "Decrypts"
        val input = if (command.endsWith("encrypt")) "plaintext" else "ciphertext"
        val output = if (command.endsWith("encrypt")) "ciphertext" else "plaintext"
        return """
            amber $command <pubkey> [TEXT | -]

            $verb with $nip to/from <pubkey> (64-character hex). Amber's account
            key does the work; this tool never sees it. Pass the $input as an
            argument, or - to read it from stdin (a trailing newline is dropped).

            Prints the $output on stdout.

            EXAMPLES
              amber $command <hex-pubkey> 'hello'
              printf 'hello' | amber $command <hex-pubkey> -

            EXIT CODES
              0 done    2 bad usage    3 not paired    4 refused in Amber
              5 approval timed out    6 no relay reachable
        """.trimIndent()
    }

    private fun ping(): String = """
        amber ping

        Asks Amber for a pong. The cheapest check that the connection, the relays
        and the app on the other side are alive; prints "pong".

        EXIT CODES
          0 pong    3 not paired    5 no answer    6 no relay reachable
    """.trimIndent()

    private fun status(): String = """
        amber status

        Shows the saved connection for --profile (default "default"): the app name,
        the connection key, the account it signs as, its relays, and whether Amber
        answers right now. Use it before a batch of requests.

        Output (plain)
          profile: default
          name: amber-cli
          signer: <connection key hex>
          user: <account key hex>
          relays: wss://..., ws://...
          state: online | offline

        With --json the same as {"ok":true,"result":{"profile":...,"online":true,...}}.

        EXIT CODES
          0 online    3 not paired (or still waiting for approval)    5 offline
    """.trimIndent()

    private fun logout(): String = """
        amber logout

        Sends NIP-46 logout (Amber forgets the connection and its grants) and
        deletes the saved profile here. Amber can also delete it from its own
        screen: deleting it there makes every later command exit with code 4.

        If Amber does not answer, the local profile is deleted anyway and a warning
        says so; delete the connection in Amber too.

        EXIT CODES
          0 logged out    3 nothing to log out
    """.trimIndent()
}
