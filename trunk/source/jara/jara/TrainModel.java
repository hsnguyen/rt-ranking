package jara;

import jara.algorithms.FRank;
import jara.algorithms.KernelDensityEstimation;
import jara.algorithms.LambdaNet;
import jara.algorithms.RandomRanker;
import jara.algorithms.RankNet;
import jara.algorithms.RankNetQN;
import jara.algorithms.RankingModel;
import jara.data.DataQuery;
import jara.data.DataSample;
import jara.data.DataSet;
import jara.data.DataSetRandomizer;
import jara.measures.KendallsTau;
import jara.measures.MeanAveragePrecision;
import jara.measures.NDCGMeasure;
import jara.measures.PrecisionAt;
import jara.measures.RankPair;
import jara.measures.RankingMeasure;
import jara.report.PerformanceReport;
import jara.utils.CSVReader;
import jara.utils.CmdLineUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

import Jama.Matrix;

public class TrainModel {


	public static abstract class Training {

		protected String prefix;

		private String getPrefix() {
			return prefix;
		}

		private File targetDir;
		private CommandLine cmdline;
		private DataSet dataset;
		protected long seed;  


		public Training( String prefix, 
				File targetDir, 
				DataSet dataset,
				long seed,
				CommandLine cmdline ) throws Exception 
				{
			this.prefix = prefix;
			this.targetDir = targetDir;
			this.dataset = dataset;
			this.seed = seed;
			this.cmdline = cmdline;
			initModel( cmdline );
				}

		public abstract RankingModel trainModel(  DataSet train, DataSet eval ) throws Exception;

		public abstract void initModel( CommandLine cmdline ) throws Exception;

		public void performTraining() throws Exception {
			
			Random random = new Random( this.seed );
			DataSetRandomizer randomizer = new DataSetRandomizer( random,dataset,1 );
			
			System.out.println( "Starting with training" );
			
			DataSet trainDataSet = randomizer.getTrainSplits(0);
			DataSet evalDataSet  = randomizer.getEvaluationSplit(0);
			DataSet testDataSet  = randomizer.getTestSplit(0);
				
			RankingModel model = trainModel( trainDataSet,evalDataSet );
			File modelFile = File.createTempFile( prefix+"-", ".rmod", targetDir);
			RankingModel.saveRankingModel(modelFile, model);
			
			System.out.println( "Writing results" );
			File resultFile = new File( modelFile.toString().replace( ".rmod", ".csv" ) );
			writeResults( resultFile,model,trainDataSet,evalDataSet,testDataSet,random );
			 
			System.out.println( "ready" );
		}

		private void writeResults(
				File resultFile, 
				RankingModel model, 
				DataSet trainDataSet, 
				DataSet evalDataSet, 
				DataSet testDataSet,
				Random random
		) {
			try {
				PrintStream out = new PrintStream( resultFile );
				
				final String sep = GlobalDefinitions.CSV_SEPARATOR;
				
				// writing multiple headers
				out.println( "kde" 
							+ sep + "prefix" 
							+ sep + "LogLikelihood" 
							+ sep + "Bandwidth" 
							+ sep + "trained.on" 
							+ sep + "evaluated.on" 
							+ sep + "query.size" 
							+ sep + "query.id" 
							+ sep + "result.file" );
				out.println( "kendall" 
							+ sep + "prefix" 
							+ sep + "kendalls.tau" 
							+ sep + "split" 
						    + sep + "query.size" 
						    + sep + "query.id" 
						    + sep + "result.file" );
				out.println( "ndcg" 
							+ sep + "prefix" 
							+ sep + "ndcg.at" 
							+ sep + "split" 
							+ sep + "fraction.at" 
							+ sep + "at"
							+ sep + "query.size"  
							+ sep + "query.id" 
							+ sep + "result.file" );
				out.println( "map" 
							+ sep + "prefix" 
							+ sep +  "map.relevant" 
							+ sep + "split" 
							+ sep + "fraction.relevant" 
							+ sep + "relevant" 
							+ sep + "query.size" 
							+ sep + "query.id" 
							+ sep + "result.file" );
				out.println( "precision" 
							+ sep + "prefix" 
							+ sep + "precision.at.relevant" 
							+ sep + "split"  
							+ sep + "fractions.relevant" 
							+ sep + "fractions.at" 
							+ sep +  "relevant" 
							+ sep + "at" 
							+ sep + "query.size" 
							+ sep + "query.id" 
							+ sep + "result.file" );
				
				writeEvaluation(resultFile, model, trainDataSet, random, out, "train" );
				writeEvaluation(resultFile, model,  evalDataSet, random, out, "eval" );
				writeEvaluation(resultFile, model,  testDataSet, random, out, "test" );
				
				KernelDensityEstimation kde = new KernelDensityEstimation( testDataSet );
				for( DataQuery query : trainDataSet ) {
					Double logli = computeModelLogLikelihood(model,kde,query);
					out.println( "kde" 
								+ sep + prefix 
								+ sep + logli 
								+ sep + kde.getBandwidth() 
								+ sep + "test" 
								+ sep + "train"  
								+ sep + query.size() 
								+ sep + query.getQueryID() 
								+ sep + resultFile ); 
				}
				for( DataQuery query : evalDataSet ) {
					Double logli = computeModelLogLikelihood(model,kde,query);
					out.println( "kde" 
								+ sep + prefix 
								+ sep + logli 
								+ sep + kde.getBandwidth() 
								+ sep + "test" 
								+ sep + "eval" 
								+ sep + query.size() 
								+ sep + query.getQueryID() 
								+ sep + resultFile ); 
				}
				
				out.close();
			} catch (FileNotFoundException e) { 
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private Double computeModelLogLikelihood(
				RankingModel model, 
				KernelDensityEstimation kde, 
				DataQuery query) 
		{
			final String qid = "prediction";
			DataQuery pred = new DataQuery( qid );
			for( DataSample sample : query ) {
				Double score = model.predict(sample);
				DataSample psample = new DataSample(score, qid, sample.getSampleID(), new double[]{} );
				pred.add(psample);
			}
			Double loli = kde.logLikelihood(pred);
			return loli;
		}

		private void writeEvaluation(File resultFile, RankingModel model,
				DataSet dataset, Random random, PrintStream out, String split) 
		{
			final double[] fractions = GlobalDefinitions.QUERY_FRACTIONS;
			final int sgn = GlobalDefinitions.RANKING_SGN;
			final String sep = GlobalDefinitions.CSV_SEPARATOR;

			for( DataQuery query : dataset ) {
				ArrayList<RankPair> pairs = RankingMeasure.predictDataSet(model, query ); 

				KendallsTau tau = new KendallsTau(pairs);
				out.println( "kendall" 
							+ sep + prefix 
							+ sep + tau.getMeasure() 
							+ sep + split 
							+ sep + query.size() 
							+ sep + query.getQueryID() 
							+ sep + resultFile );
				
				NDCGMeasure ndcg = new NDCGMeasure(pairs, random, sgn );
				for( int j=0; j!=fractions.length; ++j ) {
					int at = (int) Math.max( 1.0, fractions[j] * query.size() );
					if( at < ndcg.getNDCG().size() ) {
						out.println( "ndcg" 
								+ sep + prefix 
								+ sep + ndcg.getNDCG(at) 
								+ sep + split 
								+ sep + fractions[j] 
								+ sep + at 
								+ sep + query.size()  
								+ sep + query.getQueryID() 
								+ sep + resultFile );
					}
				}
														
				for( int i=0; i!=fractions.length; ++i ) {
					int relevant = (int) Math.max( 1.0, fractions[i] * query.size() );
					MeanAveragePrecision map = new MeanAveragePrecision( pairs, relevant, random, sgn );
					out.println(  "map" 
								+ sep + prefix 
								+ sep +  map.getMeasure() 
								+ sep + split 
								+ sep + fractions[i]
								+ sep +  relevant 
								+ sep + query.size() 
								+ sep + query.getQueryID() 
								+ sep + resultFile ); 
					
					for( int j=i; j!=fractions.length; ++j ) {
						int at = (int) Math.max( 1.0, fractions[j] * query.size() );
						PrecisionAt prec = new PrecisionAt(pairs, relevant, at, random, sgn );
						out.println( "precision" 
									+ sep + prefix 
									+ sep + prec.getMeasure() 
									+ sep + split 
									+ sep + fractions[i] 
									+ sep + fractions[j]
									+ sep + relevant 
									+ sep + at
									+ sep + query.size() 
									+ sep + query.getQueryID() 
									+ sep + resultFile ); 
					}
				}

			}
		}

	}
		
	public static class FRankTraining extends Training {

		private Integer k;
		private Integer lpf;
		private Double p;
		private Integer improvement;

		public FRankTraining(String prefix, 
				File targetDir, 
				DataSet dataset,
				long seed,
				CommandLine cmdline ) throws Exception 
				{
			super(prefix, targetDir, dataset, seed, cmdline);
				}

		@Override
		public void initModel(CommandLine cmdline) throws Exception {
			System.out.println( "Setting up FRank" );

			HashMap<String, String> popts = CmdLineUtils.prepareMultipleCommandlineOptions(cmdline, "P" );
			// k, lpf=20, p=1.0, improvement=10
			k = null!=popts.get("k") ? Integer.parseInt( popts.get("k") ) : null;
			lpf = null!=popts.get("lpf") ? Integer.parseInt( popts.get("lpf") ) : GlobalDefinitions.TRAINING_LEARNERS_PER_FEATURE;
			p = null!=popts.get("p") ? Double.parseDouble( popts.get("p") ) : GlobalDefinitions.TRAINING_PROBABILITY_P_BAR;
			improvement = null!=popts.get("improvement") ? Integer.parseInt( popts.get("improvement") ) : GlobalDefinitions.TRAINING_IMPROVEMENT;
			prefix = prefix + "-k" + k + "-l" + lpf + "-p" + p + "-imp" + improvement;
			System.out.println( "ready" );
			if( null==k
					|| null==lpf
					|| null==p 
					|| null==improvement  ) 
			{
				throw new Exception( "Not enough parameters specified! => " + popts.toString() );
			}

		}

		@Override
		public RankingModel trainModel(DataSet train, DataSet eval)
		throws Exception {
			FRank frank = new FRank();
			frank.train(train, eval, lpf, p, k, improvement);
			return frank;
		}

		@Override
		public String toString() {
			return "FRank Training (k="+k+",lpf="+lpf+",p="+p+",improvement="+improvement+")";
		}
	}

	public static class RankNetTraining extends Training {

		private Integer m;
		private Double p;
		private Double eta;
		private Integer epochs;
		private Integer improvement;
		private Integer iterations;

		public RankNetTraining(String prefix, 
				File targetDir, 
				DataSet dataset,
				long seed,
				CommandLine cmdline ) throws Exception 
				{
			super(prefix, targetDir, dataset, seed, cmdline);
				}

		@Override
		public void initModel(CommandLine cmdline ) throws Exception { 
			System.out.println( "Setting up RankNet" );

			HashMap<String, String> popts = CmdLineUtils.prepareMultipleCommandlineOptions(cmdline, "P" );
			// m, p=1.0, eta=1.0, epochs=10, improvement=10, iterations=10
			m = null!=popts.get("m") ? Integer.parseInt( popts.get("m") ) : null;
			p = null!=popts.get("p") ? Double.parseDouble( popts.get("p") ) : GlobalDefinitions.TRAINING_PROBABILITY_P_BAR;
			eta = null!=popts.get("eta") ? Double.parseDouble( popts.get("eta") ) : GlobalDefinitions.TRAINING_ETA;
			epochs = null!=popts.get("epochs") ? Integer.parseInt( popts.get("epochs") ) : GlobalDefinitions.TRAINING_EPOCHS;
			improvement = null!=popts.get("improvement") ? Integer.parseInt( popts.get("improvement") ) : GlobalDefinitions.TRAINING_IMPROVEMENT;
			iterations = null!=popts.get("iterations") ? Integer.parseInt( popts.get("iterations") ) : GlobalDefinitions.TRAINING_ITERATIONS;
			prefix = prefix + "-m" + m + "-p" + p + "-e" + eta + "-ep" + epochs + "-imp" + improvement + "-it" + iterations;
			System.out.println( "ready" );
			if( null==m 
					|| null==p 
					|| null==eta 
					|| null==epochs 
					|| null==improvement 
					|| null==iterations ) 
			{
				throw new Exception( "Not enough parameters specified! => " + popts.toString() );
			}
		}

		@Override
		public RankingModel trainModel( DataSet train, DataSet eval ) throws IOException {

			RankNet ranknet = new RankNet( train.getFeatureSize(),m );

			ranknet.train(train, eval, p, eta, epochs, improvement, iterations);

			return ranknet;
		}

		@Override
		public String toString() {
			return "RankNet Training (m="+m+",p="+p+",eta="+eta+",epochs="+epochs+",improvement="+improvement+",iterations="+iterations+")";
		}
	}

	public static class RankNetQNTraining extends Training {

		private Integer m;
		private Double p;
		
		public RankNetQNTraining(
				String prefix, 
				File targetDir, 
				DataSet dataset,
				long seed,
				CommandLine cmdline ) throws Exception 
				{
			super(prefix, targetDir, dataset, seed, cmdline);
				}

		@Override
		public void initModel(CommandLine cmdline ) throws Exception { 
			System.out.println( "Setting up RankNet QN" );

			HashMap<String, String> popts = CmdLineUtils.prepareMultipleCommandlineOptions(cmdline, "P" );
			// m, p=1.0, eta=1.0, epochs=10, improvement=10, iterations=10
			m = null!=popts.get("m") ? Integer.parseInt( popts.get("m") ) : null;
			p = null!=popts.get("p") ? Double.parseDouble( popts.get("p") ) : GlobalDefinitions.TRAINING_PROBABILITY_P_BAR;
			prefix = prefix + "-m" + m + "-p" + p;
			System.out.println( "ready" );
			if( null==m 
					|| null==p 
			) 
			{
				throw new Exception( "Not enough parameters specified! => " + popts.toString() );
			}
			System.out.println( "ready" );

		}

		@Override
		public RankingModel trainModel( DataSet train, DataSet eval ) throws IOException {

			RankNetQN ranknet = new RankNetQN( train.getFeatureSize(),m );

			try {
				ranknet.trainBFGS(train, p ); 
			} catch (Exception e) {

				e.printStackTrace();
			}

			return ranknet;
		}

		@Override
		public String toString() {
			return "RankNet Quasi-Newton Training (m="+m+",p="+p+")";
		}
	}

	public static class LambdaRankTraining extends Training {

		private Integer m;
		private Integer k;
		private Double a; 

		public LambdaRankTraining(String prefix, 
				File targetDir, 
				DataSet dataset,
				long seed,
				CommandLine cmdline ) throws Exception 
				{
					super(prefix, targetDir, dataset, seed, cmdline);
				}

		@Override
		public void initModel(CommandLine cmdline ) throws Exception { 
			System.out.println( "Setting up LambdaRank" );

			HashMap<String, String> popts = CmdLineUtils.prepareMultipleCommandlineOptions(cmdline, "P" );
			// m, p=1.0, eta=1.0, epochs=10, improvement=10, iterations=10
			m = null!=popts.get("m") ? Integer.parseInt( popts.get("m") ) : null;
			k = null!=popts.get("k") ? Integer.parseInt( popts.get("k") ) : GlobalDefinitions.TRAINING_MAX_ROUNDS;
			a = null!=popts.get("a") ? Double.parseDouble( popts.get("a") ) : 1.0;
			prefix = prefix + "-m" + m + "-k" + k + "-a" + a;
			System.out.println( "ready" );
			if( null==m 
					|| null==k
					|| null==a
			) 
			{
				throw new Exception( "Not enough parameters specified! => " + popts.toString() );
			}
			System.out.println( "ready" );

		}

		@Override
		public RankingModel trainModel( DataSet train, DataSet eval ) throws IOException {

			LambdaNet ranknet = new LambdaNet( train.getFeatureSize(),m );

			try {
				ranknet.train(train, eval, a, k ); 
			} catch (Exception e) {

				e.printStackTrace();
			}

			return ranknet;
		}

		@Override
		public String toString() {
			return "LambdaRank Training (m="+m+",k="+k+",a="+a+")";
		}
	}
	
	public static class RandomTraining extends Training {

		private RandomRanker randomRanker; 
		
		public RandomTraining(String prefix, 
				File targetDir, 
				DataSet dataset,
				long seed,
				CommandLine cmdline ) throws Exception 
				{
					super(prefix, targetDir, dataset, seed, cmdline);
				}

		@Override
		public void initModel(CommandLine cmdline ) throws Exception {
			this.randomRanker = new RandomRanker( seed );
			prefix = prefix + "-se" + seed;
		}

		@Override
		public RankingModel trainModel( DataSet train, DataSet eval ) throws IOException {
			return randomRanker;
		}

		@Override
		public String toString() {
			return "Random Ranker Training (" + seed + ")";
		}
	}
		
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception { 

		Options options = new Options();

		Option option = null;

		option = OptionBuilder
		.isRequired()
		.hasArgs(1)
		.withArgName( "seed" )
		.withDescription( "Randomisation Seed" )
		.create( "seed" );
		options.addOption( option );
		
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
		.withArgName( "file" )
		.withDescription( "Comma Separated File with queries" )
		.create( "dataset" );
		options.addOption( option );

		option = OptionBuilder
		.isRequired()
		.hasArgs(1)
		.withArgName( "string" )
		.withDescription( "Prefix for files that are generated" )
		.create( "prefix" );
		options.addOption( option );

		option = OptionBuilder
		.withArgName( "property=value" )
		.hasArgs()
		.withValueSeparator()
		.withDescription( "use value for given property" )
		.create( "P" );
		options.addOption( option );

		// Training modes
		OptionGroup group = new OptionGroup();
		option = OptionBuilder
		.withDescription( "RankNet training, required properties and defaults are m, p=1.0, eta=1.0, epochs=10, improvement=30, iterations=10" )
		.create( "ranknet" );
		options.addOption(option);
		group.addOption(option);

		option = OptionBuilder
		.withDescription( "FRank training, required properties and defaults are k, lpf=20, p=1.0, improvement=10" )
		.create( "frank" );
		options.addOption(option);
		group.addOption(option);


		option = OptionBuilder
		.withDescription( "RankNet (Quasi-Newton) training, required properties and defaults are m, p=1.0" )
		.create( "ranknetqn" );
		options.addOption(option);
		group.addOption(option);
		options.addOptionGroup(group);

		option = OptionBuilder
		.withDescription( "LambdaRank training, required properties and defaults are m, a=1.0, k=30" )
		.create( "lambdarank" );
		options.addOption(option);
		group.addOption(option);
		options.addOptionGroup(group);
		
		option = OptionBuilder
		.withDescription( "Random Ranker training" )
		.create( "random" );
		options.addOption(option);
		group.addOption(option);
		options.addOptionGroup(group);

		CommandLineParser parser = new GnuParser();
		try {
			// parse the command line arguments
			CommandLine cmdline = parser.parse( options, args );
			System.out.println( "Setting up application" );
			String prefix = cmdline.getOptionValue( "prefix" );

			String seedStr = cmdline.getOptionValue( "seed" );
			long seed = Long.parseLong( seedStr );
			prefix = prefix + "-s" + seed;
						
			String targetStr = cmdline.getOptionValue( "dir" );
			File targetDir = new File( targetStr );
			if( ! targetDir.isDirectory() ) {
				targetDir.mkdirs();
			}

			String datasetStr = cmdline.getOptionValue( "dataset" );
			File datasetFile  = new File( datasetStr );
			if( ! datasetFile.isFile() ) {
				System.err.println( "Dataset does not exist!" );
				System.exit( 1 );
			}
			DataSet dataset = CSVReader.load( datasetFile );
			prefix = prefix + "-d" + datasetFile.getName();

			System.out.println( "Set up finished" );
			
			Training training = null;
			if( cmdline.hasOption( "ranknet" ) ) {
				prefix = prefix + "-ranknet";
				training = new RankNetTraining( prefix,targetDir,dataset,seed,cmdline );
			} else if( cmdline.hasOption( "frank" ) ) {
				prefix = prefix + "-frank";
				training = new FRankTraining( prefix,targetDir,dataset,seed,cmdline );
			} else if( cmdline.hasOption( "ranknetqn" ) ) {
				prefix = prefix + "-ranknetqn";
				training = new RankNetQNTraining(  prefix,targetDir,dataset,seed,cmdline );
			} else if( cmdline.hasOption( "lambdarank" ) ) {
				prefix = prefix + "-lambdarank";
				training = new LambdaRankTraining(  prefix,targetDir,dataset,seed,cmdline );
			} else if( cmdline.hasOption( "random" ) ) {
				prefix = prefix + "-random";
				training = new RandomTraining(  prefix,targetDir,dataset,seed,cmdline );
			} 
			System.out.println( "Starting training" );
			training.performTraining();
			System.out.println( "finished" );

		} catch( ParseException exp ) {
			// automatically generate the help statement
			//		    	exp.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( "TrainModel", options );
		} catch (FileNotFoundException e) { 
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
