use rayon::prelude::*;

use crate::NativeError;

const PARALLEL_THRESHOLD: usize = 4096;

/// Computes squared Euclidean distances from one origin to packed x/y/z triples.
pub fn compute_squared_distances(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    output: &mut [i64],
) -> Result<(), NativeError> {
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    if output.len() != positions.len() / 3 {
        return Err(NativeError::OutputLengthMismatch);
    }

    if output.len() >= PARALLEL_THRESHOLD {
        output.par_iter_mut().enumerate().for_each(|(index, value)| {
            *value = squared_distance_at(origin_x, origin_y, origin_z, positions, index);
        });
    } else {
        for (index, value) in output.iter_mut().enumerate() {
            *value = squared_distance_at(origin_x, origin_y, origin_z, positions, index);
        }
    }

    Ok(())
}

/// Computes squared Euclidean distances from one origin to packed x/y/z triples.
pub fn compute_squared_distances_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [f64],
) -> Result<(), NativeError> {
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    if output.len() != positions.len() / 3 {
        return Err(NativeError::OutputLengthMismatch);
    }

    if output.len() >= PARALLEL_THRESHOLD {
        output.par_iter_mut().enumerate().for_each(|(index, value)| {
            *value = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        });
    } else {
        for (index, value) in output.iter_mut().enumerate() {
            *value = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        }
    }

    Ok(())
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

    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    if output.len() < positions.len() / 3 {
        return Err(NativeError::OutputLengthMismatch);
    }

    let mut count = 0;
    for index in 0..positions.len() / 3 {
        if squared_distance_at(origin_x, origin_y, origin_z, positions, index) <= radius_squared {
            output[count] = index as i32;
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
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    if output.len() != positions.len() / 3 {
        return Err(NativeError::OutputLengthMismatch);
    }

    let mut indices: Vec<i32> = (0..(positions.len() / 3) as i32).collect();
    indices.sort_by(|left, right| {
        let left_distance = squared_distance_at(origin_x, origin_y, origin_z, positions, *left as usize);
        let right_distance = squared_distance_at(origin_x, origin_y, origin_z, positions, *right as usize);
        left_distance
            .cmp(&right_distance)
            .then_with(|| left.cmp(right))
    });

    output.copy_from_slice(&indices);
    Ok(())
}

fn squared_distance_at(origin_x: i32, origin_y: i32, origin_z: i32, positions: &[i32], index: usize) -> i64 {
    let offset = index * 3;
    let dx = i64::from(positions[offset] - origin_x);
    let dy = i64::from(positions[offset + 1] - origin_y);
    let dz = i64::from(positions[offset + 2] - origin_z);
    dx * dx + dy * dy + dz * dz
}

fn squared_distance_at_f64(origin_x: f64, origin_y: f64, origin_z: f64, positions: &[f64], index: usize) -> f64 {
    let offset = index * 3;
    let dx = positions[offset] - origin_x;
    let dy = positions[offset + 1] - origin_y;
    let dz = positions[offset + 2] - origin_z;
    dx * dx + dy * dy + dz * dz
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compute_squared_distances_should_match_reference_values() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        compute_squared_distances(0, 64, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 41, 6]);
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
    fn compute_squared_distances_f64_should_match_reference_values() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let mut output = [0.0; 3];
        compute_squared_distances_f64(0.0, 64.0, 0.0, &positions, &mut output).unwrap();
        assert_eq!(output, [0.0, 41.0, 6.0]);
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
    fn sort_by_distance_should_match_reference_order() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        sort_by_distance(0, 64, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 2, 1]);
    }
}
