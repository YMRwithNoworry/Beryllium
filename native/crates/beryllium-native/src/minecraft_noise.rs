use crate::NativeError;
use crate::noise::NoiseGenerator;

const WRAP: f64 = 33_554_432.0;
const NORMAL_INPUT_FACTOR: f64 = 1.018_126_888_217_522_7;
const GRADIENTS: [[i32; 3]; 16] = [
    [1, 1, 0],
    [-1, 1, 0],
    [1, -1, 0],
    [-1, -1, 0],
    [1, 0, 1],
    [-1, 0, 1],
    [1, 0, -1],
    [-1, 0, -1],
    [0, 1, 1],
    [0, -1, 1],
    [0, 1, -1],
    [0, -1, -1],
    [1, 1, 0],
    [0, -1, 1],
    [-1, 1, 0],
    [0, -1, -1],
];

#[derive(Clone)]
struct ImprovedNoise {
    permutation: [u8; 256],
    xo: f64,
    yo: f64,
    zo: f64,
}

impl ImprovedNoise {
    fn from_flat(
        origins: &[f64],
        permutations: &[i32],
        level_index: usize,
    ) -> Result<Self, NativeError> {
        let origin_offset = level_index * 3;
        let permutation_offset = level_index * 256;
        let origin = &origins[origin_offset..origin_offset + 3];
        if !origin.iter().all(|value| value.is_finite()) {
            return Err(NativeError::InvalidInput);
        }

        let mut permutation = [0_u8; 256];
        for (output, value) in permutation
            .iter_mut()
            .zip(&permutations[permutation_offset..permutation_offset + 256])
        {
            *output = u8::try_from(*value).map_err(|_| NativeError::InvalidInput)?;
        }

        Ok(Self {
            permutation,
            xo: origin[0],
            yo: origin[1],
            zo: origin[2],
        })
    }

    #[inline]
    fn p(&self, index: i32) -> usize {
        self.permutation[(index & 0xff) as usize] as usize
    }

    #[inline]
    fn grad_dot(hash: usize, x: f64, y: f64, z: f64) -> f64 {
        let gradient = GRADIENTS[hash & 15];
        gradient[0] as f64 * x + gradient[1] as f64 * y + gradient[2] as f64 * z
    }

    #[inline]
    fn lerp(delta: f64, start: f64, end: f64) -> f64 {
        start + delta * (end - start)
    }

    #[inline]
    fn lerp2(x: f64, y: f64, v00: f64, v10: f64, v01: f64, v11: f64) -> f64 {
        Self::lerp(y, Self::lerp(x, v00, v10), Self::lerp(x, v01, v11))
    }

    #[inline]
    fn lerp3(x: f64, y: f64, z: f64, values: [f64; 8]) -> f64 {
        Self::lerp(
            z,
            Self::lerp2(x, y, values[0], values[1], values[2], values[3]),
            Self::lerp2(x, y, values[4], values[5], values[6], values[7]),
        )
    }

    #[inline]
    fn smoothstep(value: f64) -> f64 {
        value * value * value * (value * (value * 6.0 - 15.0) + 10.0)
    }

    #[inline]
    fn sample(&self, x: f64, y: f64, z: f64) -> f64 {
        let shifted_x = x + self.xo;
        let shifted_y = y + self.yo;
        let shifted_z = z + self.zo;
        let floor_x = shifted_x.floor() as i32;
        let floor_y = shifted_y.floor() as i32;
        let floor_z = shifted_z.floor() as i32;
        let local_x = shifted_x - floor_x as f64;
        let local_y = shifted_y - floor_y as f64;
        let local_z = shifted_z - floor_z as f64;

        let px0 = self.p(floor_x) as i32;
        let px1 = self.p(floor_x + 1) as i32;
        let pxy00 = self.p(px0 + floor_y) as i32;
        let pxy01 = self.p(px0 + floor_y + 1) as i32;
        let pxy10 = self.p(px1 + floor_y) as i32;
        let pxy11 = self.p(px1 + floor_y + 1) as i32;
        let values = [
            Self::grad_dot(self.p(pxy00 + floor_z), local_x, local_y, local_z),
            Self::grad_dot(self.p(pxy10 + floor_z), local_x - 1.0, local_y, local_z),
            Self::grad_dot(self.p(pxy01 + floor_z), local_x, local_y - 1.0, local_z),
            Self::grad_dot(
                self.p(pxy11 + floor_z),
                local_x - 1.0,
                local_y - 1.0,
                local_z,
            ),
            Self::grad_dot(self.p(pxy00 + floor_z + 1), local_x, local_y, local_z - 1.0),
            Self::grad_dot(
                self.p(pxy10 + floor_z + 1),
                local_x - 1.0,
                local_y,
                local_z - 1.0,
            ),
            Self::grad_dot(
                self.p(pxy01 + floor_z + 1),
                local_x,
                local_y - 1.0,
                local_z - 1.0,
            ),
            Self::grad_dot(
                self.p(pxy11 + floor_z + 1),
                local_x - 1.0,
                local_y - 1.0,
                local_z - 1.0,
            ),
        ];

        Self::lerp3(
            Self::smoothstep(local_x),
            Self::smoothstep(local_y),
            Self::smoothstep(local_z),
            values,
        )
    }
}

pub struct MinecraftNormalNoise {
    amplitudes: Box<[f64]>,
    first_levels: Box<[Option<ImprovedNoise>]>,
    second_levels: Box<[Option<ImprovedNoise>]>,
    lowest_frequency_input_factor: f64,
    lowest_frequency_value_factor: f64,
    value_factor: f64,
}

impl MinecraftNormalNoise {
    pub fn from_flat_state(
        amplitudes: &[f64],
        origins: &[f64],
        permutations: &[i32],
        lowest_frequency_input_factor: f64,
        lowest_frequency_value_factor: f64,
        value_factor: f64,
    ) -> Result<Self, NativeError> {
        let level_count = amplitudes.len();
        if level_count == 0
            || origins.len()
                != level_count
                    .checked_mul(6)
                    .ok_or(NativeError::InvalidInput)?
            || permutations.len()
                != level_count
                    .checked_mul(512)
                    .ok_or(NativeError::InvalidInput)?
            || !amplitudes.iter().all(|value| value.is_finite())
            || !lowest_frequency_input_factor.is_finite()
            || !lowest_frequency_value_factor.is_finite()
            || !value_factor.is_finite()
        {
            return Err(NativeError::InvalidInput);
        }

        let mut first_levels = Vec::with_capacity(level_count);
        let mut second_levels = Vec::with_capacity(level_count);
        for (level_index, amplitude) in amplitudes.iter().copied().enumerate() {
            if amplitude == 0.0 {
                first_levels.push(None);
                second_levels.push(None);
                continue;
            }
            first_levels.push(Some(ImprovedNoise::from_flat(
                origins,
                permutations,
                level_index,
            )?));
            second_levels.push(Some(ImprovedNoise::from_flat(
                origins,
                permutations,
                level_count + level_index,
            )?));
        }

        Ok(Self {
            amplitudes: amplitudes.into(),
            first_levels: first_levels.into(),
            second_levels: second_levels.into(),
            lowest_frequency_input_factor,
            lowest_frequency_value_factor,
            value_factor,
        })
    }

    #[inline]
    fn wrap(value: f64) -> f64 {
        value - (value / WRAP + 0.5).floor() * WRAP
    }

    #[inline]
    fn sample_perlin(&self, levels: &[Option<ImprovedNoise>], x: f64, y: f64, z: f64) -> f64 {
        let mut result = 0.0;
        let mut input_factor = self.lowest_frequency_input_factor;
        let mut value_factor = self.lowest_frequency_value_factor;
        for (level_index, level) in levels.iter().enumerate() {
            if let Some(level) = level {
                let sample = level.sample(
                    Self::wrap(x * input_factor),
                    Self::wrap(y * input_factor),
                    Self::wrap(z * input_factor),
                );
                result += self.amplitudes[level_index] * sample * value_factor;
            }
            input_factor *= 2.0;
            value_factor /= 2.0;
        }
        result
    }
}

impl NoiseGenerator for MinecraftNormalNoise {
    fn sample_2d(&self, x: f64, y: f64) -> f64 {
        self.sample_3d(x, y, 0.0)
    }

    fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        let scaled_x = x * NORMAL_INPUT_FACTOR;
        let scaled_y = y * NORMAL_INPUT_FACTOR;
        let scaled_z = z * NORMAL_INPUT_FACTOR;
        (self.sample_perlin(&self.first_levels, x, y, z)
            + self.sample_perlin(&self.second_levels, scaled_x, scaled_y, scaled_z))
            * self.value_factor
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn identity_permutation(level_count: usize) -> Vec<i32> {
        (0..level_count * 2).flat_map(|_| 0..256_i32).collect()
    }

    #[test]
    fn flat_state_rejects_mismatched_buffers() {
        assert!(matches!(
            MinecraftNormalNoise::from_flat_state(&[1.0], &[0.0; 5], &[0; 512], 1.0, 1.0, 1.0),
            Err(NativeError::InvalidInput)
        ));
    }

    #[test]
    fn normal_noise_is_deterministic_and_finite() {
        let noise = MinecraftNormalNoise::from_flat_state(
            &[1.0, 0.5],
            &[0.25; 12],
            &identity_permutation(2),
            0.5,
            2.0 / 3.0,
            5.0 / 6.0,
        )
        .unwrap();
        let first = noise.sample_3d(12.25, -4.5, 81.75);
        let second = noise.sample_3d(12.25, -4.5, 81.75);
        assert!(first.is_finite());
        assert_eq!(first.to_bits(), second.to_bits());
    }
}
