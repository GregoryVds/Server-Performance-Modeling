/**
 * Measurement3 acts a load generator to perform various measurements required by HW2.
 * 
 * Specifically, we will measure the performance with and without caching and compare them.
 *   
 * @author      Grégory Vander Schueren
 * @author      Jérôme Lemaire
 * @date 		December 24h, 2015
 */

///////////////////////////////////////////////////////////////////////////////////////////////////

package plotters;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;

import client.Client;
import client.ComputationResult;
import client.RecordingResult;
import lib.Constants;
import lib.Lib;

import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.ExponentialDistribution;

///////////////////////////////////////////////////////////////////////////////////////////////////

public class Measurement3 {
	static Client client;
	static ExecutorService threadPool;
	static String input;
	
	// SETUP MEASUREMENT 3.A - Response Time with/without cache - 1 Thread 

	static final int MIN_REQUEST_RATE 			= 1;
	static final int MAX_REQUEST_RATE 			= 35;
	static final int REQUEST_RATE_INCREMENT 	= 1;
	static final int MATRIX_SIZE 				= 4;
	static final int REQUESTS_PER_SAMPLE 		= 150;
	static final boolean USE_RANDOM_SLEEP_TIME 	= true;
	static final boolean USE_RANDOM_DIFFICULTY 	= true;
	static final double DIFFICULTY_MEAN 		= 50000;
	static final double DIFFICULTY_STDEV 		= 100;
	static String PLOT1_TITLE 			= "Average response time (Cache vs no-cache)";
	static String PLOT1_X_AXIS_LABEL 	= "Mean Request Rate (exponential distribution)";
	static String PLOT1_Y_AXIS_LABEL 	= "Response Time (ms)";
	
	///////////////////////////////////////////////////////////////////////////////////////////////
	
	public static void main(String[] args) throws Exception {
		client = new Client(Constants.URL);
		threadPool = Executors.newCachedThreadPool();
		input = Lib.generateSquareMatrix(MATRIX_SIZE);		
		final JFreeChart chart = ChartFactory.createXYLineChart(PLOT1_TITLE, PLOT1_X_AXIS_LABEL, PLOT1_Y_AXIS_LABEL, createDataset());
		threadPool.shutdown();
		ChartApp.displayChart(chart, PLOT1_TITLE);
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	
	private static XYDataset createDataset() throws Exception {		
		XYSeries avgReponseTimeWithCache 	= new XYSeries("Avg. Response Time with caching", false, false);
		XYSeries avgReponseTimeWithoutCache = new XYSeries("Avg. Response Time without caching", false, false);
	
		// Firt, without caching.
		client.issueDisableCachingRequest();
		simulate(avgReponseTimeWithoutCache, false);
		
		// Second, with caching.
		simulate(avgReponseTimeWithCache, true);
				
		// Create dataset
        final DefaultTableXYDataset dataset = new DefaultTableXYDataset();
		dataset.addSeries(avgReponseTimeWithCache);
		dataset.addSeries(avgReponseTimeWithoutCache);

		return dataset;
    }
	
	///////////////////////////////////////////////////////////////////////////////////////////////
	
	private static void simulate(XYSeries serie, boolean caching) throws Exception {		
		for (int requestRate=MIN_REQUEST_RATE; requestRate <= MAX_REQUEST_RATE; requestRate+=REQUEST_RATE_INCREMENT) {
			if (caching) {
				client.issueStartRecordingRequest();
				client.issueEnableCachingRequest();	
			}
			
			System.out.println(String.format("Starte new sample for request rate of %d (Sleep avg: %f)", requestRate, 1.0/requestRate));
			// Instantiate new exponential distribution with current request rate.
			ExponentialDistribution randomInterRequest 	= new ExponentialDistribution(1.0/requestRate);
			NormalDistribution randomDifficulty 		= new NormalDistribution(DIFFICULTY_MEAN, DIFFICULTY_STDEV);

			// Create array to hold futures.
			@SuppressWarnings("unchecked")
			Future<ComputationResult>[] futures = (Future<ComputationResult>[]) new Future[REQUESTS_PER_SAMPLE];
			
			for (int i=0; i<REQUESTS_PER_SAMPLE; i++) {
				// Prepare Callable.
				int randomDiff = (int)randomDifficulty.sample();
		        Callable<ComputationResult> asyncRequest = () -> {
		            return client.issueComputationRequest(input, USE_RANDOM_DIFFICULTY ? randomDiff : (int)DIFFICULTY_MEAN);
		        };
		        
				// Issue request and save future value.
				futures[i] = threadPool.submit(asyncRequest);

				long sleepTime = USE_RANDOM_SLEEP_TIME ? (long)(randomInterRequest.sample()*1000*1000*1000) : (long) (1.0/requestRate*1000*1000*1000);
				long start = System.nanoTime();
				System.out.format("Sent for difficulty %d. Sleep for %d.\n", randomDiff, sleepTime);
				while(start + sleepTime >= System.nanoTime());
			}
			
			// Compute average request time. 
			long totalResponseTime = 0;
			for (Future<ComputationResult> future : futures)
				totalResponseTime+=future.get().getTotalTime();
			
			// Record results.
			serie.add(requestRate, totalResponseTime/REQUESTS_PER_SAMPLE);
			
			if (caching) {
				RecordingResult recording = client.issueStopRecordingRequest();
				System.out.format("Hit rate:%f\n", recording.getCacheHitRate());
				client.issueDisableCachingRequest();
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
}