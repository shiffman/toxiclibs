package toxi.geom.mesh;

import toxi.geom.IsectData3D;
import toxi.geom.Ray3D;
import toxi.geom.Triangle;
import toxi.geom.TriangleIntersector;
import toxi.geom.Vec3D;
import toxi.math.Interpolation2D;
import toxi.math.MathUtils;

/**
 * Implementation of a 2D grid based heightfield with basic intersection
 * features and conversion to {@link TriangleMesh}. The terrain is always
 * located in the XZ plane with the positive Y axis as up vector.
 */
public class Terrain {

    protected float[] elevation;
    protected Vec3D[] vertices;

    protected int width;
    protected int depth;
    protected float scale;

    /**
     * Constructs a new and initially flat terrain of the given size in the XZ
     * plane, centred around the world origin.
     * 
     * @param width
     * @param depth
     * @param scale
     */
    public Terrain(int width, int depth, float scale) {
        this.width = width;
        this.depth = depth;
        this.scale = scale;
        this.elevation = new float[width * depth];
        this.vertices = new Vec3D[elevation.length];
        Vec3D offset = new Vec3D(width, 0, depth).scaleSelf(0.5f);
        for (int z = 0, i = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                vertices[i++] =
                        new Vec3D(x, 0, z).subSelf(offset).scaleSelf(scale);
            }
        }
    }

    /**
     * @return number of grid cells along the Z axis.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @param x
     * @param z
     * @return the elevation at grid point
     */
    public float getHeightAtCell(int x, int z) {
        return elevation[getIndex(x, z)];
    }

    /**
     * Computes the elevation of the terrain at the given 2D world coordinate
     * (based on current terrain scale).
     * 
     * @param x
     *            scaled world coord x
     * @param z
     *            scaled world coord z
     * @return interpolated elevation
     */
    public float getHeightAtPoint(float x, float z) {
        float xx = x / scale + width * 0.5f;
        float zz = z / scale + depth * 0.5f;
        float y = 0;
        if (xx >= 0 && xx < width && zz >= 0 && zz < depth) {
            int x2 = (int) MathUtils.min(xx + 1, width - 1);
            int z2 = (int) MathUtils.min(zz + 1, depth - 1);
            float a = getHeightAtCell((int) xx, (int) zz);
            float b = getHeightAtCell(x2, (int) zz);
            float c = getHeightAtCell((int) xx, z2);
            float d = getHeightAtCell(x2, z2);
            y =
                    Interpolation2D.bilinear(xx, zz, (int) xx, (int) zz, x2,
                            z2, a, b, c, d);
        }
        return y;
    }

    /**
     * Computes the array index for the given cell coords & checks if they're in
     * bounds. If not an {@link IndexOutOfBoundsException} is thrown.
     * 
     * @param x
     * @param z
     * @return array index
     */
    protected int getIndex(int x, int z) {
        int idx = z * width + x;
        if (idx < 0 || idx > elevation.length) {
            throw new IndexOutOfBoundsException(
                    "the given terrain cell is invalid: " + x + ";" + z);
        }
        return idx;
    }

    protected Vec3D getVertexAtCell(int x, int z) {
        return vertices[getIndex(x, z)];
    }

    /**
     * @return number of grid cells along the X axis.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Computes the 3D position (with elevation) and normal vector at the given
     * 2D location in the terrain. The position is in scaled world coordinates
     * based on the given terrain scale. The returned data is encapsulated in a
     * {@link toxi.geom.IsectData3D} instance.
     * 
     * @param x
     * @param z
     * @return intersection data parcel
     */
    public IsectData3D intersectAtPoint(float x, float z) {
        float xx = x / scale + width * 0.5f;
        float zz = z / scale + depth * 0.5f;
        IsectData3D isec = new IsectData3D();
        if (xx >= 0 && xx < width && zz >= 0 && zz < depth) {
            int x2 = (int) MathUtils.min(xx + 1, width - 1);
            int z2 = (int) MathUtils.min(zz + 1, depth - 1);
            Vec3D a = getVertexAtCell((int) xx, (int) zz);
            Vec3D b = getVertexAtCell(x2, (int) zz);
            Vec3D c = getVertexAtCell(x2, z2);
            Vec3D d = getVertexAtCell((int) xx, z2);
            Ray3D r = new Ray3D(new Vec3D(x, 10000, z), new Vec3D(0, -1, 0));
            TriangleIntersector i =
                    new TriangleIntersector(new Triangle(a, b, d));
            if (i.intersectsRay(r)) {
                isec = i.getIntersectionData();
            } else {
                i.setTriangle(new Triangle(b, c, d));
                i.intersectsRay(r);
                isec = i.getIntersectionData();
            }
        }
        return isec;
    }

    /**
     * Sets the elevation of all cells to those of the given array values.
     * 
     * @param elevation
     *            array of height values
     * @return itself
     */
    public Terrain setElevation(float[] elevation) {
        if (this.elevation.length == elevation.length) {
            for (int i = 0; i < elevation.length; i++) {
                this.vertices[i].y = this.elevation[i] = elevation[i];
            }
        } else {
            throw new IllegalArgumentException(
                    "the given elevation array size does not match terrain");
        }
        return this;
    }

    /**
     * Sets the elevation for a single given grid cell.
     * 
     * @param x
     * @param z
     * @param h
     *            new elevation value
     * @return itself
     */
    public Terrain setHeightAtCell(int x, int z, float h) {
        int index = getIndex(x, z);
        elevation[index] = h;
        vertices[index].y = h;
        return this;
    }

    /**
     * Creates a {@link TriangleMesh} instance of the terrain surface.
     * 
     * @return mesh
     */
    public TriangleMesh toMesh() {
        TriangleMesh mesh =
                new TriangleMesh("terrain", vertices.length,
                        vertices.length * 2);
        for (int z = 1; z < depth; z++) {
            for (int x = 1; x < width; x++) {
                mesh.addFace(vertices[(z - 1) * width + (x - 1)],
                        vertices[(z - 1) * width + x], vertices[z * width
                                + (x - 1)]);
                mesh.addFace(vertices[(z - 1) * width + x], vertices[z * width
                        + x], vertices[z * width + (x - 1)]);
            }
        }
        return mesh;
    }

    /**
     * Creates a {@link TriangleMesh} instance of the terrain and constructs
     * side panels and a bottom plane to form a fully enclosed mesh volume, e.g.
     * suitable for CNC fabrication or 3D printing. The bottom plane will be
     * created at the given ground level (can also be negative) and the sides
     * are extended downward to that level too.
     * 
     * @param groundLevel
     * @return mesh
     */
    public TriangleMesh toMesh(float groundLevel) {
        TriangleMesh mesh = toMesh();
        Vec3D offset = new Vec3D(width, 0, depth).scaleSelf(0.5f);
        float minX = -offset.x * scale;
        float minZ = -offset.z * scale;
        float maxX = (width - 1 - offset.x) * scale;
        float maxZ = (depth - 1 - offset.z) * scale;
        for (int z = 1; z < depth; z++) {
            Vec3D a = new Vec3D(minX, groundLevel, (z - 1 - offset.z) * scale);
            Vec3D b = new Vec3D(minX, groundLevel, (z - offset.z) * scale);
            // left
            mesh.addFace(getVertexAtCell(0, z - 1), getVertexAtCell(0, z), a);
            mesh.addFace(getVertexAtCell(0, z), b, a);
            // right
            a.x = b.x = maxX;
            mesh.addFace(getVertexAtCell(width - 1, z), getVertexAtCell(
                    width - 1, z - 1), b);
            mesh.addFace(getVertexAtCell(width - 1, z - 1), a, b);
        }
        for (int x = 1; x < width; x++) {
            Vec3D a = new Vec3D((x - 1 - offset.x) * scale, groundLevel, minZ);
            Vec3D b = new Vec3D((x - offset.x) * scale, groundLevel, minZ);
            // back
            mesh.addFace(getVertexAtCell(x, 0), getVertexAtCell(x - 1, 0), b);
            mesh.addFace(getVertexAtCell(x - 1, 0), a, b);
            // front
            a.z = b.z = maxZ;
            mesh.addFace(getVertexAtCell(x - 1, depth - 1), getVertexAtCell(x,
                    depth - 1), a);
            mesh.addFace(getVertexAtCell(x, depth - 1), b, a);
        }
        // bottom plane
        mesh.addFace(new Vec3D(minX, groundLevel, minZ), new Vec3D(minX,
                groundLevel, maxZ), new Vec3D(maxX, groundLevel, minZ));
        mesh.addFace(new Vec3D(maxX, groundLevel, minZ), new Vec3D(minX,
                groundLevel, maxZ), new Vec3D(maxZ, groundLevel, maxZ));
        return mesh;
    }
}