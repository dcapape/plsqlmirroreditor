package com.diegocapape.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TableInfo {
    private String tableName;
    private List<ColumnInfo> columns = new java.util.ArrayList<>();

    public TableInfo(String name) {
        this.tableName = name;
    }

    public void addColumn(ColumnInfo columnInfo) {
        this.columns.add(columnInfo);
    }
}