use rayon::prelude::*;
use std::cmp::Ordering;

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
    if !positions.len().is_multiple_of(3) {
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

/// Computes the vanilla PotentialCalculator point-charge energy change.
pub fn potential_energy_change(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    charges: &[f64],
    charge_multiplier: f64,
) -> Result<f64, NativeError> {
    if charge_multiplier == 0.0 {
        return Ok(0.0);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    if charges.len() != positions.len() / 3 {
        return Err(NativeError::InvalidInput);
    }

    let mut energy = 0.0;
    for (index, charge) in charges.iter().enumerate() {
        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        energy += if distance == 0.0 {
            f64::INFINITY
        } else {
            *charge / distance.sqrt()
        };
    }

    Ok(energy * charge_multiplier)
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

/// Returns whether any packed f64 x/y/z triple is within an optional exclusive squared radius.
pub fn has_any_within_radius_f64_exclusive(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    max_distance_squared: f64,
    positions: &[f64],
) -> Result<bool, NativeError> {
    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count == 0 {
        return Ok(false);
    }
    if max_distance_squared < 0.0 {
        return Ok(true);
    }

    if position_count >= PARALLEL_THRESHOLD {
        return Ok(positions.par_chunks_exact(3).any(|position| {
            squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                < max_distance_squared
        }));
    }

    Ok((0..position_count).any(|index| {
        squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
            < max_distance_squared
    }))
}

/// Finds the nearest packed block position by squared distance to its block center.
pub fn find_nearest_block_center_index(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[i32],
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
                    block_center_distance_at(origin_x, origin_y, origin_z, positions, index);
                if distance.is_nan() {
                    None
                } else {
                    Some((index, distance))
                }
            })
            .reduce_with(|left, right| nearest_block_center_pair(left, right, positions))
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for index in 0..position_count {
        let distance = block_center_distance_at(origin_x, origin_y, origin_z, positions, index);
        if distance < nearest_distance
            || (distance == nearest_distance
                && nearest_index
                    .map(|current| compare_block_pos(positions, current, index) < 0)
                    .unwrap_or(true))
        {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Finds the nearest packed block position by squared distance to its block low corner.
pub fn find_nearest_block_corner_index(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
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
            .map(|index| {
                (
                    index,
                    block_corner_distance_at(origin_x, origin_y, origin_z, positions, index),
                )
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for index in 0..position_count {
        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
}

/// Finds the nearest packed block position within an inclusive squared radius.
pub fn find_nearest_block_corner_index_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: &[i32],
) -> Result<Option<usize>, NativeError> {
    if radius_squared < 0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
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
                if squared_distance_at(origin_x, origin_y, origin_z, positions, index)
                    > radius_squared
                {
                    None
                } else {
                    Some((
                        index,
                        block_corner_distance_at(origin_x, origin_y, origin_z, positions, index),
                    ))
                }
            })
            .reduce_with(nearest_distance_pair)
            .map(|(index, _)| index));
    }

    let mut nearest_index = None;
    let mut nearest_distance = f64::MAX;
    for index in 0..position_count {
        if squared_distance_at(origin_x, origin_y, origin_z, positions, index) > radius_squared {
            continue;
        }

        let distance = block_corner_distance_at(origin_x, origin_y, origin_z, positions, index);
        if nearest_index.is_none() || distance < nearest_distance {
            nearest_index = Some(index);
            nearest_distance = distance;
        }
    }

    Ok(nearest_index)
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
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    <= radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
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

/// Filters packed f64 x/y/z triples by exclusive squared radius and returns the matching indices.
pub fn filter_within_radius_f64_exclusive(
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
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    < radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    let mut count = 0;
    for index in 0..position_count {
        if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index) < radius_squared
        {
            output[count] = index as i32;
            count += 1;
        }
    }

    Ok(count)
}

/// Filters packed f64 x/y/z triples by exclusive squared radius, then sorts matches by squared distance.
pub fn sort_within_radius_f64_exclusive(
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

    let mut matches: Vec<(i32, f64)> = if position_count >= PARALLEL_THRESHOLD {
        positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                let distance =
                    squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position);
                if distance < radius_squared {
                    Some((index as i32, distance))
                } else {
                    None
                }
            })
            .collect()
    } else {
        let mut values = Vec::new();
        for index in 0..position_count {
            let distance = squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index);
            if distance < radius_squared {
                values.push((index as i32, distance));
            }
        }
        values
    };

    if matches.len() >= PARALLEL_THRESHOLD {
        matches.par_sort_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    } else {
        matches.sort_by(|left, right| compare_distance_order_f64(left.0, left.1, right.0, right.1));
    }

    for (output_index, (index, _distance)) in matches.iter().enumerate() {
        output[output_index] = *index;
    }

    Ok(matches.len())
}

/// Filters packed f64 x/y/z triples by one inclusive squared radius per position.
pub fn filter_within_radii_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    radii_squared: &[f64],
    output: &mut [i32],
) -> Result<usize, NativeError> {
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if radii_squared.len() != position_count {
        return Err(NativeError::InvalidInput);
    }

    if radii_squared
        .iter()
        .any(|radius_squared| *radius_squared < 0.0)
    {
        return Err(NativeError::InvalidInput);
    }

    if output.len() < position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    if position_count >= PARALLEL_THRESHOLD {
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .zip(radii_squared.par_iter())
            .enumerate()
            .filter_map(|(index, (position, radius_squared))| {
                if squared_distance_at_f64_slice(origin_x, origin_y, origin_z, position)
                    <= *radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
    }

    let mut count = 0;
    for (index, radius_squared) in radii_squared.iter().enumerate() {
        if squared_distance_at_f64(origin_x, origin_y, origin_z, positions, index)
            <= *radius_squared
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
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if contains_aabb_position(min_x, min_y, min_z, max_x, max_y, max_z, position) {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
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
        let matches: Vec<i32> = boxes
            .par_chunks_exact(6)
            .enumerate()
            .filter_map(|(index, entity_box)| {
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

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
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
        let matches: Vec<i32> = positions
            .par_chunks_exact(3)
            .enumerate()
            .filter_map(|(index, position)| {
                if squared_distance_at_slice(origin_x, origin_y, origin_z, position)
                    <= radius_squared
                {
                    Some(index as i32)
                } else {
                    None
                }
            })
            .collect();

        output[..matches.len()].copy_from_slice(&matches);
        return Ok(matches.len());
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

/// Counts packed x/y/z triples within an inclusive squared radius.
pub fn count_within_radius(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    radius_squared: i64,
    positions: &[i32],
) -> Result<usize, NativeError> {
    if radius_squared < 0 {
        return Err(NativeError::InvalidInput);
    }

    if !positions.len().is_multiple_of(3) {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if position_count >= PARALLEL_THRESHOLD {
        return Ok(positions
            .par_chunks_exact(3)
            .filter(|position| {
                squared_distance_at_slice(origin_x, origin_y, origin_z, position) <= radius_squared
            })
            .count());
    }

    let mut count = 0;
    for index in 0..position_count {
        if squared_distance_at(origin_x, origin_y, origin_z, positions, index) <= radius_squared {
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

/// Sorts packed block positions by squared distance to the block low corner and writes the index order.
pub fn sort_by_block_distance(
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

    let mut indexed_distances: Vec<(i32, f64)> = if position_count >= PARALLEL_THRESHOLD {
        (0..position_count as i32)
            .into_par_iter()
            .map(|index| {
                (
                    index,
                    block_corner_distance_at(
                        origin_x,
                        origin_y,
                        origin_z,
                        positions,
                        index as usize,
                    ),
                )
            })
            .collect()
    } else {
        (0..position_count as i32)
            .map(|index| {
                (
                    index,
                    block_corner_distance_at(
                        origin_x,
                        origin_y,
                        origin_z,
                        positions,
                        index as usize,
                    ),
                )
            })
            .collect()
    };

    if indexed_distances.len() >= PARALLEL_THRESHOLD {
        indexed_distances.par_sort_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    } else {
        indexed_distances
            .sort_by(|left, right| compare_distance_order_f64(left.0, left.1, right.0, right.1));
    }

    for (output_index, (index, _distance)) in indexed_distances.iter().enumerate() {
        output[output_index] = *index;
    }
    Ok(())
}

/// Sorts packed f64 x/y/z triples by squared distance and writes the index order.
pub fn sort_by_distance_f64(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[f64],
    output: &mut [i32],
) -> Result<(), NativeError> {
    if positions.len() % 3 != 0 {
        return Err(NativeError::InvalidInput);
    }

    let position_count = positions.len() / 3;
    if output.len() != position_count {
        return Err(NativeError::OutputLengthMismatch);
    }

    let mut indexed_distances: Vec<(i32, f64)> = if position_count >= PARALLEL_THRESHOLD {
        (0..position_count as i32)
            .into_par_iter()
            .map(|index| {
                (
                    index,
                    squared_distance_at_f64(
                        origin_x,
                        origin_y,
                        origin_z,
                        positions,
                        index as usize,
                    ),
                )
            })
            .collect()
    } else {
        (0..position_count as i32)
            .map(|index| {
                (
                    index,
                    squared_distance_at_f64(
                        origin_x,
                        origin_y,
                        origin_z,
                        positions,
                        index as usize,
                    ),
                )
            })
            .collect()
    };

    if indexed_distances.len() >= PARALLEL_THRESHOLD {
        indexed_distances.par_sort_by(|left, right| {
            compare_distance_order_f64(left.0, left.1, right.0, right.1)
        });
    } else {
        indexed_distances
            .sort_by(|left, right| compare_distance_order_f64(left.0, left.1, right.0, right.1));
    }

    for (output_index, (index, _distance)) in indexed_distances.iter().enumerate() {
        output[output_index] = *index;
    }
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

fn block_center_distance_at(
    origin_x: f64,
    origin_y: f64,
    origin_z: f64,
    positions: &[i32],
    index: usize,
) -> f64 {
    let offset = index * 3;
    let dx = f64::from(positions[offset]) + 0.5 - origin_x;
    let dy = f64::from(positions[offset + 1]) + 0.5 - origin_y;
    let dz = f64::from(positions[offset + 2]) + 0.5 - origin_z;
    dx * dx + dy * dy + dz * dz
}

fn block_corner_distance_at(
    origin_x: i32,
    origin_y: i32,
    origin_z: i32,
    positions: &[i32],
    index: usize,
) -> f64 {
    let offset = index * 3;
    let dx = f64::from(positions[offset]) - f64::from(origin_x);
    let dy = f64::from(positions[offset + 1]) - f64::from(origin_y);
    let dz = f64::from(positions[offset + 2]) - f64::from(origin_z);
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

fn nearest_block_center_pair(
    left: (usize, f64),
    right: (usize, f64),
    positions: &[i32],
) -> (usize, f64) {
    if right.1 < left.1 || (right.1 == left.1 && compare_block_pos(positions, left.0, right.0) < 0)
    {
        right
    } else {
        left
    }
}

fn compare_block_pos(positions: &[i32], left_index: usize, right_index: usize) -> i32 {
    let left_offset = left_index * 3;
    let right_offset = right_index * 3;
    let left_y = positions[left_offset + 1];
    let right_y = positions[right_offset + 1];
    if left_y != right_y {
        return left_y.wrapping_sub(right_y);
    }

    let left_z = positions[left_offset + 2];
    let right_z = positions[right_offset + 2];
    if left_z != right_z {
        return left_z.wrapping_sub(right_z);
    }

    positions[left_offset].wrapping_sub(positions[right_offset])
}

fn compare_distance_order_f64(
    left_index: i32,
    left_distance: f64,
    right_index: i32,
    right_distance: f64,
) -> Ordering {
    let distance_order = compare_java_double(left_distance, right_distance);
    if distance_order == Ordering::Equal {
        left_index.cmp(&right_index)
    } else {
        distance_order
    }
}

fn compare_java_double(left: f64, right: f64) -> Ordering {
    if left < right {
        Ordering::Less
    } else if left > right {
        Ordering::Greater
    } else if left.is_nan() && !right.is_nan() {
        Ordering::Greater
    } else if !left.is_nan() && right.is_nan() {
        Ordering::Less
    } else {
        Ordering::Equal
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
    fn potential_energy_change_should_match_reference_values() {
        let positions = [3, 0, 4, 0, 0, 2, -6, 0, 8];
        let charges = [10.0, -4.0, 2.5];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, -3.0).unwrap();
        assert_eq!(result, -0.75);
    }

    #[test]
    fn potential_energy_change_should_return_infinity_at_same_position() {
        let positions = [0, 0, 0];
        let charges = [7.0];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, 2.0).unwrap();
        assert_eq!(result, f64::INFINITY);
    }

    #[test]
    fn potential_energy_change_should_skip_validation_for_zero_multiplier() {
        let positions = [1, 2];
        let charges = [];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, -0.0).unwrap();
        assert_eq!(result, 0.0);
        assert_eq!(result.to_bits(), 0.0f64.to_bits());
    }

    #[test]
    fn potential_energy_change_should_reject_wrong_charge_count() {
        let positions = [1, 2, 3, 4, 5, 6];
        let charges = [1.0];
        let result = potential_energy_change(0, 0, 0, &positions, &charges, 1.0);
        assert_eq!(result, Err(NativeError::InvalidInput));
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
    fn has_any_within_radius_f64_exclusive_should_reject_radius_boundary() {
        let positions = [2.0, 0.0, 0.0];
        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert!(!has_match);
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_accept_inner_position() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0];
        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert!(has_match);
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_accept_unbounded_positions() {
        let positions = [9.0, 0.0, 0.0];
        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, -1.0, &positions).unwrap();
        assert!(has_match);
    }

    #[test]
    fn has_any_within_radius_f64_exclusive_should_match_parallel_reference() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();

        let has_match =
            has_any_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions).unwrap();
        assert!(has_match);
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
    fn find_nearest_block_center_index_should_match_reference_index() {
        let positions = [0, 0, 0, 3, 0, 0, -1, 0, 0];
        let nearest = find_nearest_block_center_index(0.5, 0.5, 0.5, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_block_center_index_should_use_vanilla_tie_order() {
        let positions = [1, 0, 0, -1, 0, 0, 0, 1, 0, 0, 1, 1];
        let nearest = find_nearest_block_center_index(0.5, 0.5, 0.5, &positions).unwrap();
        assert_eq!(nearest, Some(2));
    }

    #[test]
    fn find_nearest_block_center_index_should_match_parallel_reference_index() {
        let positions: Vec<i32> = (0..5000).flat_map(|index| [0, 4999 - index, 0]).collect();

        let nearest = find_nearest_block_center_index(0.5, 0.5, 0.5, &positions).unwrap();
        assert_eq!(nearest, Some(4999));
    }

    #[test]
    fn find_nearest_block_corner_index_should_match_reference_index() {
        let positions = [3, 0, 0, 0, 0, 0, -1, 0, 0];
        let nearest = find_nearest_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(1));
    }

    #[test]
    fn find_nearest_block_corner_index_should_preserve_tie_order() {
        let positions = [1, 0, 0, -1, 0, 0, 0, 2, 0];
        let nearest = find_nearest_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(0));
    }

    #[test]
    fn find_nearest_block_corner_index_should_match_parallel_reference_index() {
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();

        let nearest = find_nearest_block_corner_index(0, 0, 0, &positions).unwrap();
        assert_eq!(nearest, Some(4999));
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_include_radius_boundary() {
        let positions = [3, 0, 0, 2, 0, 0, 5, 0, 0];
        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, Some(1));
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_preserve_tie_order() {
        let positions = [2, 0, 0, -2, 0, 0, 1, 0, 0];
        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, Some(2));
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_reject_out_of_radius_positions() {
        let positions = [3, 0, 0, 4, 0, 0];
        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 4, &positions).unwrap();
        assert_eq!(nearest, None);
    }

    #[test]
    fn find_nearest_block_corner_index_within_radius_should_match_parallel_reference_index() {
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();

        let nearest =
            find_nearest_block_corner_index_within_radius(0, 0, 0, 1024, &positions).unwrap();
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
    fn filter_within_radius_f64_exclusive_should_reject_radius_boundary() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0];
        let mut output = [0; 2];
        let count = filter_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions, &mut output)
            .unwrap();
        assert_eq!(count, 1);
        assert_eq!(&output[..count], &[1]);
    }

    #[test]
    fn filter_within_radius_f64_exclusive_should_match_parallel_reference_indices() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count =
            filter_within_radius_f64_exclusive(0.0, 0.0, 0.0, 1024.0, &positions, &mut output)
                .unwrap();
        assert_eq!(count, 32);
        assert_eq!(&output[..count], &(4968..5000).collect::<Vec<_>>()[..]);
    }

    #[test]
    fn sort_within_radius_f64_exclusive_should_filter_and_sort_by_distance() {
        let positions = [2.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0];
        let mut output = [0; 3];
        let count =
            sort_within_radius_f64_exclusive(0.0, 0.0, 0.0, 4.0, &positions, &mut output).unwrap();
        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[1, 2]);
    }

    #[test]
    fn sort_within_radius_f64_exclusive_should_match_parallel_reference_order() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        let count =
            sort_within_radius_f64_exclusive(0.0, 0.0, 0.0, 1024.0, &positions, &mut output)
                .unwrap();
        assert_eq!(count, 32);
        assert_eq!(
            &output[..count],
            &(4968..5000).rev().collect::<Vec<_>>()[..]
        );
    }

    #[test]
    fn filter_within_radii_f64_should_match_reference_indices() {
        let positions = [
            0.0, 8.0, 0.0, 10.0, 0.0, 0.0, 12.0, 0.0, 0.0, 15.1, 0.0, 0.0,
        ];
        let radii_squared = [64.0, 64.0, 144.0, 225.0];
        let mut output = [0; 4];
        let count = filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output)
            .unwrap();

        assert_eq!(count, 2);
        assert_eq!(&output[..count], &[0, 2]);
    }

    #[test]
    fn filter_within_radii_f64_should_match_parallel_reference_indices() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let radii_squared: Vec<f64> = (0..5000)
            .map(|index| {
                let x = (4999 - index) as f64;
                if index % 1000 == 0 || index >= 4997 {
                    x * x
                } else {
                    0.25
                }
            })
            .collect();
        let mut output = vec![0; 5000];

        let count = filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output)
            .unwrap();
        assert_eq!(count, 8);
        assert_eq!(
            &output[..count],
            &[0, 1000, 2000, 3000, 4000, 4997, 4998, 4999]
        );
    }

    #[test]
    fn filter_within_radii_f64_should_reject_negative_radius() {
        let positions = [0.0, 0.0, 0.0];
        let radii_squared = [-1.0];
        let mut output = [0; 1];

        let result =
            filter_within_radii_f64(0.0, 0.0, 0.0, &positions, &radii_squared, &mut output);
        assert_eq!(result, Err(NativeError::InvalidInput));
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
    fn count_within_radius_should_match_reference_count() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let count = count_within_radius(0, 64, 0, 40, &positions).unwrap();
        assert_eq!(count, 2);
    }

    #[test]
    fn count_within_radius_should_match_parallel_reference_count() {
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();

        let count = count_within_radius(0, 0, 0, 1024, &positions).unwrap();
        assert_eq!(count, 33);
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

    #[test]
    fn sort_by_block_distance_should_match_reference_order() {
        let positions = [0, 64, 0, 3, 68, 4, -1, 63, -2];
        let mut output = [0; 3];
        sort_by_block_distance(0, 64, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 2, 1]);
    }

    #[test]
    fn sort_by_block_distance_should_preserve_tie_order() {
        let positions = [1, 0, 0, -1, 0, 0, 2, 0, 0];
        let mut output = [0; 3];
        sort_by_block_distance(0, 0, 0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 1, 2]);
    }

    #[test]
    fn sort_by_block_distance_should_match_parallel_reference_order() {
        let positions: Vec<i32> = (0..5000)
            .flat_map(|index| [(4999 - index) as i32, 0, 0])
            .collect();
        let mut output = vec![0; 5000];

        sort_by_block_distance(0, 0, 0, &positions, &mut output).unwrap();
        let expected: Vec<i32> = (0..5000).rev().map(|index| index as i32).collect();
        assert_eq!(output, expected);
    }

    #[test]
    fn sort_by_distance_f64_should_match_reference_order() {
        let positions = [0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0];
        let mut output = [0; 3];
        sort_by_distance_f64(0.0, 64.0, 0.0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 2, 1]);
    }

    #[test]
    fn sort_by_distance_f64_should_preserve_tie_order() {
        let positions = [1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 2.0, 0.0, 0.0];
        let mut output = [0; 3];
        sort_by_distance_f64(0.0, 0.0, 0.0, &positions, &mut output).unwrap();
        assert_eq!(output, [0, 1, 2]);
    }

    #[test]
    fn sort_by_distance_f64_should_match_parallel_reference_order() {
        let positions: Vec<f64> = (0..5000)
            .flat_map(|index| [(4999 - index) as f64, 0.0, 0.0])
            .collect();
        let mut output = vec![0; 5000];

        sort_by_distance_f64(0.0, 0.0, 0.0, &positions, &mut output).unwrap();

        let expected: Vec<i32> = (0..5000).rev().map(|index| index as i32).collect();
        assert_eq!(output, expected);
    }
}
