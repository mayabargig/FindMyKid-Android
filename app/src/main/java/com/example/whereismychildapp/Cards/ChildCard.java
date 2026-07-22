package com.example.whereismychildapp.Cards;

public class ChildCard {
    public String uid;
    public String nickname;
    public Long lastTimestamp;

    public ChildCard(String uid, String nickname, Long lastTimestamp) {
        this.uid = uid;
        this.nickname = nickname;
        this.lastTimestamp = lastTimestamp;
    }
}

