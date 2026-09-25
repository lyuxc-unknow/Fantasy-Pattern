package cn.lyxc.fantasytechnology.client.render;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TesseractGeometryTest {
    @Test
    void topologyContainsEveryHypercubeEdgeAndSquareExactlyOnce() {
        var edges = new HashSet<Integer>();
        int[] degree = new int[16];
        for (int[] edge : TesseractGeometry.EDGES) {
            assertEquals(1, Integer.bitCount(edge[0] ^ edge[1]));
            assertTrue(edges.add(edge[0] * 16 + edge[1]));
            degree[edge[0]]++;
            degree[edge[1]]++;
        }
        assertEquals(32, edges.size());
        for (int count : degree) {
            assertEquals(4, count);
        }
        var faces = new HashSet<String>();
        for (int[] face : TesseractGeometry.FACES) {
            for (int i = 0; i < 4; i++) {
                int a = face[i];
                int b = face[(i + 1) % 4];
                assertTrue(edges.contains(Math.min(a, b) * 16 + Math.max(a, b)));
            }
            int[] sorted = face.clone();
            Arrays.sort(sorted);
            assertTrue(faces.add(Arrays.toString(sorted)));
        }
        assertEquals(24, faces.size());
    }

    @Test
    void rotationsStayFiniteAndInsideTheSingleBlockHousing() {
        float[] points = new float[64];
        // Cover both ordinary play and worlds far past the float tick precision limit.
        for (double epoch : new double[] { 0, 1L << 25, 1L << 40 }) {
            for (int sample = 0; sample < 4096; sample++) {
                TesseractGeometry.project(epoch + sample * 7.375, points);
                for (int v = 0; v < 64; v += 4) {
                    double radiusSquared = 0;
                    for (int axis = 0; axis < 3; axis++) {
                        assertTrue(Float.isFinite(points[v + axis]));
                        radiusSquared += (double) points[v + axis] * points[v + axis];
                    }
                    assertTrue(radiusSquared <= TesseractGeometry.MAX_RADIUS * TesseractGeometry.MAX_RADIUS + 1e-7);
                    assertTrue(points[v + 3] >= 0 && points[v + 3] <= 1);
                }
            }
        }
    }

    @Test
    void partialTicksStillAnimateOldWorldsWithoutJumps() {
        float[] before = new float[64];
        float[] after = new float[64];
        double ticks = (1L << 34) + 0.25;
        TesseractGeometry.project(ticks, before);
        TesseractGeometry.project(ticks + 0.05, after);
        double displacement = 0;
        for (int i = 0; i < before.length; i++) {
            float delta = Math.abs(before[i] - after[i]);
            assertTrue(delta < 0.001, "Partial ticks must remain continuous");
            displacement += delta;
        }
        assertTrue(displacement > 1e-5, "Animation must not freeze after 2^24 ticks");
    }

    @Test
    void fourthDimensionalRotationChangesDepthAndApparentEdgeLength() {
        float[] rest = new float[64];
        float[] folded = new float[64];
        TesseractGeometry.project(0, rest);
        TesseractGeometry.project(170, folded);
        // A spatial rotation alone preserves edge lengths and each vertex's W coordinate.
        double restLength = edgeLength(rest, 0, 1);
        double foldedLength = edgeLength(folded, 0, 1);
        assertTrue(Math.abs(restLength - foldedLength) > 0.01);
        assertTrue(Math.abs(rest[3] - folded[3]) > 0.1);
    }

    private static double edgeLength(float[] points, int a, int b) {
        double sum = 0;
        for (int axis = 0; axis < 3; axis++) {
            double delta = points[a * 4 + axis] - points[b * 4 + axis];
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }
}
