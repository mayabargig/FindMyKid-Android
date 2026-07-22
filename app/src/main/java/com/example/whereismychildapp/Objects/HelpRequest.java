package com.example.whereismychildapp.Objects;

public class HelpRequest {
    public String requestId;
    public String childUid;

    public String childName;
    public double latitude;
    public double longitude;
    public String note;

    public String status;   // "open" / "handled"
    public long timestamp;

    public HelpRequest() {}

    public HelpRequest(String childUid, String requestId, long timestamp,
                       double latitude, double longitude, String note, String childName, String status) {
        this.childUid = childUid;
        this.requestId = requestId;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.note = note;
        this.childName = childName;
        this.status = status;
    }
}
