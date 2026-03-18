package com.sqlens.models;

public class ExplainRes {
    private int id;
    private String select_type;
    private String table;
    private String type;
    private String possible_keys;
    private String key;
    private int key_len;
    private String ref;
    private long rows;
    private double filtered;
    private String extra;

    public ExplainRes(int id, String select_type, String table, String type,
        String possible_keys, String key, int key_len, String ref, long rows,
        double filtered, String extra
    ) {
        this.id = id;
        this.select_type = select_type;
        this.table = table;
        this.type = type;
        this.possible_keys = possible_keys;
        this.key = key;
        this.key_len = key_len;
        this.ref = ref;
        this.rows = rows;
        this.filtered = filtered;
        this.extra = extra;
    }

    public int getId() { return id;}
    public String getSelectType() { return select_type;}
    public String getTable() { return table;}
    public String getType() { return type;}
    public String getPossibleKeys() { return possible_keys;}
    public String getKey() { return key;}
    public int getKeyLen() { return key_len;}
    public String getRef() { return ref;}
    public long getRows() { return rows;}
    public double getFiltered() { return filtered;}
    public String getExtra() { return extra;}
}
