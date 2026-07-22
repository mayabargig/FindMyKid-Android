package com.example.whereismychildapp.Objects;

public class User {

    public enum UserType {
        PARENT,
        CHILD
    }

    protected String email;
    protected String nickname;
    protected String uid;
    protected UserType userType;


    public User() { }

    public User(String email, String nickname, String uid, UserType userType) {
        this.email = email;
        this.nickname = nickname;
        this.uid = uid;
        this.userType = userType;
    }



    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }
}
