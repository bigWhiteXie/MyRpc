package test;

import service.ServiceFactory;
import service.UserService;

public class ServiceFactoryTest {
    public static void main(String[] args) throws ClassNotFoundException {
        UserService serviceImpl = ServiceFactory.getServiceImpl(UserService.class);
        System.out.println(serviceImpl.getMy());

    }
}
