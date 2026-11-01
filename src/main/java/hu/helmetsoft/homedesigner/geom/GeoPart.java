package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.material.SurfaceMaterial;

/** A mesh with a single material, belonging to a model object (wall, room, roof, lamp, furniture...). */
public final class GeoPart {
    public final Object owner;
    public final SurfaceMaterial material;
    public final Mesh mesh;

    public GeoPart(Object owner, SurfaceMaterial material, Mesh mesh) {
        this.owner = owner;
        this.material = material;
        this.mesh = mesh;
    }
}
