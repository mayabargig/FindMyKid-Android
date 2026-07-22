package com.example.whereismychildapp.Objects;

import java.util.ArrayList;
import java.util.List;

public class Child extends User {

    private double latitude;
    private double longitude;
    private boolean emergencyMode;
    private List<String> parentUids;

    // חובה ל-Firebase
    public Child() {
        super();
        this.userType = UserType.CHILD;
        this.emergencyMode = false;
        this.parentUids = new ArrayList<>();
    }

    // constructor רגיל
    public Child(String email, String nickname, String uid) {
        super(email, nickname, uid, UserType.CHILD);
        this.emergencyMode = false;
        this.parentUids = new ArrayList<>();
    }


    public void updateLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    // ===== חירום =====
    public boolean isEmergencyMode() {
        return emergencyMode;
    }

    public void setEmergencyMode(boolean emergencyMode) {
        this.emergencyMode = emergencyMode;
    }

    // ===== הורים =====
    public List<String> getParentUids() {
        return parentUids;
    }

    public void setParentUids(List<String> parentUids) {
        this.parentUids = parentUids;
    }

    public void addParent(String parentUid) {
        if (parentUids == null) {
            parentUids = new ArrayList<>();
        }
        if (!parentUids.contains(parentUid)) {
            parentUids.add(parentUid);
        }
    }

    public void removeParent(String parentUid) {
        if (parentUids != null) {
            parentUids.remove(parentUid);
        }
    }
}
