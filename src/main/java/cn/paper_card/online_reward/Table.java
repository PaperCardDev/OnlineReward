package cn.paper_card.online_reward;

import cn.paper_card.database.api.Parser;
import cn.paper_card.database.api.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

class Table extends Parser<RewardInfo> {
    private final static String NAME = "online_reward";

    private final @NotNull Connection connection;

    private PreparedStatement psInsert = null;

    private PreparedStatement psQuery = null;

    Table(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s
                (
                    uid1      BIGINT       NOT NULL,
                    uid2      BIGINT       NOT NULL,
                    time      BIGINT       NOT NULL,
                    day_begin BIGINT       NOT NULL,
                    coins     BIGINT       NOT NULL,
                    comment   VARCHAR(128) NOT NULL,
                    PRIMARY KEY (uid1, uid2, day_begin)
                );""".formatted(NAME));
    }

    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getPsInsert() throws SQLException {
        if (this.psInsert == null) {
            this.psInsert = this.connection.prepareStatement("""
                    INSERT INTO %s (uid1, uid2, time, day_begin, coins, comment)
                    VALUES (?, ?, ?, ?, ?, ?);""".formatted(NAME));
        }
        return this.psInsert;
    }

    private @NotNull PreparedStatement getPsQuery() throws SQLException {
        if (this.psQuery == null) {
            this.psQuery = this.connection.prepareStatement("""
                    SELECT uid1, uid2, time, day_begin, coins, comment
                    FROM %s
                    WHERE (uid1, uid2) = (?, ?)
                      AND time > ?
                    LIMIT 1;""".formatted(NAME));
        }
        return this.psQuery;
    }

    int insert(@NotNull RewardInfo info) throws SQLException {
        final PreparedStatement ps = this.getPsInsert();
        ps.setLong(1, info.playerId().getMostSignificantBits());
        ps.setLong(2, info.playerId().getLeastSignificantBits());
        ps.setLong(3, info.time());
        ps.setLong(4, info.dayBegin());
        ps.setLong(5, info.coins());
        ps.setString(6, info.comment());

        return ps.executeUpdate();
    }

    @Nullable RewardInfo query(@NotNull UUID uuid, long time) throws SQLException {
        final PreparedStatement ps = this.getPsQuery();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, time);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOne(resultSet);
    }

    @Override
    public @NotNull RewardInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
        final long uid1 = resultSet.getLong(1);
        final long uid2 = resultSet.getLong(2);
        final long time = resultSet.getLong(3);
        final long dayBegin = resultSet.getLong(4);
        final long coins = resultSet.getLong(5);
        final String comment = resultSet.getString(6);
        return new RewardInfo(new UUID(uid1, uid2), time, dayBegin, coins, comment);
    }
}
