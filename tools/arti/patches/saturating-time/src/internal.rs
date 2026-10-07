//! Internal parts used for sealing.
//!
//! This module primarily consists of the internal [`SaturatingTime`] trait, an
//! unstable abstraction used internally to implement the main logic behind
//! this.
//!
//! Normal users should not be using this.

use std::{
    cmp,
    sync::LazyLock,
    time::{Duration, Instant, SystemTime},
};

/// The maximum value of [`SystemTime`] for this platform.
static MAX_SYSTEM_TIME: LazyLock<SystemTime> = LazyLock::new(find_max);

/// The minimum value of [`SystemTime`] for this platform.
static MIN_SYSTEM_TIME: LazyLock<SystemTime> = LazyLock::new(find_min);

/// The maximum value of [`Instant`] for this platform.
static MAX_INSTANT: LazyLock<Instant> = LazyLock::new(find_max);

/// The minimum value of [`Instant`] for this platform.
static MIN_INSTANT: LazyLock<Instant> = LazyLock::new(find_min);

/// An internal trait implementing the actual magic behind this.
pub trait SaturatingTime: Sized + Copy + PartialEq {
    /// Anchor method to obtain an instance of this type.
    fn anchor() -> Self;

    /// Returns the maximum value of this type.
    fn max_value() -> Self;

    /// Returns the minimum value of this type.
    fn min_value() -> Self;

    /// Performs a checked addition on this type.
    fn checked_add(&self, duration: Duration) -> Option<Self>;

    /// Performs a checked subtraction on this type.
    fn checked_sub(&self, duration: Duration) -> Option<Self>;

    /// Performs a checked time delta on this type.
    fn checked_duration_since(&self, earlier: Self) -> Option<Duration>;
}

impl SaturatingTime for SystemTime {
    fn anchor() -> Self {
        Self::UNIX_EPOCH
    }

    fn max_value() -> Self {
        *MAX_SYSTEM_TIME
    }

    fn min_value() -> Self {
        *MIN_SYSTEM_TIME
    }

    fn checked_add(&self, duration: Duration) -> Option<Self> {
        Self::checked_add(self, duration)
    }

    fn checked_sub(&self, duration: Duration) -> Option<Self> {
        Self::checked_sub(self, duration)
    }

    fn checked_duration_since(&self, earlier: Self) -> Option<Duration> {
        Self::duration_since(self, earlier).ok()
    }
}

impl SaturatingTime for Instant {
    fn anchor() -> Self {
        use web_time_compat::InstantExt;
        Self::get()
    }

    fn max_value() -> Self {
        *MAX_INSTANT
    }

    fn min_value() -> Self {
        *MIN_INSTANT
    }

    fn checked_add(&self, duration: Duration) -> Option<Self> {
        Self::checked_add(self, duration)
    }

    fn checked_sub(&self, duration: Duration) -> Option<Self> {
        Self::checked_sub(self, duration)
    }

    /// DO NOT USE!
    ///
    /// Instead, override the top-level provided implementation with the already
    /// existing [`Instant::saturating_duration_since()`].
    fn checked_duration_since(&self, _earlier: Self) -> Option<Duration> {
        unreachable!()
    }
}

/// Finds the value for [`SaturatingTime::max_value()`].
fn find_max<T: SaturatingTime>() -> T {
    find_limit(T::checked_add)
}

/// Finds the value for [`SaturatingTime::min_value()`].
fn find_min<T: SaturatingTime>() -> T {
    find_limit(T::checked_sub)
}

/// Internal algorithm of [`find_max()`] and [`find_min()`].
///
/// It works by performing `f` with a very large [`Duration`] onto
/// [`SaturatingTime::anchor()`] until this call returns [`None`], in which case
/// this [`Duration`] gets halved.  This process is repeated until `f` returns
/// [`None`] and the [`Duration`] has reached 1ns.
///
/// # Algorithm
///
/// 1. Set `step` to `INITIAL_STEP` and `res` to [`SaturatingTime::anchor()`].
/// 2. Call `f(&res, step)`.
///     1. If [`Some`], set `res` to the returned value and continue.
///     2. If [`None`] and `step == 1ns`, return `res`.
///     3. Else, set `step` to `MAX{1ns, step / 2}` and continue.
fn find_limit<T, F>(f: F) -> T
where
    T: SaturatingTime,
    F: Fn(&T, Duration) -> Option<T>,
{
    const INITIAL_STEP: Duration = Duration::new(1_000_000_000_000_000_000, 0);
    const ONE_NS: Duration = Duration::new(0, 1);

    // (1) Set step to INITIAL_STEP and res to T::anchor().
    let mut step = INITIAL_STEP;
    let mut res = T::anchor();

    loop {
        // (2) Call f().
        let next = f(&res, step);
        match next {
            // Amber patch: a step finer than the clock's resolution comes back
            // unchanged (Windows' SystemTime ticks in 100ns, so 1-99ns
            // round to 0) instead of None; treat "no progress" like None,
            // or this loops forever at step == 1ns.
            Some(st) if st != res => {
                // (2.1) If Some, set res to the returned value and continue.
                res = st;
            }
            _ => {
                if step == ONE_NS {
                    // (2.2) If None and step == 1ns, return res.
                    return res;
                } else {
                    // (2.3) Else, set step to MAX{1ns, step / 2}.
                    step = cmp::max(ONE_NS, step / 2);
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    // @@ begin test lint list maintained by maint/add_warning @@
    #![allow(clippy::bool_assert_comparison)]
    #![allow(clippy::clone_on_copy)]
    #![allow(clippy::dbg_macro)]
    #![allow(clippy::mixed_attributes_style)]
    #![allow(clippy::print_stderr)]
    #![allow(clippy::print_stdout)]
    #![allow(clippy::single_char_pattern)]
    #![allow(clippy::unwrap_used)]
    #![allow(clippy::unchecked_time_subtraction)]
    #![allow(clippy::useless_vec)]
    #![allow(clippy::needless_pass_by_value)]
    #![allow(clippy::string_slice)] // See arti#2571
    //! <!-- @@ end test lint list maintained by maint/add_warning @@ -->

    use std::{
        fmt::Debug,
        ops::{Add, Sub},
    };

    use super::*;

    /// Checks whether the minimum and maximum values are correct.
    fn min_max<T>()
    where
        T: SaturatingTime
            + PartialEq
            + Debug
            + Add<Duration, Output = T>
            + Sub<Duration, Output = T>,
    {
        assert_eq!(
            T::max_value().checked_add(Duration::ZERO),
            Some(T::max_value())
        );
        assert_eq!(T::max_value().checked_add(Duration::new(0, 1)), None);
        assert_eq!(
            T::max_value().checked_sub(Duration::ZERO),
            Some(T::max_value())
        );
        assert_eq!(
            T::max_value().checked_sub(Duration::new(0, 1)),
            Some(T::max_value() - Duration::new(0, 1))
        );

        assert_eq!(
            T::min_value().checked_sub(Duration::ZERO),
            Some(T::min_value())
        );
        assert_eq!(T::min_value().checked_sub(Duration::new(0, 1)), None);
        assert_eq!(
            T::min_value().checked_add(Duration::ZERO),
            Some(T::min_value())
        );
        assert_eq!(
            T::min_value().checked_add(Duration::new(0, 1)),
            Some(T::min_value() + Duration::new(0, 1))
        );
    }

    /// Verifies [`SystemTime::min_value()`] and [`SystemTime::max_value()`] are
    /// correct.
    #[test]
    fn system_time_min_max() {
        min_max::<SystemTime>();
    }

    /// Verifies [`Instant::min_value()`] and [`Instant::max_value()`] are
    /// correct.
    #[test]
    fn instant_min_max() {
        min_max::<Instant>();
    }

    /// Verifies [`SystemTime::min_value()`] and [`SystemTime::max_value()`] are
    /// correct on Unix systems.
    #[cfg(target_family = "unix")]
    #[test]
    fn system_time_min_max_unix() {
        assert_eq!(
            SystemTime::max_value(),
            SystemTime::UNIX_EPOCH + Duration::new(i64::MAX as u64, 999_999_999)
        );
        assert_eq!(
            SystemTime::min_value(),
            SystemTime::UNIX_EPOCH - Duration::new(i64::MAX as u64 + 1, 0)
        );
    }

    /// Verifies that [`Instant::min_value()`] and [`Instant::max_value()`] are
    /// correct on Unix systems.
    #[test]
    #[cfg(target_family = "unix")]
    fn instant_min_max_unix() {
        // Using format is not nice but I cannot see a better way for now.
        assert_eq!(
            format!("{:?}", Instant::max_value()),
            format!(
                "Instant {{ tv_sec: {}, tv_nsec: {} }}",
                i64::MAX,
                999_999_999
            )
        );

        assert_eq!(
            format!("{:?}", Instant::min_value()),
            format!("Instant {{ tv_sec: {}, tv_nsec: {} }}", i64::MIN, 0)
        );
    }
}
