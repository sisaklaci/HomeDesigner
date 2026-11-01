package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A storey's coordinates and elevations are local to its finished-floor datum. */
public final class BuildingLevel {
    public String id = UUID.randomUUID().toString();
    public String name = "Földszint";
    public double baseHeight;
    public List<Wall> walls = new ArrayList<>();
    public List<Room> rooms = new ArrayList<>();
    public List<Furniture> furniture = new ArrayList<>();
    public List<LightSource> lights = new ArrayList<>();
    public List<DimensionLine> dimensions = new ArrayList<>();
    public List<Roof> roofs = new ArrayList<>();
    public List<Balcony> balconies = new ArrayList<>();
    public List<RetainingWall> retainingWalls = new ArrayList<>();

    public boolean isEmpty() {
        return walls.isEmpty() && rooms.isEmpty() && furniture.isEmpty() && lights.isEmpty()
                && dimensions.isEmpty() && roofs.isEmpty() && balconies.isEmpty()
                && (retainingWalls == null || retainingWalls.isEmpty());
    }

    @Override public String toString() {
        return I18n.display(name) + "  (" + String.format(java.util.Locale.ROOT, "%+.2f m", baseHeight) + ")";
    }
}
