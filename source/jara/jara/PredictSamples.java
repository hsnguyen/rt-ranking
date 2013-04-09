package jara;

import jara.algorithms.RankingModel;
import jara.data.DataQuery;
import jara.data.DataSample;
import jara.data.DataSet;
import jara.utils.CSVReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


public class PredictSamples {

	private static class Prediction {
		public final DataSample sample;
		public final Double prediction;
		
		public Prediction( DataSample sample, Double prediction ) {
			this.sample = sample;
			this.prediction = prediction;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Options options = new Options();

		Option option = null;

		option = OptionBuilder
			.isRequired()
			.hasArgs(1)
			.withArgName( "target" )
			.withDescription( "Target directory where results are stored" )
			.create( "dir" );
		options.addOption( option );
		
		option = OptionBuilder
			.isRequired()
			.hasArgs(1)
			.withArgName( "string" )
			.withDescription( "Prefix for files that are generated" )
			.create( "prefix" );
		options.addOption( option );
		

		option = OptionBuilder
			.isRequired()
			.hasArgs(1)
			.withArgName( "file" )
			.withDescription( "Model file" )
			.create( "model" );
		options.addOption( option );
		
		option = OptionBuilder
			.hasArgs(1)
			.withArgName( "file" )
			.withDescription( "Data input (first column is neglected)" )
			.create( "input" );
		options.addOption( option );
				
		CommandLineParser parser = new GnuParser();
		try {
			// parse the command line arguments
			CommandLine cmdline = parser.parse( options, args );
			System.out.println( "Setting up application" );
			String prefix = cmdline.getOptionValue( "prefix" );
			
			String targetStr = cmdline.getOptionValue( "dir" );
			File targetDir = new File( targetStr );
			if( ! targetDir.isDirectory() ) {
				targetDir.mkdirs();
			}
			
			String modelStr = cmdline.getOptionValue( "model" );
			File modelFile = new File( modelStr );
			if( ! modelFile.isFile() ) {
				System.err.println( "Model file does not exist!" );
				System.exit(1);
			}
			RankingModel model = RankingModel.loadRankingModel( modelFile );
			
			// TODO Hier weiterarbeiten
			String inputStr = cmdline.getOptionValue( "input" );
			File inputFile = new File( inputStr );
			if( ! modelFile.isFile() ) {
				System.err.println( "Input file does not exist!" );
				System.exit(1);
			}
			
			DataSet dataset = CSVReader.load( inputFile );

			ArrayList< Prediction > predictions = new ArrayList<Prediction>();
			for( DataQuery query : dataset ) {
				for( DataSample sample : query ) {
					Prediction prediction = new Prediction( sample, model.predict( sample ) ); 
					predictions.add( prediction );
				}
				
			}
			
			Collections.sort( predictions, new Comparator<Prediction>() {

				public int compare(Prediction o1, Prediction o2) {
					return - Double.compare( o1.prediction, o2.prediction );
				}
				
			});

			File resultFile = File.createTempFile(prefix + "-prediction_", ".csv", targetDir );
			PrintStream ps = new PrintStream( resultFile );
			ps.println( "" + GlobalDefinitions.HEADER_PREDICTION
					+ GlobalDefinitions.CSV_SEPARATOR + GlobalDefinitions.HEADER_SCORE
					+ GlobalDefinitions.CSV_SEPARATOR + GlobalDefinitions.HEADER_QID 
					+ GlobalDefinitions.CSV_SEPARATOR + GlobalDefinitions.HEADER_SID  
					+ GlobalDefinitions.CSV_SEPARATOR + GlobalDefinitions.HEADER_MODEL );
			
			for( int i=0; i < predictions.size(); ++i ) {
				Prediction prediction = predictions.get(i);
				ps.println( "" + prediction.prediction
						+ GlobalDefinitions.CSV_SEPARATOR + prediction.sample.getQueryID() 
						+ GlobalDefinitions.CSV_SEPARATOR + prediction.sample.getSampleID() 
						+ GlobalDefinitions.CSV_SEPARATOR + modelFile.getName()
						+ GlobalDefinitions.CSV_COMMENT + " " + GlobalDefinitions.CSV_SEPARATOR + prediction.sample.getScore()
						);
				
			}
			
			ps.close();
			
		} catch( ParseException exp ) {
			// automatically generate the help statement
			//		    	exp.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( "PredictSamples", options );
		} catch (FileNotFoundException e) { 
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	

}
