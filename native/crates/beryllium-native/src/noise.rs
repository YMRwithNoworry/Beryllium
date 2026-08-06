pub mod perlin;
pub mod simplex;
pub mod opensimplex2;

use crate::NativeError;

pub trait NoiseGenerator: Send + Sync {
    fn sample_2d(&self, x: f64, y: f64) -> f64;
    fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64;
}

pub fn batch_sample_noise_2d(
    generator: &dyn NoiseGenerator,
    positions: &[f64],
    output: &mut [f64],
) -> Result<(), NativeError> {
    if positions.len() % 2 != 0 {
        return Err(NativeError::InvalidInput);
    }
    let count = positions.len() / 2;
    if output.len() != count {
        return Err(NativeError::OutputLengthMismatch);
    }

    for i in 0..count {
        let x = positions[i * 2];
        let y = positions[i * 2 + 1];
        output[i] = generator.sample_2d(x, y);
    }

    Ok(())
}

pub fn batch_sample_noise_3d(
    generator: &dyn NoiseGenerator,
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

    for i in 0..count {
        let x = positions[i * 3];
        let y = positions[i * 3 + 1];
        let z = positions[i * 3 + 2];
        output[i] = generator.sample_3d(x, y, z);
    }

    Ok(())
}

pub fn batch_sample_noise_3d_parallel(
    generator: &(dyn NoiseGenerator + Sync),
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

    use rayon::prelude::*;
    const PARALLEL_THRESHOLD: usize = 1024;

    if count < PARALLEL_THRESHOLD {
        return batch_sample_noise_3d(generator, positions, output);
    }

    output.par_iter_mut().enumerate().for_each(|(i, out)| {
        let x = positions[i * 3];
        let y = positions[i * 3 + 1];
        let z = positions[i * 3 + 2];
        *out = generator.sample_3d(x, y, z);
    });

    Ok(())
}
