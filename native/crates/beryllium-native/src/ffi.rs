use jni::objects::{JClass, JDoubleArray, JIntArray, JLongArray};
use jni::sys::{jdouble, jint, jlong};
use jni::JNIEnv;

use crate::{
    kernel::compute_squared_distances, kernel::compute_squared_distances_f64,
    kernel::filter_within_radius, kernel::find_nearest_index_f64, kernel::sort_by_distance,
    NativeError,
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
        Err(error) => return NativeStatus::from(error).code(),
    };

    if env
        .set_int_array_region(&output, 0, &output_buffer[..count])
        .is_err()
    {
        return NativeStatus::Jni.code();
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

fn cast_i64_to_jlong(values: &[i64]) -> &[jlong] {
    values
}

fn native_index_error_code(status: NativeStatus) -> jint {
    -1 - status.code()
}

impl NativeStatus {
    fn code(self) -> jint {
        self as jint
    }
}
