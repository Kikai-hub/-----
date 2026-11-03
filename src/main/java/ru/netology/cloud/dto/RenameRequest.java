package ru.netology.cloud.dto;

/**
 * Тело PUT /file. Спецификация называет поле "name", а FRONT присылает "filename" — принимаем оба.
 */
public record RenameRequest(String name, String filename) {

    public String newName() {
        return name != null ? name : filename;
    }
}
