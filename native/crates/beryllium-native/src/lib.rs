mod error;
mod ffi;
mod kernel;

pub use error::NativeError;
pub use ffi::{
    Java_alku_beryllium_bridge_NativeBridge_computeSquaredDistancesNative,
    Java_alku_beryllium_bridge_NativeBridge_findNearestBlockCenterIndexNative,
    Java_alku_beryllium_bridge_NativeBridge_findNearestBlockCornerIndexNative,
    Java_alku_beryllium_bridge_NativeBridge_sortByDistanceDoubleNative,
    Java_alku_beryllium_bridge_NativeBridge_sortWithinRadiusExclusiveDoubleNative, NativeStatus,
};
pub use kernel::{
    compute_squared_distances, find_nearest_block_center_index, find_nearest_block_corner_index,
    sort_by_distance_f64, sort_within_radius_f64_exclusive,
};
