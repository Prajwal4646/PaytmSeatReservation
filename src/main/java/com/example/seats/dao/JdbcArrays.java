package com.example.seats.dao;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Small helper for binding Postgres text[] parameters. */
final class JdbcArrays {
    private JdbcArrays() {}

    static Array textArray(Connection con, List<String> values) throws SQLException {
        return con.createArrayOf("text", values.toArray());
    }
}
