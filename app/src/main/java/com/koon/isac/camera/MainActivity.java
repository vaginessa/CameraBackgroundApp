package com.koon.isac.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.location.Location;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationManagerProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
	private Locale currentLocale;
	private static final String TAG = "ISAC.CAMERA";

	private final Object syncObj = new Object();

	public static File mediaStorageDir;

	public static SurfaceView mSurfaceView;
	public static SurfaceHolder mSurfaceHolder;

	private NumberPicker periodPicker;
	private Button start, end;

	private Timer timer;

	private Camera camera;

	private LocationManagerProvider provider;

	private Location lastLocation;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		getSavePath();

		getWindow().setFormat(PixelFormat.UNKNOWN);
		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.setFixedSize(1, 1);
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		start = (Button) findViewById(R.id.btn_start);
		end = (Button) findViewById(R.id.btn_end);
		end.setEnabled(false);

		periodPicker = (NumberPicker) findViewById(R.id.period_picker);
		periodPicker.setMinValue(1);
		periodPicker.setMaxValue(10);
		periodPicker.setValue(5);

		cameraSetting();


		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");

		provider = new LocationManagerProvider();


		start.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				wl.acquire();

				SmartLocation.with(MainActivity.this).location(provider)
						.config(LocationParams.NAVIGATION)
						.start(new OnLocationUpdatedListener() {
							@Override
							public void onLocationUpdated(Location location) {
								synchronized (syncObj) {
									lastLocation = location;
								}
							}
						});

				periodPicker.setEnabled(false);
				start.setEnabled(false);
				end.setEnabled(true);

				timer = new Timer();
				timer.scheduleAtFixedRate(
						new TimerTask() {
							@Override
							public void run() {
								camera.takePicture(null, null, mPicture);
							}
						},
						0,
						periodPicker.getValue() * 1000);
			}
		});

		end.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				timer.cancel();

				SmartLocation.with(MainActivity.this).location(provider)
						.stop();

				end.setEnabled(false);
				start.setEnabled(true);
				periodPicker.setEnabled(true);

				wl.release();
			}
		});
	}

	@Override
	public void surfaceCreated(SurfaceHolder surfaceHolder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
	}

	/**
	 * 저장할 파일명 생성
	 */
	private File getOutputMediaFile() {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", currentLocale).format(new Date());
		File mediaFile = new File(MainActivity.mediaStorageDir.getPath() + File.separator + timeStamp + ".jpg");
		Log.i(TAG, "Saved at" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));

		return mediaFile;
	}

	private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "picture was taken. trying to save");

			File pictureFile = getOutputMediaFile();
			if (pictureFile == null) {
				Toast.makeText(MainActivity.this, "Error saving!!", Toast.LENGTH_SHORT).show();
				return;
			}

			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();

				markGeoTagImage(pictureFile.getAbsolutePath(), lastLocation);
			} catch (FileNotFoundException e) {
				Log.d(TAG, "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d(TAG, "Error accessing file: " + e.getMessage());
			}

			camera.startPreview();
		}
	};

	private void getSavePath() {
		if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			Toast.makeText(
					this,
					"Cannot access external storage!",
					Toast.LENGTH_LONG
			).show();

			finish();
		}

		Log.d(TAG, "check external storage state");
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			Log.d(TAG, "no external storage");
		}

		mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES), "CameraLog");
		// 굳이 이 경로로 하지 않아도 되지만 가장 안전한 경로이므로 추천함.

		// 없는 경로라면 따로 생성한다.
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d(TAG, "failed to create directory");
			}
		}

		currentLocale = getResources().getConfiguration().locale;
	}

	private void cameraSetting() {
		Log.d(TAG, "Start to open camera");
		camera = Camera.open();

		Camera.Parameters params = camera.getParameters();
		Camera.Size size = params.getSupportedPictureSizes().get(params.getSupportedPictureSizes().size() - 1);
		params.setPictureSize(size.width, size.height);
		params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
		params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
		params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
		params.setExposureCompensation(0);
		params.setPictureFormat(ImageFormat.JPEG);
		params.setJpegQuality(100);
		camera.setParameters(params);

		Log.d(TAG, "write parameters");

		try {
			camera.setPreviewDisplay(MainActivity.mSurfaceHolder);
		} catch (IOException e) {
			e.printStackTrace();
		}

		camera.startPreview();
	}

	private void markGeoTagImage(String imagePath, Location location) {
		try {
			synchronized (syncObj) {
				ExifInterface exif = new ExifInterface(imagePath);
				exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GPS2EXIF.convert(location.getLatitude()));
				exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GPS2EXIF.latitudeRef(location.getLatitude()));
				exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPS2EXIF.convert(location.getLongitude()));
				exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GPS2EXIF.longitudeRef(location.getLongitude()));
				SimpleDateFormat fmt_Exif = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", currentLocale);
				exif.setAttribute(ExifInterface.TAG_DATETIME, fmt_Exif.format(new Date(location.getTime())));
				exif.saveAttributes();
			}
		} catch (NullPointerException e) {
			Log.i(TAG, "gps doesn't sensing location yet");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
