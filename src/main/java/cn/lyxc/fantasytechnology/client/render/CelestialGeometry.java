package cn.lyxc.fantasytechnology.client.render;

/// Geometry of the device access block's celestial display: a globe of nested shells, and two star rings
/// counter-rotating around it. {@code CelestialGeometryTest} holds the shape to what this comment promises.
///
/// Like {@link TesseractGeometry} this is arithmetic only - no client or graphics initialization is needed to verify
/// it - and a whole frame is written into caller-owned storage, so nothing is allocated while rendering.
///
/// The display turns inside the block: coordinates are blocks relative to the block's centre, and nothing reaches
/// past {@link #BLOCK_HALF_EXTENT}, so it all stays within the cage the block model leaves open. The globe and the
/// rings keep the proportions of the large body this began as, scaled down together by {@link #SCALE}.
///
/// A frame is {@link #TOTAL_QUADS} consecutive quads. A quad is four vertices, a vertex is four floats: x, y, z and
/// a tone in [0, 1]. The tone is the only colour channel - 0 is cold and dim, 1 is white hot, and the renderer maps
/// it through one gradient - and all four vertices of a quad carry the same tone.
final class CelestialGeometry {

    /// Star bands in one ring, and therefore one quad each: a ring's unit of "a star goes past".
    static final int RING_SEGMENTS = 32;
    /// Latitude and longitude steps of the globe, and how many nested shells make up its volume.
    private static final int GLOBE_LATITUDE_STEPS = 12;
    private static final int GLOBE_LONGITUDE_STEPS = 16;
    private static final int GLOBE_SHELLS = 3;
    /// The nucleus is the same sphere at a much coarser cut, since it is small on screen.
    private static final int NUCLEUS_LATITUDE_STEPS = 5;
    private static final int NUCLEUS_LONGITUDE_STEPS = 7;

    /// The display is scaled down to sit inside one block. It began as a large body - a globe ten blocks across, with
    /// the rings sweeping just clear of it so a star passes in front of the globe and then behind it - and every
    /// length here is that body divided by {@link #SCALE}, so the proportions are unchanged.
    static final float SCALE = 11.4f;
    /// Half the width of that body, before scaling.
    private static final float GLOBE_HALF_WIDTH = 5.0f;
    /// The rings swept a little wider than the globe, which is what makes a star pass in front of it and then behind.
    private static final float RING_HALF_WIDTH = 5.55f;
    static final float GLOBE_RADIUS = GLOBE_HALF_WIDTH / SCALE;
    static final float RING_RADIUS = RING_HALF_WIDTH / SCALE;
    static final float NUCLEUS_RADIUS = 0.62f / SCALE;

    /// The furthest any vertex reaches from the block's centre, over x, y and z. The rings define this.
    static final float MAX_REACH = RING_RADIUS;
    /// Half the block, which the display may not reach past: it has to turn inside the cage, not through it.
    static final float BLOCK_HALF_EXTENT = 0.5f;

    /// The globe and the nucleus are the same shape at two scales.
    private static final Sphere GLOBE = new Sphere(GLOBE_RADIUS, GLOBE_LATITUDE_STEPS, GLOBE_LONGITUDE_STEPS,
            GLOBE_SHELLS, 0.0135, 0.22, 0.0, 0.017, 0.02f, 0.30f);
    private static final Sphere NUCLEUS = new Sphere(NUCLEUS_RADIUS, NUCLEUS_LATITUDE_STEPS, NUCLEUS_LONGITUDE_STEPS,
            1, -0.0045, 0.10, 2.4, 0.031, 0.94f, 0.06f);

    /// Two rings and two rings' worth of sparkles; the spheres fill in the rest.
    private static final int RING_QUADS = 2 * RING_SEGMENTS;
    private static final int BEAD_QUADS = 2 * RING_SEGMENTS;

    /// Quad index at which each part of the frame begins, in the order {@link #project} writes them.
    static final int OUTER_RING_QUAD = 0;
    static final int INNER_RING_QUAD = RING_SEGMENTS;
    static final int OUTER_BEADS_QUAD = RING_QUADS;
    static final int INNER_BEADS_QUAD = RING_QUADS + RING_SEGMENTS;
    static final int GLOBE_QUAD = RING_QUADS + BEAD_QUADS;
    static final int NUCLEUS_QUAD = GLOBE_QUAD + GLOBE.quads();

    static final int TOTAL_QUADS = NUCLEUS_QUAD + NUCLEUS.quads();
    /// Floats one {@link #project} call writes: four vertices of four floats per quad.
    static final int STRIDE = TOTAL_QUADS * 16;

    /// Tilt of a ring against the horizon. Opposite signs, so the rings cross rather than sit on top of each other.
    private static final double RING_TILT = 0.26;
    /// The tilt wobbles by this much, slowly, so neither ring is ever quite at rest. Bounded by {@link #MAX_REACH}.
    private static final double TILT_WOBBLE = 0.05;
    /// Radians per tick a ring advances. Equal in size and opposite in sign, so the rings counter-rotate.
    private static final double OUTER_SPIN = 0.016;
    private static final double INNER_SPIN = -0.021;
    /// Radius of the inner ring, just inside the outer one.
    private static final double INNER_RING_RADIUS = RING_RADIUS * 0.92;
    /// How much of a ring's radius one band's inner edge keeps, and how much of a step one band spans. A step is
    /// exactly one band, so neighbouring bands share an edge and the ring is a closed hoop rather than a dashed one;
    /// its sparkle-quality comes from the beads riding its outer edge.
    private static final double BAND_INNER = 0.90;
    private static final double STAR_SPAN = 1.0;
    /// Half extent of the sparkle riding the outer edge of every band, in blocks: a fixed fraction of the ring it sits
    /// on, so it stays a bead rather than a lump at any scale.
    private static final double BEAD_RADIUS = RING_RADIUS * 0.026;

    private static final float OUTER_RING_TONE = 0.62f;
    private static final float INNER_RING_TONE = 0.86f;
    private static final float BEAD_TONE = 1.0f;

    private static final double TAU = Math.PI * 2;

    private CelestialGeometry() {
    }

    /// Writes one frame. The quads appear in this order, which nothing depends on: the display is blended additively,
    /// so the order they arrive in cannot change the result.
    /// <ol>
    /// <li>the outer star ring, then the counter-rotating inner one;</li>
    /// <li>a sparkle on the outer edge of every band of both rings;</li>
    /// <li>the globe, longitude by longitude and latitude by latitude, innermost shell first;</li>
    /// <li>the nucleus at the centre.</li>
    /// </ol>
    static void project(double ticks, float[] out) {
        // Each ring leans its own way and wobbles at its own rate, so the two never settle into one rigid figure.
        double outerTilt = RING_TILT + TILT_WOBBLE * Math.sin(ticks * 0.0051);
        double innerTilt = -RING_TILT + TILT_WOBBLE * Math.sin(ticks * 0.0043 + 1.7);
        double outerPhase = ticks * OUTER_SPIN;
        double innerPhase = ticks * INNER_SPIN;

        int index = OUTER_RING_QUAD * 16;
        index = ring(out, index, outerPhase, outerTilt, RING_RADIUS, OUTER_RING_TONE);
        index = ring(out, index, innerPhase, innerTilt, INNER_RING_RADIUS, INNER_RING_TONE);
        index = beads(out, index, outerPhase, outerTilt, RING_RADIUS);
        index = beads(out, index, innerPhase, innerTilt, INNER_RING_RADIUS);
        index = sphere(out, index, ticks, GLOBE);
        index = sphere(out, index, ticks, NUCLEUS);
        if (index != STRIDE) {
            throw new IllegalStateException("Celestial frame stride mismatch: " + index + " != " + STRIDE);
        }
    }

    /// One star ring: {@link #RING_SEGMENTS} bands laid end to end around a tilted circle. A band spans
    /// {@link #STAR_SPAN} of a step along the ring, from its radius inwards to {@link #BAND_INNER} of it.
    private static int ring(float[] out, int index, double phase, double tilt, double radius, float tone) {
        double cosTilt = Math.cos(tilt);
        double sinTilt = Math.sin(tilt);
        double innerRadius = radius * BAND_INNER;
        double step = TAU / RING_SEGMENTS;
        double span = step * STAR_SPAN;
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double leading = phase + segment * step;
            double trailing = leading + span;
            index = ringVertex(out, index, radius, leading, cosTilt, sinTilt, tone);
            index = ringVertex(out, index, radius, trailing, cosTilt, sinTilt, tone);
            index = ringVertex(out, index, innerRadius, trailing, cosTilt, sinTilt, tone);
            index = ringVertex(out, index, innerRadius, leading, cosTilt, sinTilt, tone);
        }
        return index;
    }

    /// A point at {@code radius} and {@code angle} around a ring, tilted about the X axis.
    private static int ringVertex(float[] out, int index, double radius, double angle, double cosTilt,
            double sinTilt, float tone) {
        return ringPoint(out, index, radius * Math.cos(angle), radius * Math.sin(angle), cosTilt, sinTilt, tone);
    }

    /// A raw point of a ring's own plane, tilted about the X axis.
    private static int ringPoint(float[] out, int index, double x, double z, double cosTilt, double sinTilt,
            float tone) {
        return vertex(out, index, x, z * sinTilt, z * cosTilt, tone);
    }

    /// A sparkle riding the outer edge of every band: a small square in the ring's own plane, centred on the band.
    /// It reads as a point of light from above and below, and as a bright bead seen edge on.
    private static int beads(float[] out, int index, double phase, double tilt, double radius) {
        double cosTilt = Math.cos(tilt);
        double sinTilt = Math.sin(tilt);
        double step = TAU / RING_SEGMENTS;
        double halfTangential = BEAD_RADIUS * 0.7;
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double angle = phase + (segment + STAR_SPAN * 0.5) * step;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // The ring's radial and tangential axes, at the sparkle's own scale.
            double radialX = cos * BEAD_RADIUS;
            double radialZ = sin * BEAD_RADIUS;
            double tangentialX = -sin * halfTangential;
            double tangentialZ = cos * halfTangential;
            double centerX = (radius - BEAD_RADIUS) * cos;
            double centerZ = (radius - BEAD_RADIUS) * sin;
            index = ringPoint(out, index, centerX + radialX, centerZ + radialZ, cosTilt, sinTilt, BEAD_TONE);
            index = ringPoint(out, index, centerX + tangentialX, centerZ + tangentialZ, cosTilt, sinTilt,
                    BEAD_TONE);
            index = ringPoint(out, index, centerX - radialX, centerZ - radialZ, cosTilt, sinTilt, BEAD_TONE);
            index = ringPoint(out, index, centerX - tangentialX, centerZ - tangentialZ, cosTilt, sinTilt,
                    BEAD_TONE);
        }
        return index;
    }

    /// One sphere of the display; the globe and the nucleus differ only in the numbers of {@link Sphere}.
    ///
    /// The shells are concentric and the display is blended additively, so a viewer looks through the near shell into
    /// the far ones and the middle of the sphere gathers the most light of all: a body that glows from the inside
    /// rather than a painted ball.
    ///
    /// The surface turns about its pole and the pole itself leans, so the lattice never settles into a fixed grid.
    /// The light does not turn with it - a lit side sweeps across the face while the surface underneath moves - which
    /// is what separates a lit body from a spinning texture.
    private static int sphere(float[] out, int index, double ticks, Sphere sphere) {
        // The pole leans about the Z axis; the surface also turns about the pole, which is the longitude offset.
        double lean = sphere.lean() * Math.sin(ticks * 0.0061 + sphere.phase());
        double cosLean = Math.cos(lean);
        double sinLean = Math.sin(lean);
        double spin = sphere.spin() * ticks;
        // A light a little above the horizon, so the terminator sweeps the face instead of cutting it in half.
        double lightAngle = sphere.lightSpin() * ticks;
        double lightX = Math.cos(lightAngle);
        double lightY = 0.55;
        double lightZ = Math.sin(lightAngle);

        double latitudeStep = Math.PI / sphere.latSteps();
        double longitudeStep = TAU / sphere.lonSteps();
        double halfLatitude = latitudeStep * 0.5;
        double halfLongitude = longitudeStep * 0.5;
        double innerRadius = sphere.innerRadius();
        // The outermost shell sits exactly on the sphere's radius, so the globe really is that many blocks across.
        double radiusStep = (sphere.radius() - innerRadius) / Math.max(1, sphere.shells() - 1);

        for (int latitude = 0; latitude < sphere.latSteps(); latitude++) {
            double theta = (latitude + 0.5) * latitudeStep;
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);
            for (int longitude = 0; longitude < sphere.lonSteps(); longitude++) {
                double phi = longitude * longitudeStep + spin;
                double sinPhi = Math.sin(phi);
                double cosPhi = Math.cos(phi);
                // The patch's own outward normal, leaned exactly as its vertices are, against the fixed light.
                double normalX = sinTheta * cosPhi;
                double normalY = cosTheta;
                double normalZ = sinTheta * sinPhi;
                double lit = 0.5 + 0.5 * ((normalX * cosLean - normalY * sinLean) * lightX
                        + (normalX * sinLean + normalY * cosLean) * lightY + normalZ * lightZ);
                float tone = (float) Math.min(1, Math.max(0, sphere.baseTone() + sphere.toneRange() * lit));

                // The four corners of this patch on a unit sphere; only their radius differs between shells.
                double upTheta = theta + halfLatitude;
                double downTheta = theta - halfLatitude;
                double leftPhi = phi + halfLongitude;
                double rightPhi = phi - halfLongitude;
                double sinUp = Math.sin(upTheta);
                double sinDown = Math.sin(downTheta);
                double upCosLeft = sinUp * Math.cos(leftPhi);
                double upSinLeft = sinUp * Math.sin(leftPhi);
                double upCosRight = sinUp * Math.cos(rightPhi);
                double upSinRight = sinUp * Math.sin(rightPhi);
                double downCosLeft = sinDown * Math.cos(leftPhi);
                double downSinLeft = sinDown * Math.sin(leftPhi);
                double downCosRight = sinDown * Math.cos(rightPhi);
                double downSinRight = sinDown * Math.sin(rightPhi);
                double upY = Math.cos(upTheta);
                double downY = Math.cos(downTheta);
                for (int shell = 0; shell < sphere.shells(); shell++) {
                    double radius = innerRadius + radiusStep * shell;
                    index = sphereVertex(out, index, upCosLeft, upY, upSinLeft, radius, cosLean, sinLean, tone);
                    index = sphereVertex(out, index, upCosRight, upY, upSinRight, radius, cosLean, sinLean, tone);
                    index = sphereVertex(out, index, downCosRight, downY, downSinRight, radius, cosLean, sinLean,
                            tone);
                    index = sphereVertex(out, index, downCosLeft, downY, downSinLeft, radius, cosLean, sinLean,
                            tone);
                }
            }
        }
        return index;
    }

    /// One vertex of a sphere patch: a direction on the unit sphere, scaled to the shell's radius, then leaned about
    /// the Z axis so the body's pole tips over instead of staying put.
    private static int sphereVertex(float[] out, int index, double x, double y, double z, double radius,
            double cosLean, double sinLean, float tone) {
        double scaledX = x * radius;
        double scaledY = y * radius;
        return vertex(out, index, scaledX * cosLean - scaledY * sinLean,
                scaledX * sinLean + scaledY * cosLean, z * radius, tone);
    }

    private static int vertex(float[] out, int index, double x, double y, double z, float tone) {
        out[index] = (float) x;
        out[index + 1] = (float) y;
        out[index + 2] = (float) z;
        out[index + 3] = tone;
        return index + 4;
    }

    /// The shape of one sphere: how big it is, how finely it is cut, how it moves and how it is lit.
    ///
    /// @param radius    distance from the centre to the outermost shell
    /// @param latSteps  divisions from pole to pole
    /// @param lonSteps  divisions around the pole
    /// @param shells    concentric surfaces stacked from {@link #innerRadius()} outwards to {@code radius}
    /// @param spin      radians per tick the surface turns about its pole
    /// @param lean      radians the pole itself tips over
    /// @param phase     offset into the lean cycle, so two spheres do not lean together
    /// @param lightSpin radians per tick the lit side sweeps around the sphere
    /// @param baseTone  tone of a patch facing away from the light
    /// @param toneRange tone added to a patch facing into it
    private record Sphere(double radius, int latSteps, int lonSteps, int shells, double spin, double lean,
            double phase, double lightSpin, float baseTone, float toneRange) {

        /// Radius of the innermost shell, as a fraction of {@link #radius}.
        private static final double INNER_SCALE = 0.55;

        /// The innermost shell. A single-shell sphere keeps only one surface, so it is drawn at {@link #radius}
        /// rather than at a fraction of it - otherwise the shell it does have would sit inside its own radius, and
        /// {@link #radius} would mean nothing for it.
        double innerRadius() {
            return shells > 1 ? radius * INNER_SCALE : radius;
        }

        int quads() {
            return latSteps * lonSteps * shells;
        }
    }
}