/*
 *    BasicClassificationPerformanceEvaluator.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *    @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *    
 */
package moa.evaluation;

import moa.core.Example;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.Utils;

import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Prediction;
import moa.options.AbstractOptionHandler;
import moa.tasks.TaskMonitor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Classification evaluator that performs basic incremental evaluation.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * @version $Revision: 7 $
 */
public class BasicClassificationPerformanceEvaluator extends AbstractOptionHandler
        implements LearningPerformanceEvaluator<Example<Instance>> {

    private static final long serialVersionUID = 1L;

    protected Estimator weightCorrect;

    protected Estimator[] columnKappa;

    protected Estimator[] rowKappa;

    protected int numClasses;

    private Estimator weightCorrectNoChangeClassifier;

    private Estimator weightMajorityClassifier;

    private int lastSeenClass;

    private double totalWeightObserved;
    
    protected Estimator[] precision;
    
    protected Estimator[] recall;

    @Override
    public void reset() {
        reset(this.numClasses);
    }

    public void reset(int numClasses) {
        this.numClasses = numClasses;
        this.rowKappa = new  Estimator[numClasses];
        this.columnKappa = new  Estimator[numClasses];
        this.precision = new  Estimator[numClasses];
        this.recall = new  Estimator[numClasses];
        for (int i = 0; i < this.numClasses; i++) {
            this.rowKappa[i] = newEstimator();
            this.columnKappa[i] = newEstimator();
            this.precision[i] = newEstimator();
            this.recall[i] = newEstimator();
        }
        this.weightCorrect = newEstimator();
        this.weightCorrectNoChangeClassifier = newEstimator();
        this.weightMajorityClassifier = newEstimator();
        this.lastSeenClass = 0;
        this.totalWeightObserved = 0;
    }

    @Override
    public void addResult(Example<Instance> example, double[] classVotes) {
        Instance inst = example.getData();
        double weight = inst.weight();
        if (inst.classIsMissing() == false){
            int trueClass = (int) inst.classValue();
            int predictedClass = Utils.maxIndex(classVotes);
            if (weight > 0.0) {
                if (this.totalWeightObserved == 0) {
                    reset(inst.dataset().numClasses());
                }
                this.totalWeightObserved += weight;//weight eh o numero da instancia
                this.weightCorrect.add(predictedClass == trueClass ? weight : 0);
                for (int i = 0; i < this.numClasses; i++) {
                    this.rowKappa[i].add(predictedClass == i ? weight: 0);
                    this.columnKappa[i].add(trueClass == i ? weight: 0);
                    if(trueClass == i){
                        precision[i].add(predictedClass == trueClass ? weight: 0);
                    }
                    if(predictedClass == i){
                        recall[i].add(predictedClass == trueClass ? weight: 0);
                    }
                    
                }
            }
            this.weightCorrectNoChangeClassifier.add(this.lastSeenClass == trueClass ? weight: 0);
            this.weightMajorityClassifier.add(getMajorityClass() == trueClass ? weight: 0);
            this.lastSeenClass = trueClass;
        }
    }

    private int getMajorityClass() {
        int majorityClass = 0;
        double maxProbClass = 0.0;
        for (int i = 0; i < this.numClasses; i++) {
            if (this.columnKappa[i].estimation() > maxProbClass) {
                majorityClass = i;
                maxProbClass = this.columnKappa[i].estimation();
            }
        }
        return majorityClass;
    }

    @Override
    public Measurement[] getPerformanceMeasurements() {
        List<Measurement> measurements = new ArrayList<>();
        measurements.add( new Measurement("classified instances",
                              getTotalWeightObserved()) );
        measurements.add( new Measurement("classifications correct (percent)",
                              getFractionCorrectlyClassified() * 100.0) );
        measurements.add( new Measurement("Kappa Statistic (percent)",
                              getKappaStatistic() * 100.0) );
        measurements.add( new Measurement("Kappa Temporal Statistic (percent)",
                              getKappaTemporalStatistic() * 100.0) );
        measurements.add( new Measurement("Kappa M Statistic (percent)",
                              getKappaMStatistic() * 100.0) );
        measurements.add( new Measurement("Precision (percent)",
                              getPrecisionStatistic() * 100.0) );
        for(int i = 0; i < this.numClasses; i++){
            measurements.add( new Measurement("Precision for class " + i + " (percent)",
                                  getPrecisionStatistic(i) * 100.0) );
        }
        measurements.add( new Measurement("Recall (percent)",
                              getRecallStatistic() * 100.0) );
        for(int i = 0; i < this.numClasses; i++){
            measurements.add( new Measurement("Recall for class " + i + " (percent)",
                                  getRecallStatistic(i) * 100.0) );
        }
        Measurement[] result = new Measurement[measurements.size()];
        
        return measurements.toArray(result);

    }

    public double getTotalWeightObserved() {
        return this.totalWeightObserved;
    }

    public double getFractionCorrectlyClassified() {
        return this.weightCorrect.estimation();
    }

    public double getFractionIncorrectlyClassified() {
        return 1.0 - getFractionCorrectlyClassified();
    }

    public double getKappaStatistic() {
        if (this.getTotalWeightObserved() > 0.0) {
            double p0 = getFractionCorrectlyClassified();
            double pc = 0.0;
            for (int i = 0; i < this.numClasses; i++) {
                pc += this.rowKappa[i].estimation()
                        * this.columnKappa[i].estimation();
            }
            return (p0 - pc) / (1.0 - pc);
        } else {
            return 0;
        }
    }

    public double getKappaTemporalStatistic() {
        if (this.getTotalWeightObserved() > 0.0) {
            double p0 = getFractionCorrectlyClassified();
            double pc = this.weightCorrectNoChangeClassifier.estimation();

            return (p0 - pc) / (1.0 - pc);
        } else {
            return 0;
        }
    }

    public double getKappaMStatistic() {
        if (this.getTotalWeightObserved() > 0.0) {
            double p0 = getFractionCorrectlyClassified();
            double pc = this.weightMajorityClassifier.estimation();

            return (p0 - pc) / (1.0 - pc);
        } else {
            return 0;
        }
    }
    
    public double getPrecisionStatistic(){
        double total = 0;
        for (Estimator ck : this.precision) {
            total += ck.estimation();
        }
        return total/this.precision.length;
    }
    
    public double getPrecisionStatistic(int numClass){
        return this.precision[numClass].estimation();
    }
    
    public double getRecallStatistic(){
        double total = 0;
        for (Estimator ck : this.recall) {
            total += ck.estimation();
        }
        return total/this.recall.length;
    }

    public double getRecallStatistic(int numClass){
        return this.recall[numClass].estimation();
    }
    
    @Override
    public void getDescription(StringBuilder sb, int indent) {
        Measurement.getMeasurementsDescription(getPerformanceMeasurements(),
                sb, indent);
    }



	@Override
	public void addResult(Example<Instance> testInst, Prediction prediction) {
		// TODO Auto-generated method stub
		
	}

    @Override
    protected void prepareForUseImpl(TaskMonitor monitor, ObjectRepository repository) {

    }

    public interface Estimator extends Serializable {

        void add(double value);

        double estimation();
    }

    public class BasicEstimator implements Estimator {

        protected double len;

        protected double sum;

        @Override
        public void add(double value) {
            sum += value;
            len++;
        }

        @Override
        public double estimation(){
            return sum/len;
        }

    }

    protected Estimator newEstimator() {
        return new BasicEstimator();
    }
}
