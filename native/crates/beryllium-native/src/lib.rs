#[cfg(feature = "cubecl-preview")]
mod cubecl_preview;
mod biome;
mod error;
mod ffi;
mod kernel;
mod noise;
mod simd;

pub use error::NativeError;
pub use ffi::{
    NativeStatus, beryllium_compute_potential_energy_change, beryllium_compute_squared_distances,
    beryllium_compute_squared_distances_double, beryllium_count_within_radius,
    beryllium_filter_intersecting_aabb_double, beryllium_filter_within_aabb_double,
    beryllium_filter_within_exclusive_chunk_distance, beryllium_filter_within_radii_double,
    beryllium_filter_within_radius, beryllium_filter_within_radius_double,
    beryllium_filter_within_radius_exclusive_double, beryllium_find_nearest_block_center_index,
    beryllium_find_nearest_block_center_index_prefix, beryllium_find_nearest_block_corner_index,
    beryllium_find_nearest_block_corner_index_within_radius, beryllium_find_nearest_index_double,
    beryllium_find_nearest_index_exclusive_double,
    beryllium_find_nearest_packed_block_corner_index,
    beryllium_find_nearest_packed_block_corner_index_within_radius,
    beryllium_has_any_within_radius_exclusive_double, beryllium_interpolate_density_cells,
    beryllium_potential_clear_cache, beryllium_potential_compute_cached,
    beryllium_potential_cubecl_status, beryllium_potential_set_charges,
    beryllium_select_nearest_chunk_indices,
    beryllium_select_nearest_indices_within_radius_exclusive_double,
    beryllium_sort_by_block_distance, beryllium_sort_by_distance,
    beryllium_sort_by_distance_and_count_within_radius_exclusive_double,
    beryllium_sort_by_distance_double, beryllium_sort_within_radius_exclusive_double,
};
pub use kernel::{
    clear_cached_potential_charges, compute_cached_potential_energy_change,
    compute_squared_distances, count_within_radius, find_nearest_block_center_index,
    find_nearest_block_corner_index, find_nearest_block_corner_index_within_radius,
    find_nearest_packed_block_corner_index, find_nearest_packed_block_corner_index_within_radius,
    interpolate_density_cells, potential_energy_change, select_nearest_chunk_indices,
    select_nearest_indices_within_radius_f64_exclusive, set_cached_potential_charges,
    sort_by_distance_and_count_within_radius_f64_exclusive, sort_by_distance_f64,
    sort_within_radius_f64_exclusive,
};
