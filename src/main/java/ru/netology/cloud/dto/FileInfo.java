package ru.netology.cloud.dto;

/**
 * @param editedAt время последнего изменения, миллисекунды Unix (FRONT показывает его в колонке «Дата изменения»)
 */
public record FileInfo(String filename, long size, long editedAt) {
}
