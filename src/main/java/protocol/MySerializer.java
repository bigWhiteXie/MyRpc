package protocol;

import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public interface MySerializer {
    <T> byte[] serializ(T obj) throws IOException;
    <T> T deserializ(Class<T> clazz, byte[] bytes) throws IOException, ClassNotFoundException;

    enum Algorithm implements MySerializer{
        Java{
            @Override
            public <T> byte[] serializ(T obj) throws IOException {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(obj);
                return bos.toByteArray();
            }

            @Override
            public <T> T deserializ(Class<T> clazz, byte[] bytes) throws IOException, ClassNotFoundException {
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
                T o = (T) ois.readObject();
                return o;
            }
        },

        Json{
            @Override
            public <T> byte[] serializ(T obj) throws IOException {
                Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new ClassCodec()).create();
                byte[] bytes = gson.toJson(obj).getBytes(StandardCharsets.UTF_8);
                return bytes;
            }

            @Override
            public <T> T deserializ(Class<T> clazz, byte[] bytes) throws IOException, ClassNotFoundException {
                Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new ClassCodec()).create();

                String json = new String(bytes, StandardCharsets.UTF_8);
                T t = gson.fromJson(json, clazz);
                return t;
            }
        }

    }

    class ClassCodec implements JsonSerializer<Class<?>>, JsonDeserializer<Class<?>> {

        @Override
        public Class<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                String str = json.getAsString();
                return Class.forName(str);
            } catch (ClassNotFoundException e) {
                throw new JsonParseException(e);
            }
        }

        @Override             //   String.class
        public JsonElement serialize(Class<?> src, Type typeOfSrc, JsonSerializationContext context) {
            // class -> json
            return new JsonPrimitive(src.getName());
        }
    }
}
