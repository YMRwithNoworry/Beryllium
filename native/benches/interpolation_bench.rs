use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion, Throughput};
use beryllium_native::kernel::interpolate_density_cells;

fn generate_test_corners(interpolator_count: usize) -> Vec<f64> {
    (0..interpolator_count)
        .flat_map(|i| {
            let base = (i as f64) * 0.1;
            [
                base - 1.25,
                base + 3.5,
                base + 7.75,
                base - 9.0,
                base + 0.125,
                base - 4.25,
                base + 16.0,
                base + 2.0,
            ]
        })
        .collect()
}

fn bench_interpolation_single(c: &mut Criterion) {
    let mut group = c.benchmark_group("interpolation_single");

    for &cell_size in &[4, 8] {
        let corners = generate_test_corners(1);
        let cell_volume = cell_size * cell_size * cell_size;
        let mut output = vec![0.0; cell_volume];

        group.throughput(Throughput::Elements(1));
        group.bench_with_input(
            BenchmarkId::new("cell_size", cell_size),
            &cell_size,
            |b, &size| {
                b.iter(|| {
                    interpolate_density_cells(
                        black_box(&corners),
                        black_box(size),
                        black_box(size),
                        black_box(&mut output),
                    )
                    .unwrap();
                });
            },
        );
    }
    group.finish();
}

fn bench_interpolation_batch(c: &mut Criterion) {
    let mut group = c.benchmark_group("interpolation_batch");

    let cell_width = 4;
    let cell_height = 8;
    let cell_volume = cell_width * cell_width * cell_height;

    for &count in &[4, 16, 64, 256, 1024] {
        let corners = generate_test_corners(count);
        let mut output = vec![0.0; count * cell_volume];

        group.throughput(Throughput::Elements(count as u64));
        group.bench_with_input(
            BenchmarkId::new("interpolators", count),
            &count,
            |b, &_count| {
                b.iter(|| {
                    interpolate_density_cells(
                        black_box(&corners),
                        black_box(cell_width),
                        black_box(cell_height),
                        black_box(&mut output),
                    )
                    .unwrap();
                });
            },
        );
    }
    group.finish();
}

fn bench_interpolation_slab(c: &mut Criterion) {
    let mut group = c.benchmark_group("interpolation_slab");

    // 模拟真实的 Minecraft NoiseChunk slab：
    // 通常有 3-5 个 interpolators，cellCountY=32，cellCountXZ=4
    let interpolator_count = 4;
    let cell_count_y = 32;
    let cell_count_xz = 4;
    let total_cells = interpolator_count * cell_count_y * cell_count_xz;

    let cell_width = 4;
    let cell_height = 8;
    let cell_volume = cell_width * cell_width * cell_height;

    let corners = generate_test_corners(total_cells);
    let mut output = vec![0.0; total_cells * cell_volume];

    group.throughput(Throughput::Elements(total_cells as u64));
    group.bench_function("minecraft_slab", |b| {
        b.iter(|| {
            interpolate_density_cells(
                black_box(&corners),
                black_box(cell_width),
                black_box(cell_height),
                black_box(&mut output),
            )
            .unwrap();
        });
    });
    group.finish();
}

criterion_group!(
    benches,
    bench_interpolation_single,
    bench_interpolation_batch,
    bench_interpolation_slab
);
criterion_main!(benches);
