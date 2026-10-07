#![doc = include_str!("../README.md")]
// @@ begin lint list maintained by maint/add_warning @@
#![allow(renamed_and_removed_lints)] // @@REMOVE_WHEN(ci_arti_stable)
#![allow(unknown_lints)] // @@REMOVE_WHEN(ci_arti_nightly)
#![warn(missing_docs)]
#![warn(noop_method_call)]
#![warn(unreachable_pub)]
#![warn(clippy::all)]
#![deny(clippy::await_holding_lock)]
#![deny(clippy::cargo_common_metadata)]
#![deny(clippy::cast_lossless)]
#![deny(clippy::checked_conversions)]
#![allow(clippy::cognitive_complexity)] // See arti#2556
#![deny(clippy::debug_assert_with_mut_call)]
#![deny(clippy::exhaustive_enums)]
#![deny(clippy::exhaustive_structs)]
#![deny(clippy::expl_impl_clone_on_copy)]
#![deny(clippy::fallible_impl_from)]
#![deny(clippy::implicit_clone)]
#![deny(clippy::large_stack_arrays)]
#![warn(clippy::manual_ok_or)]
#![deny(clippy::missing_docs_in_private_items)]
#![warn(clippy::needless_borrow)]
#![warn(clippy::needless_pass_by_value)]
#![warn(clippy::option_option)]
#![deny(clippy::print_stderr)]
#![deny(clippy::print_stdout)]
#![warn(clippy::rc_buffer)]
#![deny(clippy::ref_option_ref)]
#![warn(clippy::semicolon_if_nothing_returned)]
#![warn(clippy::trait_duplication_in_bounds)]
#![deny(clippy::unchecked_time_subtraction)]
#![deny(clippy::unnecessary_wraps)]
#![warn(clippy::unseparated_literal_suffix)]
#![deny(clippy::unwrap_used)]
#![deny(clippy::mod_module_files)]
#![allow(clippy::let_unit_value)] // This can reasonably be done for explicitness
#![allow(clippy::uninlined_format_args)]
#![allow(clippy::significant_drop_in_scrutinee)] // arti/-/merge_requests/588/#note_2812945
#![allow(clippy::result_large_err)] // temporary workaround for arti#587
#![allow(clippy::needless_raw_string_hashes)] // complained-about code is fine, often best
#![allow(clippy::needless_lifetimes)] // See arti#1765
#![allow(mismatched_lifetime_syntaxes)] // temporary workaround for arti#2060
#![allow(clippy::collapsible_if)] // See arti#2342
#![deny(clippy::unused_async)]
#![deny(clippy::string_slice)] // See arti#2571
//! <!-- @@ end lint list maintained by maint/add_warning @@ -->
#![allow(unexpected_cfgs)]
#![forbid(unsafe_code)]
#![cfg_attr(
    saturating_time_nightly,
    feature(time_systemtime_limits, time_saturating_systemtime)
)]

use std::time::{Duration, Instant, SystemTime};

mod internal;

/// The core trait of this crait, [`SaturatingTime`].
///
/// This trait provides methods for performing saturating arithmetic on those
/// types in [`std::time`] that not already come with such a functionality,
/// such as [`SystemTime`] or [`Instant`].
///
/// The trait itself is not implementable from the outside, because it is sealed
/// by an internal trait.
///
/// See the methods or the top-level documentation for concrete code examples.
pub trait SaturatingTime: internal::SaturatingTime {
    /// Returns the maximum value for this type on the current platform.
    ///
    /// This limit is highly platform specific.  It differs heavily between
    /// Unix, Windows, and other operating systems.
    ///
    /// The limit itself is calculated dynamically during runtime with a correct
    /// algorithm.  Afterwards, it gets stored in a lazy static value, meaning
    /// that only the first call to it will be slightly more expensive, whereas
    /// all latter calls will result in an immediate return of the value.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::time::{Duration, SystemTime};
    /// use saturating_time::SaturatingTime;
    ///
    /// let max = SystemTime::max_value();
    ///
    /// // Adding zero to the maximum value will change nothing.
    /// assert!(max.checked_add(Duration::ZERO).is_some());
    ///
    /// // Adding 1ns to the maximum value will fail.
    /// assert!(max.checked_add(Duration::new(0, 1)).is_none());
    ///
    /// // Subtracting 1ns from the maximum value will work of course.
    /// assert!(max.checked_sub(Duration::new(0, 1)).is_some());
    /// ```
    fn max_value() -> Self {
        internal::SaturatingTime::max_value()
    }

    /// Returns the minimum value for this type on the current platform.
    ///
    /// This limit is highly platform specific.  It differs heavily between
    /// Unix, Windows, and other operating systems.
    ///
    /// The limit itself is calculated dynamically during runtime with a correct
    /// algorithm.  Afterwards, it gets stored in a lazy static value, meaning
    /// that only the first call to it will be slightly more expensive, whereas
    /// all latter calls will result in an immediate return of the value.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::time::{Duration, SystemTime};
    /// use saturating_time::SaturatingTime;
    ///
    /// let min = SystemTime::min_value();
    ///
    /// // Subtracting a zero from the minimum value will change nothing.
    /// assert!(min.checked_sub(Duration::ZERO).is_some());
    ///
    /// // Subtracting 1ns from the minimum value will fail.
    /// assert!(min.checked_sub(Duration::new(0, 1)).is_none());
    ///
    /// // Adding 1ns to the minimum value will work of course.
    /// assert!(min.checked_add(Duration::new(0, 1)).is_some());
    /// ```
    fn min_value() -> Self {
        internal::SaturatingTime::min_value()
    }

    /// Performs a saturating addition of a [`Duration`].
    ///
    /// The resulting value will saturate to [`SaturatingTime::max_value()`] in
    /// the case the addition would have caused an overflow of value.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::time::{Duration, SystemTime};
    /// use saturating_time::SaturatingTime;
    ///
    /// let max = SystemTime::max_value();
    ///
    /// // Adding zero will change nothing.
    /// assert_eq!(max.saturating_add(Duration::ZERO), max);
    ///
    /// // Adding 1ns would overflow so we saturate to the maximum.
    /// assert_eq!(max.saturating_add(Duration::new(0, 1)), max);
    /// ```
    fn saturating_add(self, duration: Duration) -> Self {
        self.checked_add(duration)
            .unwrap_or(SaturatingTime::max_value())
    }

    /// Performs a saturating subtraction of a [`Duration`].
    ///
    /// The resulting value will saturate to [`SaturatingTime::min_value()`] in
    /// the case the subtraction would have caused an overflow of value.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::time::{Duration, SystemTime};
    /// use saturating_time::SaturatingTime;
    ///
    /// let min = SystemTime::min_value();
    ///
    /// // Subtracting zero will change nothing.
    /// assert_eq!(min.saturating_sub(Duration::ZERO), min);
    ///
    /// // Subtracting 1ns would overflow so we saturate to the minimum.
    /// assert_eq!(min.saturating_sub(Duration::new(0, 1)), min);
    /// ```
    fn saturating_sub(self, duration: Duration) -> Self {
        self.checked_sub(duration)
            .unwrap_or(SaturatingTime::min_value())
    }

    /// Performs a saturating time difference calculation between two points.
    ///
    /// The resulting value will saturate to [`Duration::ZERO`] in the case that
    /// the `earlier` point in time is actually not earlier, thereby resulting
    /// in a negative difference.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::time::{Duration, SystemTime};
    /// use saturating_time::SaturatingTime;
    ///
    /// let epoch = SystemTime::UNIX_EPOCH;
    /// let now = SystemTime::now();
    /// let min = SystemTime::min_value();
    ///
    /// assert!(now.saturating_duration_since(epoch).as_secs() > 0);
    /// assert!(epoch.saturating_duration_since(epoch) == Duration::ZERO);
    /// assert!(min.saturating_duration_since(epoch) == Duration::ZERO);
    /// ```
    fn saturating_duration_since(&self, earlier: Self) -> Duration {
        self.checked_duration_since(earlier)
            .unwrap_or(Duration::ZERO)
    }
}

// Use nightly implementation if compiled with the nightly feature.
#[cfg(saturating_time_nightly)]
impl SaturatingTime for SystemTime {
    fn max_value() -> Self {
        Self::MAX
    }

    fn min_value() -> Self {
        Self::MIN
    }

    fn saturating_add(self, duration: Duration) -> Self {
        Self::saturating_add(&self, duration)
    }

    fn saturating_sub(self, duration: Duration) -> Self {
        Self::saturating_sub(&self, duration)
    }

    fn saturating_duration_since(&self, earlier: Self) -> Duration {
        Self::saturating_duration_since(&self, earlier)
    }
}

// Otherwise, use the default one.
#[cfg(not(saturating_time_nightly))]
impl SaturatingTime for SystemTime {}

impl SaturatingTime for Instant {
    // Override to use the provided implementation from the standard library.
    fn saturating_duration_since(&self, earlier: Self) -> Duration {
        Self::saturating_duration_since(self, earlier)
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

    use super::*;
    use crate::internal;
    use std::{
        fmt::Debug,
        ops::{Add, Sub},
        time::{Instant, SystemTime},
    };

    /// Verifies the maximum and minimum values of [`SaturatingTime`] equal
    /// their pedant in [`internal::SaturatingTime`].
    fn min_max<T: SaturatingTime + PartialEq + Debug>() {
        assert_eq!(
            <T as SaturatingTime>::max_value(),
            <T as internal::SaturatingTime>::max_value()
        );
        assert_eq!(
            <T as SaturatingTime>::min_value(),
            <T as internal::SaturatingTime>::min_value()
        );
    }

    /// Verifies the saturating arithmetic for [`SaturatingTime`].
    fn saturating_add_sub<
        T: SaturatingTime + PartialEq + Debug + Add<Duration, Output = T> + Sub<Duration, Output = T>,
    >() {
        let max = <T as SaturatingTime>::max_value();
        assert_eq!(max.saturating_add(Duration::ZERO), max);
        assert_eq!(max.saturating_add(Duration::new(0, 1)), max);
        assert_eq!(max.saturating_sub(Duration::ZERO), max);
        assert_eq!(
            max.saturating_sub(Duration::new(0, 1)),
            max - Duration::new(0, 1)
        );

        let min = <T as SaturatingTime>::min_value();
        assert_eq!(min.saturating_sub(Duration::ZERO), min);
        assert_eq!(min.saturating_sub(Duration::new(0, 1)), min);
        assert_eq!(min.saturating_add(Duration::ZERO), min);
        assert_eq!(
            min.saturating_add(Duration::new(0, 1)),
            min + Duration::new(0, 1)
        );
    }

    /// Verifies whether the saturating logic behind [`Duration`] types work.
    fn saturating_duration<T: SaturatingTime + PartialEq + Debug>() {
        // The duration from the same anchor should always be zero.
        let anchor = T::anchor();
        assert_eq!(anchor.saturating_duration_since(anchor), Duration::ZERO);

        // Try with a later anchor.
        let later_anchor = anchor.checked_add(Duration::from_secs(1)).unwrap();
        assert!(later_anchor.saturating_duration_since(anchor) == Duration::from_secs(1));
        assert_eq!(
            anchor.saturating_duration_since(later_anchor),
            Duration::ZERO
        );

        // Try with min and max.
        let max = <T as SaturatingTime>::max_value();
        let min = <T as SaturatingTime>::min_value();

        // This first assertion might not be so portable, maybe remove it if
        // this becomes a problem.
        assert_eq!(max.saturating_duration_since(min), Duration::MAX);
        assert_eq!(min.saturating_duration_since(max), Duration::ZERO);
    }

    /// Calls [`min_max()`] using [`SystemTime`].
    #[test]
    fn system_time_min_max() {
        min_max::<SystemTime>();
    }

    /// Calls [`min_max()`] using [`Instant`].
    #[test]
    fn instant_min_max() {
        min_max::<Instant>();
    }

    /// Calls [`saturating_add_sub()`] and [`saturating_duration()`] using [`SystemTime`].
    #[test]
    fn system_time_saturating() {
        saturating_add_sub::<SystemTime>();
        saturating_duration::<SystemTime>();
    }

    /// Calls [`saturating_add_sub()`] and [`saturating_duration()`] using [`Instant`].
    #[test]
    fn instant_saturating() {
        saturating_add_sub::<Instant>();
        saturating_duration::<Instant>();
    }
}
