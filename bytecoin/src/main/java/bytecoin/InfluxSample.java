package bytecoin;

import java.util.concurrent.TimeUnit;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

public class InfluxSample {

	public static void main(String[] args) {

		
//		DepthCache depthCacheObj = new DepthCache("BNBBTC");
//		depthCacheObj.initialize();
		

		InfluxDB influxDB = InfluxDBFactory.connect("http://localhost:8086");
		String dbName = "aTimeSeries";
		influxDB.query(new Query("CREATE DATABASE " + dbName));
		influxDB.setDatabase(dbName);
		String rpName = "aRetentionPolicy";
		influxDB.query(new Query("CREATE RETENTION POLICY " + rpName + " ON " + dbName + " DURATION 30h REPLICATION 2 SHARD DURATION 30m DEFAULT"));
		influxDB.setRetentionPolicy(rpName);

		influxDB.enableBatch(BatchOptions.DEFAULTS);

		influxDB.write(Point.measurement("cpu")
		    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
		    .addField("idle", 90L)
		    .addField("user", 9L)
		    .addField("system", 1L)
		    .build());


		Query query = new Query("SELECT idle FROM cpu", dbName);
		System.out.println(influxDB.query(query));
		
//		influxDB.query(new Query("DROP RETENTION POLICY " + rpName + " ON " + dbName));
//		influxDB.query(new Query("DROP DATABASE " + dbName));
//		influxDB.close();
		
	}

}

