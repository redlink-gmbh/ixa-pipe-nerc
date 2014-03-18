/*
 *  Copyright 2013 Rodrigo Agerri

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

package ixa.pipe.nerc;

import ixa.kaflib.KAFDocument;
import ixa.kaflib.Term;
import ixa.kaflib.WF;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

/**
 * @author ragerri
 * 
 */
public class Annotate {
 
  private NameFinder nameFinder;
  private GazetteerNameFinder perDictFinder;
  private GazetteerNameFinder orgDictFinder;
  private GazetteerNameFinder locDictFinder;
  private static final boolean DEBUG = true;
  private boolean STATISTICAL;
  private boolean POSTPROCESS; 
  private boolean DICTTAG;
  
  public Annotate(String cmdOption) {
    Models modelRetriever = new Models();
    InputStream nerModel = modelRetriever.getNERModel(cmdOption);
    NameFactory nameFactory = new NameFactory();
    nameFinder = new StatisticalNameFinder(nerModel,nameFactory);
    STATISTICAL = true;
  }
  
  public Annotate(String cmdOption, String gazetteerOption) {
    NameFactory nameFactory = new NameFactory();
    Models modelRetriever = new Models();
    InputStream nerModel = modelRetriever.getNERModel(cmdOption);
    nameFinder = new StatisticalNameFinder(nerModel,nameFactory);
    perDictFinder = createDictNameFinder("en-wikipeople.lst","PERSON",nameFactory);
    orgDictFinder = createDictNameFinder("en-wikiorganization.lst","ORGANIZATION",nameFactory);
    locDictFinder = createDictNameFinder("en-wikilocation.lst","LOCATION",nameFactory);
    if (gazetteerOption.equalsIgnoreCase("post")) { 
      POSTPROCESS = true;
      STATISTICAL = true;
    }
    if (gazetteerOption.equalsIgnoreCase("tag")) {
      DICTTAG = true;
      STATISTICAL = false;
      POSTPROCESS = false;
    }
  }
  
  public void annotateNEsToKAF(KAFDocument kaf)
      throws IOException {
    
    List<Span> allSpans = new ArrayList<Span>();
    List<List<WF>> sentences = kaf.getSentences();
    for (List<WF> sentence : sentences) {
      String[] tokens = new String[sentence.size()];
      String[] tokenIds = new String[sentence.size()];
      for (int i = 0; i < sentence.size(); i++) {
        tokens[i] = sentence.get(i).getForm();
        tokenIds[i] = sentence.get(i).getId();
      }
      if (STATISTICAL) {
        allSpans = nameFinder.nercToSpans(tokens);
      }
      if (POSTPROCESS) {
        List<Span> perDictSpans = perDictFinder.nercToSpans(tokens);
        List<Span> orgDictSpans = orgDictFinder.nercToSpans(tokens);
        List<Span> locDictSpans = locDictFinder.nercToSpans(tokens);
        perDictFinder.concatenateSpans(perDictSpans,orgDictSpans);
        perDictFinder.concatenateSpans(perDictSpans,locDictSpans);
        //TODO deal with post-processing better
        List<Span> dictSpans = perDictFinder.concatenateNoOverlappingSpans(allSpans, perDictSpans);
        allSpans = dictSpans;
      }
      if (DICTTAG) {
        allSpans = perDictFinder.nercToSpans(tokens);
        List<Span> orgDictSpans = orgDictFinder.nercToSpans(tokens);
        List<Span> locDictSpans = locDictFinder.nercToSpans(tokens);
        perDictFinder.concatenateSpans(allSpans,orgDictSpans);
        perDictFinder.concatenateSpans(allSpans,locDictSpans);
      }
      Span[] allSpansArray = NameFinderME.dropOverlappingSpans(allSpans.toArray(new Span[allSpans.size()]));
      List<Name> names = nameFinder.getNamesFromSpans(allSpansArray, tokens);
      for (Name name : names) {
        Integer start_index = name.getSpan().getStart();
        Integer end_index = name.getSpan().getEnd();
        List<Term> nameTerms = kaf.getTermsFromWFs(Arrays.asList(Arrays
            .copyOfRange(tokenIds, start_index, end_index)));
        List<List<Term>> references = new ArrayList<List<Term>>();
        references.add(nameTerms);
        kaf.createEntity(name.getType(), references);
      }
    }
  }
  
  public GazetteerNameFinder createDictNameFinder(String dictFile, String type, NameFactory nameFactory) { 
    InputStream dictStream = getClass().getResourceAsStream("/"+dictFile);
    Gazetteer dict = new Gazetteer(dictStream);
    GazetteerNameFinder dictNameFinder = new GazetteerNameFinder(dict,type,nameFactory);
    return dictNameFinder;
  }
  
}
  