package org.cloudsky.cordovaPlugins;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import io.hbar.badgescanner.R;

import java.util.List;

public class ZBarScannerActivity extends Activity implements SurfaceHolder.Callback {
    public static final int RESULT_ERROR = RESULT_FIRST_USER + 1;

    private final String DEBUG_TAG = "BadeScanner";

    private Camera camera;
    private SurfaceView scannerSurface;
    private ARView arView;
    private SurfaceHolder holder;

    private int surfW, surfH;


    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cszbarscanner);


        scannerSurface = new SurfaceView(this);
        FrameLayout scannerView = (FrameLayout) findViewById(R.id.csZbarScannerView);


        scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ));
        holder = scannerSurface.getHolder();
        holder.addCallback(this);

        arView = new ARView(this);
        arView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ));

        scannerView.addView(scannerSurface);

        scannerView.addView(arView);
        scannerView.bringChildToFront(arView);

        scannerView.requestLayout();
        scannerView.invalidate();
    }

    @Override
    public void onBackPressed ()
    {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    public void surfaceCreated (SurfaceHolder hld)
    {
        camera = Camera.open();

        try { 
           camera.setPreviewDisplay(holder); 
       } catch (IOException e) { 
          Log.e(DEBUG_TAG, "surfaceCreated exception: ", e);     
       }  
    }

    @Override
    public void surfaceDestroyed (SurfaceHolder holder)
    {
        camera.stopPreview(); 
        camera.setPreviewCallback(null);
        holder.removeCallback(this);
        camera.release();

        die("The camera surface was destroyed");
    }

    @Override
    public void surfaceChanged (SurfaceHolder hld, int fmt, int w, int h)
    {
        Camera.Parameters params = camera.getParameters();      
        List<Size> prevSizes = params.getSupportedPreviewSizes(); 
        for (Size s : prevSizes) 
        { 
            if((s.height <= h) && (s.width <= w)) 
            { 
                params.setPreviewSize(s.width, s.height); 
                break; 
            }  
        } 

        camera.setDisplayOrientation(90);
        camera.setPreviewCallback(previewCb);
        camera.setParameters(params); 
        camera.startPreview(); 
    }

    private PreviewCallback previewCb = new PreviewCallback()
    {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            // Image img = new Image(size.width, size.height, "Y800");
            // img.setData(data);

            arView.drawCircle((float) (Math.random() * 100), (float) (Math.random() * 100), (float) (Math.random() * 100));
        }
    };

    private void die(String msg)
    {
        Log.d(DEBUG_TAG, msg);
        setResult(RESULT_ERROR);
        finish();
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
