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
        output
            .par_iter_mut()
            .enumerate()
            .for_each(|(index, value)| {
                *value = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
            });
    } else {
        for (index, value) in output.iter_mut().enumerate() {
            *value = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
        }
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

fn find_nearest_index_f64_by_limit(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: &[f64],
    within_limit: fn(f64, f64) -> bool,
) -> Result<Option<usize>, NativeError> {
    if positions.len() % 3 != 0 {
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

    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        let matches: Vec<Option<i32>> = positions
            .par_chunks_exact(3)
            .enumerate()
            .map(|(index, position)| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    <= radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        let mut count = 0;
        for index in matches.into_iter().flatten() {
            output[count] = index;
            count += 1;
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

/// Filters packed f64 x/y/z triples by AABB containment and returns the matching indices.
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
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        let matches: Vec<Option<i32>> = positions
            .par_chunks_exact(3)
            .enumerate()
            .map(|(index, position)| {
                if contains_aabb_position(min_x, min_y, min_z, max_x, max_y, max_z, position) {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        let mut count = 0;
        for index in matches.into_iter().flatten() {
            output[count] = index;
            count += 1;
        }

        return Ok(count);
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
    if boxes.len() % 6 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let box_count = boxes.len() / 6;
    if output.len() < box_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if box_count >= PARALLEL_THRESHOLD {
        let matches: Vec<Option<i32>> = boxes
            .par_chunks_exact(6)
            .enumerate()
            .map(|(index, entity_box)| {
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

        let mut count = 0;
        for index in matches.into_iter().flatten() {
            output[count] = index;
            count += 1;
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

    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        let matches: Vec<Option<i32>> = positions
            .par_chunks_exact(3)
            .enumerate()
            .map(|(index, position)| {
                if squared_distance_at_slice(origin_x, origin_y, origin_z, position)
                    <= radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        let mut count = 0;
        for index in matches.into_iter().flatten() {
            output[count] = index;
            count += 1;
        }

        return Ok(count);
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
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();
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
    fn find_nearest_index_f64_should_match_parallel_reference_index() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();

        let nearest = find_nearest_index_f64(0.0, 0.0, 0.0, 1024.0, &positions).unwrap();
        assert_eq!(nearest, Some(4999));
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
    fn filter_within_radius_should_match_parallel_reference_indices() {
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();
        let mut output = vec![0; 5000];

        let count = filter_within_radius(0, 0, 0, 1024, &positions, &mut output).unwrap();
        assert_eq!(count, 33);
        assert_eq!(&output[..count], &(4967..5000).collect::<Vec<_>>()[..]);
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
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();
        let mut output = vec![0; 5000];

        sort_by_distance(0, 0, 0, &positions, &mut output).unwrap();
        let expected: Vec<i32> = (0..5000).rev().map(|index| index as i32).collect();
        assert_eq!(output, expected);
    }
}
