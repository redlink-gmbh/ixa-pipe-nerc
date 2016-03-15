/*
 * Copyright 2014 Rodrigo Agerri

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
package eus.ixa.ixa.pipe.sequence.features;

import java.util.List;

import eus.ixa.ixa.pipe.sequence.utils.Flags;

import opennlp.tools.util.featuregen.FeatureGeneratorAdapter;

/**
 * Adds bigram features based on tokens and token class using {@code TokenClassFeatureGenerator}.
 * @author ragerri
 *
 */
public class BigramClassFeatureGenerator extends FeatureGeneratorAdapter {

  public void createFeatures(List<String> features, String[] tokens, int index, String[] previousOutcomes) {
    String wc = TokenClassFeatureGenerator.tokenShapeFeature(tokens[index]);
    //bi-gram features 
    if (index > 0) {
      features.add("pw,w=" + tokens[index-1] + "," + tokens[index]);
      String pwc = TokenClassFeatureGenerator.tokenShapeFeature(tokens[index - 1]);
      features.add("pwc,wc=" + pwc + "," + wc);
      if (Flags.DEBUG) {
        System.err.println("-> " + tokens[index] + ": pw,w=" + tokens[index-1] + "," + tokens[index]);
        System.err.println("-> " + tokens[index] + ": pwc,wc=" + pwc + "," + wc);
      }
    }
    if (index + 1 < tokens.length) {
      features.add("w,nw=" + tokens[index] + "," + tokens[index + 1]);
      String nwc = TokenClassFeatureGenerator.tokenShapeFeature(tokens[index + 1]);
      features.add("wc,nc=" + wc + "," + nwc);
      if (Flags.DEBUG) {
        System.err.println("-> " + tokens[index] + ": w,nw=" + tokens[index] + "," + tokens[index + 1]);
        System.err.println("-> " + tokens[index] + ": wc,nc=" + wc + "," + nwc);
      }
    }
  } 
}
