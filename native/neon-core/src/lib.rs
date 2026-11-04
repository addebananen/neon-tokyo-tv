//! Neon Tokyo's shared core.
//!
//! Everything here is plain logic with no network, disk or UI: the apps fetch a URL that the core
//! builds, hand the response text back, and get typed models out. The same code runs in the
//! Android TV app, the iPhone and Apple TV apps (Swift, through UniFFI) and the Windows app
//! (Kotlin on the JVM, through UniFFI), so a fix here reaches all of them in one change.
//!
//! The rules mirror the Android app's own (domain/ContinueWatching.kt, domain/EpisodeUtils.kt,
//! ui/player/PlayerViewModel.kt) so every platform agrees on what "watched" and "next" mean.

uniffi::setup_scaffolding!();

pub mod addon;
pub mod error;
pub mod starter;
pub mod stream;
pub mod sync;
pub mod watch;

pub use addon::*;
pub use error::CoreError;
pub use starter::*;
pub use stream::*;
pub use sync::*;
pub use watch::*;

/// The core's version, shown in the apps' About screen so a bug report names the core it ran.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
