use std::sync::OnceLock;

// ---------------------------------------------------------------------------
// Runtime CPU feature detection
// ---------------------------------------------------------------------------

static AVX2_AVAILABLE: OnceLock<bool> = OnceLock::new();

fn avx2_available() -> bool {
    *AVX2_AVAILABLE.get_or_init(|| {
        #[cfg(target_arch = "x86_64")]
        {
            std::is_x86_feature_detected!("avx2")
        }
        #[cfg(not(target_arch = "x86_64"))]
        {
            false
        }
    })
}

// ---------------------------------------------------------------------------
// x86_64 AVX2 kernels
// ---------------------------------------------------------------------------

#[cfg(target_arch = "x86_64")]
mod x86 {
    use std::arch::x86_64::*;

    /// Compute 4 f64 squared distances from interleaved positions using AVX2 gather.
    ///
    /// # Safety
    /// `positions` must have at least 12 readable f64 values.
    /// `output` must have at least 4 writable f64 values.
    #[target_feature(enable = "avx2")]
    pub unsafe fn squared_distances_f64_x4(
        origin_x: f64,
        origin_y: f64,
        origin_z: f64,
        positions: *const f64,
        output: *mut f64,
    ) {
        // Gather x,y,z for 4 positions with stride-3 (8 bytes per f64, index step = 3).
        let gather_indices = _mm256_set_epi64x(9, 6, 3, 0);
        let x = _mm256_i64gather_pd(positions, gather_indices, 8);
        let y = _mm256_i64gather_pd(positions.add(1), gather_indices, 8);
        let z = _mm256_i64gather_pd(positions.add(2), gather_indices, 8);

        let ox = _mm256_set1_pd(origin_x);
        let oy = _mm256_set1_pd(origin_y);
        let oz = _mm256_set1_pd(origin_z);

        let dx = _mm256_sub_pd(x, ox);
        let dy = _mm256_sub_pd(y, oy);
        let dz = _mm256_sub_pd(z, oz);

        // dx*dx + dy*dy + dz*dz
        let dx2 = _mm256_mul_pd(dx, dx);
        let dy2 = _mm256_mul_pd(dy, dy);
        let dz2 = _mm256_mul_pd(dz, dz);
        let result = _mm256_add_pd(_mm256_add_pd(dx2, dy2), dz2);

        _mm256_storeu_pd(output, result);
    }

    /// Partial potential energy contribution for 4 point charges using AVX2.
    ///
    /// Each lane computes `charge / sqrt(dx^2+dy^2+dz^2)`. If the distance is zero,
    /// the lane contributes +inf.
    ///
    /// # Safety
    /// `positions` must have at least 12 readable i32 values.
    /// `charges` must have at least 4 readable f64 values.
    /// `partial` receives the lane-wise contributions.
    #[target_feature(enable = "avx2")]
    pub unsafe fn potential_energy_partial_x4(
        origin_x: i32,
        origin_y: i32,
        origin_z: i32,
        positions: *const i32,
        charges: *const f64,
        partial: *mut f64,
    ) {
        let zero = _mm256_setzero_pd();
        let infinity = _mm256_set1_pd(f64::INFINITY);

        // Gather 4 i32 x/y/z values with stride-3 (4 bytes per i32, index step = 3).
        let gather_indices_i32 = _mm_set_epi32(9, 6, 3, 0);
        let x_i32 = _mm_i32gather_epi32(positions, gather_indices_i32, 4);
        let y_i32 = _mm_i32gather_epi32(positions.add(1), gather_indices_i32, 4);
        let z_i32 = _mm_i32gather_epi32(positions.add(2), gather_indices_i32, 4);

        let x = _mm256_cvtepi32_pd(x_i32);
        let y = _mm256_cvtepi32_pd(y_i32);
        let z = _mm256_cvtepi32_pd(z_i32);

        let ox = _mm256_set1_pd(f64::from(origin_x));
        let oy = _mm256_set1_pd(f64::from(origin_y));
        let oz = _mm256_set1_pd(f64::from(origin_z));

        let dx = _mm256_sub_pd(x, ox);
        let dy = _mm256_sub_pd(y, oy);
        let dz = _mm256_sub_pd(z, oz);

        let dx2 = _mm256_mul_pd(dx, dx);
        let dy2 = _mm256_mul_pd(dy, dy);
        let dz2 = _mm256_mul_pd(dz, dz);
        let distance = _mm256_add_pd(_mm256_add_pd(dx2, dy2), dz2);

        let sqrt_distance = _mm256_sqrt_pd(distance);

        let charge_vec = _mm256_loadu_pd(charges);
        let contribution = _mm256_div_pd(charge_vec, sqrt_distance);

        // Clobber lanes where distance == 0 with +inf
        let is_zero = _mm256_cmp_pd(distance, zero, _CMP_EQ_OQ);
        let contribution = _mm256_blendv_pd(contribution, infinity, is_zero);

        _mm256_storeu_pd(partial, contribution);
    }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/// Returns true when AVX2 is available at runtime.
#[inline]
pub fn has_avx2() -> bool {
    avx2_available()
}

/// Compute an entire f64 squared-distance output using AVX2 when available.
///
/// Falls back to the caller's scalar/rayon path on non-x86 or when AVX2 is absent.
/// Returns the number of elements processed (a multiple of 4). The caller must
/// handle the remaining tail.
///
/// # Safety
/// All pointer/slice bounds must be checked by the caller.
#[cfg(target_arch = "x86_64")]
pub unsafe fn squared_distances_f64_avx2(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [f64],
) -> usize {
    let position_count = output.len();
    let simd_count = (position_count / 4) * 4;
    if simd_count == 0 {
        return 0;
    }

    let pos_ptr = positions.as_ptr();
    let out_ptr = output.as_mut_ptr();
    for chunk in 0..(simd_count / 4) {
        let offset = chunk * 12; // 4 positions * 3 coords = 12 f64 values
        unsafe {
            x86::squared_distances_f64_x4(
                origin_x,
                origin_y,
                origin_z,
                pos_ptr.add(offset),
                out_ptr.add(chunk * 4),
            );
        }
    }
    simd_count
}

/// Compute partial potential energy contributions for a 4-element block via AVX2.
///
/// Returns the number of elements processed (a multiple of 4).
///
/// # Safety
/// Caller must ensure valid pointers within bounds.
#[cfg(target_arch = "x86_64")]
pub unsafe fn potential_energy_partial_f64_avx2(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    charges: &[f64],
    partial: &mut [f64],
) -> usize {
    let count = partial.len();
    let simd_count = (count / 4) * 4;
    if simd_count == 0 {
        return 0;
    }

    let pos_ptr = positions.as_ptr();
    let chg_ptr = charges.as_ptr();
    let part_ptr = partial.as_mut_ptr();
    for chunk in 0..(simd_count / 4) {
        let offset = chunk * 12; // 4 positions * 3 coords = 12 i32 values
        unsafe {
            x86::potential_energy_partial_x4(
                origin_x,
                origin_y,
                origin_z,
                pos_ptr.add(offset),
                chg_ptr.add(chunk * 4),
                part_ptr.add(chunk * 4),
            );
        }
    }
    simd_count
}

// Stub implementations for non-x86 targets
#[cfg(not(target_arch = "x86_64"))]
pub fn squared_distances_f64_avx2(
    _origin_x: f64,
    _origin_y: f64,
    _origin_z: f64,
    _positions: &[f64],
    _output: &mut [f64],
) -> usize {
    0
}

#[cfg(not(target_arch = "x86_64"))]
pub fn potential_energy_partial_f64_avx2(
    _origin_x: i32,
    _origin_y: i32,
    _origin_z: i32,
    _positions: &[i32],
    _charges: &[f64],
    _partial: &mut [f64],
) -> usize {
    0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn avx2_detection_should_not_panic() {
        let _ = has_avx2();
    }

    #[cfg(target_arch = "x86_64")]
    mod x86_tests {
        use super::*;

        #[test]
        fn squared_distances_f64_x4_should_match_scalar() {
            if !has_avx2() {
                return;
            }

            let positions = [0.0, 0.0, 0.0, 3.0, 4.0, 0.0, -1.0, 0.0, 2.0, 100.0, 0.0, -100.0];
            let mut output = [0.0_f64; 4];

            unsafe {
                x86::squared_distances_f64_x4(
                    0.0, 0.0, 0.0,
                    positions.as_ptr(),
                    output.as_mut_ptr(),
                );
            }

            assert!((output[0] - 0.0).abs() < 1e-10);
            assert!((output[1] - 25.0).abs() < 1e-10);
            assert!((output[2] - 5.0).abs() < 1e-10);
            assert!((output[3] - 20000.0).abs() < 1e-10);
        }

        #[test]
        fn potential_energy_partial_x4_should_match_expected() {
            if !has_avx2() {
                return;
            }

            let positions: [i32; 12] = [0, 0, 0, 3, 4, 0, -1, 0, 2, 100, 0, -100];
            let charges = [1.0_f64, 10.0, 5.0, 2.0];
            let mut partial = [0.0_f64; 4];

            unsafe {
                x86::potential_energy_partial_x4(
                    0, 0, 0,
                    positions.as_ptr(),
                    charges.as_ptr(),
                    partial.as_mut_ptr(),
                );
            }

            // distance == 0 -> +inf
            assert!(partial[0].is_infinite() && partial[0].is_sign_positive());
            // distance = 25, sqrt = 5
            assert!((partial[1] - (10.0 / 5.0)).abs() < 1e-10);
            // distance = 5, sqrt ~ 2.236
            let expected_2 = 5.0 / 5.0_f64.sqrt();
            assert!((partial[2] - expected_2).abs() < 1e-10);
            // distance = 20000, sqrt ~ 141.42
            let expected_3 = 2.0 / 20000.0_f64.sqrt();
            assert!((partial[3] - expected_3).abs() < 1e-10);
        }
    }
}
