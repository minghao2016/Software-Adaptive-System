package org.femosaa.invalid;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import jmetal.core.Operator;
import jmetal.core.Problem;
import jmetal.core.Solution;
import jmetal.core.SolutionSet;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;
import jmetal.util.Ranking;

import org.femosaa.core.SASSolution;
import org.femosaa.operator.ClassicBitFlipMutation;
import org.femosaa.operator.ClassicUniformCrossoverSAS;
import org.femosaa.operator.InvalidityAwareBinaryTournament2;

public class SASValidityAndInvalidityCoEvolver {

	// this merges the two populations together
	// private SolutionSet allSolutions;
	private SolutionSet invalidSolutions;

	private SolutionSet offSpringInvalidSolutions;

	private Operator selectionOperator;

	private Operator mutationOperator;
	private Operator crossoverOperator;

	public SASValidityAndInvalidityCoEvolver(HashMap<String, Object> parameters) {
		invalidSolutions = new SolutionSet();
		offSpringInvalidSolutions = new SolutionSet();
		// This can be changed to other modified ones.
		selectionOperator = new InvalidityAwareBinaryTournament2(parameters);
		crossoverOperator = new ClassicUniformCrossoverSAS(parameters);
		mutationOperator = new ClassicBitFlipMutation(parameters);
	}

	public boolean createInitialSolution(Solution solution, Problem problem_)
			throws JMException {
		for (int i = 0; i < solution.getDecisionVariables().length; i++) {

			int value = (int) (PseudoRandom.randInt(
					(int) solution.getDecisionVariables()[i].getLowerBound(),
					(int) solution.getDecisionVariables()[i].getUpperBound()));
			solution.getDecisionVariables()[i].setValue(value);
		} // if

		if (((SASSolution) solution).isSolutionValid()) {
			// problem_.evaluate(solution);
			// problem_.evaluateConstraints(solution);
			return true;
		}

		invalidSolutions.add(solution);
		return false;
	}

	public Solution doMatingSelection(SolutionSet validSolutions)
			throws JMException {
		return (Solution) selectionOperator.execute(new SolutionSet[] {
				validSolutions, invalidSolutions });
	}

	public Solution[] doReproduction(Solution[] parents, Problem problem_)
			throws JMException {
		Solution[] offSpring = (Solution[]) crossoverOperator.execute(parents);
		mutationOperator.execute(offSpring[0]);
		mutationOperator.execute(offSpring[1]);

		int count = -1;// 0 = first one, 1 = second one, 2 = both

		if (((SASSolution) offSpring[0]).isSolutionValid()) {
			problem_.evaluate(offSpring[0]);
			problem_.evaluateConstraints(offSpring[0]);
			count = 0;
		} else {
			offSpringInvalidSolutions.add(offSpring[0]);
		}

		if (((SASSolution) offSpring[1]).isSolutionValid()) {
			problem_.evaluate(offSpring[1]);
			problem_.evaluateConstraints(offSpring[1]);
			count = count == 0 ? 2 : 1;
		} else {
			offSpringInvalidSolutions.add(offSpring[1]);
		}

		if (count == -1) {
			return new Solution[] {};
		}

		return count == 2 ? offSpring
				: count == 0 ? new Solution[] { offSpring[0] }
						: new Solution[] { offSpring[1] };
	}

	public void doEnvironmentalSelection(SolutionSet validSolutions) {
		// This could be changed.
		int size = validSolutions.size();

		List<Solution> union = new ArrayList<Solution>();

		for (int i = 0; i < invalidSolutions.size(); i++) {
			union.add(invalidSolutions.get(i));
		}

		for (int i = 0; i < offSpringInvalidSolutions.size(); i++) {
			union.add(offSpringInvalidSolutions.get(i));
		}

		SolutionSet population = new SolutionSet();
		this.selectByViolationThenDiversity(validSolutions, union, population,
				size);

		// Reset the temp set.
		offSpringInvalidSolutions.clear();
		invalidSolutions = population;
	}
	
	// TODO add another selectByViolationAndDiversityViaMutiplcity (or knee point)?
	/**
	 * We use only probability here.
	 */
	private void selectByViolationAndDiversityViaMutiplcity(
			SolutionSet validSolutions, List<Solution> union,
			SolutionSet population, int size) {
		
		Map<Solution, Double> map = new HashMap<Solution, Double>();
		for (int i = 0; i < union.size(); i++) {
			map.put(union.get(i), ((SASSolution)union.get(i)).getProbabilityToBeNaturallyRepaired());
		}
		
		while (population.size() < size && union.size() != 0) {
			Solution add = null;
			int index = -1;
			double largest = Double.MIN_VALUE;
			for (int i = 0; i < union.size(); i++) {
				
				double localShortest = Double.MAX_VALUE;

				for (int j = 0; j < validSolutions.size(); j++) {
					double d = calculateHammingDistance(union.get(i),
							validSolutions.get(j));
					if (d < localShortest) {
						localShortest = d;
					}
				}

				for (int j = 0; j < population.size(); j++) {
					double d = calculateHammingDistance(union.get(i),
							population.get(j));
					if (d < localShortest) {
						localShortest = d;
					}
				}
				
				double rank = map.get(union.get(i)) * localShortest;
				if(rank > largest) {
					largest = rank;
					add = union.get(i);
					index = i;
				}
				
			}
			
			population.add(add);
			union.remove(index);
		}	
	}
	
	@Deprecated
	private void selectByViolationAndDiversityViaDominance(
			SolutionSet validSolutions, List<Solution> union,
			SolutionSet population, int size) {
		boolean useCount = true;
		SolutionSet newPopulation = new SolutionSet();
		Map<Solution, Integer> map = new HashMap<Solution, Integer>();
		for (int i = 0; i < union.size(); i++) {
			
			Solution s = new Solution(2);
			map.put(s, i);
			s.setObjective(0, useCount? ((SASSolution)union.get(i)).countDegreeOfViolation() :
				(0 - ((SASSolution)union.get(i)).getProbabilityToBeNaturallyRepaired()));
			
			

			// Calculate distance against valid solutions only.
			double localShortest = Double.MAX_VALUE;

			for (int j = 0; j < validSolutions.size(); j++) {
				double d = calculateHammingDistance(union.get(i),
						validSolutions.get(j));
				if (d < localShortest) {
					localShortest = d;
				}
			}

			s.setObjective(1, (0 - localShortest));
			newPopulation.add(s);
		}
		
		// Dominance sort
		
		Ranking ranking = new Ranking(population);
		for (int i = 0; i < ranking.getNumberOfSubfronts(); i++) {
			if(population.size() >= size) {
				break;
			}
			
			SolutionSet set = ranking.getSubfront(i);
			int setSize = 0;
			if (set.size() <= (size - population.size())) {				
				setSize = set.size();				
			} else {
				// Since the order is random, just push the first n solutions.
				setSize = (size - population.size());
			}
			
			
			for (int j = 0; j < setSize; j++) {
				population.add(union.get(map.get(set.get(j))));
			}
		}
		
	}
	
	private void selectByViolationWithProbNotPushAllThenDiversity(
			SolutionSet validSolutions, List<Solution> union,
			SolutionSet population, int size) {
		SolutionSet selected = null;
		Map<Double, SolutionSet> map = new HashMap<Double, SolutionSet>();
		List<Double> sort = new ArrayList<Double>();
		while (population.size() < size && union.size() != 0) {
			selected = insertInvalidSolutionsByViolation(map, sort, union, population,
					size, true, false);
			this.insertInvalidSolutionsByDistance(validSolutions, population,
					selected, size, false);
		}

	}

	private void selectByViolationNotPushAllThenDiversity(
			SolutionSet validSolutions, List<Solution> union,
			SolutionSet population, int size) {
		SolutionSet selected = null;
		Map<Double, SolutionSet> map = new HashMap<Double, SolutionSet>();
		List<Double> sort = new ArrayList<Double>();
		while (population.size() < size && union.size() != 0) {
			selected = insertInvalidSolutionsByViolation(map, sort, union, population,
					size, false, false);
			this.insertInvalidSolutionsByDistance(validSolutions, population,
					selected, size, false);
		}

	}

	private void selectByViolationThenDiversity(SolutionSet validSolutions,
			List<Solution> union, SolutionSet population, int size) {
		SolutionSet selected = null;
		Map<Double, SolutionSet> map = new HashMap<Double, SolutionSet>();
		List<Double> sort = new ArrayList<Double>();
		while (selected == null && population.size() < size
				&& union.size() != 0) {
			selected = insertInvalidSolutionsByViolation(map, sort, union, population,
					size, false, true);
		}

		this.insertInvalidSolutionsByDistance(validSolutions, population,
				selected, size, true);
	}

	private SolutionSet insertInvalidSolutionsByViolation(
			Map<Double, SolutionSet> map, List<Double> sort,
			List<Solution> union, SolutionSet population, int size,
			boolean isProb /*
							 * use number of violation or probability to become
							 * valid
							 */, boolean isPushAll /*
													 * if put all solutions with
													 * the same violation level
													 * to the population
													 */) {

		if (map.size() == 0) {

			// Get the ones that have the least number of violation/largest
			// probability
			for (int i = 0; i < union.size(); i++) {
				double c = isProb ? (0 - ((SASSolution) union.get(i))
						.getProbabilityToBeNaturallyRepaired())
						: ((SASSolution) union.get(i)).countDegreeOfViolation();
				if (!map.containsKey(c)) {
					map.put(c, new SolutionSet());
				}

				sort.add(c);

				map.get(c).add(union.get(i));
			}

			Collections.sort(sort);

		}

		SolutionSet s = map.get(sort.get(0));
		map.remove(sort.get(0));
		sort.remove(0);

		if (isPushAll) {
			if (s.size() <= (size - population.size())) {

				for (int i = 0; i < s.size(); i++) {
					population.add(s.get(i));
					union.remove(s.get(i));
				}
				return null;
			}
		} else {
			if (s.size() <= (size - population.size())) {
				for (int i = 0; i < s.size(); i++) {
					union.remove(s.get(i));
				}
			}
		}

		return s;
	}

	private void insertInvalidSolutionsByDistance(SolutionSet validSolutions,
			SolutionSet population, SolutionSet selected, int size,
			boolean isPushAll) {
		int count = (size - population.size()) > selected.size() ? selected
				.size() : size - population.size();
		boolean isChanged = true;
		List<DiversitySort> list = new ArrayList<DiversitySort>();

		double currentProb = 1;
		double decentProb = 1 / selected.size();

		while (count > 0) {

			if (isChanged) {
				list.clear();
				for (int i = 0; i < selected.size(); i++) {

					double localShortest = Double.MAX_VALUE;

					for (int j = 0; j < validSolutions.size(); j++) {
						double d = calculateHammingDistance(selected.get(i),
								validSolutions.get(j));
						if (d < localShortest) {
							localShortest = d;
						}
					}

					for (int j = 0; j < population.size(); j++) {
						double d = calculateHammingDistance(selected.get(i),
								population.get(j));
						if (d < localShortest) {
							localShortest = d;
						}
					}

					list.add(new DiversitySort(i, localShortest));
				}

				Collections.sort(list);
			}

			
			DiversitySort ds = list.get(0);
			if (isPushAll) {
				population.add(selected.get(ds.index));
				isChanged = true;
				// do not need to list.remove(0), as the list will be cleared anyway.
			} else {

				if (PseudoRandom.randDouble() < currentProb) {
					population.add(selected.get(ds.index));
					isChanged = true;
					// do not need to list.remove(0), as the list will be cleared anyway.
				} else {
					isChanged = false;
					list.remove(0);
				}

				currentProb = currentProb - decentProb;
			}

			count--;
			selected.remove(ds.index);

		}

	}

	private double calculateHammingDistance(Solution s1, Solution s2) {

		int d = 0;

		for (int i = 0; i < s1.getDecisionVariables().length; i++) {
			double v1 = 0, v2 = 0;
			try {
				v1 = s1.getDecisionVariables()[i].getValue();
				v2 = s2.getDecisionVariables()[i].getValue();
			} catch (JMException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (v1 != v2) {
				d++;
			}
		}

		return d;
	}

	private class DiversitySort implements Comparable {
		private int index;
		private double distance;

		public DiversitySort(int index, double distance) {
			super();
			this.index = index;
			this.distance = distance;
		}

		@Override
		public int compareTo(Object arg0) {
			DiversitySort ds2 = (DiversitySort) arg0;
			if (this.distance >= ds2.distance) {
				return -1;
			} else {
				return 1;
			}
		}

	}
}