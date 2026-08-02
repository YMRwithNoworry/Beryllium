use cubecl::cpu::CpuRuntime;
use cubecl::prelude::*;
use cubecl::server::Handle;
use std::hint::black_box;
use std::time::Instant;

pub const MIN_CHARGE_COUNT: usize = 262_144;
const TUNING_WARMUPS: usize = 2;
const TUNING_SAMPLES: usize = 7;
const FINAL_WARMUPS: usize = 3;
const FINAL_SAMPLES: usize = 41;

#[cube(launch_unchecked)]
fn potential_contributions<N: Size>(
    positions_x: &[Vector<f64, N>],
    positions_y: &[Vector<f64, N>],
    positions_z: &[Vector<f64, N>],
    charges: &[Vector<f64, N>],
    output: &mut [Vector<f64, N>],
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
) {
    if ABSOLUTE_POS < output.len() {
        let dx = positions_x[ABSOLUTE_POS] - Vector::new(origin_x);
        let dy = positions_y[ABSOLUTE_POS] - Vector::new(origin_y);
        let dz = positions_z[ABSOLUTE_POS] - Vector::new(origin_z);
        let distance = (dx * dx + dy * dy + dz * dz).sqrt();
        let zero = Vector::new(0.0f64);
        output[ABSOLUTE_POS] = select_many(
            distance.equal(&zero),
            Vector::new(f64::INFINITY),
            charges[ABSOLUTE_POS] / distance,
        );
    }
}

pub(crate) struct CubePotentialCache {
    client: ComputeClient<CpuRuntime>,
    positions_x: Handle,
    positions_y: Handle,
    positions_z: Handle,
    charges: Handle,
    output: Handle,
    charge_count: usize,
    padded_count: usize,
    cube_count: u32,
    cube_dim: u32,
    vector_size: usize,
}

impl CubePotentialCache {
    #[cfg(test)]
    pub(crate) fn new(positions: &[i32], charges: &[f64]) -> Result<Self, String> {
        let available_parallelism = std::thread::available_parallelism()
            .map(|count| count.get())
            .unwrap_or(1);
        Self::new_configured(positions, charges, 8, available_parallelism)
    }

    pub(crate) fn calibrate<F>(
        positions: &[i32],
        charges: &[f64],
        is_current: impl Fn() -> bool,
        fallback: &F,
    ) -> Option<Self>
    where
        F: Fn(i32, i32, i32, f64) -> Option<f64>,
    {
        let available_parallelism = std::thread::available_parallelism().ok()?.get();
        if !is_current() || charges.len() < MIN_CHARGE_COUNT || available_parallelism < 2 {
            return None;
        }

        let cube_dims = [
            (available_parallelism / 4).max(1),
            (available_parallelism / 2).max(1),
            available_parallelism,
        ];
        let mut cache = Self::new_configured(
            positions,
            charges,
            preferred_vector_size(),
            available_parallelism,
        )
        .ok()?;
        let mut best_dimension = None;

        for (index, cube_dim) in cube_dims.into_iter().enumerate() {
            if !is_current() {
                return None;
            }
            if cube_dims[..index].contains(&cube_dim) {
                continue;
            }
            cache.set_cube_dim(cube_dim);
            let median = cache.measure_configuration(fallback)?;
            if best_dimension
                .as_ref()
                .is_none_or(|(_, best_median)| median < *best_median)
            {
                best_dimension = Some((cube_dim, median));
            }
        }

        let (cube_dim, _) = best_dimension?;
        cache.set_cube_dim(cube_dim);
        (is_current() && cache.has_safe_speedup(fallback)).then_some(cache)
    }

    fn new_configured(
        positions: &[i32],
        charges: &[f64],
        vector_size: usize,
        requested_cube_dim: usize,
    ) -> Result<Self, String> {
        if !positions.len().is_multiple_of(3) || charges.len() != positions.len() / 3 {
            return Err("positions and charges must contain matching triples".to_owned());
        }
        if vector_size == 0 || requested_cube_dim == 0 {
            return Err("vector size and cube dimension must be non-zero".to_owned());
        }

        let charge_count = charges.len();
        let padded_count = charge_count.next_multiple_of(vector_size).max(vector_size);
        let mut positions_x = vec![0.0; padded_count];
        let mut positions_y = vec![0.0; padded_count];
        let mut positions_z = vec![0.0; padded_count];
        let mut padded_charges = vec![0.0; padded_count];
        for index in 0..charge_count {
            let offset = index * 3;
            positions_x[index] = positions[offset] as f64;
            positions_y[index] = positions[offset + 1] as f64;
            positions_z[index] = positions[offset + 2] as f64;
            padded_charges[index] = charges[index];
        }

        let client = CpuRuntime::client(&Default::default());
        let vector_count = padded_count / vector_size;
        let cube_dim = vector_count.min(requested_cube_dim).max(1) as u32;
        let cube_count = vector_count.div_ceil(cube_dim as usize) as u32;

        Ok(Self {
            positions_x: client.create_from_slice(f64::as_bytes(&positions_x)),
            positions_y: client.create_from_slice(f64::as_bytes(&positions_y)),
            positions_z: client.create_from_slice(f64::as_bytes(&positions_z)),
            charges: client.create_from_slice(f64::as_bytes(&padded_charges)),
            output: client.empty(padded_count * std::mem::size_of::<f64>()),
            client,
            charge_count,
            padded_count,
            cube_count,
            cube_dim,
            vector_size,
        })
    }

    fn set_cube_dim(&mut self, requested_cube_dim: usize) {
        let vector_count = self.padded_count / self.vector_size;
        self.cube_dim = vector_count.min(requested_cube_dim).max(1) as u32;
        self.cube_count = vector_count.div_ceil(self.cube_dim as usize) as u32;
    }

    fn measure_configuration<F>(&self, fallback: &F) -> Option<u128>
    where
        F: Fn(i32, i32, i32, f64) -> Option<f64>,
    {
        for iteration in 0..TUNING_WARMUPS {
            let (origin_x, origin_y, origin_z) = calibration_origin(iteration);
            let expected = fallback(origin_x, origin_y, origin_z, 0.75)?;
            let actual = self.compute(origin_x, origin_y, origin_z, 0.75).ok()?;
            if actual.to_bits() != expected.to_bits() {
                return None;
            }
        }

        let mut samples = [0_u128; TUNING_SAMPLES];
        for (iteration, sample) in samples.iter_mut().enumerate() {
            let (origin_x, origin_y, origin_z) = calibration_origin(iteration + TUNING_WARMUPS);
            let expected = fallback(origin_x, origin_y, origin_z, 0.75)?;
            let start = Instant::now();
            let actual = black_box(self.compute(origin_x, origin_y, origin_z, 0.75).ok()?);
            *sample = start.elapsed().as_nanos();
            if actual.to_bits() != expected.to_bits() {
                return None;
            }
        }
        samples.sort_unstable();
        Some(samples[TUNING_SAMPLES / 2])
    }

    fn has_safe_speedup<F>(&self, fallback: &F) -> bool
    where
        F: Fn(i32, i32, i32, f64) -> Option<f64>,
    {
        for iteration in 0..FINAL_WARMUPS {
            let (origin_x, origin_y, origin_z) = calibration_origin(iteration + 17);
            let Some(expected) = fallback(origin_x, origin_y, origin_z, 0.75) else {
                return false;
            };
            let Ok(actual) = self.compute(origin_x, origin_y, origin_z, 0.75) else {
                return false;
            };
            if actual.to_bits() != expected.to_bits() {
                return false;
            }
        }

        let mut cube_samples = [0_u128; FINAL_SAMPLES];
        let mut fallback_samples = [0_u128; FINAL_SAMPLES];
        for iteration in 0..FINAL_SAMPLES {
            let (origin_x, origin_y, origin_z) = calibration_origin(iteration + FINAL_WARMUPS + 17);
            let run_cube = || {
                let start = Instant::now();
                let result = black_box(self.compute(origin_x, origin_y, origin_z, 0.75));
                (result, start.elapsed().as_nanos())
            };
            let run_fallback = || {
                let start = Instant::now();
                let result = black_box(fallback(origin_x, origin_y, origin_z, 0.75));
                (result, start.elapsed().as_nanos())
            };

            let ((actual, cube_elapsed), (expected, fallback_elapsed)) = if iteration % 2 == 0 {
                (run_cube(), run_fallback())
            } else {
                let fallback = run_fallback();
                let cube = run_cube();
                (cube, fallback)
            };
            let (Ok(actual), Some(expected)) = (actual, expected) else {
                return false;
            };
            if actual.to_bits() != expected.to_bits() {
                return false;
            }
            cube_samples[iteration] = cube_elapsed;
            fallback_samples[iteration] = fallback_elapsed;
        }

        cube_samples.sort_unstable();
        fallback_samples.sort_unstable();
        let cube_median = cube_samples[FINAL_SAMPLES / 2];
        let fallback_median = fallback_samples[FINAL_SAMPLES / 2];
        let cube_p90 = cube_samples[FINAL_SAMPLES * 9 / 10];
        let fallback_p10 = fallback_samples[FINAL_SAMPLES / 10];
        cube_median.saturating_mul(2) <= fallback_median && cube_p90 <= fallback_p10
    }

    pub(crate) fn compute(
        &self,
        origin_x: i32,
        origin_y: i32,
        origin_z: i32,
        charge_multiplier: f64,
    ) -> Result<f64, String> {
        if charge_multiplier == 0.0 || self.charge_count == 0 {
            return Ok(0.0);
        }

        unsafe {
            potential_contributions::launch_unchecked::<CpuRuntime>(
                &self.client,
                CubeCount::Static(self.cube_count, 1, 1),
                CubeDim::new_1d(self.cube_dim),
                self.vector_size,
                BufferArg::from_raw_parts(self.positions_x.clone(), self.padded_count),
                BufferArg::from_raw_parts(self.positions_y.clone(), self.padded_count),
                BufferArg::from_raw_parts(self.positions_z.clone(), self.padded_count),
                BufferArg::from_raw_parts(self.charges.clone(), self.padded_count),
                BufferArg::from_raw_parts(self.output.clone(), self.padded_count),
                origin_x as f64,
                origin_y as f64,
                origin_z as f64,
            )
        }

        let bytes = self
            .client
            .read_one(self.output.clone())
            .map_err(|error| error.to_string())?;
        let contributions = f64::from_bytes(&bytes);
        let mut energy = 0.0;
        for contribution in &contributions[..self.charge_count] {
            energy += contribution;
        }
        Ok(energy * charge_multiplier)
    }
}

fn calibration_origin(iteration: usize) -> (i32, i32, i32) {
    let value = iteration as i32;
    (value % 23 - 11, 64 + value % 5 - 2, 7 - value % 19)
}

fn preferred_vector_size() -> usize {
    #[cfg(target_arch = "x86_64")]
    {
        if std::is_x86_feature_detected!("avx2") {
            return 4;
        }
    }
    2
}

#[cfg(test)]
mod tests {
    use super::*;

    fn potential_energy_change(
        origin_x: i32,
        origin_y: i32,
        origin_z: i32,
        positions: &[i32],
        charges: &[f64],
        multiplier: f64,
    ) -> Option<f64> {
        if !positions.len().is_multiple_of(3) || positions.len() / 3 != charges.len() {
            return None;
        }
        let mut energy = 0.0;
        for (index, charge) in charges.iter().enumerate() {
            let offset = index * 3;
            let dx = f64::from(positions[offset]) - f64::from(origin_x);
            let dy = f64::from(positions[offset + 1]) - f64::from(origin_y);
            let dz = f64::from(positions[offset + 2]) - f64::from(origin_z);
            let distance = dx * dx + dy * dy + dz * dz;
            energy += if distance == 0.0 {
                f64::INFINITY
            } else {
                *charge / distance.sqrt()
            };
        }
        Some(energy * multiplier)
    }

    #[test]
    fn cubecl_potential_should_match_reference_order() {
        let positions = [
            -4, 2, 1, 7, -3, 5, 11, 13, -17, 19, -23, 29, 31, 37, 41, -43, 47, -53,
        ];
        let charges = [0.25, -1.5, 2.0, -0.75, 4.0, 0.5];
        let cache = CubePotentialCache::new(&positions, &charges).unwrap();
        let expected = potential_energy_change(3, -2, 7, &positions, &charges, 0.75).unwrap();
        let actual = cache.compute(3, -2, 7, 0.75).unwrap();
        assert_eq!(expected.to_bits(), actual.to_bits());
    }

    #[test]
    fn cubecl_potential_should_keep_zero_distance_positive_infinity() {
        let positions = [2, 3, 4, 8, 9, 10];
        let charges = [-5.0, 1.0];
        let cache = CubePotentialCache::new(&positions, &charges).unwrap();
        let actual = cache.compute(2, 3, 4, 1.0).unwrap();
        assert_eq!(f64::INFINITY.to_bits(), actual.to_bits());
    }

    #[test]
    fn cubecl_calibration_should_cancel_before_backend_initialization() {
        let checked = std::cell::Cell::new(false);
        let result = CubePotentialCache::calibrate(
            &[],
            &[],
            || {
                checked.set(true);
                false
            },
            &|_, _, _, _| None,
        );
        assert!(checked.get());
        assert!(result.is_none());
    }
}
