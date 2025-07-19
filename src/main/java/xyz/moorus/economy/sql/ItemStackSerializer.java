package xyz.moorus.economy.sql;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ItemStackSerializer {

    // Сериализация ItemStack в строку Base64
    public static String serializeItemStack(ItemStack item) {
        if (item == null) return null;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream);

            // Сериализуем Map, который возвращает ItemStack#serialize()
            Map<String, Object> serializedItem = item.serialize();
            dataOutput.writeObject(serializedItem);

            // Конвертируем в Base64 строку
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Десериализация ItemStack из строки Base64
    public static ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) return null;

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            ObjectInputStream dataInput = new ObjectInputStream(inputStream);

            // Читаем сериализованный Map
            @SuppressWarnings("unchecked")
            Map<String, Object> serializedItem = (Map<String, Object>) dataInput.readObject();

            // Восстанавливаем ItemStack
            return ItemStack.deserialize(serializedItem);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
