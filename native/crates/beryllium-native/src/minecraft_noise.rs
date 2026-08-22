use crate::NativeError;
use crate::noise::NoiseGenerator;

const WRAP: f64 = 33_554_432.0;
const NORMAL_INPUT_FACTOR: f64 = 1.018_126_888_217_522_7;
const BLENDED_LIMIT_OCTAVES: usize = 16;
const BLENDED_MAIN_OCTAVES: usize = 8;
const BLENDED_TOTAL_OCTAVES: usize = BLENDED_LIMIT_OCTAVES * 2 + BLENDED_MAIN_OCTAVES;
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
        self.sample_with_y(x, y, z, 0.0, 0.0)
    }

    #[inline]
    fn sample_with_y(&self, x: f64, y: f64, z: f64, y_scale: f64, y_max: f64) -> f64 {
        let shifted_x = x + self.xo;
        let shifted_y = y + self.yo;
        let shifted_z = z + self.zo;
        let floor_x = shifted_x.floor() as i32;
        let floor_y = shifted_y.floor() as i32;
        let floor_z = shifted_z.floor() as i32;
        let local_x = shifted_x - floor_x as f64;
        let local_y = shifted_y - floor_y as f64;
        let local_z = shifted_z - floor_z as f64;
        let y_offset = if y_scale != 0.0 {
            let limited_y = if y_max >= 0.0 && y_max < local_y {
                y_max
            } else {
                local_y
            };
            (limited_y / y_scale + 1.0E-7_f32 as f64).floor() * y_scale
        } else {
            0.0
        };
        let gradient_y = local_y - y_offset;

        let px0 = self.p(floor_x) as i32;
        let px1 = self.p(floor_x + 1) as i32;
        let pxy00 = self.p(px0 + floor_y) as i32;
        let pxy01 = self.p(px0 + floor_y + 1) as i32;
        let pxy10 = self.p(px1 + floor_y) as i32;
        let pxy11 = self.p(px1 + floor_y + 1) as i32;
        let values = [
            Self::grad_dot(self.p(pxy00 + floor_z), local_x, gradient_y, local_z),
            Self::grad_dot(self.p(pxy10 + floor_z), local_x - 1.0, gradient_y, local_z),
            Self::grad_dot(self.p(pxy01 + floor_z), local_x, gradient_y - 1.0, local_z),
            Self::grad_dot(
                self.p(pxy11 + floor_z),
                local_x - 1.0,
                gradient_y - 1.0,
                local_z,
            ),
            Self::grad_dot(self.p(pxy00 + floor_z + 1), local_x, gradient_y, local_z - 1.0),
            Self::grad_dot(
                self.p(pxy10 + floor_z + 1),
                local_x - 1.0,
                gradient_y,
                local_z - 1.0,
            ),
            Self::grad_dot(
                self.p(pxy01 + floor_z + 1),
                local_x,
                gradient_y - 1.0,
                local_z - 1.0,
            ),
            Self::grad_dot(
                self.p(pxy11 + floor_z + 1),
                local_x - 1.0,
                gradient_y - 1.0,
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

pub struct MinecraftBlendedNoise {
    min_limit_levels: Box<[ImprovedNoise]>,
    max_limit_levels: Box<[ImprovedNoise]>,
    main_levels: Box<[ImprovedNoise]>,
    xz_multiplier: f64,
    y_multiplier: f64,
    xz_factor: f64,
    y_factor: f64,
    smear_scale_multiplier: f64,
}

impl MinecraftBlendedNoise {
    pub fn from_flat_state(
        origins: &[f64],
        permutations: &[i32],
        xz_multiplier: f64,
        y_multiplier: f64,
        xz_factor: f64,
        y_factor: f64,
        smear_scale_multiplier: f64,
    ) -> Result<Self, NativeError> {
        if origins.len() != BLENDED_TOTAL_OCTAVES * 3
            || permutations.len() != BLENDED_TOTAL_OCTAVES * 256
            || ![
                xz_multiplier,
                y_multiplier,
                xz_factor,
                y_factor,
                smear_scale_multiplier,
            ]
            .iter()
            .all(|value| value.is_finite())
            || xz_factor == 0.0
            || y_factor == 0.0
        {
            return Err(NativeError::InvalidInput);
        }

        let mut levels = Vec::with_capacity(BLENDED_TOTAL_OCTAVES);
        for level_index in 0..BLENDED_TOTAL_OCTAVES {
            levels.push(ImprovedNoise::from_flat(origins, permutations, level_index)?);
        }
        let main_levels = levels.split_off(BLENDED_LIMIT_OCTAVES * 2);
        let max_limit_levels = levels.split_off(BLENDED_LIMIT_OCTAVES);

        Ok(Self {
            min_limit_levels: levels.into(),
            max_limit_levels: max_limit_levels.into(),
            main_levels: main_levels.into(),
            xz_multiplier,
            y_multiplier,
            xz_factor,
            y_factor,
            smear_scale_multiplier,
        })
    }

    #[inline]
    fn wrap(value: f64) -> f64 {
        value - (value / WRAP + 0.5).floor() * WRAP
    }

    #[inline]
    fn clamped_lerp(start: f64, end: f64, delta: f64) -> f64 {
        if delta < 0.0 {
            start
        } else if delta > 1.0 {
            end
        } else {
            start + delta * (end - start)
        }
    }
}

impl NoiseGenerator for MinecraftBlendedNoise {
    fn sample_2d(&self, x: f64, y: f64) -> f64 {
        self.sample_3d(x, y, 0.0)
    }

    fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        let scaled_x = x * self.xz_multiplier;
        let scaled_y = y * self.y_multiplier;
        let scaled_z = z * self.xz_multiplier;
        let main_x = scaled_x / self.xz_factor;
        let main_y = scaled_y / self.y_factor;
        let main_z = scaled_z / self.xz_factor;
        let smear_scale = self.y_multiplier * self.smear_scale_multiplier;
        let main_smear_scale = smear_scale / self.y_factor;
        let mut min_limit = 0.0;
        let mut max_limit = 0.0;
        let mut main = 0.0;
        let mut octave_scale = 1.0;

        for level in self.main_levels.iter() {
            main += level.sample_with_y(
                Self::wrap(main_x * octave_scale),
                Self::wrap(main_y * octave_scale),
                Self::wrap(main_z * octave_scale),
                main_smear_scale * octave_scale,
                main_y * octave_scale,
            ) / octave_scale;
            octave_scale /= 2.0;
        }

        let blend = (main / 10.0 + 1.0) / 2.0;
        let skip_min = blend >= 1.0;
        let skip_max = blend <= 0.0;
        octave_scale = 1.0;

        for level_index in 0..BLENDED_LIMIT_OCTAVES {
            let noise_x = Self::wrap(scaled_x * octave_scale);
            let noise_y = Self::wrap(scaled_y * octave_scale);
            let noise_z = Self::wrap(scaled_z * octave_scale);
            let y_scale = smear_scale * octave_scale;
            let y_max = scaled_y * octave_scale;
            if !skip_min {
                min_limit += self.min_limit_levels[level_index]
                    .sample_with_y(noise_x, noise_y, noise_z, y_scale, y_max)
                    / octave_scale;
            }
            if !skip_max {
                max_limit += self.max_limit_levels[level_index]
                    .sample_with_y(noise_x, noise_y, noise_z, y_scale, y_max)
                    / octave_scale;
            }
            octave_scale /= 2.0;
        }

        Self::clamped_lerp(min_limit / 512.0, max_limit / 512.0, blend) / 128.0
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

    #[test]
    fn blended_noise_rejects_mismatched_state() {
        assert!(matches!(
            MinecraftBlendedNoise::from_flat_state(
                &[0.0; BLENDED_TOTAL_OCTAVES * 3 - 1],
                &[0; BLENDED_TOTAL_OCTAVES * 256],
                1.0,
                1.0,
                80.0,
                160.0,
                8.0,
            ),
            Err(NativeError::InvalidInput)
        ));
    }
}
