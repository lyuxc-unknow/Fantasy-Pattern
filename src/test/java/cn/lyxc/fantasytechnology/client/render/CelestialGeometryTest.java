package cn.lyxc.fantasytechnology.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelestialGeometryTest {

    /// Long enough to cover a few full rotations, and far enough past 2^24 ticks to catch a loss of precision.
    private static final double[] EPOCHS = { 0, 137.5, 1L << 25, 1L << 40 };
    /// Slack for the exact-radius assertions, wide enough to absorb the rounding of one float.
    private static final double SLACK = 2.0e-3;

    @Test
    void everyFrameFillsItsBufferExactlyOnce() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        for (double epoch : EPOCHS) {
            for (int sample = 0; sample < 512; sample++) {
                // A sentinel after the buffer: a frame that overruns it is caught rather than silently accepted.
                float[] padded = new float[CelestialGeometry.STRIDE + 1];
                padded[CelestialGeometry.STRIDE] = 12345;
                CelestialGeometry.project(epoch + sample * 3.125, padded);
                assertEquals(12345, padded[CelestialGeometry.STRIDE], "The frame must not overrun its buffer");
                System.arraycopy(padded, 0, frame, 0, frame.length);
                for (float value : frame) {
                    assertTrue(Float.isFinite(value), "Geometry must stay finite at tick " + epoch);
                }
            }
        }
    }

    /// The display now turns inside the block, so nothing may leave the block's own half extent on any axis, and it
    /// has to stay clear of the cage the model draws around its corners and edges.
    @Test
    void theDisplayFitsInsideTheBlock() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        double worstReach = 0;
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (double epoch : EPOCHS) {
            // Fine enough to land on a star's outer tip and on the widest point of the globe.
            for (int sample = 0; sample < 4096; sample++) {
                CelestialGeometry.project(epoch + sample * 0.735, frame);
                for (int vertex = 0; vertex < CelestialGeometry.STRIDE; vertex += 4) {
                    double x = frame[vertex];
                    double y = frame[vertex + 1];
                    double z = frame[vertex + 2];
                    // x, y and z are local to the block's centre.
                    double reach = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
                    worstReach = Math.max(worstReach, reach);
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                    assertTrue(reach <= CelestialGeometry.BLOCK_HALF_EXTENT,
                            "Vertex " + vertex + " reached " + reach + " at tick " + epoch);
                }
            }
        }
        assertTrue(worstReach > CelestialGeometry.MAX_REACH * 0.9,
                "The rings should reach their full radius, but only reached " + worstReach);
        assertTrue(highest - lowest > CelestialGeometry.GLOBE_RADIUS * 1.95,
                "The globe should be a real body, not a disc: it spans only " + (highest - lowest));
    }

    /// The globe keeps the proportions of the ten-block body it began as: twice its radius across, on every axis.
    @Test
    void theGlobeKeepsItsProportionsOnEveryAxis() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double epoch : EPOCHS) {
            for (int sample = 0; sample < 512; sample++) {
                CelestialGeometry.project(epoch + sample * 1.875, frame);
                // Only the globe quads; the rings and the nucleus are deliberately smaller.
                for (int quad = CelestialGeometry.GLOBE_QUAD; quad < CelestialGeometry.NUCLEUS_QUAD; quad++) {
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int base = quad * 16 + vertex * 4;
                        double x = frame[base];
                        double y = frame[base + 1];
                        double z = frame[base + 2];
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                        assertTrue(Math.sqrt(x * x + y * y + z * z)
                                        <= CelestialGeometry.GLOBE_RADIUS + SLACK,
                                "A globe vertex escaped its own radius");
                    }
                }
            }
        }
        // A sphere of this radius spans twice it on every axis, and the sweep has to find all of that span.
        double diameter = CelestialGeometry.GLOBE_RADIUS * 2;
        for (double span : new double[] { maxX - minX, maxY - minY, maxZ - minZ }) {
            assertTrue(span > diameter * 0.97 && span <= diameter,
                    "The globe should span " + diameter + ", but spanned " + span);
        }
    }

    @Test
    void tonesStayInsideTheGradient() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        for (double epoch : EPOCHS) {
            for (int sample = 0; sample < 256; sample++) {
                CelestialGeometry.project(epoch + sample * 5.5, frame);
                for (int vertex = 0; vertex < CelestialGeometry.STRIDE; vertex += 4) {
                    float tone = frame[vertex + 3];
                    assertTrue(tone >= 0 && tone <= 1, "Tone " + tone + " left [0, 1]");
                }
            }
        }
    }

    /// Each quad is one flat patch, so all four of its vertices share a tone; the renderer relies on that when it
    /// reads the tone of the first vertex only.
    @Test
    void everyQuadCarriesOneTone() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        for (double epoch : EPOCHS) {
            CelestialGeometry.project(epoch + 42.25, frame);
            for (int quad = 0; quad < CelestialGeometry.TOTAL_QUADS; quad++) {
                float tone = frame[quad * 16 + 3];
                for (int vertex = 1; vertex < 4; vertex++) {
                    assertEquals(tone, frame[quad * 16 + vertex * 4 + 3], 1.0e-6f,
                            "Quad " + quad + " is not flat");
                }
            }
        }
    }

    /// The two rings are the requested feature: they have to turn, in opposite directions, and never stop.
    @Test
    void theStarRingsCounterRotate() {
        float[] before = new float[CelestialGeometry.STRIDE];
        float[] after = new float[CelestialGeometry.STRIDE];
        CelestialGeometry.project(1000, before);
        CelestialGeometry.project(1000.5, after);

        double outerTurn = signedTurn(before, after, CelestialGeometry.OUTER_BEADS_QUAD);
        double innerTurn = signedTurn(before, after, CelestialGeometry.INNER_BEADS_QUAD);
        assertTrue(outerTurn * innerTurn < 0, "The rings must turn opposite ways: " + outerTurn + " vs " + innerTurn);
        assertTrue(Math.abs(outerTurn) > 1.0e-4 && Math.abs(innerTurn) > 1.0e-4,
                "Both rings must be moving");

        // And they must still be moving in a world old enough to have left float tick precision behind.
        CelestialGeometry.project(1L << 40, before);
        CelestialGeometry.project((1L << 40) + 0.5, after);
        assertTrue(Math.abs(signedTurn(before, after, CelestialGeometry.OUTER_BEADS_QUAD)) > 1.0e-4,
                "The outer ring must not freeze in an old world");
        assertTrue(Math.abs(signedTurn(before, after, CelestialGeometry.INNER_BEADS_QUAD)) > 1.0e-4,
                "The inner ring must not freeze in an old world");
    }

    /// The angle a whole ring of sparkles turned through, as the sum of their individual turns. Cheap, and it does
    /// not care which sparkle ended up where.
    private static double signedTurn(float[] before, float[] after, int firstQuad) {
        double turn = 0;
        for (int quad = firstQuad; quad < firstQuad + CelestialGeometry.RING_SEGMENTS; quad++) {
            int base = quad * 16;
            // The sparkle's centre is the mean of its four corners.
            double beforeX = 0;
            double beforeZ = 0;
            double afterX = 0;
            double afterZ = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                beforeX += before[base + vertex * 4];
                beforeZ += before[base + vertex * 4 + 2];
                afterX += after[base + vertex * 4];
                afterZ += after[base + vertex * 4 + 2];
            }
            turn += Math.atan2(beforeX * afterZ - beforeZ * afterX, beforeX * afterX + beforeZ * afterZ);
        }
        return turn;
    }

    /// A sphere's declared radius has to be where its outermost surface actually is, for every shell count. The nucleus
    /// has a single shell, and a one-shell sphere whose shell sat at a fraction of its radius would make the constant
    /// mean nothing - which is exactly the bug this guards.
    @Test
    void everySphereReachesItsDeclaredRadius() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        for (double epoch : EPOCHS) {
            for (int sample = 0; sample < 128; sample++) {
                CelestialGeometry.project(epoch + sample * 2.5, frame);
                assertEquals(CelestialGeometry.GLOBE_RADIUS,
                        widestRadius(frame, CelestialGeometry.GLOBE_QUAD, CelestialGeometry.NUCLEUS_QUAD), SLACK,
                        "The globe must reach its declared radius");
                assertEquals(CelestialGeometry.NUCLEUS_RADIUS,
                        widestRadius(frame, CelestialGeometry.NUCLEUS_QUAD, CelestialGeometry.TOTAL_QUADS), SLACK,
                        "The nucleus must reach its declared radius");
            }
        }
    }

    /// The largest distance from the block's centre to any vertex of the given quads.
    private static double widestRadius(float[] frame, int firstQuad, int lastQuad) {
        double widest = 0;
        for (int quad = firstQuad; quad < lastQuad; quad++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = quad * 16 + vertex * 4;
                double x = frame[base];
                double y = frame[base + 1];
                double z = frame[base + 2];
                widest = Math.max(widest, Math.sqrt(x * x + y * y + z * z));
            }
        }
        return widest;
    }

    /// Partial ticks have to stay continuous even in a world past 2^24 ticks, where a float tick count would stall.
    @Test
    void partialTicksStayContinuousInOldWorlds() {
        float[] before = new float[CelestialGeometry.STRIDE];
        float[] after = new float[CelestialGeometry.STRIDE];
        double ticks = (1L << 34) + 0.25;
        CelestialGeometry.project(ticks, before);
        CelestialGeometry.project(ticks + 0.05, after);
        double displacement = 0;
        for (int i = 0; i < before.length; i++) {
            double delta = Math.abs(before[i] - after[i]);
            assertTrue(delta < 0.01, "Partial ticks must remain continuous");
            displacement += delta;
        }
        assertTrue(displacement > 1.0e-4, "The display must not freeze after 2^24 ticks");
    }

    /// The globe is a body, not a shell: the shells behind the near one have to be there to be looked through.
    @Test
    void theGlobeIsMadeOfConcentricShells() {
        float[] frame = new float[CelestialGeometry.STRIDE];
        CelestialGeometry.project(0, frame);
        int first = CelestialGeometry.GLOBE_QUAD * 16;
        int last = CelestialGeometry.NUCLEUS_QUAD * 16;
        double innermost = Double.MAX_VALUE;
        double outermost = 0;
        for (int vertex = first; vertex < last; vertex += 4) {
            double radius = Math.sqrt(frame[vertex] * frame[vertex]
                    + frame[vertex + 1] * frame[vertex + 1] + frame[vertex + 2] * frame[vertex + 2]);
            innermost = Math.min(innermost, radius);
            outermost = Math.max(outermost, radius);
        }
        assertTrue(innermost < outermost * 0.62, "The globe must be more than one shell thick");
        assertTrue(outermost > CelestialGeometry.GLOBE_RADIUS * 0.95, "The globe must fill its radius");
        assertTrue(innermost > 0.1, "No globe vertex may collapse onto the centre");
    }
}