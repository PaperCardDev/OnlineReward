package cn.paper_card.online_reward;

import cn.paper_card.database.api.DatabaseApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

class RewardServiceImpl implements RewardService {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Connection connection = null;
    private Table table = null;

    RewardServiceImpl(DatabaseApi.@NotNull MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    void close() throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t = this.table;

            this.connection = null;
            this.table = null;

            if (t != null) t.close();
        }
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.table = new Table(newCon);
        this.connection = newCon;
        return this.table;
    }

    @Override
    public @Nullable RewardInfo queryOneTimeAfter(@NotNull UUID uuid, long time) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final RewardInfo info = t.query(uuid, time);
                this.mySqlConnection.setLastUseTime();
                return info;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public void add(@NotNull RewardInfo info) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int inserted = t.insert(info);
                this.mySqlConnection.setLastUseTime();
                if (inserted != 1) throw new RuntimeException("插入了%d条数据！");
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }
}
