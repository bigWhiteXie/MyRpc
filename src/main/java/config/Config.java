package config;

import protocol.MySerializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static Properties properties;
    static {
        InputStream resource = Config.class.getResourceAsStream("/application.properties");
        properties = new Properties();
        try {
            properties.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MySerializer.Algorithm getSeriliztionAlgortithm(){
        final String value = properties.getProperty("mySerializer.algorithm");
        if(value == null)
        {
            return MySerializer.Algorithm.Java;
        }else{
            // 拼接成  MySerializer.Algorithm.Java 或 MySerializer.Algorithm.Json
            return MySerializer.Algorithm.valueOf(value);
        }
    }
}
