use jni::objects::{JClass, JDoubleArray, JIntArray, JLongArray};
use jni::sys::{jdouble, jint, jlong};
use jni::JNIEnv;

use crate::{
    kernel::compute_squared_distances, kernel::compute_squared_distances_f64,
    kernel::filter_intersecting_aabb_f64, kernel::filter_within_aabb_f64,
    kernel::filter_within_radii_f64, kernel::filter_within_radius,
    kernel::filter_within_radius_f64, kernel::filter_within_radius_f64_exclusive,
    kernel::find_nearest_block_center_index, kernel::find_nearest_block_corner_index,
    kernel::find_nearest_index_f64, kernel::find_nearest_index_f64_exclusive,
    kernel::has_any_within_radius_f64_exclusive, kernel::sort_by_block_distance,
    kernel::sort_by_distance, kernel::sort_by_distance_f64, NativeError,
};

/// Result code returned by the FFI layer.
#[repr(i32)]
#[derive(Debug, Copy, Clone, Eq, PartialEq)]
pub enum NativeStatus {
    /// The call succeeded.
    Ok = 0,
    /// The call failed with invalid input.
    InvalidInput = 1,
    /// The output buffer had an unexpected size.
    OutputLengthMismatch = 2,
    /// The JNI layer failed while copying data.
    Jni = 3,
}

impl From<NativeError> for NativeStatus {
    fn from(error: NativeError) -> Self {
        match error {
            NativeError::InvalidInput => Self::InvalidInput,
            NativeError::OutputLengthMismatch => Self::OutputLengthMismatch,
            NativeError::Jni => Self::Jni,
        }
    }
}

/// JNI entry point for batched squared-distance calculation.
#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_computeSquaredDistancesNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
    output: JLongArray<'_>,
) -> jint {
    compute_squared_distances_jni(env, origin_x, origin_y, origin_z, positions, output).code()
}

/// JNI entry point for batched squared-distance calculation on f64 positions.
#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_computeSquaredDistancesDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JDoubleArray<'_>,
    output: JDoubleArray<'_>,
) -> jint {
    compute_squared_distances_double_jni(env, origin_x, origin_y, origin_z, positions, output)
        .code()
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_filterWithinRadiusNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    radius_squared: jlong,
    positions: JIntArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    filter_within_radius_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_filterWithinRadiusDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    radius_squared: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    filter_within_radius_double_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_filterWithinRadiusExclusiveDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    radius_squared: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    filter_within_radius_exclusive_double_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_filterWithinRadiiDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JDoubleArray<'_>,
    radii_squared: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    filter_within_radii_double_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        positions,
        radii_squared,
        output,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_filterWithinAabbDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    min_x: jdouble,
    min_y: jdouble,
    min_z: jdouble,
    max_x: jdouble,
    max_y: jdouble,
    max_z: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    filter_within_aabb_double_jni(
        env, min_x, min_y, min_z, max_x, max_y, max_z, positions, output,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_filterIntersectingAabbDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    query_min_x: jdouble,
    query_min_y: jdouble,
    query_min_z: jdouble,
    query_max_x: jdouble,
    query_max_y: jdouble,
    query_max_z: jdouble,
    boxes: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    filter_intersecting_aabb_double_jni(
        env,
        query_min_x,
        query_min_y,
        query_min_z,
        query_max_x,
        query_max_y,
        query_max_z,
        boxes,
        output,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_findNearestIndexDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    max_distance_squared: jdouble,
    positions: JDoubleArray<'_>,
) -> jint {
    find_nearest_index_double_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_findNearestIndexExclusiveDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    max_distance_squared: jdouble,
    positions: JDoubleArray<'_>,
) -> jint {
    find_nearest_index_exclusive_double_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_hasAnyWithinRadiusExclusiveDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    max_distance_squared: jdouble,
    positions: JDoubleArray<'_>,
) -> jint {
    has_any_within_radius_exclusive_double_jni(
        env,
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
    )
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_findNearestBlockCenterIndexNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JIntArray<'_>,
) -> jint {
    find_nearest_block_center_index_jni(env, origin_x, origin_y, origin_z, positions)
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_findNearestBlockCornerIndexNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
) -> jint {
    find_nearest_block_corner_index_jni(env, origin_x, origin_y, origin_z, positions)
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_sortByDistanceNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    sort_by_distance_jni(env, origin_x, origin_y, origin_z, positions, output).code()
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_sortByBlockDistanceNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    sort_by_block_distance_jni(env, origin_x, origin_y, origin_z, positions, output).code()
}

#[no_mangle]
pub extern "system" fn Java_alku_beryllium_bridge_NativeBridge_sortByDistanceDoubleNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    sort_by_distance_double_jni(env, origin_x, origin_y, origin_z, positions, output).code()
}

fn compute_squared_distances_jni(
    env: JNIEnv<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
    output: JLongArray<'_>,
) -> NativeStatus {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };

    let mut positions_buffer = vec![0; positions_len];
    if env
        .get_int_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    let mut output_buffer = vec![0; output_len];
    if let Err(error) = compute_squared_distances(
        origin_x,
        origin_y,
        origin_z,
        &positions_buffer,
        &mut output_buffer,
    ) {
        return error.into();
    }

    if env
        .set_long_array_region(&output, 0, cast_i64_to_jlong(&output_buffer))
        .is_err()
    {
        return NativeStatus::Jni;
    }

    NativeStatus::Ok
}

fn compute_squared_distances_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JDoubleArray<'_>,
    output: JDoubleArray<'_>,
) -> NativeStatus {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    let mut output_buffer = vec![0.0; output_len];
    if let Err(error) = compute_squared_distances_f64(
        origin_x,
        origin_y,
        origin_z,
        &positions_buffer,
        &mut output_buffer,
    ) {
        return error.into();
    }

    if env
        .set_double_array_region(&output, 0, &output_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    NativeStatus::Ok
}

fn filter_within_radius_jni(
    env: JNIEnv<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    radius_squared: jlong,
    positions: JIntArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni.code(),
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni.code(),
    };

    let mut positions_buffer = vec![0; positions_len];
    if env
        .get_int_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return NativeStatus::Jni.code();
    }

    let mut output_buffer = vec![0; output_len];
    let count = match filter_within_radius(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        &positions_buffer,
        &mut output_buffer,
    ) {
        Ok(value) => value,
        Err(error) => return native_count_error_code(error.into()),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    count as jint
}

fn filter_within_radius_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    radius_squared: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    let mut output_buffer = vec![0; output_len];
    let count = match filter_within_radius_f64(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        &positions_buffer,
        &mut output_buffer,
    ) {
        Ok(value) => value,
        Err(error) => return native_count_error_code(error.into()),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    count as jint
}

fn filter_within_radius_exclusive_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    radius_squared: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    let mut output_buffer = vec![0; output_len];
    let count = match filter_within_radius_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        &positions_buffer,
        &mut output_buffer,
    ) {
        Ok(value) => value,
        Err(error) => return native_count_error_code(error.into()),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    count as jint
}

fn filter_within_radii_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JDoubleArray<'_>,
    radii_squared: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };
    let radii_len = match env.get_array_length(&radii_squared) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    let mut radii_squared_buffer = vec![0.0; radii_len];
    if env
        .get_double_array_region(&radii_squared, 0, &mut radii_squared_buffer)
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    let mut output_buffer = vec![0; output_len];
    let count = match filter_within_radii_f64(
        origin_x,
        origin_y,
        origin_z,
        &positions_buffer,
        &radii_squared_buffer,
        &mut output_buffer,
    ) {
        Ok(value) => value,
        Err(error) => return native_count_error_code(error.into()),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    count as jint
}

fn filter_within_aabb_double_jni(
    env: JNIEnv<'_>,
    min_x: jdouble,
    min_y: jdouble,
    min_z: jdouble,
    max_x: jdouble,
    max_y: jdouble,
    max_z: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    let mut output_buffer = vec![0; output_len];
    let count = match filter_within_aabb_f64(
        min_x,
        min_y,
        min_z,
        max_x,
        max_y,
        max_z,
        &positions_buffer,
        &mut output_buffer,
    ) {
        Ok(value) => value,
        Err(error) => return native_count_error_code(error.into()),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    count as jint
}

fn filter_intersecting_aabb_double_jni(
    env: JNIEnv<'_>,
    query_min_x: jdouble,
    query_min_y: jdouble,
    query_min_z: jdouble,
    query_max_x: jdouble,
    query_max_y: jdouble,
    query_max_z: jdouble,
    boxes: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> jint {
    let boxes_len = match env.get_array_length(&boxes) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return native_count_error_code(NativeStatus::Jni),
    };

    let mut boxes_buffer = vec![0.0; boxes_len];
    if env
        .get_double_array_region(&boxes, 0, &mut boxes_buffer)
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    let mut output_buffer = vec![0; output_len];
    let count = match filter_intersecting_aabb_f64(
        query_min_x,
        query_min_y,
        query_min_z,
        query_max_x,
        query_max_y,
        query_max_z,
        &boxes_buffer,
        &mut output_buffer,
    ) {
        Ok(value) => value,
        Err(error) => return native_count_error_code(error.into()),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return native_count_error_code(NativeStatus::Jni);
    }

    count as jint
}

fn find_nearest_index_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    max_distance_squared: jdouble,
    positions: JDoubleArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_index_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_index_error_code(NativeStatus::Jni);
    }

    match find_nearest_index_f64(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        &positions_buffer,
    ) {
        Ok(Some(index)) => index as jint,
        Ok(None) => -1,
        Err(error) => native_index_error_code(error.into()),
    }
}

fn find_nearest_index_exclusive_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    max_distance_squared: jdouble,
    positions: JDoubleArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_index_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_index_error_code(NativeStatus::Jni);
    }

    match find_nearest_index_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        &positions_buffer,
    ) {
        Ok(Some(index)) => index as jint,
        Ok(None) => -1,
        Err(error) => native_index_error_code(error.into()),
    }
}

fn has_any_within_radius_exclusive_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    max_distance_squared: jdouble,
    positions: JDoubleArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_boolean_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_boolean_error_code(NativeStatus::Jni);
    }

    match has_any_within_radius_f64_exclusive(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        &positions_buffer,
    ) {
        Ok(true) => 1,
        Ok(false) => 0,
        Err(error) => native_boolean_error_code(error.into()),
    }
}

fn find_nearest_block_center_index_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_index_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0; positions_len];
    if env
        .get_int_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_index_error_code(NativeStatus::Jni);
    }

    match find_nearest_block_center_index(origin_x, origin_y, origin_z, &positions_buffer) {
        Ok(Some(index)) => index as jint,
        Ok(None) => -1,
        Err(error) => native_index_error_code(error.into()),
    }
}

fn find_nearest_block_corner_index_jni(
    env: JNIEnv<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
) -> jint {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return native_index_error_code(NativeStatus::Jni),
    };

    let mut positions_buffer = vec![0; positions_len];
    if env
        .get_int_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return native_index_error_code(NativeStatus::Jni);
    }

    match find_nearest_block_corner_index(origin_x, origin_y, origin_z, &positions_buffer) {
        Ok(Some(index)) => index as jint,
        Ok(None) => -1,
        Err(error) => native_index_error_code(error.into()),
    }
}

fn sort_by_distance_jni(
    env: JNIEnv<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
    output: JIntArray<'_>,
) -> NativeStatus {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };

    let mut positions_buffer = vec![0; positions_len];
    if env
        .get_int_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    let mut output_buffer = vec![0; output_len];
    if let Err(error) = sort_by_distance(
        origin_x,
        origin_y,
        origin_z,
        &positions_buffer,
        &mut output_buffer,
    ) {
        return error.into();
    }

    if env
        .set_int_array_region(&output, 0, &output_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    NativeStatus::Ok
}

fn sort_by_block_distance_jni(
    env: JNIEnv<'_>,
    origin_x: jint,
    origin_y: jint,
    origin_z: jint,
    positions: JIntArray<'_>,
    output: JIntArray<'_>,
) -> NativeStatus {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };

    let mut positions_buffer = vec![0; positions_len];
    if env
        .get_int_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    let mut output_buffer = vec![0; output_len];
    if let Err(error) = sort_by_block_distance(
        origin_x,
        origin_y,
        origin_z,
        &positions_buffer,
        &mut output_buffer,
    ) {
        return error.into();
    }

    if env
        .set_int_array_region(&output, 0, &output_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    NativeStatus::Ok
}

fn sort_by_distance_double_jni(
    env: JNIEnv<'_>,
    origin_x: jdouble,
    origin_y: jdouble,
    origin_z: jdouble,
    positions: JDoubleArray<'_>,
    output: JIntArray<'_>,
) -> NativeStatus {
    let positions_len = match env.get_array_length(&positions) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };
    let output_len = match env.get_array_length(&output) {
        Ok(value) => value as usize,
        Err(_) => return NativeStatus::Jni,
    };

    let mut positions_buffer = vec![0.0; positions_len];
    if env
        .get_double_array_region(&positions, 0, &mut positions_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    let mut output_buffer = vec![0; output_len];
    if let Err(error) = sort_by_distance_f64(
        origin_x,
        origin_y,
        origin_z,
        &positions_buffer,
        &mut output_buffer,
    ) {
        return error.into();
    }

    if env
        .set_int_array_region(&output, 0, &output_buffer)
        .is_err()
    {
        return NativeStatus::Jni;
    }

    NativeStatus::Ok
}

fn cast_i64_to_jlong(values: &[i64]) -> &[jlong] {
    values
}

fn native_index_error_code(status: NativeStatus) -> jint {
    -1 - status.code()
}

fn native_count_error_code(status: NativeStatus) -> jint {
    -1 - status.code()
}

fn native_boolean_error_code(status: NativeStatus) -> jint {
    -1 - status.code()
}

impl NativeStatus {
    fn code(self) -> jint {
        self as jint
    }
}
