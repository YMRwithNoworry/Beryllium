use beryllium_native::noise::perlin::PerlinNoise;
use beryllium_native::noise::simplex::SimplexNoise;
use beryllium_native::noise::NoiseGenerator;
use std::time::Instant;

fn main() {
    println!("=== Beryllium 世界生成优化性能测试 ===\n");

    let perlin = PerlinNoise::new(12345);
    let simplex = SimplexNoise::new(12345);

    println!("测试 1: 单点噪声采样性能");
    println!("{}", "-".repeat(50));
    
    let iterations = 1_000_000;
    
    let start = Instant::now();
    let mut sum = 0.0;
    for i in 0..iterations {
        let x = (i as f64) * 0.1;
        let y = (i as f64) * 0.2;
        let z = (i as f64) * 0.3;
        sum += perlin.sample_3d(x, y, z);
    }
    let perlin_duration = start.elapsed();
    
    let start = Instant::now();
    let mut sum2 = 0.0;
    for i in 0..iterations {
        let x = (i as f64) * 0.1;
        let y = (i as f64) * 0.2;
        let z = (i as f64) * 0.3;
        sum2 += simplex.sample_3d(x, y, z);
    }
    let simplex_duration = start.elapsed();
    
    println!("Perlin Noise:  {} 次采样耗时 {:?}", iterations, perlin_duration);
    println!("               平均每次: {:.2} ns", perlin_duration.as_nanos() as f64 / iterations as f64);
    println!("Simplex Noise: {} 次采样耗时 {:?}", iterations, simplex_duration);
    println!("               平均每次: {:.2} ns", simplex_duration.as_nanos() as f64 / iterations as f64);
    println!("(sum={:.2}, sum2={:.2})", sum, sum2);
    
    println!("\n测试 2: 批量噪声采样性能");
    println!("{}", "-".repeat(50));
    
    use beryllium_native::noise::batch_sample_noise_3d;
    
    let sample_sizes = vec![1000, 10000, 100000];
    
    for &size in &sample_sizes {
        let mut positions = Vec::with_capacity(size * 3);
        for i in 0..size {
            positions.push(i as f64 * 0.1);
            positions.push(i as f64 * 0.2);
            positions.push(i as f64 * 0.3);
        }
        let mut output = vec![0.0; size];
        
        let start = Instant::now();
        batch_sample_noise_3d(&perlin, &positions, &mut output).unwrap();
        let duration = start.elapsed();
        
        println!("批量采样 {} 个点: {:?}", size, duration);
        println!("  平均每次: {:.2} ns", duration.as_nanos() as f64 / size as f64);
        println!("  吞吐量: {:.2} M samples/s", size as f64 / duration.as_secs_f64() / 1_000_000.0);
    }
    
    println!("\n测试 3: 生物群系混合性能");
    println!("{}", "-".repeat(50));
    
    use beryllium_native::biome::{batch_compute_biome_weights_3d, BiomeWeight};
    
    let sample_count = 10000;
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
    
    let start = Instant::now();
    batch_compute_biome_weights_3d(
        &sample_positions,
        &biome_centers,
        200.0,
        &mut output,
        max_biomes,
    ).unwrap();
    let duration = start.elapsed();
    
    println!("生物群系混合 {} 个样本点: {:?}", sample_count, duration);
    println!("  平均每次: {:.2} ns", duration.as_nanos() as f64 / sample_count as f64);
    println!("  吞吐量: {:.2} M samples/s", sample_count as f64 / duration.as_secs_f64() / 1_000_000.0);
    
    println!("\n=== 性能测试完成 ===");
    println!("\n关键指标:");
    println!("- Perlin 噪声采样: ~{:.0} ns/sample", perlin_duration.as_nanos() as f64 / iterations as f64);
    println!("- Simplex 噪声采样: ~{:.0} ns/sample", simplex_duration.as_nanos() as f64 / iterations as f64);
    println!("- 生物群系混合: ~{:.0} ns/sample", duration.as_nanos() as f64 / sample_count as f64);
}
