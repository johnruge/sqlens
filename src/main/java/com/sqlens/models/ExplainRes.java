package com.sqlens.models;

/**
 * One row of MySQL's traditional EXPLAIN output.
 * Boxed types are used because MySQL returns NULL for several columns
 * (e.g. id is NULL for UNION RESULT rows, key_len is NULL when no index is used).
 * key_len is a String because index_merge plans report values like "4,4".
 */
public record ExplainRes(
    Integer id,
    String selectType,
    String table,
    String type,
    String possibleKeys,
    String key,
    String keyLen,
    String ref,
    Long rows,
    Double filtered,
    String extra
) {}
