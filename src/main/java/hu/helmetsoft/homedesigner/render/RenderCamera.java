package hu.helmetsoft.homedesigner.render;

/** Pinhole camera in world space (Y up). */
public final class RenderCamera {
    public final double[] eye = new double[3];
    public final double[] fwd = new double[3], right = new double[3], up = new double[3];
    /** Vertical field of view in degrees. */
    public double fovY = 60;

    public RenderCamera() {
    }

    /** yaw: rotation around Y (0 looks toward -Z / north, positive turns right), pitch: up/down; degrees. */
    public static RenderCamera of(double ex, double ey, double ez, double yawDeg, double pitchDeg, double fovY) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        RenderCamera c = new RenderCamera();
        c.eye[0] = ex;
        c.eye[1] = ey;
        c.eye[2] = ez;
        c.fovY = fovY;
        double cp = Math.cos(pitch);
        c.fwd[0] = cp * Math.sin(yaw);
        c.fwd[1] = Math.sin(pitch);
        c.fwd[2] = -cp * Math.cos(yaw);
        // right = fwd x worldUp
        double rx = -c.fwd[2], rz = c.fwd[0];
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-9) {
            rx = Math.cos(yaw);
            rz = Math.sin(yaw);
            rl = 1;
        }
        c.right[0] = rx / rl;
        c.right[1] = 0;
        c.right[2] = rz / rl;
        // up = right x fwd
        c.up[0] = c.right[1] * c.fwd[2] - c.right[2] * c.fwd[1];
        c.up[1] = c.right[2] * c.fwd[0] - c.right[0] * c.fwd[2];
        c.up[2] = c.right[0] * c.fwd[1] - c.right[1] * c.fwd[0];
        return c;
    }
}
