package bytecoin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Entry {

	public static void main(String[] args) throws FileNotFoundException, IOException {

		List<String> coinList = new ArrayList<>();
		ExecutorService depthCacheRunnerThreadPool = Executors.newFixedThreadPool(50);

		Entry main = new Entry();
		File file = main.getFileFromResources("coin_pair_test.list");

		if (file == null)
			return;
		try (FileReader reader = new FileReader(file); BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				coinList.add(line);
			}
		}

		for (String coin : coinList) {
			depthCacheRunnerThreadPool.execute(new DepthCacheRunner(coin));
		}
	}

	// get file from classpath, resources folder
	private File getFileFromResources(String fileName) {
		ClassLoader classLoader = getClass().getClassLoader();
		URL resource = classLoader.getResource(fileName);

		if (resource == null) {
			throw new IllegalArgumentException("file is not found!");
		} else {
			return new File(resource.getFile());
		}
	}

}
