package mekanism.api;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.dimension.DimensionType;

public class Chunk3D extends ChunkPos {

    public final DimensionType dimension;

    public Chunk3D(int x, int z, DimensionType dimension) {
        super(x, z);
        this.dimension = dimension;
    }

    public Chunk3D(Coord4D coord) {
        this(coord.x >> 4, coord.z >> 4, coord.dimension);
    }

    public Set<Chunk3D> expand(int chunkRadius) {
        Set<Chunk3D> ret = new HashSet<>();
        for (int i = x - chunkRadius; i <= x + chunkRadius; i++) {
            for (int j = z - chunkRadius; j <= z + chunkRadius; j++) {
                ret.add(new Chunk3D(i, j, dimension));
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        return "[Chunk3D: " + x + ", " + z + ", dim=" + dimension.getId() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Chunk3D && ((Chunk3D) obj).x == x && ((Chunk3D) obj).z == z && ((Chunk3D) obj).dimension == dimension;
    }

    @Override
    public int hashCode() {
        int code = 1;
        code = 31 * code + x;
        code = 31 * code + z;
        code = 31 * code + dimension.hashCode();
        return code;
    }
}
