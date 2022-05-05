package service;


import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceFactory {
    private static Properties properties;
    private static Map<Class<?>,Object> map = new ConcurrentHashMap<>();

    static {
        InputStream resource = ServiceFactory.class.getResourceAsStream("/application.properties");
        properties = new Properties();
        try {
            properties.load(resource);
            Set<String> strings = properties.stringPropertyNames();
            for(String s:strings){
                if(s.endsWith("Service")){
                    Class<?> interfaceClass = Class.forName(s);
                    Class<?> instantClass = Class.forName(properties.getProperty(s));
                    map.put(interfaceClass,instantClass.newInstance());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public  static <T> T getServiceImpl(Class<T> clazz){
        return (T) map.get(clazz);
    }
}
