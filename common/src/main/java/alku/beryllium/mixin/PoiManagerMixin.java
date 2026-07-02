package alku.beryllium.mixin;

import alku.beryllium.compute.BlockDistanceSearch;
import alku.beryllium.compute.BlockDistanceSort;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin {
    @Shadow
    public abstract Stream<PoiRecord> getInSquare(
        Predicate<Holder<PoiType>> typePredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    );

    /**
     * @reason Batch POI radius filtering before applying the vanilla position predicate.
     * @author YMRwithNoworry
     */
    @Overwrite
    public Stream<BlockPos> findAll(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> positionPredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    ) {
        List<PoiRecord> records = new ArrayList<>();
        this.getInSquare(typePredicate, origin, radius, occupancy).forEach(records::add);
        return BlockDistanceSearch.filterByDistanceWithinInclusiveRadius(
            records,
            origin,
            radius,
            PoiRecord::getPos,
            record -> positionPredicate.test(record.getPos())
        ).stream().map(PoiRecord::getPos);
    }

    /**
     * @reason Batch typed POI radius filtering before applying the vanilla position predicate.
     * @author YMRwithNoworry
     */
    @Overwrite
    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> positionPredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    ) {
        List<PoiRecord> records = new ArrayList<>();
        this.getInSquare(typePredicate, origin, radius, occupancy).forEach(records::add);
        return BlockDistanceSearch.filterByDistanceWithinInclusiveRadius(
            records,
            origin,
            radius,
            PoiRecord::getPos,
            record -> positionPredicate.test(record.getPos())
        ).stream().map(record -> Pair.of(record.getPoiType(), record.getPos()));
    }

    /**
     * @reason Batch POI block-distance ordering through the native block sort kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> positionPredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    ) {
        List<Pair<Holder<PoiType>, BlockPos>> pointsOfInterest = new ArrayList<>();
        this.findAllWithType(typePredicate, positionPredicate, origin, radius, occupancy).forEach(pointsOfInterest::add);
        BlockDistanceSort.sortByDistance(pointsOfInterest, origin, Pair::getSecond);
        return pointsOfInterest.stream();
    }

    /**
     * @reason Batch POI closest-position selection through the native block nearest kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    public Optional<BlockPos> findClosest(
        Predicate<Holder<PoiType>> typePredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    ) {
        List<PoiRecord> records = new ArrayList<>();
        this.getInSquare(typePredicate, origin, radius, occupancy).forEach(records::add);
        PoiRecord nearestRecord = BlockDistanceSearch.findNearestByDistanceWithinInclusiveRadius(
            records,
            origin,
            radius,
            PoiRecord::getPos
        );
        return nearestRecord == null ? Optional.empty() : Optional.of(nearestRecord.getPos());
    }

    /**
     * @reason Batch POI closest-record selection through the native block nearest kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(
        Predicate<Holder<PoiType>> typePredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    ) {
        List<PoiRecord> records = new ArrayList<>();
        this.getInSquare(typePredicate, origin, radius, occupancy).forEach(records::add);
        PoiRecord nearestRecord = BlockDistanceSearch.findNearestByDistanceWithinInclusiveRadius(
            records,
            origin,
            radius,
            PoiRecord::getPos
        );
        return nearestRecord == null ? Optional.empty() : Optional.of(Pair.of(nearestRecord.getPoiType(), nearestRecord.getPos()));
    }

    /**
     * @reason Batch filtered POI closest-position selection through the native block nearest kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    public Optional<BlockPos> findClosest(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> positionPredicate,
        BlockPos origin,
        int radius,
        PoiManager.Occupancy occupancy
    ) {
        List<PoiRecord> records = new ArrayList<>();
        this.getInSquare(typePredicate, origin, radius, occupancy).forEach(records::add);
        return Optional.ofNullable(BlockDistanceSearch.findNearestPositionByDistanceWithinInclusiveRadius(
            records,
            origin,
            radius,
            PoiRecord::getPos,
            positionPredicate
        ));
    }
}
