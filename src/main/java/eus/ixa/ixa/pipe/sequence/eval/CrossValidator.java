/*
 *  Copyright 2014 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package eus.ixa.ixa.pipe.sequence.eval;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.EvaluationMonitor;
import eus.ixa.ixa.pipe.sequence.BilouCodec;
import eus.ixa.ixa.pipe.sequence.BioCodec;
import eus.ixa.ixa.pipe.sequence.SequenceEvaluationErrorListener;
import eus.ixa.ixa.pipe.sequence.SequenceLabelerCrossValidator;
import eus.ixa.ixa.pipe.sequence.SequenceLabelerDetailedFMeasureListener;
import eus.ixa.ixa.pipe.sequence.SequenceLabelerEvaluationMonitor;
import eus.ixa.ixa.pipe.sequence.SequenceLabelerFactory;
import eus.ixa.ixa.pipe.sequence.SequenceSample;
import eus.ixa.ixa.pipe.sequence.SequenceSampleTypeFilter;
import eus.ixa.ixa.pipe.sequence.features.XMLFeatureDescriptor;
import eus.ixa.ixa.pipe.sequence.nerc.train.AbstractTrainer;
import eus.ixa.ixa.pipe.sequence.nerc.train.DefaultTrainer;
import eus.ixa.ixa.pipe.sequence.utils.Flags;

/**
 * Abstract class for common training functionalities. Every other trainer class
 * needs to extend this class.
 * @author ragerri
 * @version 2014-04-17
 */
public class CrossValidator {
  
  /**
   * The language.
   */
  private String lang;
  /**
   * String holding the training data.
   */
  private String trainData;
  /**
   * ObjectStream of the training data.
   */
  private ObjectStream<SequenceSample> trainSamples;
  /**
   * beamsize value needs to be established in any class extending this one.
   */
  private int beamSize;
  /**
   * The folds value for cross validation.
   */
  private int folds;
  /**
   * The sequence encoding of the named entity spans, e.g., BIO or BILOU.
   */
  private SequenceCodec<String> sequenceCodec;
  /**
   * The corpus format: conll02, conll03.
   */
  private String corpusFormat;
  /**
   * features needs to be implemented by any class extending this one.
   */
  private SequenceLabelerFactory nameClassifierFactory;
  /**
   * The evaluation listeners.
   */
  private List<EvaluationMonitor<SequenceSample>> listeners = new LinkedList<EvaluationMonitor<SequenceSample>>();
  SequenceLabelerDetailedFMeasureListener detailedFListener;

  
  public CrossValidator(final TrainingParameters params) throws IOException {
    
    this.lang = Flags.getLanguage(params);
    String clearFeatures = Flags.getClearTrainingFeatures(params);
    this.corpusFormat = Flags.getCorpusFormat(params);
    this.trainData = params.getSettings().get("TrainSet");
    trainSamples = AbstractTrainer.getNameStream(trainData, clearFeatures, corpusFormat);
    this.beamSize = Flags.getBeamsize(params);
    this.folds = Flags.getFolds(params);
    this.sequenceCodec =  SequenceLabelerFactory.instantiateSequenceCodec(getSequenceCodec(Flags.getSequenceCodec(params)));
    if (params.getSettings().get("Types") != null) {
      String netypes = params.getSettings().get("Types");
      String[] neTypes = netypes.split(",");
      trainSamples = new SequenceSampleTypeFilter(neTypes, trainSamples);
    }
    createNameFactory(params);
    getEvalListeners(params);
  }

  private void createNameFactory(TrainingParameters params) throws IOException {
    String featureDescription = XMLFeatureDescriptor
        .createXMLFeatureDescriptor(params);
    System.err.println(featureDescription);
    byte[] featureGeneratorBytes = featureDescription.getBytes(Charset
        .forName("UTF-8"));
    Map<String, Object> resources = DefaultTrainer.loadResources(params, featureGeneratorBytes);
    this.nameClassifierFactory = SequenceLabelerFactory.create(
        SequenceLabelerFactory.class.getName(), featureGeneratorBytes,
        resources, sequenceCodec);
  }
  
  private void getEvalListeners(TrainingParameters params) {
    if (params.getSettings().get("EvaluationType").equalsIgnoreCase("error")) {
      listeners.add(new SequenceEvaluationErrorListener());
    }
    if (params.getSettings().get("EvaluationType").equalsIgnoreCase("detailed")) {
      detailedFListener = new SequenceLabelerDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }
  }
  
  public final void crossValidate(final TrainingParameters params) {
    if (nameClassifierFactory == null) {
      throw new IllegalStateException(
          "Classes derived from AbstractNameFinderTrainer must create and fill the AdaptiveFeatureGenerator features!");
    }
    SequenceLabelerCrossValidator validator = null;
    try {
      validator = new SequenceLabelerCrossValidator(lang,
          null, params, nameClassifierFactory,
          listeners.toArray(new SequenceLabelerEvaluationMonitor[listeners.size()]));
      validator.evaluate(trainSamples, folds);
    } catch (IOException e) {
      System.err.println("IO error while loading training set!");
      e.printStackTrace();
      System.exit(1);
    } finally {
      try {
        trainSamples.close();
      } catch (IOException e) {
        System.err.println("IO error with the train samples!");
      }
    }
    if (detailedFListener == null) {
      System.out.println(validator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }
  
  /**
   * Get the Sequence codec.
   * @param seqCodecOption the codec chosen
   * @return the sequence codec
   */
  public final String getSequenceCodec(String seqCodecOption) {
    String seqCodec = null;
    if ("BIO".equals(seqCodecOption)) {
      seqCodec = BioCodec.class.getName();
    }
    else if ("BILOU".equals(seqCodecOption)) {
      seqCodec = BilouCodec.class.getName();
    }
    return seqCodec;
  }
  
  public final int getBeamSize() {
    return beamSize;
  }

}

