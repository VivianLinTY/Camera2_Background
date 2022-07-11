package com.example.testbgcamera;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CameraService extends Service {

    private final static String TAG = "CameraService";

    public final static String ACTION_START = "com.compal.camera.start";
    public final static String ACTION_STOPPED = "com.compal.camera.stop";

    private final static int ONGOING_NOTIFICATION_ID = 5566;
    private final static String CHANNEL_ID = "cam_service_channel_id";
    private final static String CHANNEL_NAME = "cam_service_channel_name";

    public final static boolean SHOW_PREVIEW = false;
    //For preview
    private WindowManager mWindowManager;
    private View mRootView;
    private TextureView mTextureView;

    private ImageReader mImageReader;
    private Size mPreviewSize;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;

    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

    };

    private final ImageReader.OnImageAvailableListener mImageListener = reader -> {
        if (null != reader) {
            Image image = reader.acquireLatestImage();
            if (null == image) {
                return;
            }
            Log.d(TAG, "onImageAvailable: " + image.getWidth() + " x " + image.getHeight());
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);

            image.close();
        }
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (SHOW_PREVIEW) {
            // Initialize view drawn over other apps
            initOverlay();
            if (mTextureView.isAvailable()) {
                initCam();
            } else {
                mTextureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        } else {
            initCam();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            initCam();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private void initOverlay() {
        mRootView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.overlay, null);
        mTextureView = mRootView.findViewById(R.id.texPreview);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(mRootView, params);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCamera();
        if (mRootView != null) {
            mWindowManager.removeView(mRootView);
            mRootView = null;
        }
        sendBroadcast(new Intent(ACTION_STOPPED));
    }

    private void initCam() {
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String camId = null;
        try {
            for (String id : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    camId = id;
                    break;
                }
            }

            if (!TextUtils.isEmpty(camId)) {
                mPreviewSize = chooseSupportedSize(camId);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mCameraManager.openCamera(camId, mStateCallback, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Size chooseSupportedSize(String camId) throws CameraAccessException {
        // Get all supported sizes for TextureView
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(camId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedSizes = map.getOutputSizes(SurfaceTexture.class);

        if (supportedSizes.length > 0) {
            return supportedSizes[0];
        }

        return new Size(320, 240);
    }

    private void startForeground() {
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.app_name))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.app_name))
                .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    private void createCaptureSession() {
        try {
            // Prepare surfaces we want to use in capture session
            List<Surface> targetSurfaces = new ArrayList<>();
            // Configure target surface for background processing (ImageReader)
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(mImageListener, null);
            targetSurfaces.add(mImageReader.getSurface());
            CaptureRequest.Builder requestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(mImageReader.getSurface());
            // Set some additional parameters for the request
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            if (SHOW_PREVIEW) {
                SurfaceTexture texture = mTextureView.getSurfaceTexture();
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                Surface previewSurface = new Surface(texture);
                targetSurfaces.add(previewSurface);
                requestBuilder.addTarget(previewSurface);
            }

            if (null == mCameraDevice) {
                return;
            }

            // Prepare CameraCaptureSession
            mCameraDevice.createCaptureSession(targetSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCameraCaptureSession = session;
                    try {
                        // Now we can start capturing
                        mCaptureRequest = requestBuilder.build();
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, mCaptureCallback, null);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "CreateCaptureSession onConfigureFailed!");
                }
            }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopCamera() {
        try {
            if (null != mCameraCaptureSession) {
                mCameraCaptureSession.close();
                mCameraCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
