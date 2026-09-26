package cn.lyxc.fantasytechnology.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/// The celestial display is only visible because the block's model leaves its interior open. This holds the model to
/// that, since nothing else would notice the day someone re-parented it to a solid cube again.
class DeviceAccessModelTest {
    private static final Path MODEL = Path.of(
            "src/main/resources/assets/fantasy_technology/models/block/fantasy_device_access.json");

    private static JsonObject model() throws IOException {
        return JsonParser.parseString(Files.readString(MODEL, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theModelIsStyledAndTextured() throws IOException {
        JsonObject model = model();
        var textures = model.getAsJsonObject("textures");
        assertTrue(textures.has("frame"), "The cage needs the frame texture");
        assertTrue(textures.has("particle"), "Particles need a texture too");
    }

    /// Every element has to be a thin shell piece: a clamp at a corner, or a rail along an edge. An element that
    /// spans the block on all three axes would fill the interior and hide the display completely.
    @Test
    void noElementFillsTheInterior() throws IOException {
        var elements = model().getAsJsonArray("elements");
        assertTrue(elements.size() >= 20, "A cage needs 8 corner clamps and 12 edge rails");
        for (var element : elements) {
            var json = element.getAsJsonObject();
            String name = json.get("name").getAsString();
            double[] from = read(json, "from");
            double[] to = read(json, "to");
            int longAxes = 0;
            for (int axis = 0; axis < 3; axis++) {
                if (to[axis] - from[axis] > 4) {
                    longAxes++;
                }
            }
            assertTrue(longAxes < 2,
                    name + " spans the block on " + longAxes + " axes; it would hide the display");
        }
    }

    /// The cage is open in the middle of every face: there is a clear tunnel straight through the block along each
    /// axis, which is where the globe and the rings are seen from.
    @Test
    void theCageIsOpenOnEveryAxis() throws IOException {
        var elements = model().getAsJsonArray("elements");
        for (int axis = 0; axis < 3; axis++) {
            boolean blocked = false;
            for (var element : elements) {
                var json = element.getAsJsonObject();
                double[] from = read(json, "from");
                double[] to = read(json, "to");
                // Does any element sit on the axis through the centre of the block?
                boolean coversCentre = true;
                for (int other = 0; other < 3; other++) {
                    if (other != axis && (from[other] > 8 || to[other] < 8)) {
                        coversCentre = false;
                        break;
                    }
                }
                if (coversCentre && (from[axis] < 8 && to[axis] > 8)) {
                    blocked = true;
                }
            }
            assertFalse(blocked, "The cage blocks the view along axis " + axis);
        }
    }

    private static double[] read(JsonObject json, String key) {
        var array = json.getAsJsonArray(key);
        assertEquals(3, array.size(), key + " needs three coordinates");
        return new double[] { array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble() };
    }
}