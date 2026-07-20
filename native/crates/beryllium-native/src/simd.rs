use std::sync::OnceLock;

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

#[cfg(target_arch = "x86_64")]
mod avx2_kernels {
    use std::arch::x86_64::*;

    #[target_feature(enable = "avx2")]
    pub unsafe fn squared_distances_f64_x4(
        origin_x: f64,
        origin_y: f64,
        origin_z: f64,
        positions: *const f64,
        output: *mut f64,
    ) {
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

        let dx2 = _mm256_mul_pd(dx, dx);
        let dy2 = _mm256_mul_pd(dy, dy);
        let dz2 = _mm256_mul_pd(dz, dz);
        let result = _mm256_add_pd(_mm256_add_pd(dx2, dy2), dz2);

        _mm256_storeu_pd(output, result);
    }

    #[target_feature(enable = "avx2")]
    pub unsafe fn intersecting_aabb_f64_x4(
        query_min_x: f64,
        query_min_y: f64,
        query_min_z: f64,
        query_max_x: f64,
        query_max_y: f64,
        query_max_z: f64,
        boxes: *const f64,
    ) -> u8 {
        let gather_indices = _mm256_set_epi64x(18, 12, 6, 0);
        let min_x = _mm256_i64gather_pd(boxes, gather_indices, 8);
        let min_y = _mm256_i64gather_pd(boxes.add(1), gather_indices, 8);
        let min_z = _mm256_i64gather_pd(boxes.add(2), gather_indices, 8);
        let max_x = _mm256_i64gather_pd(boxes.add(3), gather_indices, 8);
        let max_y = _mm256_i64gather_pd(boxes.add(4), gather_indices, 8);
        let max_z = _mm256_i64gather_pd(boxes.add(5), gather_indices, 8);

        let min_x_before_max = _mm256_cmp_pd(min_x, _mm256_set1_pd(query_max_x), _CMP_LT_OQ);
        let min_y_before_max = _mm256_cmp_pd(min_y, _mm256_set1_pd(query_max_y), _CMP_LT_OQ);
        let min_z_before_max = _mm256_cmp_pd(min_z, _mm256_set1_pd(query_max_z), _CMP_LT_OQ);
        let max_x_after_min = _mm256_cmp_pd(max_x, _mm256_set1_pd(query_min_x), _CMP_GT_OQ);
        let max_y_after_min = _mm256_cmp_pd(max_y, _mm256_set1_pd(query_min_y), _CMP_GT_OQ);
        let max_z_after_min = _mm256_cmp_pd(max_z, _mm256_set1_pd(query_min_z), _CMP_GT_OQ);

        let intersects = _mm256_and_pd(
            _mm256_and_pd(min_x_before_max, min_y_before_max),
            _mm256_and_pd(
                _mm256_and_pd(min_z_before_max, max_x_after_min),
                _mm256_and_pd(max_y_after_min, max_z_after_min),
            ),
        );
        _mm256_movemask_pd(intersects) as u8
    }

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

        let is_zero = _mm256_cmp_pd(distance, zero, _CMP_EQ_OQ);
        let contribution = _mm256_blendv_pd(contribution, infinity, is_zero);

        _mm256_storeu_pd(partial, contribution);
    }
}

#[inline]
pub fn has_avx2() -> bool {
    avx2_available()
}

#[cfg(target_arch = "x86_64")]
pub unsafe fn squared_distances_f64_avx2(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [f64],
) -> usize {
    let count = output.len();
    let simd_count = (count / 4) * 4;
    if simd_count == 0 {
        return 0;
    }

    let pos_ptr = positions.as_ptr();
    let out_ptr = output.as_mut_ptr();
    for chunk in 0..(simd_count / 4) {
        let offset = chunk * 12;
        unsafe {
            avx2_kernels::squared_distances_f64_x4(
                origin_x, origin_y, origin_z,
                pos_ptr.add(offset),
                out_ptr.add(chunk * 4),
            );
        }
    }
    simd_count
}

#[cfg(not(target_arch = "x86_64"))]
pub fn squared_distances_f64_avx2(
    _: f64, _: f64, _: f64,
    _: &[f64], _: &mut [f64],
) -> usize { 0 }

#[cfg(target_arch = "x86_64")]
pub unsafe fn batch_4_distances(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    offset: usize,
    output: &mut [f64; 4],
) {
    let pos_ptr = positions.as_ptr().add(offset);
    avx2_kernels::squared_distances_f64_x4(origin_x, origin_y, origin_z, pos_ptr, output.as_mut_ptr());
}

#[cfg(not(target_arch = "x86_64"))]
pub fn batch_4_distances(
    _: f64, _: f64, _: f64,
    _: &[f64], _: usize, _: &mut [f64; 4],
) {}

#[cfg(target_arch = "x86_64")]
pub unsafe fn batch_4_aabb_intersections(
    query_min_x: f64,
    query_min_y: f64,
    query_min_z: f64,
    query_max_x: f64,
    query_max_y: f64,
    query_max_z: f64,
    boxes: &[f64],
    offset: usize,
) -> u8 {
    avx2_kernels::intersecting_aabb_f64_x4(
        query_min_x,
        query_min_y,
        query_min_z,
        query_max_x,
        query_max_y,
        query_max_z,
        boxes.as_ptr().add(offset),
    )
}

#[cfg(not(target_arch = "x86_64"))]
pub fn batch_4_aabb_intersections(
    _: f64,
    _: f64,
    _: f64,
    _: f64,
    _: f64,
    _: f64,
    _: &[f64],
    _: usize,
) -> u8 {
    0
}

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
        let offset = chunk * 12;
        unsafe {
            avx2_kernels::potential_energy_partial_x4(
                origin_x, origin_y, origin_z,
                pos_ptr.add(offset),
                chg_ptr.add(chunk * 4),
                part_ptr.add(chunk * 4),
            );
        }
    }
    simd_count
}

#[cfg(not(target_arch = "x86_64"))]
pub fn potential_energy_partial_f64_avx2(
    _: i32, _: i32, _: i32,
    _: &[i32], _: &[f64], _: &mut [f64],
) -> usize { 0 }

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn avx2_detection_should_not_panic() {
        let _ = has_avx2();
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn squared_distances_x4_should_match_scalar() {
        if !has_avx2() { return; }

        let positions = [0.0, 0.0, 0.0, 3.0, 4.0, 0.0, -1.0, 0.0, 2.0, 100.0, 0.0, -100.0];
        let mut output = [0.0_f64; 4];

        unsafe {
            avx2_kernels::squared_distances_f64_x4(0.0, 0.0, 0.0, positions.as_ptr(), output.as_mut_ptr());
        }

        assert!((output[0] - 0.0).abs() < 1e-10);
        assert!((output[1] - 25.0).abs() < 1e-10);
        assert!((output[2] - 5.0).abs() < 1e-10);
        assert!((output[3] - 20000.0).abs() < 1e-10);
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn aabb_intersections_x4_should_exclude_touching_boundaries() {
        if !has_avx2() { return; }

        let boxes = [
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
            0.5, 0.0, 0.0, 1.5, 1.0, 1.0,
            1.0, 0.0, 0.0, 2.0, 1.0, 1.0,
            0.0, 2.0, 0.0, 1.0, 3.0, 1.0,
        ];

        let mask = unsafe {
            avx2_kernels::intersecting_aabb_f64_x4(
                0.0,
                0.0,
                0.0,
                1.0,
                1.0,
                1.0,
                boxes.as_ptr(),
            )
        };

        assert_eq!(mask, 0b0011);
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn potential_energy_x4_should_match_expected() {
        if !has_avx2() { return; }

        let positions: [i32; 12] = [0, 0, 0, 3, 4, 0, -1, 0, 2, 100, 0, -100];
        let charges = [1.0_f64, 10.0, 5.0, 2.0];
        let mut partial = [0.0_f64; 4];

        unsafe {
            avx2_kernels::potential_energy_partial_x4(0, 0, 0, positions.as_ptr(), charges.as_ptr(), partial.as_mut_ptr());
        }

        assert!(partial[0].is_infinite() && partial[0].is_sign_positive());
        assert!((partial[1] - (10.0 / 5.0)).abs() < 1e-10);
        assert!((partial[2] - (5.0 / 5.0_f64.sqrt())).abs() < 1e-10);
        assert!((partial[3] - (2.0 / 20000.0_f64.sqrt())).abs() < 1e-10);
    }
}
