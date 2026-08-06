use super::NoiseGenerator;

pub struct OpenSimplex2Noise {
    permutation: [i16; 2048],
}

impl OpenSimplex2Noise {
    pub fn new(seed: i64) -> Self {
        let mut perm = [0i16; 2048];
        let mut source = [0i16; 2048];
        
        for i in 0..2048 {
            source[i] = i as i16;
        }

        let mut rng = seed.wrapping_mul(6364136223846793005_i64).wrapping_add(1442695040888963407_i64);
        
        for i in (0..2048).rev() {
            rng = rng.wrapping_mul(6364136223846793005_i64).wrapping_add(1442695040888963407_i64);
            let r = (((rng.wrapping_add(31) as u64) % ((i + 1) as u64)) as usize);
            perm[i] = source[r];
            source[r] = source[i];
        }

        Self { permutation: perm }
    }

    const GRADIENTS_3D: [[f64; 3]; 72] = [
        [ 2.22474487139,  2.22474487139, -1.0],
        [ 2.22474487139,  2.22474487139,  1.0],
        [ 3.0862664687972017,  1.1721513422464978,  0.0],
        [ 1.1721513422464978,  3.0862664687972017,  0.0],
        [-2.22474487139,  2.22474487139, -1.0],
        [-2.22474487139,  2.22474487139,  1.0],
        [-1.1721513422464978,  3.0862664687972017,  0.0],
        [-3.0862664687972017,  1.1721513422464978,  0.0],
        [-1.0, -2.22474487139, -2.22474487139],
        [ 1.0, -2.22474487139, -2.22474487139],
        [ 0.0, -3.0862664687972017, -1.1721513422464978],
        [ 0.0, -1.1721513422464978, -3.0862664687972017],
        [-1.0, -2.22474487139,  2.22474487139],
        [ 1.0, -2.22474487139,  2.22474487139],
        [ 0.0, -1.1721513422464978,  3.0862664687972017],
        [ 0.0, -3.0862664687972017,  1.1721513422464978],
        [-2.22474487139, -2.22474487139, -1.0],
        [-2.22474487139, -2.22474487139,  1.0],
        [-3.0862664687972017, -1.1721513422464978,  0.0],
        [-1.1721513422464978, -3.0862664687972017,  0.0],
        [-2.22474487139, -1.0, -2.22474487139],
        [-2.22474487139,  1.0, -2.22474487139],
        [-1.1721513422464978,  0.0, -3.0862664687972017],
        [-3.0862664687972017,  0.0, -1.1721513422464978],
        [-2.22474487139, -1.0,  2.22474487139],
        [-2.22474487139,  1.0,  2.22474487139],
        [-3.0862664687972017,  0.0,  1.1721513422464978],
        [-1.1721513422464978,  0.0,  3.0862664687972017],
        [-1.0,  2.22474487139, -2.22474487139],
        [ 1.0,  2.22474487139, -2.22474487139],
        [ 0.0,  1.1721513422464978, -3.0862664687972017],
        [ 0.0,  3.0862664687972017, -1.1721513422464978],
        [-1.0,  2.22474487139,  2.22474487139],
        [ 1.0,  2.22474487139,  2.22474487139],
        [ 0.0,  3.0862664687972017,  1.1721513422464978],
        [ 0.0,  1.1721513422464978,  3.0862664687972017],
        [ 2.22474487139, -2.22474487139, -1.0],
        [ 2.22474487139, -2.22474487139,  1.0],
        [ 1.1721513422464978, -3.0862664687972017,  0.0],
        [ 3.0862664687972017, -1.1721513422464978,  0.0],
        [ 2.22474487139, -1.0, -2.22474487139],
        [ 2.22474487139,  1.0, -2.22474487139],
        [ 3.0862664687972017,  0.0, -1.1721513422464978],
        [ 1.1721513422464978,  0.0, -3.0862664687972017],
        [ 2.22474487139, -1.0,  2.22474487139],
        [ 2.22474487139,  1.0,  2.22474487139],
        [ 1.1721513422464978,  0.0,  3.0862664687972017],
        [ 3.0862664687972017,  0.0,  1.1721513422464978],
        [ 3.0862664687972017,  1.1721513422464978,  0.0],
        [ 3.0862664687972017, -1.1721513422464978,  0.0],
        [ 0.0,  3.0862664687972017,  1.1721513422464978],
        [ 0.0,  3.0862664687972017, -1.1721513422464978],
        [ 1.1721513422464978,  0.0,  3.0862664687972017],
        [-1.1721513422464978,  0.0,  3.0862664687972017],
        [ 0.0, -1.1721513422464978,  3.0862664687972017],
        [ 0.0,  1.1721513422464978,  3.0862664687972017],
        [ 0.0, -3.0862664687972017,  1.1721513422464978],
        [ 0.0, -3.0862664687972017, -1.1721513422464978],
        [-3.0862664687972017, -1.1721513422464978,  0.0],
        [-3.0862664687972017,  1.1721513422464978,  0.0],
        [-1.1721513422464978,  0.0, -3.0862664687972017],
        [ 1.1721513422464978,  0.0, -3.0862664687972017],
        [ 0.0,  1.1721513422464978, -3.0862664687972017],
        [ 0.0, -1.1721513422464978, -3.0862664687972017],
        [ 3.0862664687972017,  0.0,  1.1721513422464978],
        [ 3.0862664687972017,  0.0, -1.1721513422464978],
        [-3.0862664687972017,  0.0,  1.1721513422464978],
        [-3.0862664687972017,  0.0, -1.1721513422464978],
        [ 1.1721513422464978,  3.0862664687972017,  0.0],
        [-1.1721513422464978,  3.0862664687972017,  0.0],
        [ 1.1721513422464978, -3.0862664687972017,  0.0],
        [-1.1721513422464978, -3.0862664687972017,  0.0],
    ];

    #[inline]
    fn grad(&self, hash: i16, x: f64, y: f64, z: f64) -> f64 {
        let h = (hash as usize) % 72;
        let grad = Self::GRADIENTS_3D[h];
        grad[0] * x + grad[1] * y + grad[2] * z
    }
}

impl NoiseGenerator for OpenSimplex2Noise {
    fn sample_2d(&self, x: f64, y: f64) -> f64 {
        const STRETCH_2D: f64 = -0.211324865405187;
        const SQUISH_2D: f64 = 0.366025403784439;

        let stretch_offset = (x + y) * STRETCH_2D;
        let xs = x + stretch_offset;
        let ys = y + stretch_offset;

        let xsb = xs.floor() as i32;
        let ysb = ys.floor() as i32;

        let squish_offset = (xsb + ysb) as f64 * SQUISH_2D;
        let dx0 = x - (xsb as f64 + squish_offset);
        let dy0 = y - (ysb as f64 + squish_offset);

        let xins = xs - xsb as f64;
        let yins = ys - ysb as f64;

        let in_sum = xins + yins;

        let mut value = 0.0;

        let hash0 = self.permutation[((self.permutation[(xsb & 0x7FF) as usize] as i32 + ysb) & 0x7FF) as usize];
        let dx = dx0;
        let dy = dy0;
        let attn = 2.0 - dx * dx - dy * dy;
        if attn > 0.0 {
            value += attn * attn * attn * attn * self.grad(hash0, dx, dy, 0.0);
        }

        let dx1 = dx0 - 1.0 - SQUISH_2D;
        let dy1 = dy0 - 0.0 - SQUISH_2D;
        let attn1 = 2.0 - dx1 * dx1 - dy1 * dy1;
        if attn1 > 0.0 {
            let hash1 = self.permutation[((self.permutation[((xsb + 1) & 0x7FF) as usize] as i32 + ysb) & 0x7FF) as usize];
            value += attn1 * attn1 * attn1 * attn1 * self.grad(hash1, dx1, dy1, 0.0);
        }

        let dx2 = dx0 - 0.0 - SQUISH_2D;
        let dy2 = dy0 - 1.0 - SQUISH_2D;
        let attn2 = 2.0 - dx2 * dx2 - dy2 * dy2;
        if attn2 > 0.0 {
            let hash2 = self.permutation[((self.permutation[(xsb & 0x7FF) as usize] as i32 + ysb + 1) & 0x7FF) as usize];
            value += attn2 * attn2 * attn2 * attn2 * self.grad(hash2, dx2, dy2, 0.0);
        }

        if in_sum <= 1.0 {
            let zins = 1.0 - in_sum;
            if zins > xins || zins > yins {
                if xins > yins {
                    let dx_ext = dx0 - 1.0 - 2.0 * SQUISH_2D;
                    let dy_ext = dy0 - 2.0 * SQUISH_2D;
                    let hash_ext = self.permutation[((self.permutation[((xsb + 1) & 0x7FF) as usize] as i32 + ysb - 1) & 0x7FF) as usize];
                    let attn_ext = 2.0 - dx_ext * dx_ext - dy_ext * dy_ext;
                    if attn_ext > 0.0 {
                        value += attn_ext * attn_ext * attn_ext * attn_ext * self.grad(hash_ext, dx_ext, dy_ext, 0.0);
                    }
                } else {
                    let dx_ext = dx0 - 2.0 * SQUISH_2D;
                    let dy_ext = dy0 - 1.0 - 2.0 * SQUISH_2D;
                    let hash_ext = self.permutation[((self.permutation[((xsb - 1) & 0x7FF) as usize] as i32 + ysb + 1) & 0x7FF) as usize];
                    let attn_ext = 2.0 - dx_ext * dx_ext - dy_ext * dy_ext;
                    if attn_ext > 0.0 {
                        value += attn_ext * attn_ext * attn_ext * attn_ext * self.grad(hash_ext, dx_ext, dy_ext, 0.0);
                    }
                }
            }
        } else {
            let dx3 = dx0 - 1.0 - 2.0 * SQUISH_2D;
            let dy3 = dy0 - 1.0 - 2.0 * SQUISH_2D;
            let attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
            if attn3 > 0.0 {
                let hash3 = self.permutation[((self.permutation[((xsb + 1) & 0x7FF) as usize] as i32 + ysb + 1) & 0x7FF) as usize];
                value += attn3 * attn3 * attn3 * attn3 * self.grad(hash3, dx3, dy3, 0.0);
            }
        }

        value * 10.0
    }

    fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        const STRETCH_3D: f64 = -1.0 / 6.0;
        const SQUISH_3D: f64 = 1.0 / 3.0;

        let stretch_offset = (x + y + z) * STRETCH_3D;
        let xs = x + stretch_offset;
        let ys = y + stretch_offset;
        let zs = z + stretch_offset;

        let xsb = xs.floor() as i32;
        let ysb = ys.floor() as i32;
        let zsb = zs.floor() as i32;

        let squish_offset = (xsb + ysb + zsb) as f64 * SQUISH_3D;
        let xb = xsb as f64 + squish_offset;
        let yb = ysb as f64 + squish_offset;
        let zb = zsb as f64 + squish_offset;

        let xins = xs - xsb as f64;
        let yins = ys - ysb as f64;
        let zins = zs - zsb as f64;

        let in_sum = xins + yins + zins;

        let mut dx0 = x - xb;
        let mut dy0 = y - yb;
        let mut dz0 = z - zb;

        let mut value = 0.0;

        let hash0 = self.permutation[((self.permutation[((self.permutation[(xsb & 0x7FF) as usize] as i32 + ysb) & 0x7FF) as usize] as i32 + zsb) & 0x7FF) as usize];
        let mut dx = dx0;
        let mut dy = dy0;
        let mut dz = dz0;
        let mut attn = 2.0 - dx * dx - dy * dy - dz * dz;
        if attn > 0.0 {
            attn *= attn;
            value += attn * attn * self.grad(hash0, dx, dy, dz);
        }

        if in_sum <= 1.0 {
            let a_po = 1.0 - xins;
            if a_po > yins && a_po > zins {
                dx = dx0 - (-1.0) - SQUISH_3D;
                dy = dy0 - 0.0 - SQUISH_3D;
                dz = dz0 - 0.0 - SQUISH_3D;
                let hash = self.permutation[((self.permutation[((self.permutation[((xsb - 1) & 0x7FF) as usize] as i32 + ysb) & 0x7FF) as usize] as i32 + zsb) & 0x7FF) as usize];
                attn = 2.0 - dx * dx - dy * dy - dz * dz;
                if attn > 0.0 {
                    attn *= attn;
                    value += attn * attn * self.grad(hash, dx, dy, dz);
                }
            }

            let b_po = 1.0 - yins;
            if b_po > xins && b_po > zins {
                dx = dx0 - 0.0 - SQUISH_3D;
                dy = dy0 - (-1.0) - SQUISH_3D;
                dz = dz0 - 0.0 - SQUISH_3D;
                let hash = self.permutation[((self.permutation[((self.permutation[(xsb & 0x7FF) as usize] as i32 + ysb - 1) & 0x7FF) as usize] as i32 + zsb) & 0x7FF) as usize];
                attn = 2.0 - dx * dx - dy * dy - dz * dz;
                if attn > 0.0 {
                    attn *= attn;
                    value += attn * attn * self.grad(hash, dx, dy, dz);
                }
            }

            let c_po = 1.0 - zins;
            if c_po > xins && c_po > yins {
                dx = dx0 - 0.0 - SQUISH_3D;
                dy = dy0 - 0.0 - SQUISH_3D;
                dz = dz0 - (-1.0) - SQUISH_3D;
                let hash = self.permutation[((self.permutation[((self.permutation[(xsb & 0x7FF) as usize] as i32 + ysb) & 0x7FF) as usize] as i32 + zsb - 1) & 0x7FF) as usize];
                attn = 2.0 - dx * dx - dy * dy - dz * dz;
                if attn > 0.0 {
                    attn *= attn;
                    value += attn * attn * self.grad(hash, dx, dy, dz);
                }
            }
        } else {
            dx = dx0 - 1.0 - SQUISH_3D;
            dy = dy0 - 1.0 - SQUISH_3D;
            dz = dz0 - 1.0 - SQUISH_3D;
            let hash = self.permutation[((self.permutation[((self.permutation[((xsb + 1) & 0x7FF) as usize] as i32 + ysb + 1) & 0x7FF) as usize] as i32 + zsb + 1) & 0x7FF) as usize];
            attn = 2.0 - dx * dx - dy * dy - dz * dz;
            if attn > 0.0 {
                attn *= attn;
                value += attn * attn * self.grad(hash, dx, dy, dz);
            }
        }

        value * 10.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn opensimplex2_noise_deterministic() {
        let noise = OpenSimplex2Noise::new(12345);
        let v1 = noise.sample_3d(1.5, 2.5, 3.5);
        let v2 = noise.sample_3d(1.5, 2.5, 3.5);
        assert_eq!(v1, v2);
    }
}
