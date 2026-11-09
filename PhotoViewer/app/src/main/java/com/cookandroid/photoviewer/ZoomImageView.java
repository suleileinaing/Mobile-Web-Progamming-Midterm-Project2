package com.cookandroid.photoviewer;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;

    private float zoom = 1f;
    private float lastX;
    private float lastY;

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector) {

                        float newZoom = Math.max(
                                1f,
                                Math.min(
                                        zoom * detector.getScaleFactor(),
                                        5f
                                )
                        );

                        float factor = newZoom / zoom;
                        zoom = newZoom;

                        matrix.postScale(
                                factor,
                                factor,
                                detector.getFocusX(),
                                detector.getFocusY()
                        );

                        keepImageInBounds();
                        return true;
                    }
                }
        );
    }

    public void resetZoom() {
        Drawable drawable = getDrawable();

        if (drawable == null
                || getWidth() == 0
                || getHeight() == 0) {
            return;
        }

        float imageWidth = drawable.getIntrinsicWidth();
        float imageHeight = drawable.getIntrinsicHeight();

        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        float fitScale = Math.min(
                getWidth() / imageWidth,
                getHeight() / imageHeight
        );

        matrix.reset();
        matrix.postScale(fitScale, fitScale);

        matrix.postTranslate(
                (getWidth() - imageWidth * fitScale) / 2f,
                (getHeight() - imageHeight * fitScale) / 2f
        );

        zoom = 1f;
        setImageMatrix(matrix);
    }

    private void keepImageInBounds() {
        Drawable drawable = getDrawable();

        if (drawable == null) {
            return;
        }

        RectF bounds = new RectF(
                0,
                0,
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight()
        );

        matrix.mapRect(bounds);

        float dx = 0f;
        float dy = 0f;

        if (bounds.width() <= getWidth()) {
            dx = getWidth() / 2f - bounds.centerX();
        } else if (bounds.left > 0) {
            dx = -bounds.left;
        } else if (bounds.right < getWidth()) {
            dx = getWidth() - bounds.right;
        }

        if (bounds.height() <= getHeight()) {
            dy = getHeight() / 2f - bounds.centerY();
        } else if (bounds.top > 0) {
            dy = -bounds.top;
        } else if (bounds.bottom < getHeight()) {
            dy = getHeight() - bounds.bottom;
        }

        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    @Override
    protected void onSizeChanged(
            int width, int height,
            int oldWidth, int oldHeight) {

        super.onSizeChanged(
                width, height, oldWidth, oldHeight
        );

        resetZoom();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1
                        && !scaleDetector.isInProgress()
                        && zoom > 1f) {

                    matrix.postTranslate(
                            event.getX() - lastX,
                            event.getY() - lastY
                    );

                    keepImageInBounds();
                }

                lastX = event.getX();
                lastY = event.getY();
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                int remainingIndex =
                        event.getActionIndex() == 0 ? 1 : 0;

                lastX = event.getX(remainingIndex);
                lastY = event.getY(remainingIndex);
                return true;

            case MotionEvent.ACTION_UP:
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public void zoomIn() {
        zoomBy(1.25f);
    }

    public void zoomOut() {
        zoomBy(1f / 1.25f);
    }

    private void zoomBy(float factor) {
        if (getDrawable() == null) {
            return;
        }

        float newZoom = Math.max(
                1f,
                Math.min(zoom * factor, 5f)
        );

        float actualFactor = newZoom / zoom;
        zoom = newZoom;

        matrix.postScale(
                actualFactor,
                actualFactor,
                getWidth() / 2f,
                getHeight() / 2f
        );

        keepImageInBounds();
    }
}