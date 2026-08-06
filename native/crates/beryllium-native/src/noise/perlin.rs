use super::NoiseGenerator;

pub struct PerlinNoise {
    permutation: [u8; 512],
}

impl PerlinNoise {
    pub fn new(seed: i64) -> Self {
        let mut perm = [0u8; 256];
        for i in 0..256 {
            perm[i] = i as u8;
        }

        let mut rng = seed as u64;
        for i in (1..256).rev() {
            rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
            let j = ((rng >> 32) as usize) % (i + 1);
            perm.swap(i, j);
        }

        let mut permutation = [0u8; 512];
        for i in 0..512 {
            permutation[i] = perm[i & 255];
        }

        Self { permutation }
    }

    #[inline]
    fn fade(t: f64) -> f64 {
        t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }

    #[inline]
    fn grad_2d(hash: u8, x: f64, y: f64) -> f64 {
        let h = hash & 3;
        let u = if h < 2 { x } else { -x };
        let v = if h < 2 { y } else { -y };
        u + v
    }

    #[inline]
    fn grad_3d(hash: u8, x: f64, y: f64, z: f64) -> f64 {
        let h = hash & 15;
        let u = if h < 8 { x } else { y };
        let v = if h < 4 { y } else if h == 12 || h == 14 { x } else { z };
        let a = if h & 1 == 0 { u } else { -u };
        let b = if h & 2 == 0 { v } else { -v };
        a + b
    }

    #[inline]
    fn lerp(t: f64, a: f64, b: f64) -> f64 {
        a + t * (b - a)
    }
}

impl NoiseGenerator for PerlinNoise {
    fn sample_2d(&self, x: f64, y: f64) -> f64 {
        let xi = (x.floor() as i32) & 255;
        let yi = (y.floor() as i32) & 255;
        let xf = x - x.floor();
        let yf = y - y.floor();

        let u = Self::fade(xf);
        let v = Self::fade(yf);

        let aa = self.permutation[(self.permutation[xi as usize] as usize + yi as usize) & 511];
        let ab = self.permutation[(self.permutation[xi as usize] as usize + yi as usize + 1) & 511];
        let ba = self.permutation[(self.permutation[xi as usize + 1] as usize + yi as usize) & 511];
        let bb = self.permutation[(self.permutation[xi as usize + 1] as usize + yi as usize + 1) & 511];

        let x1 = Self::lerp(u, Self::grad_2d(aa, xf, yf), Self::grad_2d(ba, xf - 1.0, yf));
        let x2 = Self::lerp(u, Self::grad_2d(ab, xf, yf - 1.0), Self::grad_2d(bb, xf - 1.0, yf - 1.0));

        Self::lerp(v, x1, x2)
    }

    fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        let xi = (x.floor() as i32) & 255;
        let yi = (y.floor() as i32) & 255;
        let zi = (z.floor() as i32) & 255;
        let xf = x - x.floor();
        let yf = y - y.floor();
        let zf = z - z.floor();

        let u = Self::fade(xf);
        let v = Self::fade(yf);
        let w = Self::fade(zf);

        let aaa = self.permutation[(self.permutation[(self.permutation[xi as usize] as usize + yi as usize) & 511] as usize + zi as usize) & 511];
        let aba = self.permutation[(self.permutation[(self.permutation[xi as usize] as usize + yi as usize + 1) & 511] as usize + zi as usize) & 511];
        let aab = self.permutation[(self.permutation[(self.permutation[xi as usize] as usize + yi as usize) & 511] as usize + zi as usize + 1) & 511];
        let abb = self.permutation[(self.permutation[(self.permutation[xi as usize] as usize + yi as usize + 1) & 511] as usize + zi as usize + 1) & 511];
        let baa = self.permutation[(self.permutation[(self.permutation[xi as usize + 1] as usize + yi as usize) & 511] as usize + zi as usize) & 511];
        let bba = self.permutation[(self.permutation[(self.permutation[xi as usize + 1] as usize + yi as usize + 1) & 511] as usize + zi as usize) & 511];
        let bab = self.permutation[(self.permutation[(self.permutation[xi as usize + 1] as usize + yi as usize) & 511] as usize + zi as usize + 1) & 511];
        let bbb = self.permutation[(self.permutation[(self.permutation[xi as usize + 1] as usize + yi as usize + 1) & 511] as usize + zi as usize + 1) & 511];

        let x1 = Self::lerp(u, Self::grad_3d(aaa, xf, yf, zf), Self::grad_3d(baa, xf - 1.0, yf, zf));
        let x2 = Self::lerp(u, Self::grad_3d(aba, xf, yf - 1.0, zf), Self::grad_3d(bba, xf - 1.0, yf - 1.0, zf));
        let y1 = Self::lerp(v, x1, x2);

        let x3 = Self::lerp(u, Self::grad_3d(aab, xf, yf, zf - 1.0), Self::grad_3d(bab, xf - 1.0, yf, zf - 1.0));
        let x4 = Self::lerp(u, Self::grad_3d(abb, xf, yf - 1.0, zf - 1.0), Self::grad_3d(bbb, xf - 1.0, yf - 1.0, zf - 1.0));
        let y2 = Self::lerp(v, x3, x4);

        Self::lerp(w, y1, y2)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn perlin_noise_deterministic() {
        let noise = PerlinNoise::new(12345);
        let v1 = noise.sample_3d(1.5, 2.5, 3.5);
        let v2 = noise.sample_3d(1.5, 2.5, 3.5);
        assert_eq!(v1, v2);
    }

    #[test]
    fn perlin_noise_range() {
        let noise = PerlinNoise::new(12345);
        for i in 0..100 {
            let x = i as f64 * 0.1;
            let y = i as f64 * 0.2;
            let z = i as f64 * 0.3;
            let v = noise.sample_3d(x, y, z);
            assert!(v >= -1.0 && v <= 1.0, "Value {} out of range", v);
        }
    }
}
