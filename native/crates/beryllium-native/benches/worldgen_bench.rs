use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use beryllium_native::noise::perlin::PerlinNoise;
use beryllium_native::noise::simplex::SimplexNoise;
use beryllium_native::noise::NoiseGenerator;
use beryllium_native::octave_noise::{OctaveNoise, batch_sample_octave_noise_3d};

fn bench_single_noise_sample(c: &mut Criterion) {
    let perlin = PerlinNoise::new(12345);
    let simplex = SimplexNoise::new(12345);

    c.bench_function("perlin_single_sample", |b| {
        b.iter(|| {
            perlin.sample_3d(black_box(1.0), black_box(2.0), black_box(3.0))
        })
    });

    c.bench_function("simplex_single_sample", |b| {
        b.iter(|| {
            simplex.sample_3d(black_box(1.0), black_box(2.0), black_box(3.0))
        })
    });
}

fn bench_batch_noise_sampling(c: &mut Criterion) {
    let mut group = c.benchmark_group("batch_noise_sampling");
    
    for size in [100, 1000, 10000].iter() {
        let mut positions = Vec::with_capacity(size * 3);
        for i in 0..*size {
            positions.push(i as f64 * 0.1);
            positions.push(i as f64 * 0.2);
            positions.push(i as f64 * 0.3);
        }
        let mut output = vec![0.0; *size];

        let perlin = PerlinNoise::new(12345);
        group.bench_with_input(BenchmarkId::new("perlin", size), size, |b, _| {
            b.iter(|| {
                for i in 0..*size {
                    output[i] = perlin.sample_3d(
                        positions[i * 3],
                        positions[i * 3 + 1],
                        positions[i * 3 + 2],
                    );
                }
            })
        });
    }
    
    group.finish();
}

fn bench_octave_noise(c: &mut Criterion) {
    let octaves = vec![
        PerlinNoise::new(12345),
        PerlinNoise::new(23456),
        PerlinNoise::new(34567),
        PerlinNoise::new(45678),
    ];
    let octave_noise = OctaveNoise::new(octaves, -2);

    c.bench_function("octave_noise_single", |b| {
        b.iter(|| {
            octave_noise.sample_3d(black_box(1.0), black_box(2.0), black_box(3.0))
        })
    });

    let size = 10000;
    let mut positions = Vec::with_capacity(size * 3);
    for i in 0..size {
        positions.push(i as f64 * 0.1);
        positions.push(i as f64 * 0.2);
        positions.push(i as f64 * 0.3);
    }
    let mut output = vec![0.0; size];

    c.bench_function("octave_noise_batch_10k", |b| {
        b.iter(|| {
            batch_sample_octave_noise_3d(&octave_noise, &positions, &mut output).unwrap()
        })
    });
}

fn bench_biome_mixing(c: &mut Criterion) {
    use beryllium_native::biome::batch_compute_biome_weights_3d;
    use beryllium_native::biome::BiomeWeight;

    let sample_count = 1000;
    let mut sample_positions = Vec::with_capacity(sample_count * 3);
    for i in 0..sample_count {
        sample_positions.push(i as f64 * 10.0);
        sample_positions.push(64.0);
        sample_positions.push(i as f64 * 10.0);
    }

    let biome_centers = vec![
        0.0, 64.0, 0.0,
        100.0, 64.0, 100.0,
        -100.0, 64.0, 100.0,
        100.0, 64.0, -100.0,
        -100.0, 64.0, -100.0,
    ];

    let max_biomes = 4;
    let mut output = vec![BiomeWeight { biome_index: -1, weight: 0.0 }; sample_count * max_biomes];

    c.bench_function("biome_mixing_1k_samples", |b| {
        b.iter(|| {
            batch_compute_biome_weights_3d(
                &sample_positions,
                &biome_centers,
                200.0,
                &mut output,
                max_biomes,
            ).unwrap()
        })
    });
}

criterion_group!(
    benches,
    bench_single_noise_sample,
    bench_batch_noise_sampling,
    bench_octave_noise,
    bench_biome_mixing
);
criterion_main!(benches);
