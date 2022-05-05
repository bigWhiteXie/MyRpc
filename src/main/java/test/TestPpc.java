package test;

import client.ClientManager;
import service.UserService;

public class TestPpc {
    public static void main(String[] args) {
        UserService proxy = ClientManager.getServiceProxy(UserService.class);
        System.out.println(proxy.getMy());
    }
}
