# `saturating-time`

A trait for limits and saturations on types inside [`std::time`].

* **Easy**: `saturating-time` only adds a single trait, [`SaturatingTime`] that
  is implemented for various types from the standard library.
* **Future-Proof**: In the case that [`SaturatingTime::saturating_add()`] and
  [`SaturatingTime::saturating_sub()`] become a part of the standard library,
  developers would only have to remove the `use saturating_time::SaturatingTime`
  line from their code.[^1] [^2].
* **Portable**: The algorithm for determining the limits is portable across
  operating systems: enjoy this crate from Windows, Darwin, and Linux, across
  the BSD systems, up to exotic ones such as Hermit OS and Redox.
* **Secure**: This crate does not make any use of `unsafe` Rust code.

## Overview

`saturating-time` is a very minimal crate that only exposes a minimal trait:
[`SaturatingTime`].

The trait itself offers the following methods:
* [`SaturatingTime::max_value()`] – Returns the maximum value for this type.
* [`SaturatingTime::min_value()`] – Returns the minimum value for this type.
* [`SaturatingTime::saturating_add()`] – Saturating addition for this type.
* [`SaturatingTime::saturating_sub()`] – Saturating subtraction for this type.
* [`SaturatingTime::saturating_duration_since()`] - Saturating time deltas for this type.

This trait is sealed, meaning applications may not implement it themselves.
However, this crate implements this trait for two structures:
* [`std::time::Instant`]
* [`std::time::SystemTime`]

## Example

Add `saturating-time` to your `Cargo.toml`.

If you use Rust nightly, you may want to additionally configure the following
in your `.cargo/config.toml`:
```toml
[build]
rustflags = ["--cfg", "saturating_time_nightly"]
```

Now, you can use `saturating-time` in your code:
```rust
use std::time::{Duration, SystemTime};
use saturating_time::SaturatingTime;

// Get the maximum and minimum.
let max = SystemTime::max_value();
let min = SystemTime::min_value();

assert_eq!(max.saturating_add(Duration::new(1, 0)), max);
assert_eq!(min.saturating_sub(Duration::new(1, 0)), min);
assert!(max.saturating_duration_since(SystemTime::UNIX_EPOCH) >= Duration::ZERO);
```

## Standardization Efforts

The eventual goal is to get this functionality into the Rust standard library.

### `SystemTime`

#### `SystemTime::MIN` and `SystemTime::MAX`

In December 2025, `SystemTime::MIN` and `SystemTime::MAX` got merged
into nightly.[^3]

This feature is guarded behind `time_systemtime_limits` and a tracking
issue regarding the stabilization of it exists.[^4]

#### `SystemTime::saturating_add()`, et, al.

In January 2025, `SystemTime::saturating_add()`, `SystemTime::saturating_sub()`,
and `SystemTime::saturating_duration_since()` got merged into nightly.[^5]

This feature is guarded behind `time_saturating_systemtime` and a tracking
issue regarding the stabilization of it exists.[^6]

### `Instant`

None yet.

[^1]: This is an effort the maintainers are actively working upon.
[^2]: Assuming the name, signature, and behavior does not change.
      Unfortunately, we likely have to change the signature for the parameters
      because we currently use `self` whereas the standard library uses `&self`.
      It should not be a big problem though, because both `Instant` and
      `SystemTime` implement `Copy`.
[^3]: <https://github.com/rust-lang/rust/pull/149979>
[^4]: <https://github.com/rust-lang/rust/issues/149067>
[^5]: <https://github.com/rust-lang/rust/pull/151200>
[^6]: <https://github.com/rust-lang/rust/issues/151199>

## History

Up until (and including) version 0.4.0, `saturating-time` was maintained as an
independent project hosted on [Codeberg](https://codeberg.org/cve/saturating-time).

Starting with version 0.5.0, it has been incorporated into the
[Arti](https://arti.torproject.org) and is therefore maintained by
*The Tor Project, Inc.*

## License and Copyright

This crate is licensed under `MIT OR Apache-2.0`.
