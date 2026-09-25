package cn.lyxc.fantasytechnology.client.render;

/** Geometry only: no client or graphics initialization is needed to verify the projection. */
final class TesseractGeometry {
    static final int VERTEX_COUNT = 16;
    static final double MAX_RADIUS = 0.40;
    static final int[][] EDGES = new int[32][2];
    static final int[][] FACES = new int[24][4];
    static final int[][] CUBE_FACES = {
            { 0, 1, 3, 2 }, { 4, 6, 7, 5 }, { 0, 4, 5, 1 },
            { 2, 3, 7, 6 }, { 0, 2, 6, 4 }, { 1, 5, 7, 3 }
    };
    private static final double TAU = Math.PI * 2;
    private static final double EYE_W = 3.4;
    // A rotated (+/-1)^4 vertex has radius 2. The maximum perspective-projected radius is
    // 2*d/sqrt(d*d - 4), attained at w=4/d. This scale bounds every rotation, not just rest.
    private static final double SCALE = MAX_RADIUS * Math.sqrt(EYE_W * EYE_W - 4) / (2 * EYE_W);

    static {
        int edge = 0;
        int face = 0;
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            for (int axis = 0; axis < 4; axis++) {
                int bit = 1 << axis;
                if ((vertex & bit) != 0) {
                    continue;
                }
                EDGES[edge++] = new int[] { vertex, vertex | bit };
                for (int other = axis + 1; other < 4; other++) {
                    int otherBit = 1 << other;
                    if ((vertex & otherBit) == 0) {
                        FACES[face++] = new int[] { vertex, vertex | bit, vertex | bit | otherBit,
                                vertex | otherBit };
                    }
                }
            }
        }
    }

    private TesseractGeometry() {
    }

    /** Writes x/y/z and normalized 4D depth into caller-owned storage; no per-frame allocations. */
    static void project(double ticks, float[] out) {
        double xw = ticks * 0.007 % TAU;
        double zw = ticks * 0.0031 % TAU;
        double yz = ticks * 0.0023 % TAU;
        double yaw = (0.48 + ticks * 0.0011) % TAU;
        double cxw = Math.cos(xw), sxw = Math.sin(xw);
        double czw = Math.cos(zw), szw = Math.sin(zw);
        double cyz = Math.cos(yz), syz = Math.sin(yz);
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double tiltCos = Math.cos(0.22), tiltSin = Math.sin(0.22);

        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            double x = (vertex & 1) == 0 ? -1 : 1;
            double y = (vertex & 2) == 0 ? -1 : 1;
            double z = (vertex & 4) == 0 ? -1 : 1;
            double w = (vertex & 8) == 0 ? -1 : 1;

            double rotatedX = x * cxw - w * sxw;
            w = x * sxw + w * cxw;
            x = rotatedX;
            double rotatedZ = z * czw - w * szw;
            w = z * szw + w * czw;
            z = rotatedZ;
            double rotatedY = y * cyz - z * syz;
            z = y * syz + z * cyz;
            y = rotatedY;

            // Perspective in W is what makes the inner and outer cubes exchange places.
            double perspective = SCALE * EYE_W / (EYE_W - w);
            rotatedX = x * cyaw + z * syaw;
            z = z * cyaw - x * syaw;
            x = rotatedX;
            rotatedY = y * tiltCos - z * tiltSin;
            z = y * tiltSin + z * tiltCos;
            y = rotatedY;

            int index = vertex * 4;
            out[index] = (float) (x * perspective);
            out[index + 1] = (float) (y * perspective);
            out[index + 2] = (float) (z * perspective);
            out[index + 3] = (float) ((w + 2) * 0.25);
        }
    }
}
