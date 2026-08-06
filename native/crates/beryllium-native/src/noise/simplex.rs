use super::NoiseGenerator;

pub struct SimplexNoise {
    permutation: [u8; 512],
}

impl SimplexNoise {
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

    const GRAD3: [[f64; 3]; 12] = [
        [1.0, 1.0, 0.0], [-1.0, 1.0, 0.0], [1.0, -1.0, 0.0], [-1.0, -1.0, 0.0],
        [1.0, 0.0, 1.0], [-1.0, 0.0, 1.0], [1.0, 0.0, -1.0], [-1.0, 0.0, -1.0],
        [0.0, 1.0, 1.0], [0.0, -1.0, 1.0], [0.0, 1.0, -1.0], [0.0, -1.0, -1.0],
    ];

    #[inline]
    fn grad_3d(hash: u8, x: f64, y: f64, z: f64) -> f64 {
        let h = (hash as usize) % 12;
        let grad = Self::GRAD3[h];
        grad[0] * x + grad[1] * y + grad[2] * z
    }
}

impl NoiseGenerator for SimplexNoise {
    fn sample_2d(&self, x: f64, y: f64) -> f64 {
        const F2: f64 = 0.3660254037844386;
        const G2: f64 = 0.21132486540518713;

        let s = (x + y) * F2;
        let i = (x + s).floor();
        let j = (y + s).floor();

        let t = (i + j) * G2;
        let x0 = x - (i - t);
        let y0 = y - (j - t);

        let (i1, j1) = if x0 > y0 { (1, 0) } else { (0, 1) };

        let x1 = x0 - i1 as f64 + G2;
        let y1 = y0 - j1 as f64 + G2;
        let x2 = x0 - 1.0 + 2.0 * G2;
        let y2 = y0 - 1.0 + 2.0 * G2;

        let ii = (i as i32) & 255;
        let jj = (j as i32) & 255;

        let gi0 = self.permutation[(ii + self.permutation[jj as usize] as i32) as usize & 511];
        let gi1 = self.permutation[(ii + i1 + self.permutation[(jj + j1) as usize] as i32) as usize & 511];
        let gi2 = self.permutation[(ii + 1 + self.permutation[(jj + 1) as usize] as i32) as usize & 511];

        let t0 = 0.5 - x0 * x0 - y0 * y0;
        let n0 = if t0 < 0.0 {
            0.0
        } else {
            let t0 = t0 * t0;
            t0 * t0 * Self::grad_3d(gi0, x0, y0, 0.0)
        };

        let t1 = 0.5 - x1 * x1 - y1 * y1;
        let n1 = if t1 < 0.0 {
            0.0
        } else {
            let t1 = t1 * t1;
            t1 * t1 * Self::grad_3d(gi1, x1, y1, 0.0)
        };

        let t2 = 0.5 - x2 * x2 - y2 * y2;
        let n2 = if t2 < 0.0 {
            0.0
        } else {
            let t2 = t2 * t2;
            t2 * t2 * Self::grad_3d(gi2, x2, y2, 0.0)
        };

        70.0 * (n0 + n1 + n2)
    }

    fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        const F3: f64 = 1.0 / 3.0;
        const G3: f64 = 1.0 / 6.0;

        let s = (x + y + z) * F3;
        let i = (x + s).floor();
        let j = (y + s).floor();
        let k = (z + s).floor();

        let t = (i + j + k) * G3;
        let x0 = x - (i - t);
        let y0 = y - (j - t);
        let z0 = z - (k - t);

        let (i1, j1, k1, i2, j2, k2) = if x0 >= y0 {
            if y0 >= z0 {
                (1, 0, 0, 1, 1, 0)
            } else if x0 >= z0 {
                (1, 0, 0, 1, 0, 1)
            } else {
                (0, 0, 1, 1, 0, 1)
            }
        } else {
            if y0 < z0 {
                (0, 0, 1, 0, 1, 1)
            } else if x0 < z0 {
                (0, 1, 0, 0, 1, 1)
            } else {
                (0, 1, 0, 1, 1, 0)
            }
        };

        let x1 = x0 - i1 as f64 + G3;
        let y1 = y0 - j1 as f64 + G3;
        let z1 = z0 - k1 as f64 + G3;
        let x2 = x0 - i2 as f64 + 2.0 * G3;
        let y2 = y0 - j2 as f64 + 2.0 * G3;
        let z2 = z0 - k2 as f64 + 2.0 * G3;
        let x3 = x0 - 1.0 + 3.0 * G3;
        let y3 = y0 - 1.0 + 3.0 * G3;
        let z3 = z0 - 1.0 + 3.0 * G3;

        let ii = (i as i32) & 255;
        let jj = (j as i32) & 255;
        let kk = (k as i32) & 255;

        let gi0 = self.permutation[(ii + self.permutation[(jj + self.permutation[kk as usize] as i32) as usize] as i32) as usize & 511];
        let gi1 = self.permutation[(ii + i1 + self.permutation[(jj + j1 + self.permutation[(kk + k1) as usize] as i32) as usize] as i32) as usize & 511];
        let gi2 = self.permutation[(ii + i2 + self.permutation[(jj + j2 + self.permutation[(kk + k2) as usize] as i32) as usize] as i32) as usize & 511];
        let gi3 = self.permutation[(ii + 1 + self.permutation[(jj + 1 + self.permutation[(kk + 1) as usize] as i32) as usize] as i32) as usize & 511];

        let t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        let n0 = if t0 < 0.0 {
            0.0
        } else {
            let t0 = t0 * t0;
            t0 * t0 * Self::grad_3d(gi0, x0, y0, z0)
        };

        let t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        let n1 = if t1 < 0.0 {
            0.0
        } else {
            let t1 = t1 * t1;
            t1 * t1 * Self::grad_3d(gi1, x1, y1, z1)
        };

        let t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        let n2 = if t2 < 0.0 {
            0.0
        } else {
            let t2 = t2 * t2;
            t2 * t2 * Self::grad_3d(gi2, x2, y2, z2)
        };

        let t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        let n3 = if t3 < 0.0 {
            0.0
        } else {
            let t3 = t3 * t3;
            t3 * t3 * Self::grad_3d(gi3, x3, y3, z3)
        };

        32.0 * (n0 + n1 + n2 + n3)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn simplex_noise_deterministic() {
        let noise = SimplexNoise::new(12345);
        let v1 = noise.sample_3d(1.5, 2.5, 3.5);
        let v2 = noise.sample_3d(1.5, 2.5, 3.5);
        assert_eq!(v1, v2);
    }

    #[test]
    fn simplex_noise_range() {
        let noise = SimplexNoise::new(12345);
        for i in 0..100 {
            let x = i as f64 * 0.1;
            let y = i as f64 * 0.2;
            let z = i as f64 * 0.3;
            let v = noise.sample_3d(x, y, z);
            assert!(v >= -1.5 && v <= 1.5, "Value {} out of range", v);
        }
    }
}
