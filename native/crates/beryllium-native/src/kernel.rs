use rayon::prelude::*;
use std::cmp::Ordering;
use std::collections::BinaryHeap;
#[cfg(feature = "cubecl-preview")]
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Mutex;
#[cfg(feature = "cubecl-preview")]
use std::sync::atomic::{AtomicU64, Ordering as AtomicOrdering};
#[cfg(feature = "cubecl-preview")]
use std::sync::{Arc, OnceLock, mpsc};
#[cfg(feature = "cubecl-preview")]
use std::thread;

use crate::NativeError;
use crate::noise::{NoiseGenerator, batch_sample_noise_3d_parallel, perlin::PerlinNoise, simplex::SimplexNoise, opensimplex2::OpenSimplex2Noise};
use crate::biome::{BiomeWeight, batch_compute_biome_weights_3d, batch_interpolate_biome_values};
#[cfg(feature = "cubecl-preview")]
use crate::cubecl_preview::{CubePotentialCache, MIN_CHARGE_COUNT as CUBECL_MIN_CHARGE_COUNT};
use crate::simd;
use crate::simd::has_avx2;
use crate::simd::{batch_4_aabb_intersections, batch_4_distances};

const PARALLEL_THRESHOLD: usize = 2048;
const FILTER_PARALLEL_THRESHOLD: usize = 16_384;
const CHUNK_SELECTION_PARALLEL_THRESHOLD: usize = 32768;
const NEAREST_SELECTION_PARALLEL_THRESHOLD: usize = 1_048_576;
const BLOCK_NEAREST_PARALLEL_THRESHOLD: usize = 65_536;
const NEAREST_SELECTION_INITIAL_CAPACITY: usize = 64;

/// Expands packed trilinear-interpolation corners into one dense Minecraft noise cell per
/// interpolator. Work remains sequential because Minecraft already parallelizes chunk generation.
pub fn interpolate_density_cells(
    corners: &[f64],
    cell_width: usize,
    cell_height: usize,
    output: &mut [f64],
) -> Result<(), NativeError> {
    if corners.len() % 8 != 0 || cell_width == 0 || cell_height == 0 {
        return Err(NativeError::InvalidInput);
    }

    let cell_volume = cell_width
        .checked_mul(cell_width)
        .and_then(|area| area.checked_mul(cell_height))
        .ok_or(NativeError::InvalidInput)?;
    let expected_output_length = (corners.len() / 8)
        .checked_mul(cell_volume)
        .ok_or(NativeError::InvalidInput)?;
    if output.len() != expected_output_length {
        return Err(NativeError::OutputLengthMismatch);
    }

    for (interpolator_index, packed_corners) in corners.chunks_exact(8).enumerate() {
        let output_start = interpolator_index * cell_volume;
        let cell_output = &mut output[output_start..output_start + cell_volume];
        for y in 0..cell_height {
            let delta_y = y as f64 / cell_height as f64;
            let value_xz00 = lerp(delta_y, packed_corners[0], packed_corners[2]);
            let value_xz10 = lerp(delta_y, packed_corners[1], packed_corners[3]);
            let value_xz01 = lerp(delta_y, packed_corners[4], packed_corners[6]);
            let value_xz11 = lerp(delta_y, packed_corners[5], packed_corners[7]);

            for x in 0..cell_width {
                let delta_x = x as f64 / cell_width as f64;
                let value_z0 = lerp(delta_x, value_xz00, value_xz10);
                let value_z1 = lerp(delta_x, value_xz01, value_xz11);
                let row_start = (y * cell_width + x) * cell_width;
                for z in 0..cell_width {
                    let delta_z = z as f64 / cell_width as f64;
                    cell_output[row_start + z] = lerp(delta_z, value_z0, value_z1);
                }
            }
        }
    }

    Ok(())
}

#[inline(always)]
fn lerp(delta: f64, start: f64, end: f64) -> f64 {
    start + delta * (end - start)
}

#[derive(Default)]
pub(crate) struct ChunkSelectionScratch {
    distances: Vec<i32>,
    buffer: Vec<usize>,
}

#[derive(Default)]
pub(crate) struct NearestSelectionScratch {
    nearest: BinaryHeap<DistanceIndex>,
}

#[derive(Default)]
pub(crate) struct DistanceSortScratch {
    pairs: Vec<(i32, f64)>,
}

/// Computes squared Euclidean distances from one origin to packed x/y/z triples.
pub fn compute_squared_distances(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    output: &mut [i64],
) -> Result<(), NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    if output.len() != positions.len() / 3 {
        return Err(NativeError::OutputLengthMismatch);
    }

    if output.len() >= PARALLEL_THRESHOLD {
        output
            .par_iter_mut()
            .enumerate()
            .for_each(|(index, value)| {
                *value = squared_distance_at(origin_x, origin_y, origin_z, positions, index);
            });
    } else {
        for (index, value) in output.iter_mut().enumerate() {
            *value = squared_distance_at(origin_x, origin_y, origin_z, positions, index);
        }
    }

    Ok(())
}

/// Selects the nearest packed chunk positions with vanilla's signed wrapping distance math.
pub fn select_nearest_chunk_indices(
    origin_x: i32,
    origin_z: i32,
    packed_chunk_positions: &[i64],
    limit: usize,
    output: &mut [i32],
) -> Result<usize, NativeError> {
    select_nearest_chunk_indices_with_scratch(
        origin_x,
        origin_z,
        packed_chunk_positions,
        limit,
        output,
        &mut ChunkSelectionScratch::default(),
    )
}

pub(crate) fn select_nearest_chunk_indices_with_scratch(
    origin_x: i32,
    origin_z: i32,
    packed_chunk_positions: &[i64],
    limit: usize,
    output: &mut [i32],
    scratch: &mut ChunkSelectionScratch,
) -> Result<usize, NativeError> {
    let selected_count = limit.min(packed_chunk_positions.len());
    if output.len() < selected_count {
        return Err(NativeError::OutputLengthMismatch);
    }
    select_nearest_chunk_indices_internal(
        origin_x,
        origin_z,
        packed_chunk_positions,
        selected_count,
        scratch,
    )?;
    for (output_index, candidate_index) in scratch.buffer.iter().enumerate() {
        output[output_index] = *candidate_index as i32;
    }
    Ok(selected_count)
}

/// Computes squared Euclidean distances from one origin to packed x/y/z triples.
#[allow(
    clippy::needless_range_loop,
    reason = "the SIMD tail uses one packed position index for input and output"
)]
pub fn compute_squared_distances_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [f64],
) -> Result<(), NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    if output.len() != positions.len() / 3 {
        return Err(NativeError::OutputLengthMismatch);
    }

    let count = output.len();

    if count >= PARALLEL_THRESHOLD {
        output
            .par_iter_mut()
            .enumerate()
            .for_each(|(index, value)| {
                *value = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
            });
    } else if has_avx2() && count >= 4 {
        let simd_count = unsafe {
            simd::squared_distances_f64_avx2(origin_x, origin_y, origin_z, positions, output)
        };
        for index in simd_count..count {
            output[index] = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        }
    } else {
        for (index, value) in output.iter_mut().enumerate() {
            *value = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        }
    }

    Ok(())
}

/// Computes the vanilla PotentialCalculator point-charge energy change.
pub fn potential_energy_change(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    charges: &[f64],
    charge_multiplier: f64,
) -> Result<f64, NativeError> {
    if charge_multiplier == 0.0 {
        return Ok(0.0);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    if charges.len() != positions.len() / 3 {
        return Err(NativeError::InvalidInput);
    }

    let charge_count = charges.len();

    if has_avx2() && charge_count >= 4 {
        return potential_energy_change_simd(
            origin_x,
            origin_y,
            origin_z,
            positions,
            charges,
            charge_multiplier,
        );
    }

    let mut energy = 0.0;
    for (index, charge) in charges.iter().enumerate() {
        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        energy += if distance == 0.0 {
            f64::INFINITY
        } else {
            *charge / distance.sqrt()
        };
    }

    Ok(energy * charge_multiplier)
}

#[allow(
    clippy::needless_range_loop,
    reason = "the SIMD tail must preserve the exact scalar accumulation order"
)]
fn potential_energy_change_simd(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    charges: &[f64],
    charge_multiplier: f64,
) -> Result<f64, NativeError> {
    let count = charges.len();
    let (mut energy, simd_count) = unsafe {
        simd::potential_energy_sum_f64_avx2(origin_x, origin_y, origin_z, positions, charges)
    };

    for index in simd_count..count {
        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        energy += if distance == 0.0 {
            f64::INFINITY
        } else {
            charges[index] / distance.sqrt()
        };
    }

    Ok(energy * charge_multiplier)
}

// ---------------------------------------------------------------------------
// Potential energy charge cache for repeated FFM calls.
// ---------------------------------------------------------------------------

#[cfg(feature = "cubecl-preview")]
enum CachedPotentialBackend {
    Disabled,
    Calibrating,
    CubeCl(Box<CubePotentialCache>),
}

#[cfg(feature = "cubecl-preview")]
struct CachedPotentialCharges {
    generation: u64,
    positions: Arc<Vec<i32>>,
    charges: Arc<Vec<f64>>,
    backend: CachedPotentialBackend,
}

#[cfg(feature = "cubecl-preview")]
struct PotentialCalibrationRequest {
    generation: u64,
    positions: Arc<Vec<i32>>,
    charges: Arc<Vec<f64>>,
}

#[cfg(not(feature = "cubecl-preview"))]
static POTENTIAL_CACHE: Mutex<Option<(Vec<i32>, Vec<f64>)>> = Mutex::new(None);
#[cfg(feature = "cubecl-preview")]
static POTENTIAL_CACHE: Mutex<Option<CachedPotentialCharges>> = Mutex::new(None);
#[cfg(feature = "cubecl-preview")]
static POTENTIAL_NEXT_GENERATION: AtomicU64 = AtomicU64::new(1);
#[cfg(feature = "cubecl-preview")]
static POTENTIAL_ACTIVE_GENERATION: AtomicU64 = AtomicU64::new(0);
#[cfg(feature = "cubecl-preview")]
static POTENTIAL_CALIBRATION_SENDER: OnceLock<Option<mpsc::Sender<PotentialCalibrationRequest>>> =
    OnceLock::new();

/// Caches one packed positions + charges snapshot.
/// Subsequent `compute_cached_potential_energy_change` calls only transmit the
/// origin coordinates.
#[cfg(not(feature = "cubecl-preview"))]
pub fn set_cached_potential_charges(
    positions: Vec<i32>,
    charges: Vec<f64>,
) -> Result<(), NativeError> {
    charge_multiplier_preconditions(&positions, &charges)?;
    *POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner()) = Some((positions, charges));
    Ok(())
}

/// Caches one packed positions + charges snapshot and schedules conservative
/// CubeCL calibration when the preview feature is explicitly enabled.
#[cfg(feature = "cubecl-preview")]
pub fn set_cached_potential_charges(
    positions: Vec<i32>,
    charges: Vec<f64>,
) -> Result<(), NativeError> {
    if charge_multiplier_preconditions(&positions, &charges).is_err() {
        return Err(NativeError::InvalidInput);
    }

    let generation = POTENTIAL_NEXT_GENERATION.fetch_add(1, AtomicOrdering::Relaxed);
    let positions = Arc::new(positions);
    let charges = Arc::new(charges);
    let should_calibrate = charges.len() >= CUBECL_MIN_CHARGE_COUNT
        && std::thread::available_parallelism().is_ok_and(|count| count.get() >= 2);
    if should_calibrate {
        crate::cubecl_preview::reset_diagnostic();
    }
    let request = should_calibrate.then(|| PotentialCalibrationRequest {
        generation,
        positions: Arc::clone(&positions),
        charges: Arc::clone(&charges),
    });

    *POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner()) = Some(CachedPotentialCharges {
        generation,
        positions,
        charges,
        backend: if should_calibrate {
            CachedPotentialBackend::Calibrating
        } else {
            CachedPotentialBackend::Disabled
        },
    });
    POTENTIAL_ACTIVE_GENERATION.store(generation, AtomicOrdering::Release);

    if let Some(request) = request
        && !schedule_potential_calibration(request)
    {
        crate::cubecl_preview::mark_runtime_failure();
        disable_cubecl_for_generation(generation);
    }
    Ok(())
}

/// Computes potential energy change using previously cached charges.
/// Returns `InvalidInput` when no cache has been set.
#[cfg(not(feature = "cubecl-preview"))]
pub fn compute_cached_potential_energy_change(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    charge_multiplier: f64,
) -> Result<f64, NativeError> {
    if charge_multiplier == 0.0 {
        return Ok(0.0);
    }
    let cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
    let (positions, charges) = cache.as_ref().ok_or(NativeError::InvalidInput)?;
    potential_energy_change(
        origin_x,
        origin_y,
        origin_z,
        positions,
        charges,
        charge_multiplier,
    )
}

/// Computes potential energy change using the preview backend only after its
/// background calibration proves exact parity and a wide performance margin.
#[cfg(feature = "cubecl-preview")]
pub fn compute_cached_potential_energy_change(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    charge_multiplier: f64,
) -> Result<f64, NativeError> {
    if charge_multiplier == 0.0 {
        return Ok(0.0);
    }
    let mut cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
    let cache = cache.as_mut().ok_or(NativeError::InvalidInput)?;
    let cubecl_result = match &cache.backend {
        CachedPotentialBackend::CubeCl(cubecl) => Some(catch_unwind(AssertUnwindSafe(|| {
            cubecl.compute(origin_x, origin_y, origin_z, charge_multiplier)
        }))),
        CachedPotentialBackend::Disabled | CachedPotentialBackend::Calibrating => None,
    };
    if let Some(result) = cubecl_result {
        if let Ok(Ok(value)) = result {
            return Ok(value);
        }
        crate::cubecl_preview::mark_runtime_failure();
        cache.backend = CachedPotentialBackend::Disabled;
    }

    potential_energy_change(
        origin_x,
        origin_y,
        origin_z,
        cache.positions.as_slice(),
        cache.charges.as_slice(),
        charge_multiplier,
    )
}

/// Clears the cached charges so the next compute call falls through to an error.
#[cfg(not(feature = "cubecl-preview"))]
pub fn clear_cached_potential_charges() {
    *POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner()) = None;
}

/// Clears the cached charges and cancels any preview calibration in flight.
#[cfg(feature = "cubecl-preview")]
pub fn clear_cached_potential_charges() {
    POTENTIAL_ACTIVE_GENERATION.store(0, AtomicOrdering::Release);
    *POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner()) = None;
}

#[cfg(feature = "cubecl-preview")]
fn schedule_potential_calibration(request: PotentialCalibrationRequest) -> bool {
    POTENTIAL_CALIBRATION_SENDER
        .get_or_init(|| {
            let (sender, receiver) = mpsc::channel();
            thread::Builder::new()
                .name("beryllium-cubecl-calibration".to_owned())
                .spawn(move || potential_calibration_worker(receiver))
                .ok()
                .map(|_| sender)
        })
        .as_ref()
        .is_some_and(|sender| sender.send(request).is_ok())
}

#[cfg(feature = "cubecl-preview")]
fn potential_calibration_worker(receiver: mpsc::Receiver<PotentialCalibrationRequest>) {
    while let Ok(mut request) = receiver.recv() {
        while let Ok(newer_request) = receiver.try_recv() {
            request = newer_request;
        }
        if POTENTIAL_ACTIVE_GENERATION.load(AtomicOrdering::Acquire) != request.generation {
            continue;
        }

        let generation = request.generation;
        let calibrated = catch_unwind(AssertUnwindSafe(|| {
            CubePotentialCache::calibrate(
                request.positions.as_slice(),
                request.charges.as_slice(),
                generation,
                || POTENTIAL_ACTIVE_GENERATION.load(AtomicOrdering::Acquire) == generation,
            )
        }))
        .ok()
        .flatten();

        finish_potential_calibration(generation, calibrated);
    }
}

static NOISE_GENERATORS: Mutex<Vec<Box<dyn NoiseGenerator>>> = Mutex::new(Vec::new());

pub fn create_perlin_noise(seed: i64) -> Result<usize, NativeError> {
    let generator = Box::new(PerlinNoise::new(seed));
    let mut generators = NOISE_GENERATORS.lock().unwrap();
    let id = generators.len();
    generators.push(generator);
    Ok(id)
}

pub fn create_simplex_noise(seed: i64) -> Result<usize, NativeError> {
    let generator = Box::new(SimplexNoise::new(seed));
    let mut generators = NOISE_GENERATORS.lock().unwrap();
    let id = generators.len();
    generators.push(generator);
    Ok(id)
}

pub fn create_opensimplex2_noise(seed: i64) -> Result<usize, NativeError> {
    let generator = Box::new(OpenSimplex2Noise::new(seed));
    let mut generators = NOISE_GENERATORS.lock().unwrap();
    let id = generators.len();
    generators.push(generator);
    Ok(id)
}

pub fn destroy_noise_generator(id: usize) -> Result<(), NativeError> {
    let mut generators = NOISE_GENERATORS.lock().unwrap();
    if id >= generators.len() {
        return Err(NativeError::InvalidInput);
    }
    generators[id] = Box::new(PerlinNoise::new(0));
    Ok(())
}

pub fn batch_sample_noise_3d(
    generator_id: usize,
    positions: &[f64],
    output: &mut [f64],
) -> Result<(), NativeError> {
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }
    let count = positions.len() / 3;
    if output.len() != count {
        return Err(NativeError::OutputLengthMismatch);
    }

    let generators = NOISE_GENERATORS.lock().unwrap();
    if generator_id >= generators.len() {
        return Err(NativeError::InvalidInput);
    }

    let generator = &*generators[generator_id];
    batch_sample_noise_3d_parallel(generator, positions, output)
}

pub fn compute_biome_weights_3d(
    sample_positions: &[f64],
    biome_centers: &[f64],
    influence_radius: f64,
    output: &mut [(i32, f64)],
    max_biomes_per_sample: usize,
) -> Result<(), NativeError> {
    let mut biome_weights: Vec<BiomeWeight> = vec![BiomeWeight { biome_index: -1, weight: 0.0 }; output.len()];
    
    batch_compute_biome_weights_3d(
        sample_positions,
        biome_centers,
        influence_radius,
        &mut biome_weights,
        max_biomes_per_sample,
    )?;

    for (i, bw) in biome_weights.iter().enumerate() {
        output[i] = (bw.biome_index, bw.weight);
    }

    Ok(())
}

pub fn interpolate_biome_values(
    biome_indices: &[i32],
    weights: &[f64],
    biome_values: &[f64],
    samples_per_position: usize,
    output: &mut [f64],
) -> Result<(), NativeError> {
    if biome_indices.len() != weights.len() {
        return Err(NativeError::InvalidInput);
    }

    let biome_weights: Vec<BiomeWeight> = biome_indices
        .iter()
        .zip(weights.iter())
        .map(|(&idx, &w)| BiomeWeight {
            biome_index: idx,
            weight: w,
        })
        .collect();

    batch_interpolate_biome_values(&biome_weights, biome_values, samples_per_position, output)
}

#[cfg(feature = "cubecl-preview")]
fn finish_potential_calibration(generation: u64, calibrated: Option<CubePotentialCache>) {
    let mut cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(cache) = cache.as_mut()
        && cache.generation == generation
        && POTENTIAL_ACTIVE_GENERATION.load(AtomicOrdering::Acquire) == generation
    {
        cache.backend = calibrated.map_or(CachedPotentialBackend::Disabled, |cubecl| {
            CachedPotentialBackend::CubeCl(Box::new(cubecl))
        });
    }
}

#[cfg(feature = "cubecl-preview")]
fn disable_cubecl_for_generation(generation: u64) {
    let mut cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(cache) = cache.as_mut()
        && cache.generation == generation
    {
        cache.backend = CachedPotentialBackend::Disabled;
    }
}

#[cfg(feature = "cubecl-preview")]
pub(crate) fn is_potential_generation_current(generation: u64) -> bool {
    POTENTIAL_ACTIVE_GENERATION.load(AtomicOrdering::Acquire) == generation
}

#[cfg(feature = "cubecl-preview")]
pub(crate) fn cubecl_preview_status() -> i32 {
    let cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
    match cache.as_ref().map(|cache| &cache.backend) {
        Some(CachedPotentialBackend::Calibrating) => 1,
        Some(CachedPotentialBackend::CubeCl(_)) => 2,
        Some(CachedPotentialBackend::Disabled) => crate::cubecl_preview::diagnostic_status(),
        None => 0,
    }
}

#[cfg(not(feature = "cubecl-preview"))]
pub(crate) fn cubecl_preview_status() -> i32 {
    0
}

fn charge_multiplier_preconditions(positions: &[i32], charges: &[f64]) -> Result<(), NativeError> {
    if !positions.len().is_multiple_of(3) || charges.len() != positions.len() / 3 {
        return Err(NativeError::InvalidInput);
    }
    Ok(())
}

/// Finds the nearest packed f64 x/y/z triple within an optional squared radius.
pub fn find_nearest_index_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: &[f64],
) -> Result<Option<usize>, NativeError> {
    find_nearest_index_f64_by_limit(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
        within_max_distance,
    )
}

/// Finds the nearest packed f64 x/y/z triple within an optional exclusive squared radius.
pub fn find_nearest_index_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: &[f64],
) -> Result<Option<usize>, NativeError> {
    find_nearest_index_f64_by_limit(
        origin_x,
        origin_y,
        origin_z,
        max_distance_squared,
        positions,
        within_max_distance_exclusive,
    )
}

/// Returns whether any packed f64 x/y/z triple is within an optional exclusive squared radius.
pub fn has_any_within_radius_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: &[f64],
) -> Result<bool, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count == 0 {
        return Ok(false);
    }
    if max_distance_squared < 0.0 {
        return Ok(true);
    }

    if position_count >= PARALLEL_THRESHOLD {
        return Ok(positions.par_chunks_exact(3).any(|position| {
            squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                < max_distance_squared
        }));
    }

    if has_avx2() && position_count >= 4 {
        let simd_chunks = position_count / 4;
        let mut buf = [0.0_f64; 4];
        for chunk in 0..simd_chunks {
            unsafe {
                batch_4_distances(
                    origin_x,
                    origin_y,
                    origin_z,
                    positions,
                    chunk * 12,
                    &mut buf,
                );
            }
            for d in buf {
                if d < max_distance_squared {
                    return Ok(true);
                }
            }
        }
        for index in (simd_chunks * 4)..position_count {
            if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
                < max_distance_squared
            {
                return Ok(true);
            }
        }
        return Ok(false);
    }

    Ok((0..position_count).any(|index| {
        squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
            < max_distance_squared
    }))
}

/// Finds the nearest packed block position by squared distance to its block center.
pub fn find_nearest_block_center_index(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[i32],
) -> Result<Option<usize>, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count == 0 {
        return Ok(None);
    }

    if position_count >= BLOCK_NEAREST_PARALLEL_THRESHOLD {
        return Ok((0..position_count)
            .into_par_iter()
            .filter_map(|index| {
                let distance =
                    block_center_distance_at(origin_x, origin_y, origin_z, positions, index);
                if distance.is_nan() {
                    None
                } else {
                    Some((index, distance))
                }
            })
            .reduce_with(|left, right| nearest_block_center_pair(left, right, positions))
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for index in 0..position_count {
        let distance = block_center_distance_at(origin_x, origin_y, origin_z, positions, index);
        if distance < nearest_distance
            || (distance == nearest_distance
                && nearest_index
                    .map(|current| compare_block_pos(positions, current, index) < 0)
                    .unwrap_or(true))
        {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Finds the nearest packed block position by squared distance to its block low corner.
pub fn find_nearest_block_corner_index(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
) -> Result<Option<usize>, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count == 0 {
        return Ok(None);
    }

    if position_count >= BLOCK_NEAREST_PARALLEL_THRESHOLD {
        return Ok((0..position_count)
            .into_par_iter()
            .map(|index| {
                (
                    index,
                    block_corner_distance_at(origin_x, origin_y, origin_z, positions, index),
                )
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for index in 0..position_count {
        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Finds the nearest packed block position within an inclusive squared radius.
pub fn find_nearest_block_corner_index_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: &[i32],
) -> Result<Option<usize>, NativeError> {
    if radius_squared < 0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count == 0 {
        return Ok(None);
    }

    if position_count >= BLOCK_NEAREST_PARALLEL_THRESHOLD {
        return Ok((0..position_count)
            .into_par_iter()
            .filter_map(|index| {
                if squared_distance_at(origin_x, origin_y, origin_z, positions, index)
                    > radius_squared
                {
                    None
                } else {
                    Some((
                        index,
                        block_corner_distance_at(origin_x, origin_y, origin_z, positions, index),
                    ))
                }
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for index in 0..position_count {
        if squared_distance_at(origin_x, origin_y, origin_z, positions, index) > radius_squared {
            continue;
        }

        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Finds the nearest compact Minecraft BlockPos by squared distance to its block low corner.
pub fn find_nearest_packed_block_corner_index(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    packed_positions: &[i64],
) -> Result<Option<usize>, NativeError> {
    if packed_positions.is_empty() {
        return Ok(None);
    }

    if packed_positions.len() >= BLOCK_NEAREST_PARALLEL_THRESHOLD {
        return Ok((0..packed_positions.len())
            .into_par_iter()
            .map(|index| {
                (
                    index,
                    packed_block_corner_distance_at(
                        origin_x,
                        origin_y,
                        origin_z,
                        packed_positions[index],
                    ),
                )
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for (index, packed_position) in packed_positions.iter().copied().enumerate() {
        let distance =
            packed_block_corner_distance_at(origin_x, origin_y, origin_z, packed_position);
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Finds the nearest compact Minecraft BlockPos within an inclusive squared radius.
pub fn find_nearest_packed_block_corner_index_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    packed_positions: &[i64],
) -> Result<Option<usize>, NativeError> {
    if radius_squared < 0 {
        return Err(NativeError::InvalidInput);
    }

    if packed_positions.is_empty() {
        return Ok(None);
    }

    if packed_positions.len() >= BLOCK_NEAREST_PARALLEL_THRESHOLD {
        return Ok((0..packed_positions.len())
            .into_par_iter()
            .filter_map(|index| {
                let packed_position = packed_positions[index];
                if packed_squared_distance_at(origin_x, origin_y, origin_z, packed_position)
                    > radius_squared
                {
                    None
                } else {
                    Some((
                        index,
                        packed_block_corner_distance_at(
                            origin_x,
                            origin_y,
                            origin_z,
                            packed_position,
                        ),
                    ))
                }
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for (index, packed_position) in packed_positions.iter().copied().enumerate() {
        if packed_squared_distance_at(origin_x, origin_y, origin_z, packed_position)
            > radius_squared
        {
            continue;
        }

        let distance =
            packed_block_corner_distance_at(origin_x, origin_y, origin_z, packed_position);
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

fn find_nearest_index_f64_by_limit(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: &[f64],
    within_limit: fn(f64, f64) -> bool,
) -> Result<Option<usize>, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count == 0 {
        return Ok(None);
    }

    if position_count >= PARALLEL_THRESHOLD {
        return Ok((0..position_count)
            .into_par_iter()
            .filter_map(|index| {
                let distance =
                    squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
                if within_limit(distance, max_distance_squared) {
                    Some((index, distance))
                } else {
                    None
                }
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = 0.0;
    if has_avx2() && position_count >= 4 {
        let simd_chunks = position_count / 4;
        let mut distances = [0.0_f64; 4];
        for chunk in 0..simd_chunks {
            let base = chunk * 4;
            unsafe {
                batch_4_distances(
                    origin_x,
                    origin_y,
                    origin_z,
                    positions,
                    chunk * 12,
                    &mut distances,
                );
            }
            for (offset, distance) in distances.iter().copied().enumerate() {
                if !within_limit(distance, max_distance_squared) {
                    continue;
                }
                if nearest_index.is_none() || distance < nearest_distance {
                    nearest_index = Some(base + offset);
                    nearest_distance = distance;
                }
            }
        }
        for index in (simd_chunks * 4)..position_count {
            let distance = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
            if !within_limit(distance, max_distance_squared) {
                continue;
            }
            if nearest_index.is_none() || distance < nearest_distance {
                nearest_index = Some(index);
                nearest_distance = distance;
            }
        }
        return Ok(nearest_index);
    }

    for index in 0..position_count {
        let distance = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        if !within_limit(distance, max_distance_squared) {
            continue;
        }
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Filters packed f64 x/y/z triples by squared radius and returns the matching indices.
pub fn filter_within_radius_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if radius_squared < 0.0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= FILTER_PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    <= radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    if has_avx2() && position_count >= 4 {
        let simd_chunks = position_count / 4;
        let mut buf = [0.0_f64; 4];
        let mut count = 0;
        for chunk in 0..simd_chunks {
            let base = chunk * 4;
            unsafe {
                batch_4_distances(
                    origin_x,
                    origin_y,
                    origin_z,
                    positions,
                    chunk * 12,
                    &mut buf,
                );
            }
            for (i, distance) in buf.iter().enumerate() {
                if *distance <= radius_squared {
                    output[count] = (base + i) as i32;
                    count += 1;
                }
            }
        }
        for index in (simd_chunks * 4)..position_count {
            if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
                <= radius_squared
            {
                output[count] = index as i32;
                count += 1;
            }
        }
        return Ok(count);
    }

    let mut count = 0;
    for index in 0..position_count {
        if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index) <= radius_squared
        {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed f64 x/y/z triples by exclusive squared radius and returns the matching indices.
pub fn filter_within_radius_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if radius_squared < 0.0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= FILTER_PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    < radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    if has_avx2() && position_count >= 4 {
        let simd_chunks = position_count / 4;
        let mut buf = [0.0_f64; 4];
        let mut count = 0;
        for chunk in 0..simd_chunks {
            let base = chunk * 4;
            unsafe {
                batch_4_distances(
                    origin_x,
                    origin_y,
                    origin_z,
                    positions,
                    chunk * 12,
                    &mut buf,
                );
            }
            for (i, distance) in buf.iter().enumerate() {
                if *distance < radius_squared {
                    output[count] = (base + i) as i32;
                    count += 1;
                }
            }
        }
        for index in (simd_chunks * 4)..position_count {
            if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
                < radius_squared
            {
                output[count] = index as i32;
                count += 1;
            }
        }
        return Ok(count);
    }

    let mut count = 0;
    for index in 0..position_count {
        if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index) < radius_squared
        {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed f64 x/z pairs by an exclusive horizontal squared radius.
pub fn filter_within_exclusive_chunk_distance(
    origin_x: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if radius_squared < 0.0 || !positions.len().is_multiple_of(2) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 2;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= FILTER_PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(2)
            .enumerate()
            .filter_map(|(index, position)| {
                let dx = position[0] - origin_x;
                let dz = position[1] - origin_z;
                if dx * dx + dz * dz < radius_squared {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    let mut count = 0;
    for index in 0..position_count {
        let offset = index * 2;
        let dx = positions[offset] - origin_x;
        let dz = positions[offset + 1] - origin_z;
        if dx * dx + dz * dz < radius_squared {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed f64 x/y/z triples by exclusive squared radius, then sorts matches by squared distance.
pub fn sort_within_radius_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    sort_within_radius_f64_exclusive_with_scratch(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
        &mut DistanceSortScratch::default(),
    )
}

pub(crate) fn sort_within_radius_f64_exclusive_with_scratch(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
    scratch: &mut DistanceSortScratch,
) -> Result<usize, NativeError> {
    if radius_squared < 0.0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        let mut matches: Vec<(i32, f64)> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                let distance =
                    squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position);
                if distance < radius_squared {
                    Some((index as i32, distance))
                } else {
                    None
                }
            })
            .collect();

        matches.par_sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });

        for (output_index, (index, _distance)) in matches.iter().enumerate() {
            output[output_index] = *index;
        }
        return Ok(matches.len());
    }

    build_simd_pairs_into(origin_x, origin_y, origin_z, positions, &mut scratch.pairs);
    scratch
        .pairs
        .retain(|(_, distance)| *distance < radius_squared);
    scratch.pairs.sort_unstable_by(|left, right| {
        compare_distance_order_f64(left.0, left.1, right.0, right.1)
    });

    for (output_index, (index, _distance)) in scratch.pairs.iter().enumerate() {
        output[output_index] = *index;
    }

    Ok(scratch.pairs.len())
}

/// Filters packed f64 x/y/z triples by one inclusive squared radius per position.
pub fn filter_within_radii_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    radii_squared: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if radii_squared.len() != position_count {
        return Err(NativeError::InvalidInput);
    }

    if radii_squared
        .iter()
        .any(|radius_squared| *radius_squared < 0.0)
    {
        return Err(NativeError::InvalidInput);
    }

    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= FILTER_PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .zip(radii_squared.par_iter())
            .enumerate()
            .filter_map(|(index, (position, radius_squared))| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    <= *radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    if has_avx2() && position_count >= 4 {
        let simd_chunks = position_count / 4;
        let mut distances = [0.0_f64; 4];
        let mut count = 0;
        for chunk in 0..simd_chunks {
            let base = chunk * 4;
            unsafe {
                batch_4_distances(
                    origin_x,
                    origin_y,
                    origin_z,
                    positions,
                    chunk * 12,
                    &mut distances,
                );
            }
            for (offset, distance) in distances.iter().enumerate() {
                if *distance <= radii_squared[base + offset] {
                    output[count] = (base + offset) as i32;
                    count += 1;
                }
            }
        }
        for (index, radius_squared) in radii_squared.iter().enumerate().skip(simd_chunks * 4) {
            if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
                <= *radius_squared
            {
                output[count] = index as i32;
                count += 1;
            }
        }
        return Ok(count);
    }

    let mut count = 0;
    for (index, radius_squared) in radii_squared.iter().enumerate() {
        if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
            <= *radius_squared
        {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed f64 x/y/z triples by AABB containment and returns the matching indices.
#[allow(
    clippy::too_many_arguments,
    reason = "the flat bounds avoid hot-path allocation"
)]
pub fn filter_within_aabb_f64(
    min_x: f64,
    min_y: f64,
    min_z: f64,
    max_x: f64,
    max_y: f64,
    max_z: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= FILTER_PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if contains_aabb_position(min_x, min_y, min_z, max_x, max_y, max_z, position) {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    let mut count = 0;
    for index in 0..position_count {
        let offset = index * 3;
        if contains_aabb(
            min_x,
            min_y,
            min_z,
            max_x,
            max_y,
            max_z,
            positions[offset],
            positions[offset + 1],
            positions[offset + 2],
        ) {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed f64 AABB min/max sextuples by intersection with one query AABB.
#[allow(
    clippy::too_many_arguments,
    reason = "the flat bounds avoid hot-path allocation"
)]
pub fn filter_intersecting_aabb_f64(
    query_min_x: f64,
    query_min_y: f64,
    query_min_z: f64,
    query_max_x: f64,
    query_max_y: f64,
    query_max_z: f64,
    boxes: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if !boxes.len().is_multiple_of(6) {
        return Err(NativeError::InvalidInput);
    }

    let box_count = boxes.len() / 6;
    if output.len() < box_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if box_count >= FILTER_PARALLEL_THRESHOLD {
        let matches: Vec<i32> = boxes
            .par_chunks_exact(6)
            .enumerate()
            .filter_map(|(index, entity_box)| {
                if intersects_aabb_box(
                    query_min_x,
                    query_min_y,
                    query_min_z,
                    query_max_x,
                    query_max_y,
                    query_max_z,
                    entity_box,
                ) {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    if has_avx2() && box_count >= 4 {
        let simd_chunks = box_count / 4;
        let mut count = 0;
        for chunk in 0..simd_chunks {
            let base = chunk * 4;
            let mask = unsafe {
                batch_4_aabb_intersections(
                    query_min_x,
                    query_min_y,
                    query_min_z,
                    query_max_x,
                    query_max_y,
                    query_max_z,
                    boxes,
                    chunk * 24,
                )
            };
            for offset in 0..4 {
                if mask & (1_u8 << offset) != 0 {
                    output[count] = (base + offset) as i32;
                    count += 1;
                }
            }
        }
        for index in (simd_chunks * 4)..box_count {
            let offset = index * 6;
            if intersects_aabb(
                query_min_x,
                query_min_y,
                query_min_z,
                query_max_x,
                query_max_y,
                query_max_z,
                boxes[offset],
                boxes[offset + 1],
                boxes[offset + 2],
                boxes[offset + 3],
                boxes[offset + 4],
                boxes[offset + 5],
            ) {
                output[count] = index as i32;
                count += 1;
            }
        }
        return Ok(count);
    }

    let mut count = 0;
    for index in 0..box_count {
        let offset = index * 6;
        if intersects_aabb(
            query_min_x,
            query_min_y,
            query_min_z,
            query_max_x,
            query_max_y,
            query_max_z,
            boxes[offset],
            boxes[offset + 1],
            boxes[offset + 2],
            boxes[offset + 3],
            boxes[offset + 4],
            boxes[offset + 5],
        ) {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed x/y/z triples by squared radius and returns the matching indices.
pub fn filter_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: &[i32],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if radius_squared < 0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if squared_distance_at_slice(origin_x, origin_y, origin_z, position)
                    <= radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    let mut count = 0;
    for index in 0..position_count {
        if squared_distance_at(origin_x, origin_y, origin_z, positions, index) <= radius_squared {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Counts packed x/y/z triples within an inclusive squared radius.
pub fn count_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: &[i32],
) -> Result<usize, NativeError> {
    if radius_squared < 0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count >= PARALLEL_THRESHOLD {
        return Ok(positions
            .par_chunks_exact(3)
            .filter(|position| {
                squared_distance_at_slice(origin_x, origin_y, origin_z, position) <= radius_squared
            })
            .count());
    }

    let mut count = 0;
    for index in 0..position_count {
        if squared_distance_at(origin_x, origin_y, origin_z, positions, index) <= radius_squared {
            count += 1;
        }
    }

    Ok(count)
}

/// Sorts packed x/y/z triples by squared distance and writes the index order.
pub fn sort_by_distance(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    output: &mut [i32],
) -> Result<(), NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() != position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    let mut indices: Vec<i32> = (0..position_count as i32).collect();
    if position_count >= PARALLEL_THRESHOLD {
        indices.par_sort_by_cached_key(|index| {
            (
                squared_distance_at(origin_x, origin_y, origin_z, positions, *index as usize),
                *index,
            )
        });
    } else {
        indices.sort_by_cached_key(|index| {
            (
                squared_distance_at(origin_x, origin_y, origin_z, positions, *index as usize),
                *index,
            )
        });
    }

    output.copy_from_slice(&indices);
    Ok(())
}

/// Sorts packed block positions by squared distance to the block low corner and writes the index order.
pub fn sort_by_block_distance(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    output: &mut [i32],
) -> Result<(), NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() != position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    let mut indexed_distances: Vec<(i32, f64)> = if position_count >= PARALLEL_THRESHOLD {
        (0..position_count as i32)
            .into_par_iter()
            .map(|index| {
                (
                    index,
                    block_corner_distance_at(
                        origin_x,
                        origin_y,
                        origin_z,
                        positions,
                        index as usize,
                    ),
                )
            })
            .collect()
    } else {
        (0..position_count as i32)
            .map(|index| {
                (
                    index,
                    block_corner_distance_at(
                        origin_x,
                        origin_y,
                        origin_z,
                        positions,
                        index as usize,
                    ),
                )
            })
            .collect()
    };

    if indexed_distances.len() >= PARALLEL_THRESHOLD {
        indexed_distances.par_sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    } else {
        indexed_distances.sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    }

    for (output_index, (index, _distance)) in indexed_distances.iter().enumerate() {
        output[output_index] = *index;
    }
    Ok(())
}

/// Sorts packed f64 x/y/z triples by squared distance and writes the index order.
pub fn sort_by_distance_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<(), NativeError> {
    sort_by_distance_f64_with_scratch(
        origin_x,
        origin_y,
        origin_z,
        positions,
        output,
        &mut DistanceSortScratch::default(),
    )
}

pub(crate) fn sort_by_distance_f64_with_scratch(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [i32],
    scratch: &mut DistanceSortScratch,
) -> Result<(), NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() != position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    build_simd_pairs_into(origin_x, origin_y, origin_z, positions, &mut scratch.pairs);

    if scratch.pairs.len() >= PARALLEL_THRESHOLD {
        scratch.pairs.par_sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    } else {
        scratch.pairs.sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    }

    for (output_index, (index, _distance)) in scratch.pairs.iter().enumerate() {
        output[output_index] = *index;
    }
    Ok(())
}

/// SIMD-batched distance pair builder - computes 4 distances at a time via AVX2
/// when available, falling back to scalar otherwise. Returns (index, distance) pairs.
#[allow(
    clippy::needless_range_loop,
    reason = "fixed four-lane loops are kept explicit for predictable unrolling"
)]
fn build_simd_pairs_into(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    pairs: &mut Vec<(i32, f64)>,
) {
    let position_count = positions.len() / 3;
    let has_simd = has_avx2();
    pairs.clear();
    if pairs.capacity() < position_count {
        pairs.reserve(position_count - pairs.capacity());
    }
    let mut buf = [0.0_f64; 4];
    let simd_chunks = position_count / 4;

    for chunk in 0..simd_chunks {
        let base = chunk * 4;
        if has_simd {
            unsafe {
                batch_4_distances(
                    origin_x,
                    origin_y,
                    origin_z,
                    positions,
                    chunk * 12,
                    &mut buf,
                );
            }
        } else {
            for i in 0..4 {
                buf[i] = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, base + i);
            }
        }
        for i in 0..4 {
            pairs.push(((base + i) as i32, buf[i]));
        }
    }
    for index in (simd_chunks * 4)..position_count {
        pairs.push((
            index as i32,
            squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index),
        ));
    }
}

/// Sorts packed f64 x/y/z triples by squared distance and returns the strict radius prefix length.
pub fn sort_by_distance_and_count_within_radius_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    sort_by_distance_and_count_within_radius_f64_exclusive_with_scratch(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        output,
        &mut DistanceSortScratch::default(),
    )
}

pub(crate) fn sort_by_distance_and_count_within_radius_f64_exclusive_with_scratch(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    output: &mut [i32],
    scratch: &mut DistanceSortScratch,
) -> Result<usize, NativeError> {
    if radius_squared < 0.0 || !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    build_simd_pairs_into(origin_x, origin_y, origin_z, positions, &mut scratch.pairs);

    if scratch.pairs.len() >= PARALLEL_THRESHOLD {
        scratch.pairs.par_sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    } else {
        scratch.pairs.sort_unstable_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    }

    let mut prefix_count = 0;
    for (output_index, (index, distance)) in scratch.pairs.iter().enumerate() {
        output[output_index] = *index;
        if *distance < radius_squared {
            prefix_count += 1;
        }
    }

    Ok(prefix_count)
}

/// Selects the first distance-ordered points inside a strict squared radius.
///
/// This keeps the Java distance comparator's tie behavior while avoiding a full
/// ordering when a caller only needs a small nearest prefix.
pub fn select_nearest_indices_within_radius_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    limit: usize,
    output: &mut [i32],
) -> Result<usize, NativeError> {
    select_nearest_indices_within_radius_f64_exclusive_with_scratch(
        origin_x,
        origin_y,
        origin_z,
        radius_squared,
        positions,
        limit,
        output,
        &mut NearestSelectionScratch::default(),
    )
}

#[allow(
    clippy::too_many_arguments,
    reason = "scratch is passed explicitly to keep the hot FFI path allocation-free"
)]
pub(crate) fn select_nearest_indices_within_radius_f64_exclusive_with_scratch(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    radius_squared: f64,
    positions: &[f64],
    limit: usize,
    output: &mut [i32],
    scratch: &mut NearestSelectionScratch,
) -> Result<usize, NativeError> {
    if radius_squared < 0.0 || !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    let selected_capacity = limit.min(position_count);
    if output.len() < selected_capacity {
        return Err(NativeError::OutputLengthMismatch);
    }
    if selected_capacity == 0 {
        return Ok(0);
    }

    if position_count >= NEAREST_SELECTION_PARALLEL_THRESHOLD {
        let nearest = positions
            .par_chunks_exact(3)
            .enumerate()
            .fold(
                || nearest_selection_heap(selected_capacity),
                |mut nearest, (index, position)| {
                    let distance =
                        squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position);
                    if distance < radius_squared {
                        retain_nearest_distance_index(
                            &mut nearest,
                            selected_capacity,
                            DistanceIndex::new(index as i32, distance),
                        );
                    }
                    nearest
                },
            )
            .reduce(
                || nearest_selection_heap(selected_capacity),
                |mut nearest, other| {
                    for candidate in other {
                        retain_nearest_distance_index(&mut nearest, selected_capacity, candidate);
                    }
                    nearest
                },
            );
        let mut nearest = nearest.into_vec();
        nearest.sort_unstable();

        for (output_index, candidate) in nearest.iter().enumerate() {
            output[output_index] = candidate.index;
        }
        return Ok(nearest.len());
    }

    scratch.nearest.clear();
    if scratch.nearest.capacity() < selected_capacity {
        scratch
            .nearest
            .reserve(selected_capacity - scratch.nearest.capacity());
    }
    for index in 0..position_count {
        let distance = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        if distance < radius_squared {
            retain_nearest_later_distance_index(
                &mut scratch.nearest,
                selected_capacity,
                DistanceIndex::new(index as i32, distance),
            );
        }
    }

    let selected_count = scratch.nearest.len();
    for output_index in (0..selected_count).rev() {
        output[output_index] = scratch
            .nearest
            .pop()
            .expect("nearest selection heap length changed")
            .index;
    }
    Ok(selected_count)
}

fn nearest_selection_heap(capacity: usize) -> BinaryHeap<DistanceIndex> {
    BinaryHeap::with_capacity(capacity.min(NEAREST_SELECTION_INITIAL_CAPACITY))
}

fn retain_nearest_distance_index(
    nearest: &mut BinaryHeap<DistanceIndex>,
    capacity: usize,
    candidate: DistanceIndex,
) {
    debug_assert!(capacity > 0);
    if nearest.len() < capacity {
        nearest.push(candidate);
    } else if candidate < *nearest.peek().expect("non-empty heap at capacity") {
        nearest.pop();
        nearest.push(candidate);
    }
}

fn retain_nearest_later_distance_index(
    nearest: &mut BinaryHeap<DistanceIndex>,
    capacity: usize,
    candidate: DistanceIndex,
) {
    debug_assert!(capacity > 0);
    if nearest.len() < capacity {
        nearest.push(candidate);
    } else if candidate.distance < nearest.peek().expect("non-empty heap at capacity").distance {
        nearest.pop();
        nearest.push(candidate);
    }
}

fn squared_distance_at(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    index: usize,
) -> i64 {
    let offset = index * 3;
    let dx = i64::from(positions[offset]) - i64::from(origin_x);
    let dy = i64::from(positions[offset + 1]) - i64::from(origin_y);
    let dz = i64::from(positions[offset + 2]) - i64::from(origin_z);
    squared_distance_components(dx, dy, dz)
}

fn chunk_distance_squared(origin_x: i32, origin_z: i32, packed_chunk_position: i64) -> i32 {
    let x = packed_chunk_position as i32;
    let z = ((packed_chunk_position as u64) >> 32) as i32;
    let dx = x.wrapping_sub(origin_x);
    let dz = z.wrapping_sub(origin_z);
    dx.wrapping_mul(dx).wrapping_add(dz.wrapping_mul(dz))
}

fn select_nearest_chunk_indices_internal(
    origin_x: i32,
    origin_z: i32,
    packed_chunk_positions: &[i64],
    selected_count: usize,
    scratch: &mut ChunkSelectionScratch,
) -> Result<(), NativeError> {
    if packed_chunk_positions.len() > i32::MAX as usize {
        return Err(NativeError::InvalidInput);
    }
    scratch.buffer.clear();
    if selected_count == 0 {
        return Ok(());
    }

    scratch.distances.resize(packed_chunk_positions.len(), 0);
    if packed_chunk_positions.len() >= CHUNK_SELECTION_PARALLEL_THRESHOLD {
        scratch
            .distances
            .par_iter_mut()
            .zip(packed_chunk_positions.par_iter())
            .for_each(|(distance, packed_position)| {
                *distance = chunk_distance_squared(origin_x, origin_z, *packed_position);
            });
    } else {
        for (distance, packed_position) in scratch
            .distances
            .iter_mut()
            .zip(packed_chunk_positions.iter())
        {
            *distance = chunk_distance_squared(origin_x, origin_z, *packed_position);
        }
    }

    let buffer_capacity = if selected_count == packed_chunk_positions.len() {
        selected_count
    } else {
        selected_count * 2
    };
    scratch.buffer.reserve(buffer_capacity);
    let mut threshold_distance = 0;

    for (index, distance) in scratch.distances.iter().copied().enumerate() {
        if scratch.buffer.is_empty() {
            scratch.buffer.push(index);
            threshold_distance = distance;
        } else if scratch.buffer.len() < selected_count {
            scratch.buffer.push(index);
            threshold_distance = threshold_distance.max(distance);
        } else if distance < threshold_distance {
            scratch.buffer.push(index);
            if scratch.buffer.len() == selected_count * 2 {
                threshold_distance =
                    trim_chunk_selection(&mut scratch.buffer, selected_count, &scratch.distances);
                scratch.buffer.truncate(selected_count);
            }
        }
    }

    stable_sort_chunk_selection(&mut scratch.buffer, &scratch.distances);
    scratch.buffer.truncate(selected_count);
    Ok(())
}

fn trim_chunk_selection(buffer: &mut [usize], selected_count: usize, distances: &[i32]) -> i32 {
    let mut left = 0;
    let mut right = selected_count * 2 - 1;
    let mut min_threshold_position = 0;
    let mut iterations = 0;
    let max_iterations = ceiling_log2(right - left) * 3;

    while left < right {
        let pivot_index = (left + right + 1) >> 1;
        let pivot_new_index =
            partition_chunk_selection(buffer, left, right, pivot_index, distances);
        if pivot_new_index > selected_count {
            right = pivot_new_index - 1;
        } else if pivot_new_index < selected_count {
            left = pivot_new_index.max(left + 1);
            min_threshold_position = pivot_new_index;
        } else {
            break;
        }

        iterations += 1;
        if iterations >= max_iterations {
            stable_sort_chunk_selection(&mut buffer[left..=right], distances);
            break;
        }
    }

    buffer[min_threshold_position + 1..selected_count]
        .iter()
        .fold(
            distances[buffer[min_threshold_position]],
            |threshold, index| threshold.max(distances[*index]),
        )
}

fn partition_chunk_selection(
    buffer: &mut [usize],
    left: usize,
    right: usize,
    pivot_index: usize,
    distances: &[i32],
) -> usize {
    let pivot_value = buffer[pivot_index];
    buffer[pivot_index] = buffer[right];
    let mut pivot_new_index = left;
    for index in left..right {
        if distances[buffer[index]] < distances[pivot_value] {
            buffer.swap(pivot_new_index, index);
            pivot_new_index += 1;
        }
    }
    buffer[right] = buffer[pivot_new_index];
    buffer[pivot_new_index] = pivot_value;
    pivot_new_index
}

fn stable_sort_chunk_selection(buffer: &mut [usize], distances: &[i32]) {
    buffer.sort_unstable_by(|left, right| distances[*left].cmp(&distances[*right]));
}

fn ceiling_log2(value: usize) -> usize {
    if value <= 1 {
        0
    } else {
        usize::BITS as usize - (value - 1).leading_zeros() as usize
    }
}

fn squared_distance_at_slice(origin_x: i32, origin_y: i32, origin_z: i32, position: &[i32]) -> i64 {
    let dx = i64::from(position[0]) - i64::from(origin_x);
    let dy = i64::from(position[1]) - i64::from(origin_y);
    let dz = i64::from(position[2]) - i64::from(origin_z);
    squared_distance_components(dx, dy, dz)
}

fn squared_distance_components(dx: i64, dy: i64, dz: i64) -> i64 {
    dx.wrapping_mul(dx)
        .wrapping_add(dy.wrapping_mul(dy))
        .wrapping_add(dz.wrapping_mul(dz))
}

fn squared_distance_at_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    index: usize,
) -> f64 {
    let offset = index * 3;
    let dx = positions[offset] - origin_x;
    let dy = positions[offset + 1] - origin_y;
    let dz = positions[offset + 2] - origin_z;
    dx * dx + dy * dy + dz * dz
}

fn squared_distance_at_f64_slice(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    position: &[f64],
) -> f64 {
    let dx = position[0] - origin_x;
    let dy = position[1] - origin_y;
    let dz = position[2] - origin_z;
    dx * dx + dy * dy + dz * dz
}

fn block_center_distance_at(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[i32],
    index: usize,
) -> f64 {
    let offset = index * 3;
    let dx = f64::from(positions[offset]) + 0.5 - origin_x;
    let dy = f64::from(positions[offset + 1]) + 0.5 - origin_y;
    let dz = f64::from(positions[offset + 2]) + 0.5 - origin_z;
    dx * dx + dy * dy + dz * dz
}

fn block_corner_distance_at(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    index: usize,
) -> f64 {
    let offset = index * 3;
    let dx = f64::from(positions[offset]) - f64::from(origin_x);
    let dy = f64::from(positions[offset + 1]) - f64::from(origin_y);
    let dz = f64::from(positions[offset + 2]) - f64::from(origin_z);
    dx * dx + dy * dy + dz * dz
}

fn packed_squared_distance_at(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    packed_position: i64,
) -> i64 {
    let (x, y, z) = unpack_packed_block_pos(packed_position);
    let dx = i64::from(x) - i64::from(origin_x);
    let dy = i64::from(y) - i64::from(origin_y);
    let dz = i64::from(z) - i64::from(origin_z);
    squared_distance_components(dx, dy, dz)
}

fn packed_block_corner_distance_at(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    packed_position: i64,
) -> f64 {
    let (x, y, z) = unpack_packed_block_pos(packed_position);
    let dx = f64::from(x) - f64::from(origin_x);
    let dy = f64::from(y) - f64::from(origin_y);
    let dz = f64::from(z) - f64::from(origin_z);
    dx * dx + dy * dy + dz * dz
}

fn unpack_packed_block_pos(packed_position: i64) -> (i32, i32, i32) {
    let bits = packed_position as u64;
    (
        sign_extend_packed_block_component(bits >> 38, 26),
        sign_extend_packed_block_component(bits, 12),
        sign_extend_packed_block_component(bits >> 12, 26),
    )
}

fn sign_extend_packed_block_component(value: u64, bit_length: u32) -> i32 {
    let shift = 64 - bit_length;
    ((value << shift) as i64 >> shift) as i32
}

fn contains_aabb_position(
    min_x: f64,
    min_y: f64,
    min_z: f64,
    max_x: f64,
    max_y: f64,
    max_z: f64,
    position: &[f64],
) -> bool {
    contains_aabb(
        min_x,
        min_y,
        min_z,
        max_x,
        max_y,
        max_z,
        position[0],
        position[1],
        position[2],
    )
}

#[allow(
    clippy::too_many_arguments,
    reason = "scalar bounds avoid temporary AABB objects"
)]
fn contains_aabb(
    min_x: f64,
    min_y: f64,
    min_z: f64,
    max_x: f64,
    max_y: f64,
    max_z: f64,
    x: f64,
    y: f64,
    z: f64,
) -> bool {
    x >= min_x && x < max_x && y >= min_y && y < max_y && z >= min_z && z < max_z
}

fn intersects_aabb_box(
    query_min_x: f64,
    query_min_y: f64,
    query_min_z: f64,
    query_max_x: f64,
    query_max_y: f64,
    query_max_z: f64,
    entity_box: &[f64],
) -> bool {
    intersects_aabb(
        query_min_x,
        query_min_y,
        query_min_z,
        query_max_x,
        query_max_y,
        query_max_z,
        entity_box[0],
        entity_box[1],
        entity_box[2],
        entity_box[3],
        entity_box[4],
        entity_box[5],
    )
}

#[allow(
    clippy::too_many_arguments,
    reason = "scalar bounds avoid temporary AABB objects"
)]
fn intersects_aabb(
    query_min_x: f64,
    query_min_y: f64,
    query_min_z: f64,
    query_max_x: f64,
    query_max_y: f64,
    query_max_z: f64,
    box_min_x: f64,
    box_min_y: f64,
    box_min_z: f64,
    box_max_x: f64,
    box_max_y: f64,
    box_max_z: f64,
) -> bool {
    box_max_x > query_min_x
        && box_min_x < query_max_x
        && box_max_y > query_min_y
        && box_min_y < query_max_y
        && box_max_z > query_min_z
        && box_min_z < query_max_z
}

fn within_max_distance(distance: f64, max_distance_squared: f64) -> bool {
    max_distance_squared < 0.0 || distance <= max_distance_squared
}

fn within_max_distance_exclusive(distance: f64, max_distance_squared: f64) -> bool {
    max_distance_squared < 0.0 || distance < max_distance_squared
}

fn nearest_distance_pair(left: (usize, f64), right: (usize, f64)) -> (usize, f64) {
    if right.1 < left.1 || (right.1 == left.1 && right.0 < left.0) {
        right
    } else {
        left
    }
}

fn nearest_block_center_pair(
    left: (usize, f64),
    right: (usize, f64),
    positions: &[i32],
) -> (usize, f64) {
    if right.1 < left.1 || (right.1 == left.1 && compare_block_pos(positions, left.0, right.0) < 0)
    {
        right
    } else {
        left
    }
}

fn compare_block_pos(positions: &[i32], left_index: usize, right_index: usize) -> i32 {
    let left_offset = left_index * 3;
    let right_offset = right_index * 3;
    let left_y = positions[left_offset + 1];
    let right_y = positions[right_offset + 1];
    if left_y != right_y {
        return left_y.wrapping_sub(right_y);
    }

    let left_z = positions[left_offset + 2];
    let right_z = positions[right_offset + 2];
    if left_z != right_z {
        return left_z.wrapping_sub(right_z);
    }

    positions[left_offset].wrapping_sub(positions[right_offset])
}

fn compare_distance_order_f64(
    left_index: i32,
    left_distance: f64,
    right_index: i32,
    right_distance: f64,
) -> Ordering {
    let distance_order = compare_java_double(left_distance, right_distance);
    if distance_order == Ordering::Equal {
        left_index.cmp(&right_index)
    } else {
        distance_order
    }
}

#[derive(Clone, Copy)]
struct DistanceIndex {
    index: i32,
    distance: f64,
}

impl DistanceIndex {
    fn new(index: i32, distance: f64) -> Self {
        Self { index, distance }
    }
}

impl PartialEq for DistanceIndex {
    fn eq(&self, other: &Self) -> bool {
        self.cmp(other) == Ordering::Equal
    }
}

impl Eq for DistanceIndex {}

impl PartialOrd for DistanceIndex {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for DistanceIndex {
    fn cmp(&self, other: &Self) -> Ordering {
        compare_distance_order_f64(self.index, self.distance, other.index, other.distance)
    }
}

fn compare_java_double(left: f64, right: f64) -> Ordering {
    if left < right {
        Ordering::Less
    } else if left > right {
        Ordering::Greater
    } else if left.is_nan() || right.is_nan() {
        match (left.is_nan(), right.is_nan()) {
            (true, true) => Ordering::Equal,
            (true, false) => Ordering::Greater,
            (false, true) => Ordering::Less,
            (false, false) => unreachable!(),
        }
    } else {
        (left.to_bits() as i64).cmp(&(right.to_bits() as i64))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    static POTENTIAL_CACHE_TEST_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

    #[test]
    fn compute_squared_distances_should_match_reference_values() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        compute_squared_distances(0, 64, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 41, 6]);
    }

    #[test]
    fn select_nearest_chunk_indices_should_return_distance_ordered_top_k() {
        let positions = [pack_chunk(3, 0), pack_chunk(1, 0), pack_chunk(2, 0)];
        let mut output = [-1; 2];
        let count = select_nearest_chunk_indices(0, 0, &positions, 2, &mut output).unwrap();
        assert_eq!((count, output), (2, [1, 2]));
    }

    #[test]
    fn select_nearest_chunk_indices_should_preserve_output_tail() {
        let positions = [pack_chunk(8, 8), pack_chunk(1, 0), pack_chunk(0, 2)];
        let mut output = [73; 5];
        select_nearest_chunk_indices(0, 0, &positions, 2, &mut output).unwrap();
        assert_eq!(output, [1, 2, 73, 73, 73]);
    }

    #[test]
    fn select_nearest_chunk_indices_should_accept_oversized_limit() {
        let positions = [pack_chunk(3, 0), pack_chunk(1, 0), pack_chunk(2, 0)];
        let mut output = [-1; 3];
        let count = select_nearest_chunk_indices(0, 0, &positions, 7, &mut output).unwrap();
        assert_eq!((count, output), (3, [1, 2, 0]));
    }

    #[test]
    fn select_nearest_chunk_indices_should_reuse_scratch_capacity() {
        let positions = (0..8192)
            .map(|index| pack_chunk(index * 31, index.rotate_left(7)))
            .collect::<Vec<_>>();
        let mut output = [0; 64];
        let mut scratch = ChunkSelectionScratch::default();

        select_nearest_chunk_indices_with_scratch(0, 0, &positions, 64, &mut output, &mut scratch)
            .unwrap();
        let distance_capacity = scratch.distances.capacity();
        let buffer_capacity = scratch.buffer.capacity();

        select_nearest_chunk_indices_with_scratch(7, -9, &positions, 64, &mut output, &mut scratch)
            .unwrap();
        assert_eq!(scratch.distances.capacity(), distance_capacity);
        assert_eq!(scratch.buffer.capacity(), buffer_capacity);
    }

    fn pack_chunk(x: i32, z: i32) -> i64 {
        i64::from(x as u32) | (i64::from(z as u32) << 32)
    }

    fn pack_block_pos(x: i32, y: i32, z: i32) -> i64 {
        ((i64::from(x) & 0x3ff_ffff) << 38)
            | ((i64::from(z) & 0x3ff_ffff) << 12)
            | (i64::from(y) & 0xfff)
    }

    #[test]
    fn compute_squared_distances_should_reject_unpacked_positions() {
        let positions = [1, 2];
        let mut output = [0; 1];
        let result = compute_squared_distances(0, 0, 0, &positions, &mut output);
        assert_eq!(result, Err(NativeError::InvalidInput));
    }

    #[test]
    fn compute_squared_distances_should_reject_wrong_output_length() {
        let positions = [1, 2, 3];
        let mut output = [0; 2];
        let result = compute_squared_distances(0, 0, 0, &positions, &mut output);
        assert_eq!(result, Err(NativeError::OutputLengthMismatch));
    }

    #[test]
    fn compute_squared_distances_should_widen_before_subtracting() {
        let positions = [i32::MAX, 0, 0];
        let mut output = [0; 1];
        compute_squared_distances(i32::MIN, 0, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [-8_589_934_591]);
    }

    #[test]
    fn compute_squared_distances_f64_should_match_reference_values() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let mut output = [0.0; 3];
        compute_squared_distances_f64(0.0, 64.0, 0.0, &positions, &mut output).unwrap();
        assert_eq!(output, [0.0, 41.0, 6.0]);
    }

    #[test]
    fn compute_squared_distances_should_match_parallel_reference_values() {
        let positions: Vec<i32> = (0..5000).flat_map(|index| [4999 - index, 0, 0]).collect();
        let mut output = vec![0; 5000];

        compute_squared_distances(0, 0, 0, &positions, &mut output).unwrap();

        let expected: Vec<i64> = (0..5000)
            .map(|index| {
                let value = (4999 - index) as i64;
                value * value
            })
            .collect();
        assert_eq!(output, expected);
    }

    #[test]
    fn compute_squared_distances_f64_should_match_parallel_reference_values() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0.0; 5000];

        compute_squared_distances_f64(0.0, 0.0, 0.0, &positions, &mut output).unwrap();

        let expected: Vec<f64> = (0..5000)
            .map(|index| {
                let value = (4999 - index) as f64;
                value * value
            })
            .collect();
        assert_eq!(output, expected);
    }

    #[test]
    fn potential_energy_change_should_match_reference_values() {
        let positions = [3, 0, 4, 0, 0, 2, -6, 0, 8];
        let charges = [10.0, -4.0, 2.5];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, -3.0).unwrap();
        assert_eq!(result, -0.75);
    }

    #[test]
    fn potential_energy_change_should_return_infinity_at_same_position() {
        let positions = [0, 0, 0];
        let charges = [7.0];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, 2.0).unwrap();
        assert_eq!(result, f64::INFINITY);
    }

    #[test]
    fn potential_energy_change_should_skip_validation_for_zero_multiplier() {
        let positions = [1, 2];
        let charges = [];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, -0.0).unwrap();
        assert_eq!(result, 0.0);
        assert_eq!(result.to_bits(), 0.0f64.to_bits());
    }

    #[test]
    fn potential_energy_change_should_reject_wrong_charge_count() {
        let positions = [1, 2, 3, 4, 5, 6];
        let charges = [1.0];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, 1.0);
        assert_eq!(result, Err(NativeError::InvalidInput));
    }

    #[test]
    fn cached_potential_should_preserve_reference_result_for_small_input() {
        let _test_guard = POTENTIAL_CACHE_TEST_LOCK
            .lock()
            .unwrap_or_else(|error| error.into_inner());
        clear_cached_potential_charges();

        let positions = vec![3, 0, 4, 0, 0, 2, -6, 0, 8];
        let charges = vec![10.0, -4.0, 2.5];
        let expected = potential_energy_change(0, 0, 0, &positions, &charges, -3.0).unwrap();
        set_cached_potential_charges(positions, charges).unwrap();

        #[cfg(feature = "cubecl-preview")]
        {
            let cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
            assert!(matches!(
                cache.as_ref().map(|cache| &cache.backend),
                Some(CachedPotentialBackend::Disabled)
            ));
        }

        let actual = compute_cached_potential_energy_change(0, 0, 0, -3.0).unwrap();
        assert_eq!(expected.to_bits(), actual.to_bits());
        clear_cached_potential_charges();
    }

    #[cfg(feature = "cubecl-preview")]
    #[test]
    fn stale_cubecl_calibration_should_not_replace_current_generation() {
        let _test_guard = POTENTIAL_CACHE_TEST_LOCK
            .lock()
            .unwrap_or_else(|error| error.into_inner());
        clear_cached_potential_charges();

        let generation = POTENTIAL_NEXT_GENERATION.fetch_add(1, AtomicOrdering::Relaxed);
        *POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner()) = Some(CachedPotentialCharges {
            generation,
            positions: Arc::new(vec![0, 0, 0]),
            charges: Arc::new(vec![1.0]),
            backend: CachedPotentialBackend::Calibrating,
        });
        POTENTIAL_ACTIVE_GENERATION.store(generation, AtomicOrdering::Release);

        finish_potential_calibration(generation.wrapping_sub(1), None);
        {
            let cache = POTENTIAL_CACHE.lock().unwrap_or_else(|e| e.into_inner());
            assert!(matches!(
                cache.as_ref().map(|cache| &cache.backend),
                Some(CachedPotentialBackend::Calibrating)
            ));
        }
        clear_cached_potential_charges();
    }

    #[test]
    fn find_nearest_index_f64_should_match_reference_index() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let nearest = find_nearest_index_f64(0.0, 64.0, 0.0, -1.0, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_index_f64_should_reject_out_of_radius_positions() {
        let positions = [3.0, 0.0, 0.0];
        let nearest = find_nearest_index_f64(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert_eq!(nearest, None);
    }

    #[test]
    fn find_nearest_index_f64_should_include_radius_boundary() {
        let positions = [2.0, 0.0, 0.0];
        let nearest = find_nearest_index_f64(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_index_f64_exclusive_should_reject_radius_boundary() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0];
        let nearest = find_nearest_index_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert_eq!(nearest, Some(1));
    }

    #[test]
    fn find_nearest_index_f64_exclusive_should_accept_unbounded_positions() {
        let positions = [2.0, 0.0, 0.0];
        let nearest = find_nearest_index_f64_exclusive(0.0, 0.0, 0.0, -1.0, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_index_f64_should_preserve_simd_boundaries_ties_and_nan_behavior() {
        let boundary_positions = [2.0, 0.0, 0.0, -2.0, 0.0, 0.0, 2.0, 0.0, 0.0, -2.0, 0.0, 0.0];
        assert_eq!(
            find_nearest_index_f64(0.0, 0.0, 0.0, 4.0, &boundary_positions).unwrap(),
            Some(0)
        );
        assert_eq!(
            find_nearest_index_f64_exclusive(0.0, 0.0, 0.0, 4.0, &boundary_positions).unwrap(),
            None
        );

        let tied_positions = [1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 2.0, 0.0, 0.0, -2.0, 0.0, 0.0];
        assert_eq!(
            find_nearest_index_f64(0.0, 0.0, 0.0, -1.0, &tied_positions).unwrap(),
            Some(0)
        );

        let nan_positions = [
            f64::NAN,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            2.0,
            0.0,
            0.0,
            3.0,
            0.0,
            0.0,
        ];
        assert_eq!(
            find_nearest_index_f64(0.0, 0.0, 0.0, -1.0, &nan_positions).unwrap(),
            Some(0)
        );
        assert_eq!(
            find_nearest_index_f64(0.0, 0.0, 0.0, 4.0, &nan_positions).unwrap(),
            Some(1)
        );
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_reject_radius_boundary() {
        let positions = [2.0, 0.0, 0.0];
        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert!(!has_match);
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_accept_inner_position() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0];
        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert!(has_match);
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_accept_unbounded_positions() {
        let positions = [9.0, 0.0, 0.0];
        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, -1.0, &positions).unwrap();
        assert!(has_match);
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_match_parallel_reference() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();

        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert!(has_match);
    }

    #[test]
    fn find_nearest_index_f64_should_match_parallel_reference_index() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();

        let nearest = find_nearest_index_f64(0.0, 0.0, 0.0, 1024.0, &positions).unwrap();
        assert_eq!(nearest, Some(4999));
    }

    #[test]
    fn find_nearest_block_center_index_should_match_reference_index() {
        let positions = [0, 0, 0, 3, 0, 0, -1, 0, 0];
        let nearest = find_nearest_block_center_index(0.5, 0.5, 0.5, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_block_center_index_should_use_vanilla_tie_order() {
        let positions = [1, 0, 0, -1, 0, 0, 0, 1, 0, 0, 1, 1];
        let nearest = find_nearest_block_center_index(0.5, 0.5, 0.5, &positions).unwrap();
        assert_eq!(nearest, Some(2));
    }

    #[test]
    fn find_nearest_block_center_index_should_match_parallel_reference_index() {
        let positions: Vec<i32> = (0..BLOCK_NEAREST_PARALLEL_THRESHOLD)
            .flat_map(|index| [0, (BLOCK_NEAREST_PARALLEL_THRESHOLD - 1 - index) as i32, 0])
            .collect();

        let nearest = find_nearest_block_center_index(0.5, 0.5, 0.5, &positions).unwrap();
        assert_eq!(nearest, Some(BLOCK_NEAREST_PARALLEL_THRESHOLD - 1));
    }

    #[test]
    fn find_nearest_block_corner_index_should_match_reference_index() {
        let positions = [3, 0, 0, 0, 0, 0, -1, 0, 0];
        let nearest = find_nearest_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(1));
    }

    #[test]
    fn find_nearest_block_corner_index_should_preserve_tie_order() {
        let positions = [1, 0, 0, -1, 0, 0, 0, 2, 0];
        let nearest = find_nearest_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_block_corner_index_should_match_parallel_reference_index() {
        let positions: Vec<i32> = (0..BLOCK_NEAREST_PARALLEL_THRESHOLD)
            .flat_map(|index| [(BLOCK_NEAREST_PARALLEL_THRESHOLD - 1 - index) as i32, 0, 0])
            .collect();

        let nearest = find_nearest_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(BLOCK_NEAREST_PARALLEL_THRESHOLD - 1));
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_include_radius_boundary() {
        let positions = [3, 0, 0, 2, 0, 0, 5, 0, 0];
        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, Some(1));
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_preserve_tie_order() {
        let positions = [2, 0, 0, -2, 0, 0, 1, 0, 0];
        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, Some(2));
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_reject_out_of_radius_positions() {
        let positions = [3, 0, 0, 4, 0, 0];
        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, None);
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_match_parallel_reference_index() {
        let positions: Vec<i32> = (0..BLOCK_NEAREST_PARALLEL_THRESHOLD)
            .flat_map(|index| [(BLOCK_NEAREST_PARALLEL_THRESHOLD - 1 - index) as i32, 0, 0])
            .collect();

        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 1024, &positions).unwrap();
        assert_eq!(nearest, Some(BLOCK_NEAREST_PARALLEL_THRESHOLD - 1));
    }

    #[test]
    fn find_nearest_packed_block_corner_index_should_decode_block_pos_coordinates() {
        let positions = [
            pack_block_pos(-33_554_432, -2_048, 33_554_431),
            pack_block_pos(-33_554_431, -2_048, 33_554_431),
            pack_block_pos(0, 0, 0),
        ];

        let nearest =
            find_nearest_packed_block_corner_index(-33_554_432, -2_048, 33_554_431, &positions)
                .unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_packed_block_corner_index_should_preserve_tie_order() {
        let positions = [
            pack_block_pos(1, 0, 0),
            pack_block_pos(-1, 0, 0),
            pack_block_pos(0, 2, 0),
        ];

        let nearest = find_nearest_packed_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_packed_block_corner_index_within_radius_should_include_boundary() {
        let positions = [pack_block_pos(3, 0, 0), pack_block_pos(2, 0, 0)];

        let nearest =
            find_nearest_packed_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, Some(1));
    }

    #[test]
    fn find_nearest_packed_block_corner_index_should_match_parallel_reference_index() {
        let positions: Vec<i64> = (0..BLOCK_NEAREST_PARALLEL_THRESHOLD)
            .map(|index| {
                pack_block_pos((BLOCK_NEAREST_PARALLEL_THRESHOLD - 1 - index) as i32, 0, 0)
            })
            .collect();

        let nearest = find_nearest_packed_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(BLOCK_NEAREST_PARALLEL_THRESHOLD - 1));
    }

    #[test]
    fn filter_within_radius_f64_should_match_reference_indices() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let mut output = [0; 3];
        let count =
            filter_within_radius_f64(0.0, 64.0, 0.0, 40.0, &positions, &mut output).unwrap();
        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 2]);
    }

    #[test]
    fn filter_within_radius_f64_should_preserve_order_across_simd_tail() {
        let positions = [
            1.0, 0.0, 0.0, 3.0, 0.0, 0.0, -2.0, 0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 1.0, 0.0,
        ];
        let mut output = [-1; 5];

        let count = filter_within_radius_f64(0.0, 0.0, 0.0, 4.0, &positions, &mut output).unwrap();

        assert_eq!(count, 3);
        assert_eq!(&output[..count], &[0, 2, 4]);
        assert_eq!(&output[count..], &[-1, -1]);
    }

    #[test]
    fn filter_within_radius_f64_should_match_parallel_reference_indices() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count =
            filter_within_radius_f64(0.0, 0.0, 0.0, 1024.0, &positions, &mut output).unwrap();
        assert_eq!(count, 33);
        assert_eq!(&output[..count], &(4967..5000).collect::<Vec<_>>()[..]);
    }

    #[test]
    fn filter_within_radius_f64_exclusive_should_reject_radius_boundary() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0];
        let mut output = [0; 2];
        let count = filter_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions, &mut output)
            .unwrap();
        assert_eq!(count, 1);
        assert_eq!(&output[..count], &[1]);
    }

    #[test]
    fn filter_within_radius_f64_exclusive_should_preserve_boundary_and_simd_tail() {
        let positions = [
            1.0, 0.0, 0.0, 2.0, 0.0, 0.0, -1.5, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 2.0, 0.0,
        ];
        let mut output = [-1; 5];

        let count = filter_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions, &mut output)
            .unwrap();

        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 2]);
        assert_eq!(&output[count..], &[-1, -1, -1]);
    }

    #[test]
    fn filter_within_radius_f64_exclusive_should_match_parallel_reference_indices() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count =
            filter_within_radius_f64_exclusive(0.0, 0.0, 0.0, 1024.0, &positions, &mut output)
                .unwrap();
        assert_eq!(count, 32);
        assert_eq!(&output[..count], &(4968..5000).collect::<Vec<_>>()[..]);
    }

    #[test]
    fn sort_within_radius_f64_exclusive_should_filter_and_sort_by_distance() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0];
        let mut output = [0; 3];
        let count =
            sort_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions, &mut output).unwrap();
        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[1, 2]);
    }

    #[test]
    fn sort_within_radius_f64_exclusive_should_match_parallel_reference_order() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count =
            sort_within_radius_f64_exclusive(0.0, 0.0, 0.0, 1024.0, &positions, &mut output)
                .unwrap();
        assert_eq!(count, 32);
        assert_eq!(
            &output[..count],
            &(4968..5000).rev().collect::<Vec<_>>()[..]
        );
    }

    #[test]
    fn filter_within_radii_f64_should_match_reference_indices() {
        let positions = [
            0.0, 8.0, 0.0, 10.0, 0.0, 0.0, 12.0, 0.0, 0.0, 15.1, 0.0, 0.0,
        ];
        let radii_squared = [64.0, 64.0, 144.0, 225.0];
        let mut output = [0; 4];
        let count = filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output)
            .unwrap();

        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 2]);
    }

    #[test]
    fn filter_within_radii_f64_should_preserve_order_across_simd_tail() {
        let positions = [
            1.0, 0.0, 0.0, 2.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0, -2.0, 0.0, 0.0,
        ];
        let radii_squared = [1.0, 4.0, 4.0, 0.0, 4.0];
        let mut output = [-1; 5];

        let count = filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output)
            .unwrap();

        assert_eq!(count, 4);
        assert_eq!(&output[..count], &[0, 1, 3, 4]);
        assert_eq!(&output[count..], &[-1]);
    }

    #[test]
    fn filter_within_radii_f64_should_match_parallel_reference_indices() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let radii_squared: Vec<f64> = (0..5000)
            .map(|index| {
                let x = (4999 - index) as f64;
                if index % 1000 == 0 || index >= 4997 {
                    x * x
                } else {
                    0.25
                }
            })
            .collect();
        let mut output = vec![0; 5000];

        let count = filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output)
            .unwrap();
        assert_eq!(count, 8);
        assert_eq!(
            &output[..count],
            &[0, 1000, 2000, 3000, 4000, 4997, 4998, 4999]
        );
    }

    #[test]
    fn filter_within_radii_f64_should_reject_negative_radius() {
        let positions = [0.0, 0.0, 0.0];
        let radii_squared = [-1.0];
        let mut output = [0; 1];

        let result =
            filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output);
        assert_eq!(result, Err(NativeError::InvalidInput));
    }

    #[test]
    fn filter_within_aabb_f64_should_match_reference_indices() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let mut output = [0; 3];
        let count =
            filter_within_aabb_f64(-1.0, 63.0, -3.0, 1.0, 65.0, 1.0, &positions, &mut output)
                .unwrap();
        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 2]);
    }

    #[test]
    fn filter_within_aabb_f64_should_match_parallel_reference_indices() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count =
            filter_within_aabb_f64(0.0, -1.0, -1.0, 33.0, 1.0, 1.0, &positions, &mut output)
                .unwrap();
        assert_eq!(count, 33);
        assert_eq!(&output[..count], &(4967..5000).collect::<Vec<_>>()[..]);
    }

    #[test]
    fn filter_intersecting_aabb_f64_should_match_reference_indices() {
        let boxes = [
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 2.0, 1.0, 1.0, -1.0, -1.0, -1.0, 0.0, 0.0,
            0.0, 0.5, 0.5, 0.5, 1.5, 1.5, 1.5,
        ];
        let mut output = [0; 4];
        let count = filter_intersecting_aabb_f64(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, &boxes, &mut output)
            .unwrap();
        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 3]);
    }

    #[test]
    fn filter_intersecting_aabb_f64_should_preserve_order_across_simd_tail() {
        let boxes = [
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.5, 0.0, 0.0, 1.5, 1.0, 1.0, 1.0, 0.0, 0.0, 2.0, 1.0,
            1.0, 0.0, 2.0, 0.0, 1.0, 3.0, 1.0, -0.5, -0.5, -0.5, 0.5, 0.5, 0.5,
        ];
        let mut output = [-1; 5];

        let count = filter_intersecting_aabb_f64(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, &boxes, &mut output)
            .unwrap();

        assert_eq!(count, 3);
        assert_eq!(&output[..count], &[0, 1, 4]);
        assert_eq!(&output[count..], &[-1, -1]);
    }

    #[test]
    fn filter_intersecting_aabb_f64_should_match_parallel_reference_indices() {
        let boxes: Vec<f64> = (0..5000)
            .flat_map(|index| {
                let min = (4999 - index) as f64;
                [min, 0.0, 0.0, min + 0.5, 1.0, 1.0]
            })
            .collect();
        let mut output = vec![0; 5000];

        let count =
            filter_intersecting_aabb_f64(0.25, -1.0, -1.0, 33.25, 2.0, 2.0, &boxes, &mut output)
                .unwrap();
        assert_eq!(count, 34);
        assert_eq!(&output[..count], &(4966..5000).collect::<Vec<_>>()[..]);
    }

    #[test]
    fn filter_within_radius_should_match_reference_indices() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        let count = filter_within_radius(0, 64, 0, 40, &positions, &mut output).unwrap();
        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 2]);
    }

    #[test]
    fn filter_within_exclusive_chunk_distance_should_keep_order_and_boundary() {
        let positions = [100.0, 0.0, 128.0, 0.0, 127.5, 0.0, 0.0, 127.5, -127.5, 0.0];
        let mut output = [77; 5];
        let count = filter_within_exclusive_chunk_distance(
            0.0,
            0.0,
            128.0 * 128.0,
            &positions,
            &mut output,
        )
        .unwrap();

        assert_eq!(count, 4);
        assert_eq!(&output[..count], &[0, 2, 3, 4]);
        assert_eq!(output[count], 77);
    }

    #[test]
    fn filter_within_exclusive_chunk_distance_should_match_parallel_order() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| {
                let x = (index % 2) as f64;
                [x, index as f64]
            })
            .collect();
        let mut output = vec![0; 5000];

        let count =
            filter_within_exclusive_chunk_distance(0.0, 0.0, 64.0 * 64.0, &positions, &mut output)
                .unwrap();

        let expected: Vec<i32> = (0..64).collect();
        assert_eq!(count, expected.len());
        assert_eq!(&output[..count], expected.as_slice());
    }

    #[test]
    fn filter_within_exclusive_chunk_distance_should_reject_invalid_input() {
        let mut output = [0; 1];
        assert_eq!(
            filter_within_exclusive_chunk_distance(0.0, 0.0, -1.0, &[0.0, 0.0], &mut output),
            Err(NativeError::InvalidInput)
        );
        assert_eq!(
            filter_within_exclusive_chunk_distance(0.0, 0.0, 1.0, &[0.0], &mut output),
            Err(NativeError::InvalidInput)
        );
    }

    #[test]
    fn filter_within_radius_should_match_parallel_reference_indices() {
        let positions: Vec<i32> = (0..5000).flat_map(|index| [4999 - index, 0, 0]).collect();
        let mut output = vec![0; 5000];

        let count = filter_within_radius(0, 0, 0, 1024, &positions, &mut output).unwrap();
        assert_eq!(count, 33);
        assert_eq!(&output[..count], &(4967..5000).collect::<Vec<_>>()[..]);
    }

    #[test]
    fn count_within_radius_should_match_reference_count() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let count = count_within_radius(0, 64, 0, 40, &positions).unwrap();
        assert_eq!(count, 2);
    }

    #[test]
    fn count_within_radius_should_match_parallel_reference_count() {
        let positions: Vec<i32> = (0..5000).flat_map(|index| [4999 - index, 0, 0]).collect();

        let count = count_within_radius(0, 0, 0, 1024, &positions).unwrap();
        assert_eq!(count, 33);
    }

    #[test]
    fn sort_by_distance_should_match_reference_order() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        sort_by_distance(0, 64, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 2, 1]);
    }

    #[test]
    fn sort_by_distance_should_match_parallel_reference_order() {
        let positions: Vec<i32> = (0..5000).flat_map(|index| [4999 - index, 0, 0]).collect();
        let mut output = vec![0; 5000];

        sort_by_distance(0, 0, 0, &positions, &mut output).unwrap();
        let expected: Vec<i32> = (0..5000).rev().collect();
        assert_eq!(output, expected);
    }

    #[test]
    fn sort_by_block_distance_should_match_reference_order() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        sort_by_block_distance(0, 64, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 2, 1]);
    }

    #[test]
    fn sort_by_block_distance_should_preserve_tie_order() {
        let positions = [1, 0, 0, -1, 0, 0, 2, 0, 0];
        let mut output = [0; 3];
        sort_by_block_distance(0, 0, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 1, 2]);
    }

    #[test]
    fn sort_by_block_distance_should_match_parallel_reference_order() {
        let positions: Vec<i32> = (0..5000).flat_map(|index| [4999 - index, 0, 0]).collect();
        let mut output = vec![0; 5000];

        sort_by_block_distance(0, 0, 0, &positions, &mut output).unwrap();
        let expected: Vec<i32> = (0..5000).rev().collect();
        assert_eq!(output, expected);
    }

    #[test]
    fn sort_by_distance_f64_should_match_reference_order() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let mut output = [0; 3];
        sort_by_distance_f64(0.0, 64.0, 0.0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 2, 1]);
    }

    #[test]
    fn sort_by_distance_f64_should_preserve_tie_order() {
        let positions = [1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 2.0, 0.0, 0.0];
        let mut output = [0; 3];
        sort_by_distance_f64(0.0, 0.0, 0.0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 1, 2]);
    }

    #[test]
    fn distance_sorts_should_reuse_scratch_capacity() {
        let positions: Vec<f64> = (0..128)
            .flat_map(|index| [(127 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = [-1; 128];
        let mut scratch = DistanceSortScratch::default();

        sort_by_distance_f64_with_scratch(0.0, 0.0, 0.0, &positions, &mut output, &mut scratch)
            .unwrap();
        let capacity = scratch.pairs.capacity();
        assert_eq!(&output[..8], &[127, 126, 125, 124, 123, 122, 121, 120]);

        let within_count = sort_within_radius_f64_exclusive_with_scratch(
            0.0,
            0.0,
            0.0,
            64.0,
            &positions,
            &mut output,
            &mut scratch,
        )
        .unwrap();
        assert_eq!(within_count, 8);
        assert_eq!(
            &output[..within_count],
            &[127, 126, 125, 124, 123, 122, 121, 120]
        );
        assert_eq!(scratch.pairs.capacity(), capacity);

        let prefix_count = sort_by_distance_and_count_within_radius_f64_exclusive_with_scratch(
            0.0,
            0.0,
            0.0,
            64.0,
            &positions,
            &mut output,
            &mut scratch,
        )
        .unwrap();
        assert_eq!(prefix_count, within_count);
        assert_eq!(&output[..8], &[127, 126, 125, 124, 123, 122, 121, 120]);
        assert_eq!(scratch.pairs.capacity(), capacity);
    }

    #[test]
    fn sort_by_distance_and_count_within_radius_f64_exclusive_should_write_full_order_and_prefix() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 4.0, 0.0, 0.0];
        let mut output = [0, 0, 0, 0, 77];

        let count = sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            4.0,
            &positions,
            &mut output,
        )
        .unwrap();

        assert_eq!(count, 2);
        assert_eq!(&output[..4], &[1, 2, 0, 3]);
        assert_eq!(output[4], 77);
    }

    #[test]
    fn sort_by_distance_and_count_within_radius_f64_exclusive_should_preserve_special_radius_semantics()
     {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 4.0, 0.0, 0.0];
        let mut output = [0; 4];

        let boundary_count = sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            1.0,
            &positions,
            &mut output,
        )
        .unwrap();
        assert_eq!(boundary_count, 0);

        let nan_count = sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::NAN,
            &positions,
            &mut output,
        )
        .unwrap();
        assert_eq!(nan_count, 0);

        let infinity_count = sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            &mut output,
        )
        .unwrap();
        assert_eq!(infinity_count, 4);
    }

    #[test]
    fn sort_by_distance_and_count_within_radius_f64_exclusive_should_match_parallel_reference_order()
     {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count = sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            1024.0,
            &positions,
            &mut output,
        )
        .unwrap();

        assert_eq!(count, 32);
        assert_eq!(
            &output[..count],
            &(4968..5000).rev().collect::<Vec<_>>()[..]
        );
        assert_eq!(&output[count..], &(0..4968).rev().collect::<Vec<_>>()[..]);
    }

    #[test]
    fn select_nearest_indices_within_radius_f64_exclusive_should_preserve_ties_and_output_tail() {
        let positions = [
            4.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 2.0, 0.0, 0.0, 3.0, 0.0, 0.0,
        ];
        let mut output = [77, 88, 99];

        let count = select_nearest_indices_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            16.0,
            &positions,
            2,
            &mut output,
        )
        .unwrap();

        assert_eq!(count, 2);
        assert_eq!(output, [1, 2, 99]);
    }

    #[test]
    fn select_nearest_indices_within_radius_f64_exclusive_should_reuse_scratch_capacity() {
        let positions: Vec<f64> = (0..128)
            .flat_map(|index| [(127 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = [-1; 16];
        let mut scratch = NearestSelectionScratch::default();

        let first_count = select_nearest_indices_within_radius_f64_exclusive_with_scratch(
            0.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            output.len(),
            &mut output,
            &mut scratch,
        )
        .unwrap();
        let capacity = scratch.nearest.capacity();

        let second_count = select_nearest_indices_within_radius_f64_exclusive_with_scratch(
            4.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            output.len(),
            &mut output,
            &mut scratch,
        )
        .unwrap();

        assert_eq!(first_count, output.len());
        assert_eq!(second_count, output.len());
        assert_eq!(scratch.nearest.capacity(), capacity);
        assert_eq!(
            output,
            [
                123, 122, 124, 121, 125, 120, 126, 119, 127, 118, 117, 116, 115, 114, 113, 112,
            ]
        );
    }

    #[test]
    fn select_nearest_indices_within_radius_f64_exclusive_should_reject_boundaries_and_nan() {
        let positions = [2.0, 0.0, 0.0, f64::NAN, 0.0, 0.0, 1.0, 0.0, 0.0];
        let mut output = [77, 88, 99];

        let count = select_nearest_indices_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            4.0,
            &positions,
            3,
            &mut output,
        )
        .unwrap();

        assert_eq!(count, 1);
        assert_eq!(output, [2, 88, 99]);

        let nan_radius_count = select_nearest_indices_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::NAN,
            &positions,
            3,
            &mut output,
        )
        .unwrap();
        assert_eq!(nan_radius_count, 0);
        assert_eq!(output, [2, 88, 99]);
    }

    #[test]
    fn select_nearest_indices_within_radius_f64_exclusive_should_match_full_sort_after_replacements()
     {
        const POSITION_COUNT: usize = 8_192;
        let positions: Vec<f64> = (0..POSITION_COUNT)
            .flat_map(|index| {
                let distance = (POSITION_COUNT - 1 - index) % 257;
                [distance as f64, 0.0, 0.0]
            })
            .collect();
        let mut selected = [-1; 16];
        let mut full_order = vec![-1; POSITION_COUNT];

        let selected_count = select_nearest_indices_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            selected.len(),
            &mut selected,
        )
        .unwrap();
        sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            &mut full_order,
        )
        .unwrap();

        assert_eq!(selected_count, selected.len());
        assert_eq!(selected.as_slice(), &full_order[..selected_count]);
    }

    #[test]
    fn select_nearest_indices_within_radius_f64_exclusive_should_match_full_sort_for_parallel_input()
     {
        const POSITION_COUNT: usize = NEAREST_SELECTION_PARALLEL_THRESHOLD;
        let positions: Vec<f64> = (0..POSITION_COUNT)
            .flat_map(|index| {
                let distance = (POSITION_COUNT - 1 - index) % 31;
                [distance as f64, 0.0, 0.0]
            })
            .collect();
        let mut selected = [-1; 16];
        let mut full_order = vec![-1; POSITION_COUNT];

        let selected_count = select_nearest_indices_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            selected.len(),
            &mut selected,
        )
        .unwrap();
        sort_by_distance_and_count_within_radius_f64_exclusive(
            0.0,
            0.0,
            0.0,
            f64::INFINITY,
            &positions,
            &mut full_order,
        )
        .unwrap();

        assert_eq!(selected_count, selected.len());
        assert_eq!(selected.as_slice(), &full_order[..selected_count]);
    }

    #[test]
    fn sort_by_distance_f64_should_match_parallel_reference_order() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        sort_by_distance_f64(0.0, 0.0, 0.0, &positions, &mut output).unwrap();

        let expected: Vec<i32> = (0..5000).rev().collect();
        assert_eq!(output, expected);
    }

    #[test]
    fn potential_energy_change_should_preserve_sequential_sum_at_large_batch() {
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [index - 2500, 64, index % 17 - 8])
            .collect();
        let charges: Vec<f64> = (0..5000).map(|index| (index % 11) as f64 - 5.0).collect();

        let mut expected_energy = 0.0;
        for (index, charge) in charges.iter().enumerate() {
            let distance = block_corner_distance_at(0, 64, 0, &positions, index);
            expected_energy += if distance == 0.0 {
                f64::INFINITY
            } else {
                *charge / distance.sqrt()
            };
        }
        expected_energy *= 0.75;
        assert_eq!(
            potential_energy_change(0, 64, 0, &positions, &charges, 0.75).unwrap(),
            expected_energy
        );
    }

    #[test]
    fn interpolate_density_cells_should_match_staged_vanilla_order() {
        let corners = [
            -1.25, 3.5, 7.75, -9.0, 0.125, -4.25, 16.0, 2.0, 1000.0, -0.0, -17.0, 31.0, 0.5, 8.0,
            -2.0, 64.0,
        ];
        let mut output = [0.0; 2 * 4 * 4 * 8];
        let mut expected = [0.0; 2 * 4 * 4 * 8];

        interpolate_density_cells(&corners, 4, 8, &mut output).unwrap();
        for interpolator in 0..2 {
            let corner_offset = interpolator * 8;
            let output_offset = interpolator * 4 * 4 * 8;
            for y in 0..8 {
                let delta_y = y as f64 / 8.0;
                let xz00 = lerp(delta_y, corners[corner_offset], corners[corner_offset + 2]);
                let xz10 = lerp(
                    delta_y,
                    corners[corner_offset + 1],
                    corners[corner_offset + 3],
                );
                let xz01 = lerp(
                    delta_y,
                    corners[corner_offset + 4],
                    corners[corner_offset + 6],
                );
                let xz11 = lerp(
                    delta_y,
                    corners[corner_offset + 5],
                    corners[corner_offset + 7],
                );
                for x in 0..4 {
                    let delta_x = x as f64 / 4.0;
                    let z0 = lerp(delta_x, xz00, xz10);
                    let z1 = lerp(delta_x, xz01, xz11);
                    for z in 0..4 {
                        expected[output_offset + (y * 4 + x) * 4 + z] =
                            lerp(z as f64 / 4.0, z0, z1);
                    }
                }
            }
        }

        assert!(
            expected
                .iter()
                .zip(output.iter())
                .all(|(left, right)| left.to_bits() == right.to_bits())
        );
    }

    #[test]
    fn interpolate_density_cells_should_validate_packed_lengths() {
        assert_eq!(
            interpolate_density_cells(&[0.0; 7], 4, 8, &mut [0.0; 128]),
            Err(NativeError::InvalidInput)
        );
        assert_eq!(
            interpolate_density_cells(&[0.0; 8], 4, 8, &mut [0.0; 127]),
            Err(NativeError::OutputLengthMismatch)
        );
    }
}
