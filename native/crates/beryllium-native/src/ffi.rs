use crate::{
    kernel::compute_squared_distances, kernel::compute_squared_distances_f64,
    kernel::count_within_radius, kernel::filter_intersecting_aabb_f64,
    kernel::filter_within_aabb_f64, kernel::filter_within_exclusive_chunk_distance,
    kernel::filter_within_radii_f64, kernel::filter_within_radius,
    kernel::filter_within_radius_f64, kernel::filter_within_radius_f64_exclusive,
    kernel::find_nearest_block_center_index, kernel::find_nearest_block_corner_index,
    kernel::find_nearest_block_corner_index_within_radius, kernel::find_nearest_index_f64,
    kernel::find_nearest_index_f64_exclusive, kernel::has_any_within_radius_f64_exclusive,
    kernel::potential_energy_change, kernel::select_nearest_chunk_indices,
    kernel::sort_by_block_distance, kernel::sort_by_distance,
    kernel::sort_by_distance_and_count_within_radius_f64_exclusive, kernel::sort_by_distance_f64,
    kernel::sort_within_radius_f64_exclusive, NativeError,
};

/// Result code returned by the stable C ABI.
#[repr(i32)]
#[derive(Debug, Copy, Clone, Eq, PartialEq)]
pub enum NativeStatus {
    /// The call succeeded.
    Ok = 0,
    /// The caller passed invalid input or a null non-empty buffer.
    InvalidInput = 1,
    /// The output buffer had an unexpected size.
    OutputLengthMismatch = 2,
}

impl NativeStatus {
    fn code(self) -> i32 {
        self as i32
    }
}

impl From<NativeError> for NativeStatus {
    fn from(error: NativeError) -> Self {
        match error {
            NativeError::InvalidInput => Self::InvalidInput,
            NativeError::OutputLengthMismatch => Self::OutputLengthMismatch,
        }
    }
}

unsafe fn read_slice<'a, T>(pointer: *const T, length: usize) -> Result<&'a [T], NativeStatus> {
    if length == 0 {
        return Ok(&[]);
    }
    if pointer.is_null() {
        return Err(NativeStatus::InvalidInput);
    }
    Ok(unsafe { std::slice::from_raw_parts(pointer, length) })
}

unsafe fn write_slice<'a, T>(pointer: *mut T, length: usize) -> Result<&'a mut [T], NativeStatus> {
    if length == 0 {
        return Ok(&mut []);
    }
    if pointer.is_null() {
        return Err(NativeStatus::InvalidInput);
    }
    Ok(unsafe { std::slice::from_raw_parts_mut(pointer, length) })
}

fn status_result(result: Result<(), NativeError>) -> i32 {
    match result {
        Ok(()) => NativeStatus::Ok.code(),
        Err(error) => NativeStatus::from(error).code(),
    }
}

fn count_result(result: Result<usize, NativeError>) -> i32 {
    match result {
        Ok(count) => i32::try_from(count).unwrap_or(-1 - NativeStatus::InvalidInput.code()),
        Err(error) => -1 - NativeStatus::from(error).code(),
    }
}

fn index_result(result: Result<Option<usize>, NativeError>) -> i32 {
    match result {
        Ok(Some(index)) => i32::try_from(index).unwrap_or(-1 - NativeStatus::InvalidInput.code()),
        Ok(None) => -1,
        Err(error) => -1 - NativeStatus::from(error).code(),
    }
}

fn boolean_result(result: Result<bool, NativeError>) -> i32 {
    match result {
        Ok(true) => 1,
        Ok(false) => 0,
        Err(error) => -1 - NativeStatus::from(error).code(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_compute_squared_distances(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: *const i32,
    positions_length: usize,
    output: *mut i64,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };

    status_result(compute_squared_distances(
        origin_x, origin_y, origin_z, positions, output,
    ))
}

#[no_mangle]
/// Selects packed chunk indices through the stable C ABI.
///
/// # Safety
/// Non-empty pointers must reference readable or writable buffers of their declared lengths.
pub unsafe extern "C" fn beryllium_select_nearest_chunk_indices(
    origin_x: i32,
    origin_z: i32,
    packed_chunk_positions: *const i64,
    positions_length: usize,
    limit: i32,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    if limit < 0 {
        return -1 - NativeStatus::InvalidInput.code();
    }
    let packed_chunk_positions =
        match unsafe { read_slice(packed_chunk_positions, positions_length) } {
            Ok(value) => value,
            Err(error) => return -1 - error.code(),
        };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(select_nearest_chunk_indices(
        origin_x,
        origin_z,
        packed_chunk_positions,
        limit as usize,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_compute_squared_distances_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut f64,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };

    status_result(compute_squared_distances_f64(
        origin_x, origin_y, origin_z, positions, output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_compute_potential_energy_change(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: *const i32,
    positions_length: usize,
    charges: *const f64,
    charges_length: usize,
    charge_multiplier: f64,
    output: *mut f64,
    output_length: usize,
) -> i32 {
    if output_length != 1 {
        return NativeStatus::OutputLengthMismatch.code();
    }
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let charges = match unsafe { read_slice(charges, charges_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };

    match potential_energy_change(
        origin_x,
        origin_y,
        origin_z,
        positions,
        charges,
        charge_multiplier,
    ) {
        Ok(value) => {
            output[0] = value;
            NativeStatus::Ok.code()
        }
        Err(error) => NativeStatus::from(error).code(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: *const i32,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_within_radius(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_count_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: *const i32,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(count_within_radius(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_within_radius_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_within_radius_f64(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_within_radius_exclusive_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_within_radius_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_within_exclusive_chunk_distance(
    origin_x: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_within_exclusive_chunk_distance(
        origin_x,
        origin_z,
        radius_squared,
        positions,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_within_radii_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: *const f64,
    positions_length: usize,
    radii_squared: *const f64,
    radii_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let radii_squared = match unsafe { read_slice(radii_squared, radii_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_within_radii_f64(
        origin_x,
        origin_y,
        origin_z,
        positions,
        radii_squared,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_find_nearest_index_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: *const f64,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    index_result(find_nearest_index_f64(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_find_nearest_index_exclusive_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: *const f64,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    index_result(find_nearest_index_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_has_any_within_radius_exclusive_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: *const f64,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    boolean_result(has_any_within_radius_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_find_nearest_block_center_index(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: *const i32,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    index_result(find_nearest_block_center_index(
        origin_x, origin_y, origin_z, positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_find_nearest_block_center_index_prefix(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: *const i32,
    positions_length: usize,
    position_count: i32,
) -> i32 {
    let position_count = match usize::try_from(position_count) {
        Ok(value) => value,
        Err(_) => return -1 - NativeStatus::InvalidInput.code(),
    };
    let used_length = match position_count.checked_mul(3) {
        Some(value) if value <= positions_length => value,
        _ => return -1 - NativeStatus::InvalidInput.code(),
    };
    let positions = match unsafe { read_slice(positions, used_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    index_result(find_nearest_block_center_index(
        origin_x, origin_y, origin_z, positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_find_nearest_block_corner_index(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: *const i32,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    index_result(find_nearest_block_corner_index(
        origin_x, origin_y, origin_z, positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_find_nearest_block_corner_index_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: *const i32,
    positions_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    index_result(find_nearest_block_corner_index_within_radius(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_within_aabb_double(
    min_x: f64,
    min_y: f64,
    min_z: f64,
    max_x: f64,
    max_y: f64,
    max_z: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_within_aabb_f64(
        min_x, min_y, min_z, max_x, max_y, max_z, positions, output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_filter_intersecting_aabb_double(
    query_min_x: f64,
    query_min_y: f64,
    query_min_z: f64,
    query_max_x: f64,
    query_max_y: f64,
    query_max_z: f64,
    boxes: *const f64,
    boxes_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let boxes = match unsafe { read_slice(boxes, boxes_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(filter_intersecting_aabb_f64(
        query_min_x,
        query_min_y,
        query_min_z,
        query_max_x,
        query_max_y,
        query_max_z,
        boxes,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_sort_by_distance(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: *const i32,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    status_result(sort_by_distance(
        origin_x, origin_y, origin_z, positions, output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_sort_by_block_distance(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: *const i32,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    status_result(sort_by_block_distance(
        origin_x, origin_y, origin_z, positions, output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_sort_by_distance_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return error.code(),
    };
    status_result(sort_by_distance_f64(
        origin_x, origin_y, origin_z, positions, output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_sort_by_distance_and_count_within_radius_exclusive_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(sort_by_distance_and_count_within_radius_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    ))
}

#[no_mangle]
pub unsafe extern "C" fn beryllium_sort_within_radius_exclusive_double(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: *const f64,
    positions_length: usize,
    output: *mut i32,
    output_length: usize,
) -> i32 {
    let positions = match unsafe { read_slice(positions, positions_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    let output = match unsafe { write_slice(output, output_length) } {
        Ok(value) => value,
        Err(error) => return -1 - error.code(),
    };
    count_result(sort_within_radius_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    ))
}

#[cfg(test)]
mod tests {
    use super::{
        beryllium_compute_squared_distances, beryllium_count_within_radius,
        beryllium_filter_within_radius_double,
    };

    #[test]
    fn c_abi_distance_entry_point_writes_native_output() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0_i64; 3];
        let status = unsafe {
            beryllium_compute_squared_distances(
                0,
                64,
                0,
                positions.as_ptr(),
                positions.len(),
                output.as_mut_ptr(),
                output.len(),
            )
        };

        assert_eq!(status, 0);
        assert_eq!(output, [0, 41, 6]);
    }

    #[test]
    fn c_abi_zero_length_null_pointer_is_valid() {
        let count = unsafe { beryllium_count_within_radius(0, 0, 0, 1, std::ptr::null(), 0) };
        assert_eq!(count, 0);
    }

    #[test]
    fn c_abi_filter_preserves_output_tail() {
        let positions = [0.0, 0.0, 0.0, 2.0, 0.0, 0.0];
        let mut output = [77, 88];
        let count = unsafe {
            beryllium_filter_within_radius_double(
                0.0,
                0.0,
                0.0,
                1.0,
                positions.as_ptr(),
                positions.len(),
                output.as_mut_ptr(),
                output.len(),
            )
        };

        assert_eq!(count, 1);
        assert_eq!(output, [0, 88]);
    }
}
