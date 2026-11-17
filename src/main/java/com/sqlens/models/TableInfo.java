package com.sqlens.models;

import java.util.List;

/** Schema facts about one table, read from information_schema. */
public record TableInfo(
    String name,
    String engine,
    Long rowEstimate,
    List<Column> columns,
    List<Index> indexes
) {
    public record Column(String name, String type, boolean nullable, String key) {}

    public record Index(String name, boolean unique, List<String> columns) {}
}
