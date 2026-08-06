use crate::NativeError;
use crate::noise::NoiseGenerator;
use rayon::prelude::*;

const PARALLEL_THRESHOLD: usize = 1024;

pub struct OctaveNoise<T: NoiseGenerator> {
    octaves: Vec<T>,
    amplitudes: Vec<f64>,
    first_octave: i32,
}

impl<T: NoiseGenerator> OctaveNoise<T> {
    pub fn new(octaves: Vec<T>, first_octave: i32) -> Self {
        let amplitude_count = octaves.len();
        let mut amplitudes = Vec::with_capacity(amplitude_count);
        
        for i in 0..amplitude_count {
            let octave_index = first_octave + i as i32;
            amplitudes.push(1.0 / (1 << octave_index.abs()) as f64);
        }
        
        Self {
            octaves,
            amplitudes,
            first_octave,
        }
    }

    pub fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        let mut result = 0.0;
        let mut frequency = 1.0;
        
        for (octave_idx, octave) in self.octaves.iter().enumerate() {
            let sample_x = x * frequency;
            let sample_y = y * frequency;
            let sample_z = z * frequency;
            
            result += octave.sample_3d(sample_x, sample_y, sample_z) * self.amplitudes[octave_idx];
            frequency *= 2.0;
        }
        
        result
    }
}

pub fn batch_sample_octave_noise_3d<T: NoiseGenerator + Sync>(
    octave_noise: &OctaveNoise<T>,
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

    if count >= PARALLEL_THRESHOLD {
        output.par_iter_mut().enumerate().for_each(|(i, out)| {
            let x = positions[i * 3];
            let y = positions[i * 3 + 1];
            let z = positions[i * 3 + 2];
            *out = octave_noise.sample_3d(x, y, z);
        });
    } else {
        for i in 0..count {
            let x = positions[i * 3];
            let y = positions[i * 3 + 1];
            let z = positions[i * 3 + 2];
            output[i] = octave_noise.sample_3d(x, y, z);
        }
    }

    Ok(())
}

pub fn batch_sample_multi_octave_3d<T: NoiseGenerator + Sync>(
    octaves: &[&dyn NoiseGenerator],
    amplitudes: &[f64],
    first_octave: i32,
    positions: &[f64],
    output: &mut [f64],
) -> Result<(), NativeError> {
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }
    if octaves.len() != amplitudes.len() {
        return Err(NativeError::InvalidInput);
    }
    
    let count = positions.len() / 3;
    if output.len() != count {
        return Err(NativeError::OutputLengthMismatch);
    }

    let compute = |i: usize| -> f64 {
        let x = positions[i * 3];
        let y = positions[i * 3 + 1];
        let z = positions[i * 3 + 2];
        
        let mut result = 0.0;
        let mut frequency = 2.0_f64.powi(first_octave);
        
        for (octave_idx, octave) in octaves.iter().enumerate() {
            let sample_x = x * frequency;
            let sample_y = y * frequency;
            let sample_z = z * frequency;
            
            result += octave.sample_3d(sample_x, sample_y, sample_z) * amplitudes[octave_idx];
            frequency *= 2.0;
        }
        
        result
    };

    if count >= PARALLEL_THRESHOLD {
        output.par_iter_mut().enumerate().for_each(|(i, out)| {
            *out = compute(i);
        });
    } else {
        for i in 0..count {
            output[i] = compute(i);
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::noise::perlin::PerlinNoise;

    #[test]
    fn octave_noise_combines_multiple_frequencies() {
        let octaves = vec![
            PerlinNoise::new(12345),
            PerlinNoise::new(54321),
        ];
        let octave_noise = OctaveNoise::new(octaves, 0);
        
        let value = octave_noise.sample_3d(1.0, 2.0, 3.0);
        assert!(value.is_finite());
    }

    #[test]
    fn batch_octave_sampling_is_deterministic() {
        let octaves = vec![
            PerlinNoise::new(12345),
            PerlinNoise::new(54321),
        ];
        let octave_noise = OctaveNoise::new(octaves, 0);
        
        let positions = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0];
        let mut output1 = [0.0; 2];
        let mut output2 = [0.0; 2];
        
        batch_sample_octave_noise_3d(&octave_noise, &positions, &mut output1).unwrap();
        batch_sample_octave_noise_3d(&octave_noise, &positions, &mut output2).unwrap();
        
        assert_eq!(output1, output2);
    }
}
