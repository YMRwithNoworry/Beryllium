mod backend;

use backend::CubePotentialCache;
use std::ffi::c_void;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

type FallbackCompute =
    unsafe extern "C" fn(i32, i32, i32, *const i32, usize, *const f64, usize, f64, *mut f64) -> i32;
type IsCurrent = unsafe extern "C" fn(u64) -> i32;

const STATUS_OK: i32 = 0;
const STATUS_ERROR: i32 = 1;

unsafe fn read_slice<'a, T>(pointer: *const T, length: usize) -> Option<&'a [T]> {
    if length == 0 {
        return Some(&[]);
    }
    if pointer.is_null() {
        return None;
    }
    Some(unsafe { std::slice::from_raw_parts(pointer, length) })
}

/// Creates and calibrates one opaque CubeCL potential cache.
///
/// A null result means the request was cancelled, failed parity, failed to initialize, or did not
/// beat the supplied fallback by the required safety margin.
///
/// # Safety
/// Non-empty input pointers must be valid for their declared lengths for the duration of this
/// call. Both callbacks must remain callable for the duration of this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn beryllium_cubecl_potential_create(
    positions: *const i32,
    positions_length: usize,
    charges: *const f64,
    charges_length: usize,
    generation: u64,
    fallback_compute: FallbackCompute,
    is_current: IsCurrent,
) -> *mut c_void {
    let Some(positions) = (unsafe { read_slice(positions, positions_length) }) else {
        return ptr::null_mut();
    };
    let Some(charges) = (unsafe { read_slice(charges, charges_length) }) else {
        return ptr::null_mut();
    };

    let fallback = |origin_x, origin_y, origin_z, multiplier| {
        let mut output = 0.0;
        let status = unsafe {
            fallback_compute(
                origin_x,
                origin_y,
                origin_z,
                positions.as_ptr(),
                positions.len(),
                charges.as_ptr(),
                charges.len(),
                multiplier,
                &mut output,
            )
        };
        (status == STATUS_OK).then_some(output)
    };
    let current = || unsafe { is_current(generation) != 0 };

    catch_unwind(AssertUnwindSafe(|| {
        CubePotentialCache::calibrate(positions, charges, current, &fallback)
    }))
    .ok()
    .flatten()
    .map_or(ptr::null_mut(), |cache| {
        Box::into_raw(Box::new(cache)).cast()
    })
}

/// Computes one potential result with an opaque cache returned by
/// `beryllium_cubecl_potential_create`.
///
/// # Safety
/// `cache` must be a live handle returned by this library and `output` must point to one writable
/// `f64`. Calls using the same cache must be externally synchronized.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn beryllium_cubecl_potential_compute(
    cache: *mut c_void,
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    charge_multiplier: f64,
    output: *mut f64,
) -> i32 {
    let Some(cache) = (unsafe { cache.cast::<CubePotentialCache>().as_ref() }) else {
        return STATUS_ERROR;
    };
    let Some(output) = (unsafe { output.as_mut() }) else {
        return STATUS_ERROR;
    };

    match catch_unwind(AssertUnwindSafe(|| {
        cache.compute(origin_x, origin_y, origin_z, charge_multiplier)
    })) {
        Ok(Ok(value)) => {
            *output = value;
            STATUS_OK
        }
        Ok(Err(_)) | Err(_) => STATUS_ERROR,
    }
}

/// Destroys one opaque CubeCL potential cache.
///
/// # Safety
/// A non-null handle must have been returned by `beryllium_cubecl_potential_create`, must not have
/// been destroyed already, and must not be in use by another thread.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn beryllium_cubecl_potential_destroy(cache: *mut c_void) {
    if cache.is_null() {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        drop(unsafe { Box::from_raw(cache.cast::<CubePotentialCache>()) });
    }));
}

#[cfg(test)]
mod tests {
    use super::*;

    unsafe extern "C" fn unused_fallback(
        _: i32,
        _: i32,
        _: i32,
        _: *const i32,
        _: usize,
        _: *const f64,
        _: usize,
        _: f64,
        _: *mut f64,
    ) -> i32 {
        STATUS_ERROR
    }

    unsafe extern "C" fn cancelled(_: u64) -> i32 {
        0
    }

    #[test]
    fn cancelled_abi_creation_should_not_initialize_cubecl() {
        let handle = unsafe {
            beryllium_cubecl_potential_create(
                ptr::null(),
                0,
                ptr::null(),
                0,
                7,
                unused_fallback,
                cancelled,
            )
        };
        assert!(handle.is_null());
    }
}
