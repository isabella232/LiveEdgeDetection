package com.mobimentum.liveedgedetection.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;

import com.mobimentum.liveedgedetection.BuildConfig;
import com.mobimentum.liveedgedetection.view.LimitedArea;
import com.mobimentum.liveedgedetection.view.Quadrilateral;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.mobimentum.liveedgedetection.constants.ScanConstants.IMAGE_NAME;
import static com.mobimentum.liveedgedetection.constants.ScanConstants.IMG_TYPE;
import static com.mobimentum.liveedgedetection.constants.ScanConstants.PDF_TYPE;
import static com.mobimentum.liveedgedetection.constants.ScanConstants.PHOTO_QUALITY;
import static com.mobimentum.liveedgedetection.constants.ScanConstants.SCHEME;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_MEAN_C;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;

public class ScanUtils {

    private static final String TAG = ScanUtils.class.getSimpleName();

    public static boolean compareFloats(double left, double right) {
        double epsilon = 0.00000001;
        return Math.abs(left - right) < epsilon;
    }

    public static Camera.Size determinePictureSize(Camera camera, Camera.Size previewSize) {
        if (camera == null) return null;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> pictureSizeList = cameraParams.getSupportedPictureSizes();
        Collections.sort(pictureSizeList, (size1, size2) -> {
            Double h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height);
            Double h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height);
            return h2.compareTo(h1);
        });
        Camera.Size retSize = null;

        // if the preview size is not supported as a picture size
        float reqRatio = ((float) previewSize.width) / previewSize.height;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        for (Camera.Size size : pictureSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
            if (ScanUtils.compareFloats(deltaRatio, 0)) {
                break;
            }
        }

        return retSize;
    }

    public static Camera.Size getOptimalPreviewSize(Camera camera, int w, int h) {
        if (camera == null) return null;
        final double targetRatio = (double) h / w;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> previewSizeList = cameraParams.getSupportedPreviewSizes();
        Collections.sort(previewSizeList, (size1, size2) -> {
            double ratio1 = (double) size1.width / size1.height;
            double ratio2 = (double) size2.width / size2.height;
            Double ratioDiff1 = Math.abs(ratio1 - targetRatio);
            Double ratioDiff2 = Math.abs(ratio2 - targetRatio);
            if (ScanUtils.compareFloats(ratioDiff1, ratioDiff2)) {
                Double h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height);
                Double h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height);
                return h2.compareTo(h1);
            }
            return ratioDiff1.compareTo(ratioDiff2);
        });

        return previewSizeList.get(0);
    }

    public static int getDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        DisplayMetrics dm = new DisplayMetrics();

        Camera.getCameraInfo(cameraId, info);
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int displayOrientation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }
        return displayOrientation;
    }

    public static Camera.Size getOptimalPictureSize(Camera camera, final int width, final int height,
                                                    final Camera.Size previewSize) {
        if (camera == null) return null;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> supportedSizes = cameraParams.getSupportedPictureSizes();

        Camera.Size size = camera.new Size(width, height);

        // convert to landscape if necessary
        if (size.width < size.height) {
            int temp = size.width;
            size.width = size.height;
            size.height = temp;
        }

        Camera.Size requestedSize = camera.new Size(size.width, size.height);

        double previewAspectRatio = (double) previewSize.width / (double) previewSize.height;

        if (previewAspectRatio < 1.0) {
            // reset ratio to landscape
            previewAspectRatio = 1.0 / previewAspectRatio;
        }

        Log.d(TAG, "CameraPreview previewAspectRatio " + previewAspectRatio);

        double aspectTolerance = 0.1;
        double bestDifference = Double.MAX_VALUE;

        for (int i = 0; i < supportedSizes.size(); i++) {
            Camera.Size supportedSize = supportedSizes.get(i);

            // Perfect match
            if (supportedSize.equals(requestedSize)) {
                Log.d(TAG, "CameraPreview optimalPictureSize " + supportedSize.width + 'x' + supportedSize.height);
                return supportedSize;
            }

            double difference = Math.abs(previewAspectRatio -
                    ((double) supportedSize.width / (double) supportedSize.height));

            if (difference < bestDifference - aspectTolerance) {
                // better aspectRatio found
                if ((width != 0 && height != 0) || (supportedSize.width * supportedSize.height < 2048 * 1024)) {
                    size.width = supportedSize.width;
                    size.height = supportedSize.height;
                    bestDifference = difference;
                }
            } else if (difference < bestDifference + aspectTolerance) {
                // same aspectRatio found (within tolerance)
                if (width == 0 || height == 0) {
                    // set highest supported resolution below 2 Megapixel
                    if ((size.width < supportedSize.width) && (supportedSize.width * supportedSize.height < 2048 * 1024)) {
                        size.width = supportedSize.width;
                        size.height = supportedSize.height;
                    }
                } else {
                    // check if this pictureSize closer to requested width and height
                    if (Math.abs(width * height - supportedSize.width * supportedSize.height)
                            < Math.abs(width * height - size.width * size.height)) {
                        size.width = supportedSize.width;
                        size.height = supportedSize.height;
                    }
                }
            }
        }
        Log.d(TAG, "CameraPreview optimalPictureSize " + size.width + 'x' + size.height);
        return size;
    }

    public static Camera.Size getOptimalPreviewSize(int displayOrientation, List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) h / w;
        }

        if (sizes == null) {
            return null;
        }

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        Log.d("optimal preview size", "w: " + optimalSize.width + " h: " + optimalSize.height);
        return optimalSize;
    }


    public static int configureCameraAngle(Activity activity) {
        int angle;

        Display display = activity.getWindowManager().getDefaultDisplay();
        switch (display.getRotation()) {
            case Surface.ROTATION_90:
                angle = 0;
                break;
            case Surface.ROTATION_180:
                angle = 270;
                break;
            case Surface.ROTATION_270:
                angle = 180;
                break;
            default:
                angle = 90;
                break;
        }

        return angle;
    }

    private static double getAvgCorner(Mat mat) {
        Mat tmp = new Mat(mat.rows(), mat.cols(), CV_8UC1);
        Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_BGR2HSV, 4);
        float k = 0.7f;
        double[] previous = new double[3];
        for (int i = 0; i < mat.width(); i++) {
            double[] colors = mat.get(0, i);
            if (i == 0) {
                previous = colors;
            }
            else {
                previous[1] = k * colors[1] + (1 - k) * previous[1];
            }
        }
        tmp.release();

        return previous[1];
    }

    public static Quadrilateral detectLargestQuadrilateral(Mat mat) {
        try {
            Mat mGrayMat = new Mat(mat.rows(), mat.cols(), CV_8UC1);
            Mat dst = new Mat(mat.rows(), mat.cols(), CV_8UC1);
            Imgproc.cvtColor(mat, mGrayMat, Imgproc.COLOR_BGR2GRAY, 4);
            // Dilatazione per sfondo scuro
            int iterations = 1;

//            double avgCorner = getAvgCorner(mat);
//            if (avgCorner >= BACKGROUND_THRESHOLD) {
//                // Dilatazione per sfondo chiaro
//                iterations = 23;
//            }

            Imgproc.bilateralFilter(mGrayMat, dst, 11, 11, 11);
            Imgproc.adaptiveThreshold(dst, dst, 255, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY_INV, 115, 4);
            int border = 3;
            Core.copyMakeBorder(dst, dst, border, border, border, border, Core.BORDER_REFLECT_101);
            Imgproc.Canny(dst, dst, 50, 150);
            Imgproc.dilate(dst, dst, new Mat(), new Point(-1, -1), iterations);

            List<MatOfPoint> largestContour = findLargestContour(dst);
            if (null != largestContour) {
                Quadrilateral mLargestRect = findQuadrilateral(largestContour);
                if (mLargestRect != null) {
                    mGrayMat.release();
                    dst.release();
                    return mLargestRect;
                }
            }
            return null;
        }
        catch (Exception e) {
            return null;
        }
    }

    static double getMaxCosine(double maxCosine, Point[] approxPoints) {
        for (int i = 2; i < 5; i++) {
            double cosine = Math.abs(angle(approxPoints[i % 4], approxPoints[i - 2], approxPoints[i - 1]));
            Log.i(TAG, String.valueOf(cosine));
            maxCosine = Math.max(cosine, maxCosine);
        }
        return maxCosine;
    }

    private static double angle(Point p1, Point p2, Point p0) {
        double dx1 = p1.x - p0.x;
        double dy1 = p1.y - p0.y;
        double dx2 = p2.x - p0.x;
        double dy2 = p2.y - p0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    private static Point[] sortPoints(Point[] src) {
        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));
        Point[] result = {null, null, null, null};

        Comparator<Point> sumComparator = (lhs, rhs) ->
                Double.valueOf(lhs.y + lhs.x).compareTo(rhs.y + rhs.x);

        Comparator<Point> diffComparator = (lhs, rhs) ->
                Double.valueOf(lhs.y - lhs.x).compareTo(rhs.y - rhs.x);

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator);
        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator);
        // top-right corner = minimal difference
        result[1] = Collections.min(srcPoints, diffComparator);
        // bottom-left corner = maximal difference
        result[3] = Collections.max(srcPoints, diffComparator);

        return result;
    }

    private static List<MatOfPoint> findLargestContour(Mat inputMat) {
        Mat mHierarchy = new Mat();
        List<MatOfPoint> mContourList = new ArrayList<>();
        //finding contours
        Imgproc.findContours(inputMat, mContourList, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat mContoursMat = new Mat();
        mContoursMat.create(inputMat.rows(), inputMat.cols(), CvType.CV_8U);

        if (mContourList != null) {
            for (int i = 0; i < mContourList.size(); i++) {
                Imgproc.drawContours(inputMat, mContourList, i, new Scalar(0, 0, 255), 3);
//                Imgproc.drawContours(inputMat, mContourList, i, new Scalar(255, 0, 0), -1);
            }
        }

        if (mContourList.size() != 0) {
            Collections.sort(mContourList, (lhs, rhs) ->
                    Double.valueOf(Imgproc.contourArea(rhs)).compareTo(Imgproc.contourArea(lhs)));
            return mContourList;
        }
        return null;
    }

    private static Quadrilateral findQuadrilateral(List<MatOfPoint> mContourList) {
        for (MatOfPoint c : mContourList) {
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            double peri = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approx, 0.09 * peri, true);
            Point[] points = approx.toArray();
            // select biggest 4 angles polygon
            if (approx.rows() == 4) {
                Point[] foundPoints = sortPoints(points);
                return new Quadrilateral(approx, foundPoints);
            }
        }
        return null;
    }

    public static Bitmap enhanceReceipt(Bitmap image, Point topLeft, Point topRight, Point bottomLeft, Point bottomRight) {
        int resultWidth = (int) (topRight.x - topLeft.x);
        int bottomWidth = (int) (bottomRight.x - bottomLeft.x);
        if (bottomWidth > resultWidth)
            resultWidth = bottomWidth;

        int resultHeight = (int) (bottomLeft.y - topLeft.y);
        int bottomHeight = (int) (bottomRight.y - topRight.y);
        if (bottomHeight > resultHeight)
            resultHeight = bottomHeight;

        Mat inputMat = new Mat(image.getHeight(), image.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(image, inputMat);
        Mat outputMat = new Mat(resultWidth, resultHeight, CvType.CV_8UC1);

        List<Point> source = new ArrayList<>();
        source.add(topLeft);
        source.add(topRight);
        source.add(bottomLeft);
        source.add(bottomRight);
        Mat startM = Converters.vector_Point2f_to_Mat(source);

        Point ocvPOut1 = new Point(0, 0);
        Point ocvPOut2 = new Point(resultWidth, 0);
        Point ocvPOut3 = new Point(0, resultHeight);
        Point ocvPOut4 = new Point(resultWidth, resultHeight);
        List<Point> dest = new ArrayList<>();
        dest.add(ocvPOut1);
        dest.add(ocvPOut2);
        dest.add(ocvPOut3);
        dest.add(ocvPOut4);
        Mat endM = Converters.vector_Point2f_to_Mat(dest);

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM);

        Imgproc.warpPerspective(inputMat, outputMat, perspectiveTransform, new Size(resultWidth, resultHeight));

        Bitmap output = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputMat, output);
        return output;
    }

    public static void saveToInternalMemory(Context context, Bitmap bitmap, OnSaveListener onSaveListener) {
        new SaveToSdcard(context, onSaveListener).execute(bitmap);
    }

    public static void saveToInternalMemory(Context context, Uri uri, OnSaveListener onSaveListener) {
        new SavePdfToSdcard(context, onSaveListener).execute(uri);
    }

    public static void compress(Context context, String path, OnSaveListener onSaveListener) {
        if (!BuildConfig.COMPRESS_ENABLED) {
            onSaveListener.onError("Api non abilitata");
            return;
        }
        new Compress(context, onSaveListener).execute(path);
    }

    public static class SaveToSdcard extends AsyncTask<Bitmap, Integer, String[]> {

        private Context context;
        private OnSaveListener onSaveListener;

        SaveToSdcard(Context context, OnSaveListener onSaveListener) {
            this.context = context;
            this.onSaveListener = onSaveListener;
        }

        @Override
        protected String[] doInBackground(Bitmap... bitmaps) {

            String[] returnParams = new String[3];
            String fileName = IMAGE_NAME + System.currentTimeMillis() / 1000 + ".jpg";

            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmaps[0].compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, bos);
                byte[] bitmapdata = bos.toByteArray();
                ByteArrayInputStream bis = new ByteArrayInputStream(bitmapdata);
                File unisaluteFolder = new File(context.getExternalFilesDir(null).getPath());
                if (!unisaluteFolder.exists()) {
                    unisaluteFolder.mkdirs();
                    unisaluteFolder.setReadable(true, false);
                    unisaluteFolder.setWritable(true, false);
                    unisaluteFolder.setExecutable(true, false);
                }
                FileOutputStream fos = new FileOutputStream(new File(unisaluteFolder.getPath(), fileName));
                byte[] b = new byte[100*1024];
                int j;

                while ((j = bis.read(b)) != -1) {
                    fos.write(b, 0, j);
                }

                fos.flush();
                fos.getFD().sync();

                fos.close();
                bis.close();

                returnParams[0] = context.getExternalFilesDir(null) + "/" + fileName;
                returnParams[1] = IMG_TYPE;
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return returnParams;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            onSaveListener.onCompleted(strings);
        }
    }

    public static class SavePdfToSdcard extends AsyncTask<Uri, Integer, String[]> {

        private Context context;
        private OnSaveListener onSaveListener;

        SavePdfToSdcard(Context context, OnSaveListener onSaveListener) {
            this.context = context;
            this.onSaveListener = onSaveListener;
        }

        @Override
        protected String[] doInBackground(Uri... uri) {

            String[] returnParams = new String[3];
            final String fileName = getFileName(context, uri[0]);

            try {
                InputStream inputStream =  context.getContentResolver().openInputStream(uri[0]);
                BufferedInputStream bis = new BufferedInputStream(inputStream);
                File unisaluteFolder = new File(context.getExternalFilesDir(null).getPath());
                if (!unisaluteFolder.exists()) {
                    unisaluteFolder.mkdirs();
                    unisaluteFolder.setReadable(true, false);
                    unisaluteFolder.setWritable(true, false);
                    unisaluteFolder.setExecutable(true, false);
                }
                FileOutputStream fos = new FileOutputStream(new File(unisaluteFolder.getPath(), fileName));
                byte[] b = new byte[100*1024];
                int j;

                while ((j = bis.read(b)) != -1) {
                    fos.write(b, 0, j);
                }

                fos.flush();
                fos.getFD().sync();

                fos.close();
                bis.close();

                returnParams[0] = context.getExternalFilesDir(null) + "/" + fileName;
                returnParams[1] = PDF_TYPE;
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return returnParams;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            onSaveListener.onCompleted(strings);
        }
    }

    public static class Compress extends AsyncTask<String, Integer, String[]> {

        private Context context;
        private OnSaveListener onSaveListener;

        Compress(Context context, OnSaveListener onSaveListener) {
            this.context = context;
            this.onSaveListener = onSaveListener;
        }

        @Override
        protected String[] doInBackground(String... path) {

            String[] returnParams = new String[3];
            String fileName = IMAGE_NAME + System.currentTimeMillis() / 1000 + ".jpg";

            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                File image = new File(path[0]);
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);
                bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, bos);
                byte[] bitmapdata = bos.toByteArray();
                ByteArrayInputStream bis = new ByteArrayInputStream(bitmapdata);
                File unisaluteFolder = new File(context.getExternalFilesDir(null).getPath());
                if (!unisaluteFolder.exists()) {
                    unisaluteFolder.mkdirs();
                    unisaluteFolder.setReadable(true, false);
                    unisaluteFolder.setWritable(true, false);
                    unisaluteFolder.setExecutable(true, false);
                }
                FileOutputStream fos = new FileOutputStream(new File(unisaluteFolder.getPath(), fileName));
                byte[] b = new byte[100*1024];
                int j;

                while ((j = bis.read(b)) != -1) {
                    fos.write(b, 0, j);
                }

                fos.flush();
                fos.getFD().sync();

                fos.close();
                bis.close();

                returnParams[0] = context.getExternalFilesDir(null) + "/" + fileName;
                returnParams[1] = IMG_TYPE;
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return returnParams;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            onSaveListener.onCompleted(strings);
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals(SCHEME)) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
            finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * Restituisce la lista dei paths delle immagini memorizzate nella cartella quando sono richiesti dall'app Ionic
     *
     * @return pathArray
     */
    public static String[] getFilesList(Context context) {
        String[] pathArray = null;
        final File imagesFolder = new File(context.getExternalFilesDir(null).getPath());
        if (imagesFolder.listFiles() != null) {
            File[] paths = imagesFolder.listFiles();
            pathArray = new String[paths.length];
            for (int i = 0; i < paths.length; i++) {
                pathArray[i] = paths[i].getPath();
            }
        }
        return pathArray;
    }

    /**
     * Listener per avviso salvataggio terminato
     */
    public interface OnSaveListener {
        void onCompleted(String[] strings);
        void onError(String message);
    }

    private static File getBaseDirectoryFromPathString(String mPath, Context mContext) {
        ContextWrapper mContextWrapper = new ContextWrapper(mContext);
        return mContextWrapper.getDir(mPath, Context.MODE_PRIVATE);
    }

    public static Bitmap decodeBitmapFromFile(String imageName) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        return BitmapFactory.decodeFile(new File(imageName).getAbsolutePath(), options);
    }

    /*
     * This method converts the dp value to px
     * @param context context
     * @param dp value in dp
     * @return px value
     */
    public static int dp2px(Context context, float dp) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
        return Math.round(px);
    }

    public static Bitmap decodeBitmapFromByteArray(byte[] data, int reqWidth, int reqHeight) {
        // Raw height and width of image
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        options.inSampleSize = inSampleSize;

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    @Deprecated
    public static Bitmap loadEfficientBitmap(byte[] data, int width, int height) {
        Bitmap bmp;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, width, height);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return bmp;
    }

    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > 1) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }

            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }

    public static Bitmap resizeToScreenContentSize(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    public static ArrayList<PointF> getPolygonDefaultPoints(Bitmap bitmap) {
        ArrayList<PointF> points;
        points = new ArrayList<>();
        points.add(new PointF(bitmap.getWidth() * (0.14f), (float) bitmap.getHeight() * (0.13f)));
        points.add(new PointF(bitmap.getWidth() * (0.84f), (float) bitmap.getHeight() * (0.13f)));
        points.add(new PointF(bitmap.getWidth() * (0.14f), (float) bitmap.getHeight() * (0.83f)));
        points.add(new PointF(bitmap.getWidth() * (0.84f), (float) bitmap.getHeight() * (0.83f)));
        return points;
    }

    public static ArrayList<PointF> getPolygonFromLimitedArea(LimitedArea limitedArea) {
        Rect rect = limitedArea.getRect();
        ArrayList<PointF> points;
        points = new ArrayList<>();
        points.add(new PointF((float) rect.left, (float) rect.top));
        points.add(new PointF((float) rect.right, (float) rect.top));
        points.add(new PointF((float) rect.left, (float) rect.bottom));
        points.add(new PointF((float) rect.right, (float) rect.bottom));
        return points;
    }

    public static boolean isScanPointsValid(Map<Integer, PointF> points) {
        return points.size() == 4;
    }

    public static Bitmap modifyOrientation(Context context, Bitmap bitmap, Uri uri) throws IOException {
        String realPath = getRealPathFromURI(context, uri);
        if (realPath == null) return null;
        ExifInterface ei = new ExifInterface(realPath);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotate(bitmap, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotate(bitmap, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotate(bitmap, 270);
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return flip(bitmap, true, false);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return flip(bitmap, false, true);
            default:
                return bitmap;
        }
    }

    private static Bitmap rotate(Bitmap bitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static Bitmap flip(Bitmap bitmap, boolean horizontal, boolean vertical) {
        Matrix matrix = new Matrix();
        matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static String getRealPathFromURI(Context context, Uri uri) {
        try {
            String filePath = "";
            String wholeID = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                wholeID = DocumentsContract.getDocumentId(uri);
            }
            String id = wholeID.split(":")[1];
            String[] column = {MediaStore.Images.Media.DATA};
            String sel = MediaStore.Images.Media._ID + "=?";
            Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    column, sel, new String[]{id}, null);
            int columnIndex = cursor.getColumnIndex(column[0]);
            if (cursor.moveToFirst()) {
                filePath = cursor.getString(columnIndex);
            }
            cursor.close();
            return filePath;
        }
        catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Immagine non presente nel MediaStore");
            return null;
        }
    }
}
