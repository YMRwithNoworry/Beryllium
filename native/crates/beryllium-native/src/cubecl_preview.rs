use libloading::Library;
use std::ffi::c_void;
use std::ptr::NonNull;
use std::sync::atomic::{AtomicI32, Ordering};

pub(crate) const MIN_CHARGE_COUNT: usize = 262_144;
static LAST_DIAGNOSTIC: AtomicI32 = AtomicI32::new(0);

type CreateCache = unsafe extern "C" fn(
    *const i32,
    usize,
    *const f64,
    usize,
    u64,
    FallbackCompute,
    IsCurrent,
) -> *mut c_void;
type ComputeCache = unsafe extern "C" fn(*mut c_void, i32, i32, i32, f64, *mut f64) -> i32;
type DestroyCache = unsafe extern "C" fn(*mut c_void);
type FallbackCompute =
    unsafe extern "C" fn(i32, i32, i32, *const i32, usize, *const f64, usize, f64, *mut f64) -> i32;
type IsCurrent = unsafe extern "C" fn(u64) -> i32;

pub(crate) struct CubePotentialCache {
    _library: Library,
    handle: NonNull<c_void>,
    compute: ComputeCache,
    destroy: DestroyCache,
}

// The sidecar cache is only accessed while the process-wide potential mutex is held.
unsafe impl Send for CubePotentialCache {}

impl CubePotentialCache {
    pub(crate) fn calibrate(
        positions: &[i32],
        charges: &[f64],
        generation: u64,
        is_current: impl Fn() -> bool,
    ) -> Option<Self> {
        if !is_current() || charges.len() < MIN_CHARGE_COUNT {
            return None;
        }

        LAST_DIAGNOSTIC.store(0, Ordering::Release);
        let Some(library) = load_sidecar() else {
            LAST_DIAGNOSTIC.store(-1, Ordering::Release);
            return None;
        };
        let Some(create) = (unsafe {
            library
                .get::<CreateCache>(b"beryllium_cubecl_potential_create\0")
                .ok()
                .map(|symbol| *symbol)
        }) else {
            LAST_DIAGNOSTIC.store(-2, Ordering::Release);
            return None;
        };
        let Some(compute) = (unsafe {
            library
                .get::<ComputeCache>(b"beryllium_cubecl_potential_compute\0")
                .ok()
                .map(|symbol| *symbol)
        }) else {
            LAST_DIAGNOSTIC.store(-2, Ordering::Release);
            return None;
        };
        let Some(destroy) = (unsafe {
            library
                .get::<DestroyCache>(b"beryllium_cubecl_potential_destroy\0")
                .ok()
                .map(|symbol| *symbol)
        }) else {
            LAST_DIAGNOSTIC.store(-2, Ordering::Release);
            return None;
        };
        let Some(handle) = NonNull::new(unsafe {
            create(
                positions.as_ptr(),
                positions.len(),
                charges.as_ptr(),
                charges.len(),
                generation,
                fallback_compute,
                generation_is_current,
            )
        }) else {
            LAST_DIAGNOSTIC.store(-3, Ordering::Release);
            return None;
        };

        if !is_current() {
            unsafe { destroy(handle.as_ptr()) };
            return None;
        }
        LAST_DIAGNOSTIC.store(2, Ordering::Release);
        Some(Self {
            _library: library,
            handle,
            compute,
            destroy,
        })
    }

    pub(crate) fn compute(
        &self,
        origin_x: i32,
        origin_y: i32,
        origin_z: i32,
        charge_multiplier: f64,
    ) -> Result<f64, String> {
        let mut output = 0.0;
        let status = unsafe {
            (self.compute)(
                self.handle.as_ptr(),
                origin_x,
                origin_y,
                origin_z,
                charge_multiplier,
                &mut output,
            )
        };
        if status == 0 {
            Ok(output)
        } else {
            Err("CubeCL preview sidecar computation failed".to_owned())
        }
    }
}

pub(crate) fn diagnostic_status() -> i32 {
    LAST_DIAGNOSTIC.load(Ordering::Acquire)
}

pub(crate) fn reset_diagnostic() {
    LAST_DIAGNOSTIC.store(0, Ordering::Release);
}

pub(crate) fn mark_runtime_failure() {
    LAST_DIAGNOSTIC.store(-4, Ordering::Release);
}

impl Drop for CubePotentialCache {
    fn drop(&mut self) {
        unsafe { (self.destroy)(self.handle.as_ptr()) };
    }
}

#[cfg(target_os = "windows")]
fn load_sidecar() -> Option<Library> {
    libloading::os::windows::Library::open_already_loaded("beryllium_cubecl.dll")
        .ok()
        .map(Into::into)
}

#[cfg(unix)]
fn load_sidecar() -> Option<Library> {
    Some(libloading::os::unix::Library::this().into())
}

#[cfg(not(any(target_os = "windows", unix)))]
fn load_sidecar() -> Option<Library> {
    None
}

unsafe extern "C" fn fallback_compute(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: *const i32,
    positions_length: usize,
    charges: *const f64,
    charges_length: usize,
    multiplier: f64,
    output: *mut f64,
) -> i32 {
    if (positions.is_null() && positions_length != 0)
        || (charges.is_null() && charges_length != 0)
        || output.is_null()
    {
        return 1;
    }
    let positions = if positions_length == 0 {
        &[]
    } else {
        unsafe { std::slice::from_raw_parts(positions, positions_length) }
    };
    let charges = if charges_length == 0 {
        &[]
    } else {
        unsafe { std::slice::from_raw_parts(charges, charges_length) }
    };
    match crate::kernel::potential_energy_change(
        origin_x, origin_y, origin_z, positions, charges, multiplier,
    ) {
        Ok(value) => {
            unsafe { *output = value };
            0
        }
        Err(_) => 1,
    }
}

unsafe extern "C" fn generation_is_current(generation: u64) -> i32 {
    i32::from(crate::kernel::is_potential_generation_current(generation))
}
