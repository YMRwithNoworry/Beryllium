use crate::NativeError;
use rayon::prelude::*;

const PARALLEL_THRESHOLD: usize = 1024;

#[derive(Clone, Copy)]
pub struct BiomeWeight {
    pub biome_index: i32,
    pub weight: f64,
}

pub fn batch_compute_biome_weights_3d(
    sample_positions: &[f64],
    biome_centers: &[f64],
    influence_radius: f64,
    output: &mut [BiomeWeight],
    max_biomes_per_sample: usize,
) -> Result<(), NativeError> {
    if sample_positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }
    if biome_centers.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let sample_count = sample_positions.len() / 3;
    let biome_count = biome_centers.len() / 3;

    if output.len() != sample_count * max_biomes_per_sample {
        return Err(NativeError::OutputLengthMismatch);
    }

    let radius_squared = influence_radius * influence_radius;

    if sample_count >= PARALLEL_THRESHOLD {
        output.par_chunks_mut(max_biomes_per_sample)
            .enumerate()
            .for_each(|(sample_idx, output_chunk)| {
                compute_sample_weights(
                    sample_idx,
                    sample_positions,
                    biome_centers,
                    biome_count,
                    radius_squared,
                    output_chunk,
                );
            });
    } else {
        for sample_idx in 0..sample_count {
            let output_start = sample_idx * max_biomes_per_sample;
            compute_sample_weights(
                sample_idx,
                sample_positions,
                biome_centers,
                biome_count,
                radius_squared,
                &mut output[output_start..output_start + max_biomes_per_sample],
            );
        }
    }

    Ok(())
}

fn compute_sample_weights(
    sample_idx: usize,
    sample_positions: &[f64],
    biome_centers: &[f64],
    biome_count: usize,
    radius_squared: f64,
    output: &mut [BiomeWeight],
) {
    let sx = sample_positions[sample_idx * 3];
    let sy = sample_positions[sample_idx * 3 + 1];
    let sz = sample_positions[sample_idx * 3 + 2];

    let mut weights = Vec::with_capacity(biome_count);
    let mut total_weight = 0.0;

    for biome_idx in 0..biome_count {
        let bx = biome_centers[biome_idx * 3];
        let by = biome_centers[biome_idx * 3 + 1];
        let bz = biome_centers[biome_idx * 3 + 2];

        let dx = sx - bx;
        let dy = sy - by;
        let dz = sz - bz;
        let dist_squared = dx * dx + dy * dy + dz * dz;

        if dist_squared < radius_squared {
            let distance = dist_squared.sqrt();
            let weight = if distance < 1e-10 {
                1e10
            } else {
                1.0 / distance
            };
            weights.push((biome_idx as i32, weight));
            total_weight += weight;
        }
    }

    weights.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap());

    let take_count = weights.len().min(output.len());

    for i in 0..take_count {
        let normalized_weight = if total_weight > 0.0 {
            weights[i].1 / total_weight
        } else {
            0.0
        };
        output[i] = BiomeWeight {
            biome_index: weights[i].0,
            weight: normalized_weight,
        };
    }

    for i in take_count..output.len() {
        output[i] = BiomeWeight {
            biome_index: -1,
            weight: 0.0,
        };
    }
}

pub fn batch_interpolate_biome_values(
    weights: &[BiomeWeight],
    biome_values: &[f64],
    samples_per_position: usize,
    output: &mut [f64],
) -> Result<(), NativeError> {
    if weights.len() % samples_per_position != 0 {
        return Err(NativeError::InvalidInput);
    }

    let position_count = weights.len() / samples_per_position;
    if output.len() != position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        output.par_iter_mut().enumerate().for_each(|(pos_idx, out)| {
            *out = compute_interpolated_value(pos_idx, weights, biome_values, samples_per_position);
        });
    } else {
        for pos_idx in 0..position_count {
            output[pos_idx] = compute_interpolated_value(pos_idx, weights, biome_values, samples_per_position);
        }
    }

    Ok(())
}

fn compute_interpolated_value(pos_idx: usize, weights: &[BiomeWeight], biome_values: &[f64], samples_per_position: usize) -> f64 {
    let mut interpolated = 0.0;
    let start = pos_idx * samples_per_position;

    for i in 0..samples_per_position {
        let weight_entry = &weights[start + i];
        if weight_entry.biome_index >= 0 {
            let biome_idx = weight_entry.biome_index as usize;
            if biome_idx < biome_values.len() {
                interpolated += biome_values[biome_idx] * weight_entry.weight;
            }
        }
    }

    interpolated
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn biome_weight_single_biome() {
        let samples = [0.0, 0.0, 0.0];
        let biomes = [0.0, 0.0, 0.0];
        let mut output = [BiomeWeight {
            biome_index: -1,
            weight: 0.0,
        }; 4];

        batch_compute_biome_weights_3d(&samples, &biomes, 10.0, &mut output, 4).unwrap();

        assert_eq!(output[0].biome_index, 0);
        assert!(output[0].weight > 0.99);
    }

    #[test]
    fn biome_interpolation() {
        let weights = [
            BiomeWeight {
                biome_index: 0,
                weight: 0.7,
            },
            BiomeWeight {
                biome_index: 1,
                weight: 0.3,
            },
        ];
        let biome_values = [10.0, 20.0];
        let mut output = [0.0];

        batch_interpolate_biome_values(&weights, &biome_values, 2, &mut output).unwrap();

        assert!((output[0] - 13.0).abs() < 0.001);
    }
}
