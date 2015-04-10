package edu.augustana.csc490.sologamev3;

import android.graphics.Paint;
import android.graphics.RectF;

class PointGate {

    int gateWidth;
    int gateHeight;
    Paint paint;
    RectF bounds;

    PointGate(int gateWidth, int gateHeight, Paint paint) {
        this.gateWidth = gateWidth;
        this.gateHeight = gateHeight;
        this.paint = paint;
        this.bounds = new RectF(0, 0, gateWidth, gateHeight);
    }

}