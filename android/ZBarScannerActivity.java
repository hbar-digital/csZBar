package org.cloudsky.cordovaPlugins;

import java.io.IOException;
import java.lang.RuntimeException;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import io.hbar.badgescanner.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ZBarScannerActivity extends Activity implements SurfaceHolder.Callback {
    public static final int RESULT_ERROR = RESULT_FIRST_USER + 1;

    // State -----------------------------------------------------------

    private Camera camera;
    private SurfaceView scannerSurface;
    private ARView arView;
    private SurfaceHolder holder;

    private int surfW, surfH;


    // Activity Lifecycle ----------------------------------------------

    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cszbarscanner);


        // Create preview SurfaceView
        scannerSurface = new SurfaceView(this) {
            @Override
            public void onSizeChanged (int w, int h, int oldW, int oldH) {
                surfW = w;
                surfH = h;
                matchSurfaceToPreviewRatio();
            }
        };
        scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ));
        scannerSurface.getHolder().addCallback(this);

        // Add preview SurfaceView to the screen
        FrameLayout scannerView = (FrameLayout) findViewById(R.id.csZbarScannerView);
        scannerView.addView(scannerSurface);


        arView = new ARView(this);
        arView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ));

        scannerView.addView(arView);
        scannerView.bringChildToFront(arView);

        scannerView.requestLayout();
        scannerView.invalidate();
    }

    @Override
    public void onResume ()
    {
        super.onResume();

        // try {
        //     if(whichCamera.equals("front")) {
        //         int numCams = Camera.getNumberOfCameras();
        //         CameraInfo cameraInfo = new CameraInfo();
        //         for(int i=0; i<numCams; i++) {
        //             Camera.getCameraInfo(i, cameraInfo);
        //             if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
        //                 camera = Camera.open(i);
        //             }
        //         }
        //     } else {
        //         camera = Camera.open();
        //     }

        //     if(camera == null) throw new Exception ("Error: No suitable camera found.");
        // } catch (RuntimeException e) {
        //     die("Error: Could not open the camera.");
        //     return;
        // } catch (Exception e) {
        //     die(e.getMessage());
        //     return;
        // }

        camera = Camera.open();

        Camera.Parameters camParams = camera.getParameters();
        camParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

        // if(flashMode.equals("on")) {
        //     camParams.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        // } else if(flashMode.equals("off")) {
        //     camParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        // } else {
        //     camParams.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        // }
        // if (android.os.Build.VERSION.SDK_INT >= 14) {
        //  camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        // }

        try { camera.setParameters(camParams); }
        catch (RuntimeException e) {
            // Log.d("csZBar", "Unsupported camera parameter reported for flash mode: "+flashMode);
        }

        tryStartPreview();
    }

    @Override
    public void onPause ()
    {
        releaseCamera();
        super.onPause();
    }

    @Override
    public void onDestroy ()
    {
        super.onDestroy();
    }

    // Event handlers --------------------------------------------------

    @Override
    public void onBackPressed ()
    {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    // SurfaceHolder.Callback implementation ---------------------------

    @Override
    public void surfaceCreated (SurfaceHolder hld)
    {
        tryStopPreview();
        holder = hld;
        tryStartPreview();
    }

    @Override
    public void surfaceDestroyed (SurfaceHolder holder)
    {
        // No surface == no preview == no point being in this Activity.
        die("The camera surface was destroyed");
    }

    @Override
    public void surfaceChanged (SurfaceHolder hld, int fmt, int w, int h)
    {
        // Sanity check - holder must have a surface...
        if(hld.getSurface() == null) die("There is no camera surface");

        surfW = w;
        surfH = h;
        matchSurfaceToPreviewRatio();

        tryStopPreview();
        holder = hld;
        tryStartPreview();
    }


    // Camera callbacks ------------------------------------------------

    // Receives frames from the camera and checks for barcodes.
    private PreviewCallback previewCb = new PreviewCallback()
    {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            // Image barcode = new Image(size.width, size.height, "Y800");
            // barcode.setData(data);

            arView.drawCircle((float) (Math.random() * 100), (float) (Math.random() * 100), (float) (Math.random() * 100));
        }
    };

    // Misc ------------------------------------------------------------

    // finish() due to error
    private void die (String msg)
    {
        setResult(RESULT_ERROR);
        finish();
    }

    private void releaseCamera ()
    {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    // Match the aspect ratio of the preview SurfaceView with the camera's preview aspect ratio,
    // so that the displayed preview is not stretched/squashed.
    private void matchSurfaceToPreviewRatio () {
        if(camera == null) return;
        if(surfW == 0 || surfH == 0) return;

        // Resize SurfaceView to match camera preview ratio (avoid stretching).
        Camera.Parameters params = camera.getParameters();
        Camera.Size size = params.getPreviewSize();
        float previewRatio = (float) size.height / size.width; // swap h and w as the preview is rotated 90 degrees
        float surfaceRatio = (float) surfW / surfH;

        if(previewRatio > surfaceRatio) {
            scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
                surfW,
                Math.round((float) surfW / previewRatio),
                Gravity.CENTER
            ));
        } else if(previewRatio < surfaceRatio) {
            scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
                Math.round((float) surfH * previewRatio),
                surfH,
                Gravity.CENTER
            ));
        }
    }

    // Stop the camera preview safely.
    private void tryStopPreview () {
        // Stop camera preview before making changes.
        try {
            camera.stopPreview();
        } catch (Exception e){
          // Preview was not running. Ignore the error.
        }
    }



    // Start the camera preview if possible.
    // If start is attempted but fails, exit with error message.
    private void tryStartPreview () {
        if(holder != null) {
            try {
                // 90 degrees rotation for Portrait orientation Activity.
                camera.setDisplayOrientation(90);

                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(previewCb);
                camera.startPreview();

                
            } catch (IOException e) {
                die("Could not start camera preview: " + e.getMessage());
            }
        }
    }

    class ARView extends View {
        private SurfaceHolder surfaceHolder;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float x = 0;
        private float y = 0;
        private float r = 0;

        public ARView(Context context) {
            super(context);
        }

        public void drawCircle(float x, float y, float r) {
            this.x = x;
            this.y = y;
            this.r = r;

            this.invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) { 
            super.onDraw(canvas); 

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.RED);

            canvas.drawCircle(x, y, r, paint);
        }
    }
}
