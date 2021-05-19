import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.inference.ChiSquareTest;

import agents.org.apache.commons.lang.SerializationUtils;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.Domain;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.BOAparameter;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.boaframework.OutcomeSpace;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import negotiator.boaframework.opponentmodel.tools.UtilitySpaceAdapter;

public class KnutModel2 extends OpponentModel {
	private double delta;
	private boolean first_time_window;
	
	/*<Issue ID, Estimated weight*/
	private Map<Integer, Double> estimated_issue_weights;
	
	/*<Issue ID, <Value, Number of times value has appeared>*/
	private Map<Integer, HashMap<Value, Integer>> count_issue_values_this_timewindow;
	
	/*<Issue ID, <Value, Number of times value has appeared>*/
	private Map<Integer, HashMap<Value, Integer>> count_issue_values_prev_timewindow;
	
	/*<Issue ID, <Value, Issue Value Estimation>*/
	private Map<Integer, HashMap<Value, Double>> issue_value_estimation;
	
	/*<Issue ID, number of times max used issue value is used*/
	private Map<Integer,Integer> max_used_issue_value;
		
	private int count_offers_received_this_timewindow;
	private int size_of_timewindow;
	private boolean timewindow_changed;
	
	private OutcomeSpace outcome_space;

	@Override
	public String getName() {
		return "Knut Model 2";
	}
	
	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
		delta = 0.15;
		size_of_timewindow = 25;
		first_time_window = true;
		timewindow_changed = false;
		
		count_offers_received_this_timewindow = 0;
		
		estimated_issue_weights = new HashMap<Integer, Double>();
		count_issue_values_this_timewindow = new HashMap<Integer, HashMap<Value,Integer>>();
		count_issue_values_prev_timewindow = new HashMap<Integer, HashMap<Value,Integer>>();
		issue_value_estimation = new HashMap<Integer, HashMap<Value,Double>>();
		max_used_issue_value = new HashMap<Integer, Integer>();
		
		
		// Get all possible bid details
		outcome_space = new OutcomeSpace(negotiationSession.getUtilitySpace());
		List<BidDetails> all_possible_bids = outcome_space.getAllOutcomes();
		
		//Iterate through all possible bid details
		for (BidDetails bid_details : all_possible_bids) {
			//Get the bid of the current possible bid detail
            Bid bid = bid_details.getBid();
            //Get the issues and values of the bid
            HashMap<Integer, Value> all_possible_issue_values = bid.getValues();
            //Iterate through issues
            for (Map.Entry<Integer, Value> e : all_possible_issue_values.entrySet()) {
            	int issue_id = e.getKey();
            	Value value_id = e.getValue();

            	// If issue is new
            	if (!(count_issue_values_this_timewindow.containsKey(issue_id))) {
            		// Initialize counter for issue values
            		HashMap<Value, Integer> count_value_map = new HashMap<Value, Integer>();
        			count_value_map.put(e.getValue(), 0);
        			count_issue_values_this_timewindow.put(issue_id, count_value_map);
        			
        			HashMap<Value, Integer> count_value_map1 = new HashMap<Value, Integer>();
        			count_value_map.put(e.getValue(), 0);
        			count_issue_values_prev_timewindow.put(issue_id, count_value_map1);
        			
        			HashMap<Value, Double> estimation_value_map = new HashMap<Value, Double>();
        			estimation_value_map.put(value_id, 0.0);
        			issue_value_estimation.put(issue_id, estimation_value_map);
            	}
            	//If issue exist and value is new
            	else if(!(count_issue_values_this_timewindow.get(issue_id).containsKey(value_id))) {
            		// Initialize counter for issue values
            		count_issue_values_this_timewindow.get(issue_id).put(value_id, 0);
            		count_issue_values_prev_timewindow.get(issue_id).put(value_id, 0);
            		issue_value_estimation.get(issue_id).put(value_id, 0.0);
            	}   				
			}
        }
		
		//Get list of all issues in negotiation
		List<Issue> issue_lst = negotiationSession.getIssues();
		//Iterate through issues
		for (Issue i : issue_lst) {
			//Initialize estimated issue weight and maximum used issue value
			estimated_issue_weights.put(i.getNumber(), 0.0);
			max_used_issue_value.put(i.getNumber(), 0);
		}
		
	}
	
	@Override
	protected void updateModel(Bid bid, double time) {		
		//Set of issues which value is statistically is unchanged between time windows
		List<Integer> unchanged_issues = new ArrayList<Integer>();
		
		//Variable to detect if the opponent is conceding
		boolean concession = false;
		
		// Increment the total number of offers received in this time_window
		count_offers_received_this_timewindow++;
		
		//If we should start a new time window		
		if (count_offers_received_this_timewindow > size_of_timewindow) {
			// If we should change size of time window - SHould only run once
			if (time > 0.75 && timewindow_changed == false) {
				//Change size of timewindow
				size_of_timewindow = 15;
				timewindow_changed = true;
				
				//Reset counter
				count_offers_received_this_timewindow = 1;
				
				//Reset prev time window and this time window
				for (HashMap.Entry<Integer, HashMap<Value, Integer>> e: count_issue_values_prev_timewindow.entrySet()) {
					int issue_id = e.getKey();
					for (Map.Entry<Value, Integer> f : count_issue_values_prev_timewindow.get(issue_id).entrySet()) {
						Value value_id = f.getKey();
						count_issue_values_prev_timewindow.get(issue_id).put(value_id,0);
						count_issue_values_this_timewindow.get(issue_id).put(value_id,0);
					}	
				}
				//List of all bids received, sorted by time, with first offer received first
				List<BidDetails> bid_details = negotiationSession.getOpponentBidHistory().sortToTime().getHistory();
				
				//Itereate through list backwards for size of time window iterations
				for (int i = bid_details.size() - 1; i > bid_details.size()-1-size_of_timewindow; i--) {
					//Get the issue values for the bid
					HashMap<Integer,Value> b = bid_details.get(i).getBid().getValues();
					//Set counter for previous time window
					for(HashMap.Entry<Integer, Value> e: b.entrySet()) {
						int issue_id = e.getKey();
						Value value_id = e.getValue();
						int value_count = count_issue_values_prev_timewindow.get(issue_id).get(value_id)+1;
						count_issue_values_prev_timewindow.get(issue_id).put(value_id, value_count);
					}
				}

			}
			// If we should not change size of time window
			else {
				//Reset counter
				count_offers_received_this_timewindow = 1;
				
				//Copy this time window into prev time window
				count_issue_values_prev_timewindow = (Map<Integer, HashMap<Value, Integer>>) SerializationUtils.clone((Serializable) count_issue_values_this_timewindow); 
				
				//Reset this time window
				for (HashMap.Entry<Integer, HashMap<Value, Integer>> e: count_issue_values_this_timewindow.entrySet()) {
					int issue_id = e.getKey();
					for (Map.Entry<Value, Integer> f : count_issue_values_this_timewindow.get(issue_id).entrySet()) {
						Value value_id = f.getKey();
						count_issue_values_this_timewindow.get(issue_id).put(value_id,0);
					}	
				}	
			}
		}
		
		// The issues and its value from the new bid received
		HashMap<Integer, Value> new_values = bid.getValues();
		
		// Iterate through new values from received bid
		for (HashMap.Entry<Integer, Value> e : new_values.entrySet()) {	
			int issue_id = e.getKey();
			Value value_id = e.getValue();
			
			//Update issue value count
			int count_value = count_issue_values_this_timewindow.get(issue_id).get(value_id)+1;
			count_issue_values_this_timewindow.get(issue_id).put(value_id, count_value);
			
			//Check if the new issue value count is the maximum for this issue.
	    	if (count_value > max_used_issue_value.get(issue_id)) {
	    		max_used_issue_value.put(issue_id, count_value);
	    	} 	
    	}
		
		
		//If we are in the middle of a time window
		if (count_offers_received_this_timewindow != size_of_timewindow) {
			return;
		}
		
		//If it is the first time window
		if (first_time_window == true) {
			first_time_window = false;
			return;
		}
		
		// Distribution Based Frequency Model Algorithm
		for (Map.Entry<Integer, HashMap<Value, Integer>> e : count_issue_values_prev_timewindow.entrySet()) {
			int issue_id = e.getKey();
			
			//Estimated utility for this issue
			double estimated_issue_utility_this_timewindow = 0.0;
	    	double estimated_issue_utility_prev_timewindow = 0.0;			
			
	    	//List of issue values frequency used to simplify chi square test
			List<Double> freq_issue_values_prev_timewindow_arrayList = new ArrayList<Double>();
			List<Long> freq_issue_values_this_timewindow_arrayList = new ArrayList<Long>();
			
			//Map of issue value frequency used to simplify issue utility estimation
			Map<Value, Double> freq_issue_values_prev_timewindow_map = new HashMap<Value, Double>();
			Map<Value, Double> freq_issue_values_this_timewindow_map = new HashMap<Value, Double>();
			
			//Iterate over all issue values
			for (Map.Entry<Value, Integer> f : count_issue_values_prev_timewindow.get(issue_id).entrySet()) {
				Value value_id = f.getKey();
				
				//Calculate frequency of issue value and add to map and arrayList
				int prev_value_count = count_issue_values_prev_timewindow.get(issue_id).get(value_id);
				double prev_freq_dist = (double)(1.0+prev_value_count)/(double)(size_of_timewindow);//+size_of_timewindow);			
				freq_issue_values_prev_timewindow_arrayList.add(prev_freq_dist);
				freq_issue_values_prev_timewindow_map.put(value_id, prev_freq_dist);
				
				//Calculate frequency of issue value and add to map and arrayList
				int this_value_count = count_issue_values_this_timewindow.get(issue_id).get(value_id); 
				double this_freq_dist = (double)(1+this_value_count)/(double)(size_of_timewindow);//+size_of_timewindow);			
				freq_issue_values_this_timewindow_arrayList.add((long) this_freq_dist); //Cast to long to simplify chi square test
				freq_issue_values_this_timewindow_map.put(value_id, this_freq_dist);
			}
			
			//Convert arrayList to primitive array [] for chi square test
			double[] freq_issue_values_prev_timewindow_array = new double[freq_issue_values_prev_timewindow_arrayList.size()];
			long[] freq_issue_values_this_timewindow_array = new long[freq_issue_values_this_timewindow_arrayList.size()];
			
			for (int i = 0; i < freq_issue_values_prev_timewindow_array.length; i++) {
				freq_issue_values_prev_timewindow_array[i] = freq_issue_values_prev_timewindow_arrayList.get(i).doubleValue();
				freq_issue_values_this_timewindow_array[i] = freq_issue_values_this_timewindow_arrayList.get(i).longValue();
			}
			
			//Chi square test
			ChiSquareTest t = new ChiSquareTest();
	        double pval = 1.0;
			try {
				pval = t.chiSquareTest(freq_issue_values_prev_timewindow_array, freq_issue_values_this_timewindow_array);
			} catch (IllegalArgumentException e2) {
				pval = 1.0;
			}
			
	        //Cannot reject null hypothesis
	        if (pval > 0.05) {
	        	unchanged_issues.add(issue_id);
	        }
	        
	        //Can reject null hypothesis
	        else {
	        	for (Map.Entry<Value, Integer> f : count_issue_values_this_timewindow.get(issue_id).entrySet()) {
					Value value_id = f.getKey();
					
					//Update issue value estimation
					int this_value_count = count_issue_values_this_timewindow.get(issue_id).get(value_id);
					double value_estimation = (double)(1+this_value_count)/(double)max_used_issue_value.get(issue_id);
			    	issue_value_estimation.get(issue_id).put(value_id, value_estimation);
			    	
			    	//Calculate estimated issue utility
			    	double freq_issue_value_prev_timewindow = freq_issue_values_prev_timewindow_map.get(value_id);
					estimated_issue_utility_prev_timewindow += freq_issue_value_prev_timewindow * value_estimation;

			    	//Calculate estimated issue utility
					double freq_issue_value_this_timewindow = freq_issue_values_this_timewindow_map.get(value_id);
					estimated_issue_utility_this_timewindow += freq_issue_value_this_timewindow * value_estimation;
				}
	        	//Check for concession
				if (estimated_issue_utility_this_timewindow < estimated_issue_utility_prev_timewindow) {
					concession = true;
				}
	        }
		}
		
		if (unchanged_issues.size() > 0 && concession == true) {
			for (Integer i : unchanged_issues) {
				estimated_issue_weights.put(i, estimated_issue_weights.get(i)+delta);
			}
		}
	}
	
	@Override
	public double getBidEvaluation(Bid bid) {
		//Estimated utility
		double	estimated_utility = 0.0;
		
		//Obtain the total sum of the estimated weights
		double estimated_weights_sum = 0.0;
		
		//Calculate the sum of the estimated weights
		for (Map.Entry<Integer, Double> entry : estimated_issue_weights.entrySet()) {
			estimated_weights_sum += entry.getValue();
		}		
		
		//If estimated weights sum is zero
		if (estimated_weights_sum == 0.0) {
			return 0.2;
		}

		// The issues and its value from the new bid received
		HashMap<Integer, Value> new_values = bid.getValues();
		
		//Iterate over issues
		for (Map.Entry<Integer, Value> entry : new_values.entrySet()) {
			
			double issue_value = issue_value_estimation.get(entry.getKey()).get(entry.getValue());
			double normalized_issue_weight = estimated_issue_weights.get(entry.getKey())/estimated_weights_sum;
		
			estimated_utility += normalized_issue_weight * issue_value;
		}
		return estimated_utility;
	}
	
	@Override
	public AdditiveUtilitySpace getOpponentUtilitySpace() {
		AdditiveUtilitySpace utilitySpace = new UtilitySpaceAdapter(this, this.negotiationSession.getDomain());
		return utilitySpace;
	}
	
	@Override
	public Set<BOAparameter> getParameterSpec(){
		Set<BOAparameter> set = new HashSet<BOAparameter>();
		/* Aquí describe los parámetros que necesita el algoritmo de aprendizaje. Ejemplos:
			set.add(new BOAparameter("n", 20.0, "The number of own best offers to be used for genetic operations"));
			set.add(new BOAparameter("n_opponent", 20.0, "The number of opponent's best offers to be used for genetic operations"));
		*/
		return set;
	}

}