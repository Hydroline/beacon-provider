package com.hydroline.beacon.provider.mtr;

import com.hydroline.beacon.provider.config.BeaconConfigPaths;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RouteFinderCacheStorage implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(RouteFinderCacheStorage.class.getName());
    private final Connection connection;

    public RouteFinderCacheStorage() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + BeaconConfigPaths.getRoutefinderCachePath().toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE IF NOT EXISTS routefinder_cache (" +
                    "dimension TEXT NOT NULL," +
                    "route_id INTEGER NOT NULL," +
                    "railway_data_version INTEGER," +
                    "routefinder_version TEXT," +
                    "format_version TEXT," +
                    "polyline_json TEXT," +
                    "edges_json TEXT," +
                    "nodes_json TEXT," +
                    "state_json TEXT," +
                    "cost_json TEXT," +
                    "updated_at INTEGER NOT NULL," +
                    "PRIMARY KEY(dimension, route_id)" +
                ")"
            );
            statement.execute("CREATE INDEX IF NOT EXISTS idx_routefinder_cache_updated_at ON routefinder_cache(updated_at)");
        }
    }

    public void upsert(RouteFinderCacheEntry entry) {
        String sql = "INSERT OR REPLACE INTO routefinder_cache (" +
            "dimension, route_id, railway_data_version, routefinder_version," +
            "format_version, polyline_json, edges_json, nodes_json, state_json," +
            "cost_json, updated_at" +
        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.getDimension());
            statement.setLong(2, entry.getRouteId());
            if (entry.getRailwayDataVersion() != null) {
                statement.setInt(3, entry.getRailwayDataVersion());
            } else {
                statement.setNull(3, java.sql.Types.INTEGER);
            }
            statement.setString(4, entry.getRoutefinderVersion());
            statement.setString(5, entry.getFormatVersion());
            statement.setString(6, entry.getPolylineJson());
            statement.setString(7, entry.getEdgesJson());
            statement.setString(8, entry.getNodesJson());
            statement.setString(9, entry.getStateJson());
            statement.setString(10, entry.getCostJson());
            statement.setLong(11, entry.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to upsert routefinder cache entry", ex);
        }
    }

    public Optional<RouteFinderCacheEntry> read(String dimension, long routeId) {
        String sql = "SELECT * FROM routefinder_cache WHERE dimension = ? AND route_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimension);
            statement.setLong(2, routeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Integer railwayVersion = resultSet.getInt("railway_data_version");
                if (resultSet.wasNull()) {
                    railwayVersion = null;
                }
                return Optional.of(new RouteFinderCacheEntry(
                    resultSet.getString("dimension"),
                    resultSet.getLong("route_id"),
                    railwayVersion,
                    resultSet.getString("routefinder_version"),
                    resultSet.getString("format_version"),
                    resultSet.getString("polyline_json"),
                    resultSet.getString("edges_json"),
                    resultSet.getString("nodes_json"),
                    resultSet.getString("state_json"),
                    resultSet.getString("cost_json"),
                    resultSet.getLong("updated_at")
                ));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to read routefinder cache entry", ex);
            return Optional.empty();
        }
    }

    public Optional<RouteFinderCacheEntrySummary> readSummary(String dimension, long routeId) {
        String sql = "SELECT dimension, route_id, railway_data_version, routefinder_version, format_version, updated_at FROM routefinder_cache WHERE dimension = ? AND route_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimension);
            statement.setLong(2, routeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Integer railwayVersion = resultSet.getInt("railway_data_version");
                if (resultSet.wasNull()) {
                    railwayVersion = null;
                }
                return Optional.of(new RouteFinderCacheEntrySummary(
                    resultSet.getString("dimension"),
                    resultSet.getLong("route_id"),
                    railwayVersion,
                    resultSet.getString("routefinder_version"),
                    resultSet.getString("format_version"),
                    resultSet.getLong("updated_at")
                ));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to read routefinder cache summary", ex);
            return Optional.empty();
        }
    }

    public java.util.List<RouteFinderCacheEntrySummary> list(String dimension) {
        String sql = "SELECT dimension, route_id, railway_data_version, routefinder_version, format_version, updated_at FROM routefinder_cache WHERE dimension = ? ORDER BY route_id";
        java.util.List<RouteFinderCacheEntrySummary> results = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dimension);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer railwayVersion = resultSet.getInt("railway_data_version");
                    if (resultSet.wasNull()) {
                        railwayVersion = null;
                    }
                    results.add(new RouteFinderCacheEntrySummary(
                        resultSet.getString("dimension"),
                        resultSet.getLong("route_id"),
                        railwayVersion,
                        resultSet.getString("routefinder_version"),
                        resultSet.getString("format_version"),
                        resultSet.getLong("updated_at")
                    ));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to list routefinder cache entries", ex);
        }
        return results;
    }

    public void deleteMissingRoutes(String dimension, Collection<Long> routeIds) {
        if (dimension == null) {
            return;
        }
        if (routeIds == null || routeIds.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM routefinder_cache WHERE dimension = ?")) {
                statement.setString(1, dimension);
                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Failed to delete routefinder cache entries for dimension", ex);
            }
            return;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM routefinder_cache WHERE dimension = ? AND route_id NOT IN (");
        int count = 0;
        for (int i = 0; i < routeIds.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            count++;
        }
        sql.append(")");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, dimension);
            int index = 2;
            for (Long id : routeIds) {
                statement.setLong(index++, id);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to delete missing routefinder cache entries", ex);
        }
    }

    public void deleteStale(long olderThanMillis) {
        String sql = "DELETE FROM routefinder_cache WHERE updated_at < ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, olderThanMillis);
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to delete stale routefinder cache entries", ex);
        }
    }

    public void deleteByVersion(String routefinderVersion) {
        String sql = "DELETE FROM routefinder_cache WHERE routefinder_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, routefinderVersion);
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to delete routefinder cache entries for version", ex);
        }
    }

    public void deleteByVersionMismatch(String routefinderVersion, Integer railwayDataVersion) {
        String sql = "DELETE FROM routefinder_cache WHERE routefinder_version != ? OR railway_data_version != ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, routefinderVersion);
            if (railwayDataVersion != null) {
                statement.setInt(2, railwayDataVersion);
            } else {
                statement.setNull(2, java.sql.Types.INTEGER);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to delete routefinder cache entries for mismatched versions", ex);
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to close routefinder cache connection", ex);
        }
    }
}
