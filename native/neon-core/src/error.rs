/// What can go wrong when the core reads an addon's answer.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    /// The answer was not JSON, or not the shape the Stremio protocol describes.
    #[error("bad addon response: {reason}")]
    BadResponse { reason: String },
    /// The text given as an addon address cannot be one.
    #[error("not an addon address: {input}")]
    BadAddress { input: String },
    /// An account (Stremio, Trakt) no longer accepts the saved sign-in: sign in again.
    #[error("signed out: {reason}")]
    SignedOut { reason: String },
}

impl From<serde_json::Error> for CoreError {
    fn from(e: serde_json::Error) -> Self {
        CoreError::BadResponse { reason: e.to_string() }
    }
}
