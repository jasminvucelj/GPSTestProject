package com.example.lux1410.testproject;

import android.location.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class LocationOutlierFinder {
    private static final int LATITUDE_INDEX = 0;
    private static final int LONGITUDE_INDEX = 1;
    private static final double IQR_COEF = 1.5;

    private static ArrayList<Double> latitudeList;
    private static ArrayList<Double> longitudeList;


    static List<Location> removeOutliers(List<Location> dataset) {

        if (dataset == null || dataset.isEmpty()) {
            return dataset;
        }

        double median[] = new double[2];
        double[][] Q = new double[2][2]; // [lat=0/lon=1][Q1=0/Q3=1]
        double[] IQR = new double[2];

        makeSortedLists(dataset);

        median[LATITUDE_INDEX] = findMedian(latitudeList);
        median[LONGITUDE_INDEX] = findMedian(longitudeList);

        Q[LATITUDE_INDEX] = findQValues(latitudeList, median[LATITUDE_INDEX]);
        Q[LONGITUDE_INDEX] = findQValues(longitudeList, median[LONGITUDE_INDEX]);

        // IQR = Q3 - Q1
        IQR[LATITUDE_INDEX] = Q[LATITUDE_INDEX][1] - Q[LATITUDE_INDEX][0];
        IQR[LONGITUDE_INDEX] = Q[LONGITUDE_INDEX][1] - Q[LONGITUDE_INDEX][0];

        return eliminateOutliers(dataset, Q, IQR);
    }


    private static void makeSortedLists(List<Location> dataset) {
        latitudeList = new ArrayList<>();
        longitudeList = new ArrayList<>();

        for (Location l : dataset) {
            latitudeList.add(l.getLatitude());
            longitudeList.add(l.getLongitude());
        }

        Collections.sort(latitudeList);
        Collections.sort(longitudeList);
    }


    // sort!
    private static double findMedian(List<Double> dataset) {
        int size = dataset.size();
        if (size == 0) {
            return 0;
        }
        else if (size % 2 == 1) {
            return dataset.get(size/2);
        }
        else {
            return (dataset.get(size/2) + dataset.get(size/2 - 1)) / 2.0;
        }
    }


    // sort!
    private static double[] findQValues(List<Double> dataset, double median) {

        List<Double> lowerHalf = new ArrayList<>();
        List<Double> upperHalf = new ArrayList<>();

        for (Double d : dataset) {
            if (d < median) {
                lowerHalf.add(d);
            }
            else if (d > median) {
                upperHalf.add(d);
            }
            else {
                lowerHalf.add(d);
                upperHalf.add(d);
            }
        }

        return new double[] {findMedian(lowerHalf), findMedian(upperHalf)};
    }


    private static List<Location> eliminateOutliers(List<Location> oldDataset, double[][] Q, double[] IQR) {
        List<Location> newDataset = new ArrayList<>();

        double[][] bounds = new double[2][2]; // [lat=0/lon=1][lower=0/upper=1]
        bounds[LATITUDE_INDEX][0] = Q[LATITUDE_INDEX][0] - IQR_COEF * IQR[LATITUDE_INDEX];
        bounds[LATITUDE_INDEX][1] = Q[LATITUDE_INDEX][1] + IQR_COEF * IQR[LATITUDE_INDEX];
        bounds[LONGITUDE_INDEX][0] = Q[LONGITUDE_INDEX][0] - IQR_COEF * IQR[LONGITUDE_INDEX];
        bounds[LONGITUDE_INDEX][1] = Q[LONGITUDE_INDEX][1] + IQR_COEF * IQR[LONGITUDE_INDEX];

        for (Location l : oldDataset) {
            double lat = l.getLatitude();
            if (lat < bounds[LATITUDE_INDEX][0] || lat > bounds[LATITUDE_INDEX][1]) {
                continue;
            }

            double lon = l.getLongitude();
            if (lon < bounds[LONGITUDE_INDEX][0] || lon > bounds[LONGITUDE_INDEX][1]) {
                continue;
            }

            newDataset.add(l);
        }

        return newDataset;
    }

}

