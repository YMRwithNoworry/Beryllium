package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.SingleValuePalette;

import java.lang.reflect.Field;

/**
 * Packs one vanilla {@code PalettedContainer.Data} network payload through the Rust kernel.
 *
 * <p>The byte layout matches vanilla {@code Data.write} exactly: bits byte, palette varints,
 * varint-prefixed little-endian long array. Palette fields are read through cached reflection so
 * the packer works identically inside the mixin appliance and in plain-JVM verifiers. Any
 * deviation from vanilla preconditions (unknown palette implementation, uninitialized
 * single-value palette, unregistered value, native failure) returns {@code -1} so the caller
 * falls back to the original serializer.</p>
 */
public final class PalettedContainerPacker {
    private static final Field SINGLE_REGISTRY = field(SingleValuePalette.class, "registry");
    private static final Field SINGLE_VALUE = field(SingleValuePalette.class, "value");
    private static final Field LINEAR_REGISTRY = field(LinearPalette.class, "registry");
    private static final Field LINEAR_VALUES = field(LinearPalette.class, "values");
    private static final Field LINEAR_SIZE = field(LinearPalette.class, "size");
    private static final Field HASH_REGISTRY = field(HashMapPalette.class, "registry");
    private static final Field HASH_VALUES = field(HashMapPalette.class, "values");

    private PalettedContainerPacker() {
    }

    /**
     * @param output scratch buffer; must hold at least
     *               {@code 6 + 5 * paletteLength + 8 * raw.length} bytes
     * @return packed byte count, or {@code -1} when the payload must stay on the vanilla path
     */
    public static int pack(BitStorage storage, Palette<?> palette, long[] raw, byte[] output) {
        return pack(storage, palette, raw, output, null);
    }

    /**
     * @param paletteIdScratch reusable id buffer; may be null or smaller than needed
     */
    public static int pack(
        BitStorage storage,
        Palette<?> palette,
        long[] raw,
        byte[] output,
        int[] paletteIdScratch
    ) {
        if (SINGLE_VALUE == null) {
            return -1;
        }

        int paletteKind;
        int paletteLength;
        int[] paletteIds;
        if (palette instanceof GlobalPalette) {
            paletteKind = 0;
            paletteLength = 0;
            paletteIds = null;
        } else if (palette instanceof SingleValuePalette singleValue) {
            Object value;
            IdMap<?> registry;
            try {
                value = SINGLE_VALUE.get(singleValue);
                registry = (IdMap<?>) SINGLE_REGISTRY.get(singleValue);
            } catch (IllegalAccessException failure) {
                return -1;
            }
            int id = registryId(registry, value);
            if (id < 0) {
                return -1;
            }
            paletteIds = paletteIdScratch != null && paletteIdScratch.length >= 1
                ? paletteIdScratch
                : new int[1];
            paletteIds[0] = id;
            paletteKind = 1;
            paletteLength = 1;
        } else if (palette instanceof LinearPalette linear) {
            IdMap<?> registry;
            Object[] values;
            int size;
            try {
                registry = (IdMap<?>) LINEAR_REGISTRY.get(linear);
                values = (Object[]) LINEAR_VALUES.get(linear);
                size = LINEAR_SIZE.getInt(linear);
            } catch (IllegalAccessException failure) {
                return -1;
            }
            if (size < 0 || size > values.length) {
                return -1;
            }
            paletteIds = paletteIdScratch != null && paletteIdScratch.length >= size
                ? paletteIdScratch
                : new int[size];
            for (int index = 0; index < size; index++) {
                int id = registryId(registry, values[index]);
                if (id < 0) {
                    return -1;
                }
                paletteIds[index] = id;
            }
            paletteKind = 2;
            paletteLength = size;
        } else if (palette instanceof HashMapPalette hashMap) {
            IdMap<?> registry;
            CrudeIncrementalIntIdentityHashBiMap<?> values;
            try {
                registry = (IdMap<?>) HASH_REGISTRY.get(hashMap);
                values = (CrudeIncrementalIntIdentityHashBiMap<?>) HASH_VALUES.get(hashMap);
            } catch (IllegalAccessException failure) {
                return -1;
            }
            int size = palette.getSize();
            paletteIds = paletteIdScratch != null && paletteIdScratch.length >= size
                ? paletteIdScratch
                : new int[size];
            for (int index = 0; index < size; index++) {
                int id = registryId(registry, values.byId(index));
                if (id < 0) {
                    return -1;
                }
                paletteIds[index] = id;
            }
            paletteKind = 2;
            paletteLength = size;
        } else {
            return -1;
        }

        return NativeBridge.packPalettedContainer(
            storage.getBits(),
            paletteKind,
            paletteIds,
            paletteLength,
            raw,
            output
        );
    }

    public static int maximumPackedSize(int paletteLength, int longCount) {
        return 6 + 5 * Math.max(paletteLength, 1) + longCount * 8;
    }

    @SuppressWarnings("unchecked")
    private static int registryId(IdMap<?> registry, Object value) {
        if (registry == null || value == null) {
            return -1;
        }
        return ((IdMap<Object>) registry).getId(value);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            return null;
        }
    }
}
