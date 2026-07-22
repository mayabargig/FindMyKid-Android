package com.example.whereismychildapp.Objects;

import java.util.ArrayList;
import java.util.List;

public class Parent extends User {

    private List<String> childrenUids;
    private boolean notificationsEnabled;

    // חובה ל-Firebase
    public Parent() {
        super(); // constructor ריק של User
        this.userType = UserType.PARENT;
        this.childrenUids = new ArrayList<>();
        this.notificationsEnabled = true;
    }

    // constructor רגיל
    public Parent(String email, String nickname, String uid) {
        super(email, nickname, uid,UserType.PARENT);
        this.childrenUids = new ArrayList<>();
        this.notificationsEnabled = true;
    }

    // הוספת ילד
    public void addChild(String childUid) {
        if (childrenUids == null) {
            childrenUids = new ArrayList<>();
        }
        if (!childrenUids.contains(childUid)) {
            childrenUids.add(childUid);
        }
    }

    // הסרת ילד
    public void removeChild(String childUid) {
        if (childrenUids != null) {
            childrenUids.remove(childUid);
        }
    }

    // getters / setters
    public List<String> getChildrenUids() {
        return childrenUids;
    }

    public void setChildrenUids(List<String> childrenUids) {
        this.childrenUids = childrenUids;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
}
