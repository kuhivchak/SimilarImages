package com.image.logic;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.opencv.imgproc.Imgproc.resize;

/**
 * TODO
 */
public class Finder {
    static FeatureDetector detector;
    static DescriptorExtractor descriptor;
    static DescriptorMatcher matcher;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        detector = FeatureDetector.create(FeatureDetector.ORB);
        descriptor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    }

    public static double match(ImageData imageData1, ImageData imageData2) {

// MATCHING
// match these two keypoints sets
        List<MatOfDMatch> matches = findMatches(imageData1.getDescriptor(), imageData2.getDescriptor(), 5);
        List<DMatch> goodMatches = filterMatches(matches);
        List<DMatch> bestMatches = filterHomography(goodMatches, imageData1.getKeyPoints(), imageData2.getKeyPoints());
        //Sort
        sortBestMatches(bestMatches);

        MatOfDMatch topTenBestMatches = new MatOfDMatch();
        topTenBestMatches.fromList(bestMatches);

        //corners(bestMatches, topTenBestMatches, imageData1.getKeyPoints(), imageData2.getKeyPoints());

       /* Mat outputImg = drawImage(imageData1.getImage(), imageData1.getKeyPoints(), imageData2.getImage(), imageData2.getKeyPoints(), topTenBestMatches);

        Imgcodec*//*s.imwrite("result.jpg", outputImg);*/

        /*System.out.println("Згенеровано КТ1: " + imageData1.getKeyPoints().toArray().length);
        System.out.println("Згенеровано КТ2: " + imageData2.getKeyPoints().toArray().length);
        System.out.println("Спільних точок: " + bestMatches.size());
        System.out.println("Відсоток спільних точок: " + (((double) bestMatches.size() / imageData2.getKeyPoints().toArray().length) * 100));*/
double result = (((double) bestMatches.size() / imageData2.getKeyPoints().toArray().length) * 100);
        return result;
    }

    public static ImageData createImageData(String fileName) {
        Mat loadedImage = loadImage(fileName);
        loadedImage = resizeImage(loadedImage, new Size(256, 256));
        MatOfKeyPoint keyPointsResizedImage = detect(loadedImage);
        Mat descriptorImage = compute(loadedImage, keyPointsResizedImage);

        return new ImageData(loadedImage, keyPointsResizedImage, descriptorImage);
    }

    private static Mat drawImage(Mat loadedImage, MatOfKeyPoint keyPointsResizedImage, Mat loadedImage2, MatOfKeyPoint keyPointsResizedImage2, MatOfDMatch topTenBestMatches) {
        // DRAWING OUTPUT
        Mat outputImg = new Mat();
        // this will draw all matches, works fine
        MatOfDMatch better_matches_mat = new MatOfDMatch();
        better_matches_mat.fromList(topTenBestMatches.toList());
        Features2d.drawMatches(loadedImage, keyPointsResizedImage, loadedImage2, keyPointsResizedImage2, topTenBestMatches, outputImg);

        return outputImg;
    }

    private static void corners(List<DMatch> bestMatches, MatOfDMatch topTenBestMatches, MatOfKeyPoint keyPointsResizedImage, MatOfKeyPoint keyPointsResizedImage2) {
        ArrayList<KeyPoint> bestKeyPoints1 = new ArrayList<>();
        ArrayList<KeyPoint> bestKeyPoints2 = new ArrayList<>();

        List<KeyPoint> keyPoints = keyPointsResizedImage.toList();
        List<KeyPoint> keyPoints2 = keyPointsResizedImage2.toList();

        for (int i = 0; i < topTenBestMatches.toList().size(); i++) {
            bestKeyPoints1.add(keyPoints.get(bestMatches.get(i).queryIdx));
            bestKeyPoints2.add(keyPoints2.get(bestMatches.get(i).trainIdx));
        }

        int counter = 0;
        for (int i = 2; i < bestKeyPoints1.size(); i++) {
            Radius.A = bestKeyPoints1.get(0).pt;
            Radius.B = bestKeyPoints1.get(1).pt;
            Radius radius = new Radius(bestKeyPoints1.get(i).pt);

            Radius.A = bestKeyPoints2.get(0).pt;
            Radius.B = bestKeyPoints2.get(1).pt;
            Radius radius2 = new Radius(bestKeyPoints2.get(i).pt);

            counter += Math.abs((radius.kut - radius2.kut));
        }
        //System.out.println("Різниця = " + counter);
    }

    private static void sortBestMatches(List<DMatch> bestMatches) {
        Collections.sort(bestMatches, (o1, o2) -> {
            if (o1.distance < o2.distance) {
                return -1;
            }
            if (o1.distance > o2.distance) {
                return 1;
            }
            return 0;
        });
    }

    private static List<DMatch> filterHomography(List<DMatch> goodMatches, MatOfKeyPoint keyPointsResizedImage, MatOfKeyPoint keyPointsResizedImage2) {
        List<Point> pts1 = new ArrayList<>();
        List<Point> pts2 = new ArrayList<>();
        for (DMatch good_match : goodMatches) {
            pts1.add(keyPointsResizedImage.toList().get(good_match.queryIdx).pt);
            pts2.add(keyPointsResizedImage2.toList().get(good_match.trainIdx).pt);
        }
        // convertion of data types - there is maybe a more beautiful way
        Mat outputMask = new Mat();
        MatOfPoint2f pts1Mat = new MatOfPoint2f();
        pts1Mat.fromList(pts1);
        MatOfPoint2f pts2Mat = new MatOfPoint2f();
        pts2Mat.fromList(pts2);
        // Find homography - here just used to perform match filtering with RANSAC, but could be used to e.g. stitch images
        // the smaller the allowed reprojection error (here 15), the more matches are filtered
        Calib3d.findHomography(pts1Mat, pts2Mat, Calib3d.RANSAC, 15, outputMask, 2000, 0.995);
        // outputMask contains zeros and ones indicating which matches are filtered
        List<DMatch> bestMatches = new ArrayList<>();
        for (int i = 0; i < goodMatches.size(); i++) {
            if (outputMask.get(i, 0)[0] != 0.0) {
                bestMatches.add(goodMatches.get(i));
            }
        }

        return bestMatches;
    }

    private static ArrayList<DMatch> filterMatches(List<MatOfDMatch> matches) {
        ArrayList<DMatch> goodMatches = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : matches) {
            if (matOfDMatch.toArray()[0].distance / matOfDMatch.toArray()[1].distance < 0.9) {
                goodMatches.add(matOfDMatch.toArray()[0]);
            }
        }

        return goodMatches;
    }

    private static List<MatOfDMatch> findMatches(Mat descriptorImage, Mat descriptorImage2, int i) {
        List<MatOfDMatch> matches = new ArrayList<>();
        matcher.knnMatch(descriptorImage, descriptorImage2, matches, i);

        return matches;
    }

    private static Mat compute(Mat loadedImage, MatOfKeyPoint keypointsResizedImage) {
        Mat mat = new Mat();
        descriptor.compute(loadedImage, keypointsResizedImage, mat);

        return mat;
    }

    private static MatOfKeyPoint detect(Mat loadedImage) {
        MatOfKeyPoint matOfKeyPoint = new MatOfKeyPoint();
        detector.detect(loadedImage, matOfKeyPoint);

        return matOfKeyPoint;
    }

    public static Mat resizeImage(Mat sourse, Size newSize) {
        Mat resizeMat = new Mat();
        resize(sourse, resizeMat, newSize);

        return resizeMat;
    }

    public static Mat loadImage(String filename) {
        Mat loadedImage = Imgcodecs.imread(filename);

        return loadedImage;
    }
}

