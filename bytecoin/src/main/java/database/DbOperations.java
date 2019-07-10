package database;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;

public class DbOperations {

	private InfluxDB influxDB;

	public DbOperations() {
		super();
		this.influxDB = InfluxDBFactory.connect("http://localhost:8086");
		setUpDb("binance", "aRetentionPolicy");
	}

	public Boolean checkDbStatus() {
		Pong response = influxDB.ping();
		if (response.getVersion().equalsIgnoreCase("unknown")) {
			System.out.println("Error pinging server.");
			return false;
		}
		System.out.println(response.getVersion());
		return true;
	}

	public void setUpDb(String dbName, String retentionPolicyName) {
		influxDB.query(new Query("CREATE DATABASE " + dbName));
		influxDB.setDatabase(dbName);

		influxDB.query(new Query("CREATE RETENTION POLICY " + retentionPolicyName + " ON " + dbName
				+ " DURATION 30h REPLICATION 2 SHARD DURATION 30m DEFAULT"));
		influxDB.setRetentionPolicy(retentionPolicyName);

		influxDB.enableBatch(100, 200, TimeUnit.MILLISECONDS);

	}

	public void DbWrite(String measurement, long time_epoch,
			Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache) {
		Point point = Point.measurement(measurement).time(time_epoch, TimeUnit.MILLISECONDS)
				.addField("depthEvent", depthCache.toString()).build();

		influxDB.write(point);
	}

}
