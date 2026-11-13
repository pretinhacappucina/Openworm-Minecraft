package com.openwormmc.physics;

/**
 * Planar articulated body (N rigid segments, N-1 joints) with viscoelastic joints driven by muscle drive, moved by
 * resistive force theory (c_perp > c_par), force-free and torque-free (overdamped), like a worm crawling/swimming on
 * a surface. NOT Sibernetic: this is an efficient approximation that keeps the causal chain
 * muscle activity -> body curvature -> net thrust. Lengths in body lengths (BL), time in simulated ms.
 */
public final class WormBody {
    public final int n;
    public final double[] theta;        // joint angles (rad), size n-1
    public double x, y, heading;        // centre-of-mass position (BL) and orientation of segment 0 (rad)
    public double vx, vy, omega;        // last solved rigid velocity (BL/ms, rad/ms)
    public double maxAngle = 0.45, gain = 0.9, tauMs = 120, cPerpOverPar = 2.0;
    private final double[] prevRx, prevRy, rx, ry, phi;

    public WormBody(int segments) {
        n = segments; theta = new double[n - 1];
        prevRx = new double[n]; prevRy = new double[n]; rx = new double[n]; ry = new double[n]; phi = new double[n];
        shape(); System.arraycopy(rx, 0, prevRx, 0, n); System.arraycopy(ry, 0, prevRy, 0, n);
    }

    /** Segment centres in the head-aligned frame (phi0 = 0), relative to centre of mass. */
    private void shape() {
        double l = 1.0 / n; phi[0] = 0;
        for (int i = 1; i < n; i++) phi[i] = phi[i - 1] + theta[i - 1];
        double px = 0, py = 0; rx[0] = 0; ry[0] = 0;
        for (int i = 1; i < n; i++) {
            px += 0.5 * l * (Math.cos(phi[i - 1]) + Math.cos(phi[i]));
            py += 0.5 * l * (Math.sin(phi[i - 1]) + Math.sin(phi[i]));
            rx[i] = px; ry[i] = py;
        }
        double cx = 0, cy = 0; for (int i = 0; i < n; i++) { cx += rx[i]; cy += ry[i]; }
        cx /= n; cy /= n; for (int i = 0; i < n; i++) { rx[i] -= cx; ry[i] -= cy; }
    }

    /** drive[s] in [-1,1] per segment (+ = bend toward the dorsal side). Advances dtMs of simulated time. */
    public void step(double[] drive, double dtMs) {
        for (int j = 0; j < n - 1; j++) {
            double target = Math.max(-maxAngle, Math.min(maxAngle, gain * 0.5 * (drive[j] + drive[j + 1])));
            theta[j] += (target - theta[j]) * (1 - Math.exp(-dtMs / tauMs));
        }
        advance(dtMs);
    }

    /** Impose joint angles directly (testing the physics independent of the brain). */
    public void stepPrescribed(double[] th, double dtMs) { System.arraycopy(th, 0, theta, 0, n - 1); advance(dtMs); }

    private void advance(double dt) {
        shape();
        double l = 1.0 / n, cpar = 1.0, cperp = cPerpOverPar;
        double[][] M = new double[3][4];
        double cs = Math.cos(heading), sn = Math.sin(heading);
        for (int i = 0; i < n; i++) {
            double ax = cs * rx[i] - sn * ry[i], ay = sn * rx[i] + cs * ry[i];   // world offset from COM
            double wxs = (rx[i] - prevRx[i]) / dt, wys = (ry[i] - prevRy[i]) / dt;
            double wx = cs * wxs - sn * wys, wy = sn * wxs + cs * wys;           // shape velocity (world)
            double ph = phi[i] + heading, tx = Math.cos(ph), ty = Math.sin(ph), nx = -ty, ny = tx;
            // D = l*(cpar t t^T + cperp n n^T)
            double dxx = l * (cpar * tx * tx + cperp * nx * nx), dxy = l * (cpar * tx * ty + cperp * nx * ny), dyy = l * (cpar * ty * ty + cperp * ny * ny);
            double jx = -ay, jy = ax;                                               // J a
            // force rows
            M[0][0] += dxx; M[0][1] += dxy; M[0][2] += dxx * jx + dxy * jy; M[0][3] -= dxx * wx + dxy * wy;
            M[1][0] += dxy; M[1][1] += dyy; M[1][2] += dxy * jx + dyy * jy; M[1][3] -= dxy * wx + dyy * wy;
            // torque row: a x (D v)
            double d1x = dxx, d1y = dxy, d2x = dxy, d2y = dyy;
            M[2][0] += ax * d2x - ay * d1x; M[2][1] += ax * d2y - ay * d1y;
            double gx = dxx * jx + dxy * jy, gy = dxy * jx + dyy * jy, hx = dxx * wx + dxy * wy, hy = dxy * wx + dyy * wy;
            M[2][2] += ax * gy - ay * gx; M[2][3] -= ax * hy - ay * hx;
        }
        double[] u = solve3(M);
        vx = u[0]; vy = u[1]; omega = u[2];
        x += vx * dt; y += vy * dt; heading += omega * dt;
        System.arraycopy(rx, 0, prevRx, 0, n); System.arraycopy(ry, 0, prevRy, 0, n);
    }

    private static double[] solve3(double[][] a) {
        for (int c = 0; c < 3; c++) {
            int piv = c; for (int r = c + 1; r < 3; r++) if (Math.abs(a[r][c]) > Math.abs(a[piv][c])) piv = r;
            double[] t = a[c]; a[c] = a[piv]; a[piv] = t;
            if (Math.abs(a[c][c]) < 1e-14) return new double[3];
            for (int r = c + 1; r < 3; r++) { double f = a[r][c] / a[c][c]; for (int k = c; k < 4; k++) a[r][k] -= f * a[c][k]; }
        }
        double[] x = new double[3];
        for (int r = 2; r >= 0; r--) { double s = a[r][3]; for (int k = r + 1; k < 3; k++) s -= a[r][k] * x[k]; x[r] = s / a[r][r]; }
        return x;
    }

    /** World position of segment i's centre (BL). */
    public double segX(int i) { return x + Math.cos(heading) * rx[i] - Math.sin(heading) * ry[i]; }
    public double segY(int i) { return y + Math.sin(heading) * rx[i] + Math.cos(heading) * ry[i]; }
    /** Speed of the centre of mass along the head-to-tail axis (BL/ms); positive = head-first. */
    public double forwardSpeed() { double ax = Math.cos(heading + phi[n / 2]), ay = Math.sin(heading + phi[n / 2]); return vx * ax + vy * ay; }
}
