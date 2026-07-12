use thiserror::Error;

/// Errors returned by the native backend.
#[derive(Debug, Error, PartialEq, Eq)]
pub enum NativeError {
    /// The caller passed invalid input.
    #[error("invalid input")]
    InvalidInput,

    /// The output buffer does not match the expected size.
    #[error("output length mismatch")]
    OutputLengthMismatch,
}
