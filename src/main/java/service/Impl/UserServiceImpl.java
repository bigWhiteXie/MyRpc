package service.Impl;

import service.UserService;

public class UserServiceImpl implements UserService {
    private String userName;
    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public String getMy() {
        return "xielei";
    }


}
